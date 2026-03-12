package migration4o.migration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.format.ExportContext;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.recipes.ExportCriteriaFilter;
import migration4o.migration.recipes.GenericObjectExporter;
import migration4o.migration.recipes.ObjectActivator;
import migration4o.migration.recipes.ObjectIdTracker;
import migration4o.migration.recipes.SchemaElementMapper;
import migration4o.migration.recipes.XMLErrorWriter;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.migration.SummaryGenerator;

/**
 * Orchestrates recursive object traversal and export to XML. Now delegates to
 * specialized components for schema lookups, field exports, and reference
 * resolution.
 */
public class ObjectExporter {
    private final ExportOperation operation;
    private final FieldExporter fieldExporter;
    private final StructuredWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    // New-path fields (null when using old constructor)
    private final ExportContext ctx;
    private final FormatHandler handler;
    /**
     * Tracks objects currently on the export call stack to detect circular
     * references.
     */
    private final Set<Long> inProgressIds = new HashSet<>();

    public ObjectExporter(ExportOperation operation, StructuredWriter xmlWriter, XSDBuilder xsdBuilder) {
        this.operation = operation;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.ctx = null;
        this.handler = null;
        // Set this exporter on the operation so FieldExporter can access it
        operation.objectExporter = this;
        // Create FieldExporter - it will access objectExporter via operation
        this.fieldExporter = new FieldExporter(operation, xmlWriter, xsdBuilder);
    }

    /**
     * New-path constructor: uses FormatHandler hooks instead of raw writer/XSD
     * builder.
     */
    public ObjectExporter(ExportContext ctx, FormatHandler handler) {
        this.ctx = ctx;
        this.handler = handler;
        this.operation = ctx.operation;
        this.xmlWriter = handler.writer;
        this.xsdBuilder = null;
        operation.objectExporter = this;
        this.fieldExporter = new FieldExporter(ctx, handler, this);
    }

