package migration4o.migration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.recipes.ExportCriteriaFilter;
import migration4o.migration.recipes.GenericObjectExporter;
import migration4o.migration.recipes.ObjectActivator;
import migration4o.migration.recipes.SchemaElementMapper;
import migration4o.models.schema.DOSchemaClass;

/**
 * Orchestrates recursive object traversal and export to XML. Now delegates to
 * specialized components for schema lookups, field exports, and reference
 * resolution.
 */
public class ObjectExporter {
    private final FieldExporter fieldExporter;
    private final ExportCurrentState ctx;
    private final FormatHandler handler;
    /**
     * Tracks objects currently on the export call stack to detect circular
     * references.
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
     * New-path export: uses FormatHandler hooks for dedup, schema observation,
     * and content writing. Called from ObjectExportLoop (new path) and from
     * XmlFormatHandler.done() for the unreached-objects pass.
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
            ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(ctx.request.container, objectId);
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
                if (!ExportCriteriaFilter.shouldExport(ctx.request.container, obj, className, false, true, ctx.exportConfig, ctx.statistics, ctx.request.referenceSchema)) {
                    return;
                }
            }

            // Mark as exported only after criteria pass
            if (!isEmbedded)
                handler.exportedIds.add(objectId);

            DOSchemaClass schemaClass = SchemaElementMapper.getSchemaClass(className, ctx.request.referenceSchema);
            String elementName = schemaClass != null ? schemaClass.attributes.destinationName : SchemaElementMapper.getElementName(className, ctx.request.referenceSchema);

            ctx.schemaClass = schemaClass;
            ctx.pushObject(obj, objectId);
            try {
                boolean handled = handler.onObject(ctx);
                if (!handled) {
                    try {
                        if (obj instanceof GenericObject && schemaClass != null) {
                            int fieldsToExport = GenericObjectExporter.countFieldsToExport(ctx.request.container, (GenericObject) obj, schemaClass, objectId, fieldExporter, ctx.request.referenceSchema);
                            if (fieldsToExport > 0) {
                                GenericObjectExporter.exportIfGenericObject(ctx.request.container, obj, schemaClass, objectId, fieldExporter, 0);
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
                        ctx.statistics.recordClassExport(schemaClass, objectId, ctx.request.referenceSchema);
                    } else {
                        ctx.statistics.recordReachedOnly(className, objectId, ctx.request.referenceSchema);
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
}
