package migration4o.migration.tasks;

import migration4o.migration.ExportOperation;
import migration4o.migration.ObjectExporter;
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

    public ObjectExportLoop(ExportOperation operation) {
        this.operation = operation;
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
            operation.monitor.onClassComplete(schemaClass.source, operation.statistics.objectsSucceeded);
        }
    }
}
