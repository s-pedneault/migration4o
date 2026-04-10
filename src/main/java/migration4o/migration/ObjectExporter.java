package migration4o.migration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabaseDelegate;
import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.recipes.ExportCriteriaFilter;
import migration4o.migration.recipes.GenericObjectExporter;
import migration4o.migration.recipes.ObjectActivator;
import migration4o.migration.recipes.SchemaElementMapper;
import migration4o.models.schema.DOSchemaClass;

/**
 * Orchestrates recursive object traversal and export to XML. Now delegates to specialized components for schema lookups, field exports, and reference resolution.
 */
public class ObjectExporter {
    private final FieldExporter fieldExporter;
    private final ExportCurrentState ctx;
    private final FormatHandler handler;
    /**
     * Tracks objects currently on the export call stack to detect circular references.
     */
    private final Set<Long> inProgressIds = new HashSet<>();

    /**
     * Creates an object exporter that drives export via FormatHandler hooks.
     */
    public ObjectExporter(ExportCurrentState ctx, FormatHandler handler) {
        this.ctx = ctx;
        this.handler = handler;
        ctx.objectExporter = this;
        this.fieldExporter = new FieldExporter(ctx, handler, this);
    }

    /**
     * New-path export: uses FormatHandler hooks for dedup, schema observation, and content writing. Called from ObjectExportLoop (new path) and from XmlFormatHandler.done() for the unreached-objects pass.
     */
    public void exportObject(long objectId, boolean isEmbedded) throws IOException {
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
            // Use ctx.delegate directly — it is always set to the correct
            // delegate before each call:
            // • Root objects: ObjectExportLoop sets ctx.delegate = dbClass.delegate
            // • Embedded IDEntite targets: FieldExporter switches ctx.delegate
            // = resolved.delegate before recursing
            // • Regular embedded objects: come from the same delegate as parent
            // Using the multi-delegate DODatabase.getByID(objectId) here would
            // only search the user delegate (to avoid cross-database reads),
            // which silently drops static-DB objects.
            ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(ctx.delegate, objectId);
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
            if (!isEmbedded && ctx.request.applyExportCriteriaFilters && ctx.exportConfig != null) {
                if (!ExportCriteriaFilter.shouldExport(ctx.delegate, obj, className, false, true, ctx.exportConfig, ctx.statistics, ctx.request.referenceSchema)) {
                    // Deactivate filtered-out objects immediately
                    ctx.delegate.deactivate(obj, 1);
                    return;
                }
            }

            // Apply organization filter for root objects
            if (!isEmbedded && ctx.organizationFilter != null) {
                DOSchemaClass filterSchemaClass = SchemaElementMapper.getSchemaClass(className, ctx.request.referenceSchema);
                if (!ctx.organizationFilter.shouldExport(filterSchemaClass, ctx.delegate, obj, true)) {
                    ctx.delegate.deactivate(obj, 1);
                    if (ctx.statistics != null) {
                        ctx.statistics.incrementSkippedByOrganization();
                    }
                    return;
                }
            }

            // Mark as exported only after criteria and org filter passes
            if (!isEmbedded)
                handler.exportedIds.add(objectId);

            DOSchemaClass schemaClass = SchemaElementMapper.getSchemaClass(className, ctx.request.referenceSchema);

            // Skip objects whose schema class is excluded from export
            if (schemaClass != null && !schemaClass.attributes.migrate) {
                ctx.delegate.deactivate(obj, 1);
                return;
            }

            String elementName = schemaClass != null ? schemaClass.attributes.destinationName : SchemaElementMapper.getElementName(className, ctx.request.referenceSchema);

            ctx.schemaClass = schemaClass;
            ctx.pushObject(obj, objectId);
            try {
                // Skip GenericObjects that would produce empty XML elements
                // (zero exportable fields and not a collection/map with items).
                // Opening and closing a structure tag with no content produces
                // whitespace that violates XSD empty-type definitions.
                if (obj instanceof GenericObject && schemaClass != null && !schemaClass.isCollectionOrMap()) {
                    int preCount = GenericObjectExporter.countFieldsToExport(ctx.delegate, (GenericObject) obj, schemaClass, objectId, fieldExporter, ctx.request.referenceSchema);
                    if (preCount == 0) {
                        return;
                    }
                }

                // Skip native Java objects (non-GenericObject) whose schema
                // class has zero exported fields and is not a collection/map.
                // Examples: java.lang.Class → "Classe" has no fields at all.
                if (!(obj instanceof GenericObject) && schemaClass != null && !schemaClass.isCollectionOrMap()) {
                    if (countExportedFields(schemaClass) == 0) {
                        return;
                    }
                }

                // Skip native collection/map objects that are empty AND have
                // no method-call fields — they produce whitespace-only content
                // that violates XSD empty-type definitions.
                // Vector → Collection, Hashtable → Map after DB4O activation.
                if (!(obj instanceof GenericObject) && schemaClass != null && schemaClass.isCollectionOrMap()) {
                    boolean collectionEmpty = false;
                    if (obj instanceof java.util.Map) {
                        collectionEmpty = ((java.util.Map<?, ?>) obj).isEmpty();
                    } else if (obj instanceof java.util.Collection) {
                        collectionEmpty = ((java.util.Collection<?>) obj).isEmpty();
                    }
                    if (collectionEmpty && !hasMethodCallFields(schemaClass)) {
                        return;
                    }
                }

                boolean handled = handler.onObject(ctx);
                if (!handled) {
                    try {
                        // Collection/map items come first (wrapped in <items>)
                        // so the XSD extension chain works: the root collection
                        // type defines <items> in its base sequence, and
                        // subclass extensions append their own fields after.
                        if (schemaClass != null) {
                            if (schemaClass.isCollectionOrMap()) {
                                try {
                                    fieldExporter.exportStandaloneCollectionItems(ctx.delegate, obj, schemaClass, objectId);
                                } catch (Exception e) {
                                    System.err.println("[Export warning] object " + objectId + " (" + className + ") standalone collection export failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                                    e.printStackTrace(System.err);
                                }
                            }
                        }
                        // Schema-defined fields
                        if (obj instanceof GenericObject && schemaClass != null) {
                            int fieldsToExport = GenericObjectExporter.countFieldsToExport(ctx.delegate, (GenericObject) obj, schemaClass, objectId, fieldExporter, ctx.request.referenceSchema);
                            if (fieldsToExport > 0) {
                                GenericObjectExporter.exportIfGenericObject(ctx.delegate, obj, schemaClass, objectId, fieldExporter, 0);
                            }
                        } else if (!(obj instanceof GenericObject) && schemaClass != null) {
                            // Native Java object — export method-call fields via reflection
                            fieldExporter.exportMethodCallFields(ctx.delegate, obj, schemaClass, 0, schemaClass.attributes.destinationName, schemaClass.attributes.source, objectId);
                        }
                    } finally {
                        handler.writer.closeStructure(elementName);
                    }
                }
                if (ctx.statistics != null) {
                    ctx.statistics.incrementSucceeded();
                    ctx.statistics.recordExportedObjectId(objectId);
                    if (schemaClass != null) {
                        ctx.statistics.recordClassExport(schemaClass, objectId, ctx.request.referenceSchema);
                    } else {
                        ctx.statistics.recordReachedOnly(className, objectId, ctx.request.referenceSchema);
                    }
                }
            } catch (Throwable t) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                System.err.println("[Export error] object " + objectId + " (" + className + "): " + errorMsg);
                t.printStackTrace(System.err);
                if (ctx.statistics != null) {
                    Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(errorMsg, t);
                    ctx.statistics.addError(objectId, className, errorMsg, wrapped);
                }
            } finally {
                ctx.popObject();
                ctx.schemaClass = null;
                // Release the object's cached field values from DB4O's
                // reference cache. Without this, every exported object
                // stays fully activated in memory for the entire session.
                ctx.delegate.deactivate(obj, 1);
            }

        } finally {
            if (isEmbedded)
                inProgressIds.remove(objectId);
        }
    }

    /**
     * Counts the number of exported fields in a schema class (non-method-call fields only, since method-call fields are handled separately).
     */
    private static int countExportedFields(DOSchemaClass schemaClass) {
        if (schemaClass.fields == null)
            return 0;
        int count = 0;
        for (var f : schemaClass.fields) {
            if (f != null && f.attributes.isExported)
                count++;
        }
        return count;
    }

    /**
     * Returns true if the schema class has at least one method-call field (source ending with "()") that is exported. These fields invoke methods via reflection on native Java objects and may produce output even when the collection itself is empty.
     */
    private static boolean hasMethodCallFields(DOSchemaClass schemaClass) {
        if (schemaClass.fields == null)
            return false;
        for (var f : schemaClass.fields) {
            if (f != null && f.attributes.isExported && f.isMethodCallField())
                return true;
        }
        return false;
    }
}
