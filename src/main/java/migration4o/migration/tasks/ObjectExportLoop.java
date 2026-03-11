package migration4o.migration.tasks;

import migration4o.migration.ExportOperation;
import migration4o.migration.ObjectExporter;
import migration4o.migration.format.ExportContext;
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
 * <li>Calls {@link ObjectExporter#exportObjectRecursively} for each ID.
 * <li>Propagates newly discovered reference classes back to the shared
 * {@link migration4o.migration.monitoring.ReferencedClassTracker}.
 * </ul>
 */
public class ObjectExportLoop {

    private final ExportOperation operation;
    // New-path fields (null when using old constructor)
    private final ExportContext ctx;
    private final FormatHandler handler;

    public ObjectExportLoop(ExportOperation operation) {
        this.operation = operation;
        this.ctx = null;
        this.handler = null;
    }

    /** New-path constructor: drives the object loop via FormatHandler hooks. */
    public ObjectExportLoop(ExportContext ctx, FormatHandler handler) {
        this.ctx = ctx;
        this.handler = handler;
        this.operation = ctx.operation;
    }

    /**
     * Registers {@code dbSchemaClass} in the current XSD builder, then iterates
     * its object IDs and exports each one via {@code objectExporter}.
     *
     * @param schemaClass Reference-schema class (used for progress labels)
     * @param dbSchemaClass Database-schema class (carries object IDs)
     * @param objectExporter Ready-to-use exporter that writes to the current
     * writer
     */
    public void run(DOSchemaClass schemaClass, DOSchemaClass dbSchemaClass, ObjectExporter objectExporter) throws Exception {
        operation.xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);

        long[] objectIds = dbSchemaClass.objectIds;
        int objectCount = (objectIds != null ? objectIds.length : 0);
        int actualCount = (operation.maxObjectsPerClass != null && objectCount > operation.maxObjectsPerClass) ? operation.maxObjectsPerClass : objectCount;

        if (operation.monitor != null) {
            operation.monitor.onClassStart(schemaClass.source, schemaClass.destinationName, actualCount);
        }

        if (objectIds != null) {
            operation.statistics.setCurrentClass(schemaClass.source, actualCount);
            int exportedCount = 0;

            for (long objectId : objectIds) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;

                if (operation.maxObjectsPerClass != null && exportedCount >= operation.maxObjectsPerClass) {
                    if (operation.statistics != null) {
                        operation.statistics.recordObjectDecision(objectId, schemaClass.source, "not exported due to class limit maxObjectsPerClass=" + operation.maxObjectsPerClass);
                    }
                    break;
                }

                objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                exportedCount++;
            }
        }

        // Propagate newly discovered references back to the shared tracker
        if (operation.referencedClassTracker != null) {
            for (String className : objectExporter.getReferencedClassTracker().getReferencedClasses()) {
                operation.referencedClassTracker.registerReferencedClass(className);
            }
        }

        if (operation.monitor != null) {
            operation.monitor.onClassComplete(schemaClass.source, operation.statistics.getUniqueExportedCount());
        }
    }

    /**
     * New-path variant: iterates {@code dbSchemaClass.objectIds} and exports
     * each via {@link ObjectExporter#exportObject}. The reference schema class
     * is taken from {@code ctx.schemaClass} (set by the caller before invoking
     * this method).
     *
     * @param dbSchemaClass Database-schema class (carries object IDs)
     */
    public void run(DOSchemaClass dbSchemaClass) throws Exception {
        long[] objectIds = dbSchemaClass.objectIds;
        int objectCount = (objectIds != null ? objectIds.length : 0);
        int actualCount = (operation.maxObjectsPerClass != null && objectCount > operation.maxObjectsPerClass) ? operation.maxObjectsPerClass : objectCount;

        // Snapshot the loop class now — exportObject nulls ctx.schemaClass in
        // its finally block
        migration4o.models.schema.DOSchemaClass loopClass = ctx.schemaClass;

        if (operation.monitor != null && loopClass != null) {
            operation.monitor.onClassStart(loopClass.source, loopClass.destinationName, actualCount);
        }

        if (objectIds != null) {
            if (ctx.statistics != null && loopClass != null) {
                ctx.statistics.setCurrentClass(loopClass.source, actualCount);
            }
            ObjectExporter objectExporter = new ObjectExporter(ctx, handler);
            int exportedCount = 0;
            for (long objectId : objectIds) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;
                if (operation.maxObjectsPerClass != null && exportedCount >= operation.maxObjectsPerClass)
                    break;
                try {
                    objectExporter.exportObject(objectId, false);
                } catch (Throwable t) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    System.err.println("[Export error] skipping object " + objectId + ": " + msg);
                    if (ctx.statistics != null) {
                        Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
                        ctx.statistics.addError(objectId, loopClass != null ? loopClass.source : "unknown", msg, wrapped);
                    }
                    if (operation.monitor != null) {
                        operation.monitor.onObjectError(loopClass != null ? loopClass.source : "unknown", objectId, msg);
                    }
                }
                exportedCount++;
            }

            // Propagate newly discovered references to the shared tracker
            if (ctx.referencedClassTracker != null && operation.referencedClassTracker != null) {
                for (String className : operation.referencedClassTracker.getReferencedClasses()) {
                    ctx.referencedClassTracker.registerReferencedClass(className);
                }
            }
        }

        if (operation.monitor != null && loopClass != null) {
            int succeeded = ctx.statistics != null ? ctx.statistics.getUniqueExportedCount() : 0;
            operation.monitor.onClassComplete(loopClass.source, succeeded);
        }
    }
}