    /**
     * New-path export: uses FormatHandler hooks for dedup, schema observation,
     * and content writing. Called from ObjectExportLoop (new path) and from
     * XmlFormatHandler.done() for the unreached-objects pass.
     */
    public void exportObject(long objectId, boolean isEmbedded) throws Exception {
        // For root objects: check without marking yet, so criteria-filtered
        // objects
        // are not consumed from exportedIds (they must remain available for
        // other
        // criteria-based config passes of the same class).
        if (!isEmbedded && handler.exportedIds.contains(objectId))
            return;
        if (ctx.allowedObjectIds != null && !ctx.allowedObjectIds.contains(objectId))
            return;
        // Cycle guard: embedded objects bypass exportedIds, so track the active
        // call stack separately
        if (isEmbedded && !inProgressIds.add(objectId))
            return;

        try {
            ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(ctx.operation.container, objectId);
            if (activation == null)
                return;

            // Only count root-level objects toward per-class progress so that
            // currentClassAttempted tracks the same thing as currentClassTotal
            // (both based on root objectIds). Embedded objects are part of the
            // root object's data, not separate entries in the progress bar.
            if (ctx.statistics != null && !isEmbedded) {
                ctx.statistics.incrementAttempted();
            }

            Object obj = activation.object;
            String className = activation.className;

            // Apply criteria filter for root objects before marking as
            // exported.
            // If filtered, return without adding to exportedIds so the next
            // criteria-based config pass can still pick up this object.
            if (!isEmbedded && ctx.operation.applyExportCriteriaFilters && ctx.exportConfig != null) {
                if (!ExportCriteriaFilter.shouldExport(ctx.operation.container, obj, className, false, true, ctx.exportConfig, ctx.statistics, ctx.operation.referenceSchema)) {
                    return;
                }
            }

            // Mark as exported only after criteria pass
            if (!isEmbedded)
                handler.exportedIds.add(objectId);

            DOSchemaClass schemaClass = SchemaElementMapper.getSchemaClass(className, ctx.operation.referenceSchema);
            String elementName = schemaClass != null ? schemaClass.destinationName : SchemaElementMapper.getElementName(className, ctx.operation.referenceSchema);

            ctx.schemaClass = schemaClass;
            ctx.pushObject(obj, objectId);
            try {
                handler.observeObject(ctx);
                boolean handled = handler.onObject(ctx);
                if (!handled) {
                    try {
                        if (obj instanceof GenericObject && schemaClass != null) {
                            int fieldsToExport = GenericObjectExporter.countFieldsToExport(ctx.operation.container, (GenericObject) obj, schemaClass, objectId, fieldExporter, ctx.operation.referenceSchema);
                            if (fieldsToExport > 0) {
                                GenericObjectExporter.exportIfGenericObject(ctx.operation.container, obj, schemaClass, objectId, fieldExporter, 0);
                            }
                        }
                    } finally {
                        handler.writer.closeStructure(elementName);
                    }
                }
                if (ctx.statistics != null) {
                    ctx.statistics.incrementSucceeded();
                    ctx.statistics.recordExportedObjectId(objectId);
                    if (schemaClass != null) {
                        ctx.statistics.recordClassExport(schemaClass, objectId, ctx.operation.referenceSchema);
                    } else {
                        ctx.statistics.recordReachedOnly(className, objectId, ctx.operation.referenceSchema);
                    }
                }
            } catch (Throwable t) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                System.err.println("[Export error] object " + objectId + " (" + className + "): " + errorMsg);
                if (ctx.statistics != null) {
                    Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(errorMsg, t);
                    ctx.statistics.addError(objectId, className, errorMsg, wrapped);
                }
            } finally {
                ctx.popObject();
                ctx.schemaClass = null;
            }
        } finally {
            if (isEmbedded)
                inProgressIds.remove(objectId);
        }
    }

    /**
     * Gets the reference tracker to query discovered classes.
     *
     * @return the reference tracker
     */
    public ReferencedClassTracker getReferencedClassTracker() {
        return operation.referencedClassTracker;
    }

    /**
     * Resets the state for a new export operation. Only clears state if not
     * using shared tracking.
     */
    public void reset() {
        if (!operation.useSharedTracking) {
            operation.exportedObjectIds.clear();
        }
        if (operation.referencedClassTracker != null) {
            operation.referencedClassTracker.reset();
        }
    }

    /**
     * Recursively exports an object and all its referenced objects. This is the
     * main entry point - assumes objects are NOT embedded by default. For root
     * objects (those directly from a class's objectIds array), the shared
     * deduplication set is NOT checked, allowing the same object to be exported
     * in multiple criteria-based exports of the same class.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel) throws IOException {
        exportObjectRecursively(container, objectId, indentLevel, false, null, null, null, null, true, null);
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * 
     * @param isEmbedded true if this object is embedded in a parent field (not
     * a top-level export)
     * @param fieldName the name of the field this object is embedded in (for
     * warning messages)
     * @param containingClassName the name of the class that contains the field
     * (for warning messages)
     * @param sourceFieldName the source field name from schema (e.g.,
     * mVectCompartiment)
     * @param sourceContainingClassName the source class name from schema (e.g.,
     * gest.vehicule.Vehicule)
     * @param isRootObject true if this is a root object (from class's
     * objectIds), false if referenced
     * @param parentObjectId the ID of the parent object containing the field
     * that references this object
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel, boolean isEmbedded, String fieldName, String containingClassName, String sourceFieldName, String sourceContainingClassName, boolean isRootObject, Long parentObjectId) throws IOException {

        // New-path bridge: when running under a FormatHandler, delegate to
        // exportObject
        if (handler != null) {
            try {
                exportObject(objectId, isEmbedded);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
            return;
        }

        if (operation.allowedObjectIds != null && !operation.allowedObjectIds.contains(objectId)) {
            if (operation.statistics != null) {
                String effectiveClass = sourceContainingClassName != null ? sourceContainingClassName : "Unknown";
                operation.statistics.recordObjectDecision(objectId, effectiveClass, "not exported (outside allowed object set)");
            }
            return;
        }

        // Check if this object should be exported (handles duplicate tracking
        // and
        // statistics)
        if (!ObjectIdTracker.shouldExport(container, objectId, isRootObject, isEmbedded, operation.exportedObjectIds, operation.statistics, parentObjectId, sourceContainingClassName, sourceFieldName)) {
            // Object already exported - just return (warnings will be generated
            // at end)
            return;
        }

        if (operation.statistics != null) {
            operation.statistics.incrementAttempted();
        }

        // Get and activate the object
        ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(container, objectId);
        if (activation == null) {
            return;
        }

        Object obj = activation.object;
        String className = activation.className;

        try {

            // Apply export criteria filtering (only for top-level objects)
            boolean wouldBeFilteredByCriteria = false;
            if (operation.applyExportCriteriaFilters) {
                if (!ExportCriteriaFilter.shouldExport(container, obj, className, isEmbedded, isRootObject, operation.exportConfig, operation.statistics, operation.referenceSchema)) {
                    return;
                }
            } else {
                wouldBeFilteredByCriteria = !ExportCriteriaFilter.shouldExport(container, obj, className, isEmbedded, isRootObject, operation.exportConfig, null, operation.referenceSchema);
            }

            // Get schema class and element name
            DOSchemaClass schemaClass = SchemaElementMapper.getSchemaClass(className, operation.referenceSchema);
            String elementName = SchemaElementMapper.getElementName(className, operation.referenceSchema);

            // XSD: record this class structure
            if (schemaClass != null) {
                xsdBuilder.addClass(schemaClass);
            }

            // First, determine if there are any fields to export (dry run)
            int fieldsToExport = GenericObjectExporter.countFieldsToExport(container, obj, schemaClass, objectId, fieldExporter, operation.referenceSchema);

            // Only write object tags when object-level exclusion allows it
            boolean wouldBeSkippedWithoutExportableFields = !operation.skipObjectsWithoutExportableFields && fieldsToExport == 0;

            if (!operation.skipObjectsWithoutExportableFields || fieldsToExport > 0) {
                boolean openedStructure = false;
                // Write start element with optional object ID at/tribute
                try {
                    Map<String, String> attributes = new java.util.LinkedHashMap<>();
                    if (operation.exportNativeIds) {
                        attributes.put("id", objectId + "");
                    }
                    java.util.List<String> skippedBecauseReasons = new ArrayList<>();
                    if (wouldBeFilteredByCriteria) {
                        skippedBecauseReasons.add("export criteria filter");
                    }
                    if (wouldBeSkippedWithoutExportableFields) {
                        skippedBecauseReasons.add("no exportable fields");
                    }
                    if (!skippedBecauseReasons.isEmpty()) {
                        attributes.put("skippedBecause", String.join("; ", skippedBecauseReasons));
                    }
                    // For JS exports: embed a server-side summary when the
                    // schema class has a summary template
                    if ("JS".equalsIgnoreCase(operation.outputFormat) && schemaClass != null && schemaClass.summary != null && !schemaClass.summary.isEmpty()) {
                        String summaryValue = SummaryGenerator.generate(container, obj, schemaClass, operation.referenceSchema);
                        if (summaryValue != null && !summaryValue.isBlank()) {
                            attributes.put("_summary", summaryValue);
                        }
                    }
                    // For JS exports: when exporting an IDEntite as a
                    // structure, replace the
                    // nested object with a flat human-readable label, stripping
                    // the "ID" prefix
                    // from the element name so the column reads as the entity
                    // name, not the ID class.
                    if ("JS".equalsIgnoreCase(operation.outputFormat) && schemaClass != null && schemaClass.isIDEntite(operation.databaseSchema)) {
                        String refLabel = SummaryGenerator.resolveIDEntiteLabel(container, obj, schemaClass, operation.referenceSchema, operation.databaseSchema, operation.idEntiteTargetCache, operation.idEntiteSummaryCache);
                        if (refLabel != null && !refLabel.isBlank()) {
                            String displayName = stripIdPrefix(elementName);
                            xmlWriter.elementWithContent(displayName, attributes.isEmpty() ? null : attributes, refLabel, false);
                            // Written as flat content — skip the structure
                            // open/field export/close below
                        } else {
                            xmlWriter.openStructure(elementName, attributes.isEmpty() ? null : attributes);
                            openedStructure = true;
                            if (fieldsToExport > 0) {
                                GenericObjectExporter.exportIfGenericObject(container, obj, schemaClass, objectId, fieldExporter, indentLevel);
                            }
                        }
                    } else {
                        xmlWriter.openStructure(elementName, attributes.isEmpty() ? null : attributes);
                        openedStructure = true;

                        // Now actually export the fields
                        if (fieldsToExport > 0) {
                            GenericObjectExporter.exportIfGenericObject(container, obj, schemaClass, objectId, fieldExporter, indentLevel);
                        }
                    }
                } finally {
                    if (openedStructure) {
                        xmlWriter.closeStructure(elementName);
                    }
                }
            }
            // If no fields, skip XML output but still count as successfully
            // processed

            // Record statistics for both empty and non-empty objects
            // Empty objects were reached and processed, just had no exportable
            // fields
            if (operation.statistics != null) {
                operation.statistics.incrementSucceeded();
                operation.statistics.recordExportedObjectId(objectId);
                if (schemaClass != null) {
                    operation.statistics.recordClassExport(schemaClass, objectId, operation.referenceSchema);
                } else {
                    operation.statistics.recordReachedOnly(className, objectId, operation.referenceSchema);
                }
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (operation.statistics != null) {
                operation.statistics.addError(objectId, className, errorMsg, e);
                // Object was seen (activated) even though export failed — mark
                // as reached
                operation.statistics.recordReachedOnly(className, objectId, operation.referenceSchema);
            }
        }
    }

    /**
     * Strips a leading "id" or "ID" prefix (optionally followed by a space)
     * from an element name, lowercasing the new first character if it was
     * uppercase. E.g. "IDTypeChampPerso" → "typeChampPerso", "id type" →
     * "type".
     */
    private static String stripIdPrefix(String name) {
        if (name == null || name.isEmpty())
            return name;
        String stripped = name.replaceFirst("(?i)^id\\s*", "");
        if (stripped.isEmpty() || stripped.equals(name))
            return name;
        return Character.isUpperCase(stripped.charAt(0)) ? Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1) : stripped;
    }

}
