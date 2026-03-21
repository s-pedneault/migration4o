package migration4o.migration.tasks;

import migration4o.database.DODatabaseClass;
import migration4o.migration.ExportRequest;
import migration4o.migration.ObjectExporter;
import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.models.schema.DOSchemaClass;

/**
 * Runs the per-class object export loop.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Registers the class in the XSD builder.
 * <li>Fires {@code onClassStart} / {@code onClassComplete} progress callbacks.
 * <li>Iterates over the class's object IDs, respecting
 * {@link ExportOperation#maxObjectsPerClass} and cancellation.
 * <li>Calls {@link ObjectExporter#exportObject} for each ID.
 * </ul>
 */
public class ObjectExportLoop {

    private final ExportRequest request;
    // New-path fields (null when using old constructor)
    private final ExportCurrentState ctx;
    private final FormatHandler handler;

    /** New-path constructor: drives the object loop via FormatHandler hooks. */
    public ObjectExportLoop(ExportCurrentState ctx, FormatHandler handler) {
        this.ctx = ctx;
        this.handler = handler;
        this.request = ctx.request;
    }

    /**
     * New-path variant: iterates {@code dbClass.objects.objectIds} and exports
     * each via {@link ObjectExporter#exportObject}. The reference schema class
     * is taken from {@code ctx.schemaClass} (set by the caller before invoking
     * this method).
     *
     * @param dbClass Database class (carries object IDs)
     */
    public void run(DODatabaseClass dbClass) throws Exception {
        // Use pre-computed smart selection when available for this class
        long[] objectIds = dbClass.objects.objectIds;
        if (request.preselectedObjectIds != null) {
            long[] preselected = request.preselectedObjectIds.get(dbClass.attributes.source);
            if (preselected != null) {
                objectIds = preselected;
                if (dbClass.attributes.source.contains("DossPrev")) {
                    System.out.println("[DEBUG-DossPrev] ObjectExportLoop: preselection for '" + dbClass.attributes.source + "': " + preselected.length + " objects (was " + (dbClass.objects.objectIds != null ? dbClass.objects.objectIds.length : 0) + ")");
                }
            }
        }
        int objectCount = (objectIds != null ? objectIds.length : 0);

        // Number of leading IDs in objectIds that are "required" (seed-matched,
        // closure-driven) and must be exported regardless of the cap.
        int requiredCount = 0;
        if (request.preselectedRequiredCounts != null) {
            Integer rc = request.preselectedRequiredCounts.get(dbClass.attributes.source);
            if (rc != null)
                requiredCount = rc;
        }

        // Compute the true expected export count: the greater of the cap and
        // the required count (required objects bypass the cap).
        int actualCount;
        if (request.maxObjectsPerClass != null && objectCount > request.maxObjectsPerClass) {
            actualCount = Math.max(request.maxObjectsPerClass, requiredCount);
        } else {
            actualCount = objectCount;
        }

        // Snapshot the loop class now — exportObject nulls ctx.schemaClass in
        // its finally block
        migration4o.models.schema.DOSchemaClass loopClass = ctx.schemaClass;

        if (request.monitor != null && loopClass != null) {
            request.monitor.onClassStart(loopClass.attributes.source, loopClass.attributes.destinationName, actualCount, handler != null ? handler.displayName() : "");
        }

        if (objectIds != null) {
            if (ctx.statistics != null && loopClass != null) {
                ctx.statistics.setCurrentClass(loopClass.attributes.source, actualCount);
                ctx.statistics.setCurrentFormatName(handler != null ? handler.displayName() : "");
            }
            // Set the active delegate so all export code routes through this
            // class's database container (user DB or static DB).
            ctx.delegate = dbClass.delegate;
            ObjectExporter objectExporter = new ObjectExporter(ctx, handler);
            int exportedCount = 0;
            int idIndex = 0;
            String formatName = handler != null ? handler.displayName() : "";
            for (long objectId : objectIds) {
                if (request.monitor != null && request.monitor.isCancelled())
                    break;
                // Required objects (at the front of the ranked array) are
                // cap-exempt:
                // they are always exported to guarantee referential integrity.
                // Seed-fill and fallback objects are subject to the normal cap.
                boolean isRequired = idIndex < requiredCount;
                idIndex++;
                // Cap is applied per export file (per ClassExportConfig),
                // counting only
                // objects that actually passed criteria and were written to
                // this file.
                if (!isRequired && request.maxObjectsPerClass != null && exportedCount >= request.maxObjectsPerClass)
                    break;
                boolean wasAlreadyExported = handler != null && handler.exportedIds.contains(objectId);
                try {
                    objectExporter.exportObject(objectId, false);
                } catch (Throwable t) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    System.err.println("[Export error] skipping object " + objectId + ": " + msg);
                    if (ctx.statistics != null) {
                        Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
                        ctx.statistics.addError(objectId, loopClass != null ? loopClass.attributes.source : "unknown", msg, wrapped);
                    }
                    if (request.monitor != null) {
                        request.monitor.onObjectError(loopClass != null ? loopClass.attributes.source : "unknown", objectId, msg);
                    }
                }
                // Only count objects that were newly written to this file
                // (passed criteria
                // and were not already exported in a previous pass).
                // Criteria-filtered
                // objects are never added to handler.exportedIds, so they do
                // not reduce
                // the remaining cap for this destination file.
                boolean wasJustExported = handler != null && !wasAlreadyExported && handler.exportedIds.contains(objectId);
                if (wasJustExported) {
                    exportedCount++;
                    // Fire progress directly from the loop — this is the only
                    // place
                    // that knows the exact per-format count, class total, AND
                    // format
                    // name simultaneously, with no shared mutable state.
                    if (request.monitor != null && exportedCount % 10 == 0 && actualCount > 0) {
                        request.monitor.onObjectProgress(loopClass != null ? loopClass.attributes.source : "", loopClass != null ? loopClass.attributes.destinationName : "", exportedCount, actualCount, formatName);
                    }
                }
            }

        }

        if (request.monitor != null && loopClass != null) {
            int succeeded = ctx.statistics != null ? ctx.statistics.getUniqueExportedCount() : 0;
            request.monitor.onClassComplete(loopClass.attributes.source, succeeded, handler != null ? handler.displayName() : "");
        }
    }
}
