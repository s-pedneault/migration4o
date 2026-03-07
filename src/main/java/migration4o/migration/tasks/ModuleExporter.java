package migration4o.migration.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import migration4o.migration.ExportOperation;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;

/**
 * Recursively exports a module tree to a matching folder structure.
 * <p>
 * Each module becomes a sub-folder (named by its ID, falling back to its
 * display name) and each class config within that module becomes a data file
 * inside that folder. Child modules are processed depth-first.
 * <p>
 * Delegates per-class file writing to {@link ClassFileExporter}.
 */
public class ModuleExporter {

    private final ExportOperation operation;

    public ModuleExporter(ExportOperation operation) {
        this.operation = operation;
    }

    // ── Counting
    // ──────────────────────────────────────────────────────────────

    /** Returns the total number of class configs across the module tree. */
    public int countTotalClasses(DOSchemaModule module) {
        int count = module.classConfigs.size();
        for (DOSchemaModule child : module.children) {
            count += countTotalClasses(child);
        }
        return count;
    }

    // ── Reference-tracker registration ───────────────────────────────────────

    /**
     * Recursively registers all class names in the module tree with the given
     * {@link ReferencedClassTracker} so the tracker can distinguish between
     * "known module classes" and truly foreign references.
     */
    public void registerModuleClasses(DOSchemaModule module, ReferencedClassTracker tracker) {
        Set<String> classNames = new HashSet<>();
        for (ClassExportConfig c : module.classConfigs) {
            classNames.add(c.getClassName());
        }
        tracker.registerModule(module.name, classNames);
        for (DOSchemaModule child : module.children) {
            registerModuleClasses(child, tracker);
        }
    }

    // ── Recursive export
    // ──────────────────────────────────────────────────────

    /**
     * Exports {@code module} and all its descendants under
     * {@code currentBasePath}. Progress callbacks are fired via
     * {@code operation.monitor}.
     *
     * @param module Module to export
     * @param currentBasePath Directory in which the module folder will be
     * created
     * @param depth Nesting depth (0 = top-level)
     */
    public void exportModuleRecursive(DOSchemaModule module, Path currentBasePath, int depth) throws Exception {
        operation.moduleStack.push(module);
        try {
            if (operation.monitor != null) {
                operation.monitor.onModuleStart(module.name, module.classConfigs.size(), depth);
            }

            Path modulePath = currentBasePath.resolve(ModulePathUtil.moduleId(module));
            Files.createDirectories(modulePath);

            ClassFileExporter classFileExporter = new ClassFileExporter(operation);

            for (ClassExportConfig config : module.classConfigs) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;

                String className = config.getClassName();
                if (config.hasCriteria()) {
                    System.out.println("DEBUG: Exporting " + className + " with " + config.getCriteria().size() + " criteria: " + config.getCriteria());
                }

                DOSchemaClass schemaClass = operation.referenceSchema.findClassByName(className);
                if (schemaClass == null)
                    continue;

                DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
                if (dbSchemaClass == null)
                    continue;

                Path xmlPath = modulePath.resolve(config.getDestinationFileName() + operation.getOutputFileExtension());
                Path xsdPath = operation.isXMLFormat() ? modulePath.resolve(config.getDestinationFileName() + ".xsd") : null;

                if (operation.isXMLFormat() && operation.exportedXMLFiles != null) {
                    operation.exportedXMLFiles.add(xmlPath.toString());
                }

                classFileExporter.exportClassToFile(schemaClass, dbSchemaClass, xmlPath, xsdPath, config);
            }

            for (DOSchemaModule childModule : module.children) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;
                exportModuleRecursive(childModule, modulePath, depth + 1);
            }

            if (operation.monitor != null) {
                operation.monitor.onModuleComplete(module.name);
            }
        } finally {
            operation.moduleStack.pop();
        }
    }
}
