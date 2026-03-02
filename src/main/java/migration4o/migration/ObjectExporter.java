package migration4o.migration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import com.db4o.ext.ExtObjectContainer;

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

    public ObjectExporter(ExportOperation operation, StructuredWriter xmlWriter, XSDBuilder xsdBuilder) {
        this.operation = operation;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        // Set this exporter on the operation so FieldExporter can access it
        operation.objectExporter = this;
        // Create FieldExporter - it will access objectExporter via operation
        this.fieldExporter = new FieldExporter(operation, xmlWriter, xsdBuilder);
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
     * Resets the state for a new export operation. Only clears state if not using
     * shared tracking.
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
     * deduplication set is NOT checked, allowing the same object to be exported in
     * multiple criteria-based exports of the same class.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel) throws IOException {
        exportObjectRecursively(container, objectId, indentLevel, false, null, null, null, null, true, null);
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * 
     * @param isEmbedded                true if this object is embedded in a parent
     *                                  field (not a top-level export)
     * @param fieldName                 the name of the field this object is
     *                                  embedded in (for warning messages)
     * @param containingClassName       the name of the class that contains the
     *                                  field (for warning messages)
     * @param sourceFieldName           the source field name from schema (e.g.,
     *                                  mVectCompartiment)
     * @param sourceContainingClassName the source class name from schema (e.g.,
     *                                  gest.vehicule.Vehicule)
     * @param isRootObject              true if this is a root object (from class's
     *                                  objectIds), false if referenced
     * @param parentObjectId            the ID of the parent object containing the
     *                                  field that references this object
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel, boolean isEmbedded, String fieldName, String containingClassName, String sourceFieldName, String sourceContainingClassName, boolean isRootObject, Long parentObjectId) throws IOException {

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
                    // For JS exports: embed a server-side summary when the schema class has a summary template
                    if ("JS".equalsIgnoreCase(operation.outputFormat) && schemaClass != null && schemaClass.summary != null && !schemaClass.summary.isEmpty()) {
                        String summaryValue = SummaryGenerator.generate(container, obj, schemaClass, operation.referenceSchema);
                        if (summaryValue != null && !summaryValue.isBlank()) {
                            attributes.put("_summary", summaryValue);
                        }
                    }
                    // For JS exports: when exporting an IDEntite as a structure, replace the
                    // nested object with a flat human-readable label, stripping the "ID" prefix
                    // from the element name so the column reads as the entity name, not the ID class.
                    if ("JS".equalsIgnoreCase(operation.outputFormat) && schemaClass != null && schemaClass.isIDEntite(operation.databaseSchema)) {
                        String refLabel = SummaryGenerator.resolveIDEntiteLabel(container, obj, schemaClass, operation.referenceSchema, operation.databaseSchema);
                        if (refLabel != null && !refLabel.isBlank()) {
                            String displayName = stripIdPrefix(elementName);
                            xmlWriter.elementWithContent(displayName, attributes.isEmpty() ? null : attributes, refLabel, false);
                            // Written as flat content — skip the structure open/field export/close below
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
                // Object was seen (activated) even though export failed — mark as reached
                operation.statistics.recordReachedOnly(className, objectId, operation.referenceSchema);
            }
        }
    }

    /**
     * Strips a leading "id" or "ID" prefix (optionally followed by a space) from an
     * element name, lowercasing the new first character if it was uppercase.
     * E.g. "IDTypeChampPerso" → "typeChampPerso",  "id type" → "type".
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
