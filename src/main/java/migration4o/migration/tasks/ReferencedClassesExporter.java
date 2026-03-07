package migration4o.migration.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import migration4o.migration.ExportOperation;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchemaClass;

/**
 * Exports classes that were referenced during a module export but that were not
 * listed in the original export request.
 * <p>
 * Referenced classes are written to a {@code Referenced/} sub-folder under the
 * db output directory. Each class is exported without criteria filtering and
 * without further reference tracking (to prevent infinite recursion).
 */
public class ReferencedClassesExporter {

    private final ExportOperation operation;

    public ReferencedClassesExporter(ExportOperation operation) {
        this.operation = operation;
    }

    /**
     * Exports every class in {@code referencedClasses} that has not been
     * exported yet as a referenced class, writing files under
     * {@code basePath/Referenced/}.
     */
    public void exportReferencedClasses(Set<String> referencedClasses, Path basePath) throws Exception {
        if (operation.monitor != null) {
            operation.monitor.onModuleStart("Referenced", referencedClasses.size(), 0);
        }

        Path referencedPath = basePath.resolve("Referenced");
        Files.createDirectories(referencedPath);

        ClassFileExporter classFileExporter = new ClassFileExporter(operation);

        for (String className : referencedClasses) {
            if (operation.monitor != null && operation.monitor.isCancelled())
                break;

            if (operation.referencedClassTracker.isReferencedClassExported(className)) {
                continue;
            }

            DOSchemaClass schemaClass = operation.referenceSchema.findClassByName(className);
            if (schemaClass == null) {
                if (operation.monitor != null) {
                    operation.monitor.onStatusMessage("Referenced class not found in schema: " + className);
                }
                continue;
            }

            DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                if (operation.monitor != null) {
                    operation.monitor.onStatusMessage("Referenced class not found in database: " + className);
                }
                continue;
            }

            Path xmlPath = referencedPath.resolve(schemaClass.destinationName + operation.getOutputFileExtension());
            Path xsdPath = operation.isXMLFormat() ? referencedPath.resolve(schemaClass.destinationName + ".xsd") : null;

            // Temporarily disable reference tracking to prevent infinite
            // recursion
            ReferencedClassTracker previousTracker = operation.referencedClassTracker;
            operation.referencedClassTracker = null;
            try {
                classFileExporter.exportClassToFile(schemaClass, dbSchemaClass, xmlPath, xsdPath, null);
            } finally {
                operation.referencedClassTracker = previousTracker;
            }

            operation.referencedClassTracker.markReferencedClassAsExported(className);

            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Exported referenced class: " + schemaClass.destinationName);
            }
        }

        if (operation.monitor != null) {
            operation.monitor.onModuleComplete("Referenced");
        }
    }
}
