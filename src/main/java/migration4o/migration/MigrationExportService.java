package migration4o.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import migration4o.database.DODatabaseService;
import migration4o.schema.DOSchemaService;
import migration4o.engine.export.ExportHistory;
import migration4o.engine.export.XMLExportEngine;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.MigrationModule;
import migration4o.schema.modules.DOModuleService;
import migration4o.ui.common.DOExportMonitor;

/**
 * Service class for coordinating XML export operations.
 * 
 * This class handles the business logic for exporting classes and modules to
 * XML,
 * separate from the UI concerns. It coordinates validation, export execution,
 * and history tracking.
 * 
 * Uses DODatabaseService and DOSchemaService singletons for data access.
 */
public class MigrationExportService {

    private final DODatabaseService databaseService = DODatabaseService.getInstance();
    private final DOSchemaService schemaService = DOSchemaService.getInstance();

    /**
     * Creates a new migration export service.
     * Uses the singleton services for database and schema access.
     */
    public MigrationExportService() {
        // Services are accessed via singletons
    }

    /**
     * Validates that export prerequisites are met.
     * 
     * @return ValidationResult indicating success or describing the validation
     *         error
     */
    public ValidationResult validateExportPrerequisites() {
        if (!databaseService.isDatabaseOpen()) {
            return ValidationResult.error("No database is currently open. Please open a database first.",
                    "No Database");
        }

        if (!schemaService.isSchemaLoaded()) {
            return ValidationResult.error("No reference schema loaded. Please load the schema first.",
                    "No Schema");
        }

        return ValidationResult.success();
    }

    /**
     * Exports a single class to XML.
     * 
     * @param schemaClass The schema class to export
     * @param outputPath  The output file path
     * @param monitor     Optional progress monitor for UI feedback
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportClass(DOSchemaClass schemaClass, String outputPath, DOExportMonitor monitor)
            throws Exception {
        return exportClass(schemaClass, outputPath, monitor, null);
    }

    /**
     * Exports a single class to XML with optional object limit.
     * 
     * @param schemaClass        The schema class to export
     * @param outputPath         The output file path
     * @param monitor            Optional progress monitor for UI feedback
     * @param maxObjectsPerClass Maximum objects to export, or null for all
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportClass(DOSchemaClass schemaClass, String outputPath, DOExportMonitor monitor,
            Integer maxObjectsPerClass)
            throws Exception {
        String className = schemaClass.source;

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(maxObjectsPerClass);

        // Find ClassExportConfig for this class to apply criteria
        migration4o.models.ui.ClassExportConfig config = findClassConfig(className);

        ExportResult result = exporter.exportClass(className, outputPath, monitor, config);

        // Save to history if successful
        if (result.errors.isEmpty()) {
            ExportHistory.saveExport(
                    ExportHistory.ExportType.CLASS,
                    className,
                    outputPath,
                    null,
                    null,
                    maxObjectsPerClass);
        }

        return result;
    }

    /**
     * Exports a module (with all its classes and nested modules) to XML.
     * This is the legacy flat export - use exportModuleStructured for folder-based
     * export.
     * 
     * @param classNames List of fully qualified class names to export
     * @param moduleName Name of the module being exported
     * @param outputPath The output file path
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     * @deprecated Use exportModuleStructured for folder-based export
     */
    @Deprecated
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        // Empty module validation is handled by UI
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        ExportResult result = exporter.exportModule(classNames, moduleName, outputPath);

        // Save to history if successful
        if (result.errors.isEmpty()) {
            ExportHistory.saveExport(
                    ExportHistory.ExportType.MODULE,
                    moduleName,
                    outputPath,
                    classNames);
        }

        return result;
    }

    /**
     * Exports a module with folder structure matching the module hierarchy.
     * Each class is exported to its own XML/XSD file within the module folders.
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory path
     * @param monitor        Optional progress monitor for UI feedback
     * @param saveHistory    Whether to save this export to history (false for bulk
     *                       exports)
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath,
            DOExportMonitor monitor, boolean saveHistory)
            throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        ExportResult result = exporter.exportModuleStructured(module, baseOutputPath, monitor);

        // Save to history if successful and requested
        if (saveHistory && result.errors.isEmpty()) {
            ExportHistory.saveExport(
                    ExportHistory.ExportType.MODULE,
                    module.getName(),
                    baseOutputPath,
                    module.getClassNames());
        }

        return result;
    }

    /**
     * Exports a module with folder structure (with history save by default).
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory path
     * @param monitor        Optional progress monitor for UI feedback
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor)
            throws Exception {
        return exportModuleStructured(module, baseOutputPath, monitor, true);
    }

    /**
     * Exports multiple modules with a shared reference tracker.
     * Used for bulk export operations to ensure referenced classes are only
     * exported once.
     * 
     * @param modules        List of modules to export
     * @param baseOutputPath The base output directory path
     * @param monitor        Optional progress monitor for UI feedback
     * @return List of ExportResults for each module
     * @throws Exception if export fails
     */
    public java.util.List<ExportResult> exportModulesWithSharedTracker(
            java.util.List<MigrationModule> modules, String baseOutputPath, DOExportMonitor monitor)
            throws Exception {
        return exportModulesWithSharedTracker(modules, baseOutputPath, monitor, null);
    }

    public java.util.List<ExportResult> exportModulesWithSharedTracker(
            java.util.List<MigrationModule> modules, String baseOutputPath, DOExportMonitor monitor,
            Integer maxObjectsPerClass)
            throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(maxObjectsPerClass);
        migration4o.engine.export.ReferencedClassTracker sharedTracker = new migration4o.engine.export.ReferencedClassTracker();

        // Pre-register ALL modules before exporting any of them
        // This ensures the tracker knows about all classes that will be exported
        for (MigrationModule module : modules) {
            registerAllModuleClasses(module, sharedTracker);
        }

        java.util.List<ExportResult> results = new java.util.ArrayList<>();

        // Export all modules with the shared tracker
        for (MigrationModule module : modules) {
            ExportResult result = exporter.exportModuleStructured(module, baseOutputPath, monitor, sharedTracker);
            results.add(result);
        }

        // Export referenced classes once at the end
        exporter.exportReferencedClasses(baseOutputPath, monitor, sharedTracker);

        return results;
    }

    /**
     * Recursively registers all classes in a module and its children.
     */
    private void registerAllModuleClasses(MigrationModule module,
            migration4o.engine.export.ReferencedClassTracker tracker) {
        java.util.Set<String> classNames = new java.util.HashSet<>(module.getClassNames());
        tracker.registerModule(module.getName(), classNames);

        for (MigrationModule childModule : module.getChildModules()) {
            registerAllModuleClasses(childModule, tracker);
        }
    }

    /**
     * Finds ClassExportConfig for a given class name across all modules.
     * Returns null if no config found (class will be exported without criteria).
     */
    private migration4o.models.ui.ClassExportConfig findClassConfig(String className) {
        java.util.List<migration4o.models.ui.MigrationModule> modules = DOModuleService.getInstance().getModules();

        for (migration4o.models.ui.MigrationModule module : modules) {
            migration4o.models.ui.ClassExportConfig config = findClassConfigInModule(module, className);
            if (config != null) {
                return config;
            }
        }
        return null;
    }

    /**
     * Recursively searches for ClassExportConfig in a module and its children.
     */
    private migration4o.models.ui.ClassExportConfig findClassConfigInModule(
            migration4o.models.ui.MigrationModule module, String className) {

        for (migration4o.models.ui.ClassExportConfig config : module.getClassConfigs()) {
            if (config.getClassName().equals(className)) {
                return config;
            }
        }

        for (migration4o.models.ui.MigrationModule childModule : module.getChildModules()) {
            migration4o.models.ui.ClassExportConfig config = findClassConfigInModule(childModule, className);
            if (config != null) {
                return config;
            }
        }

        return null;
    }

    /**
     * Repeats the last export operation if history exists.
     * Automatically detects whether to use structured (folder-based) or legacy
     * (single-file) export.
     * 
     * @return ExportResult with details about the export operation, or null if no
     *         history exists
     * @throws Exception if export fails
     */
    public ExportResult repeatLastExport(DOExportMonitor monitor) throws Exception {
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();

        if (params == null) {
            return null; // No history available
        }

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(params.maxObjectsPerClass);

        if (params.type == ExportHistory.ExportType.CLASS) {
            // Class export - extract base directory (one level up from db folder)
            // Path is like "output/54060", we want "output"
            File outputFile = new File(params.outputPath);
            String baseOutput = "output"; // safe default

            if (outputFile.getParent() != null) {
                baseOutput = outputFile.getParent();
            }

            // Find ClassExportConfig for this class to apply criteria
            migration4o.models.ui.ClassExportConfig config = findClassConfig(params.targetName);

            return exporter.exportClass(params.targetName, baseOutput, monitor, config);
        } else {
            // Module export - check if this is a bulk export (multiple modules)
            if (params.moduleNames != null && params.moduleNames.size() > 1) {
                // Bulk export - rebuild all modules
                List<MigrationModule> modules = new ArrayList<>();
                for (String moduleName : params.moduleNames) {
                    MigrationModule module = findModuleByName(moduleName);
                    if (module == null) {
                        throw new IllegalStateException("Could not find module '" + moduleName +
                                "' in migration structure. Please re-export manually.");
                    }
                    modules.add(module);
                }

                // Extract base directory
                String baseOutput = "output"; // safe default
                File outputFile = new File(params.outputPath);
                String path = outputFile.getAbsolutePath();
                int outputIndex = path.lastIndexOf("/output");
                if (outputIndex >= 0) {
                    baseOutput = path.substring(0, outputIndex + 7); // includes "/output"
                } else if (outputFile.getParent() != null) {
                    baseOutput = outputFile.getParent();
                }

                // Use bulk export with shared tracker and object limit
                List<ExportResult> results = exportModulesWithSharedTracker(modules, baseOutput, monitor,
                        params.maxObjectsPerClass);

                // Combine results for return
                List<ExportResult.ExportError> allErrors = new ArrayList<>();
                List<ExportResult.SchemaWarning> allWarnings = new ArrayList<>();
                Map<String, Integer> allClassCounts = new java.util.HashMap<>();
                int totalObjectsAttempted = 0;
                int totalObjectsSucceeded = 0;

                for (ExportResult result : results) {
                    allErrors.addAll(result.errors);
                    allWarnings.addAll(result.schemaWarnings);
                    allClassCounts.putAll(result.exportedClassCounts);
                    totalObjectsAttempted += result.objectsAttempted;
                    totalObjectsSucceeded += result.objectsSucceeded;
                }

                return new ExportResult("Bulk Export", baseOutput, totalObjectsAttempted,
                        totalObjectsSucceeded, allErrors, allWarnings, allClassCounts);
            } else {
                // Single module export
                MigrationModule module = findModuleByName(params.targetName);
                if (module != null) {
                    // Extract base directory - could be "output/54060" or legacy
                    // "output/migration/Module"
                    // We want to go back to just "output"
                    String baseOutput = "output"; // safe default
                    File outputFile = new File(params.outputPath);

                    // Try to find "output" directory in the path
                    String path = outputFile.getAbsolutePath();
                    int outputIndex = path.lastIndexOf("/output");
                    if (outputIndex >= 0) {
                        baseOutput = path.substring(0, outputIndex + 7); // includes "/output"
                    } else if (outputFile.getParent() != null) {
                        // Fallback: use parent directory
                        baseOutput = outputFile.getParent();
                    }

                    return exporter.exportModuleStructured(module, baseOutput, monitor);
                } else {
                    throw new IllegalStateException("Could not find module '" + params.targetName +
                            "' in migration structure. Please re-export manually.");
                }
            }
        }
    }

    /**
     * Finds a module by name in the migration structure.
     */
    private MigrationModule findModuleByName(String moduleName) throws Exception {
        List<MigrationModule> modules = DOModuleService.getInstance()
                .loadModuleStructure("schema/migration-format.xml");
        return findModuleRecursive(modules, moduleName);
    }

    /**
     * Recursively searches for a module by name.
     */
    private MigrationModule findModuleRecursive(List<MigrationModule> modules, String moduleName) {
        for (MigrationModule module : modules) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
            MigrationModule found = findModuleRecursive(module.getChildModules(), moduleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Gets the suggested output filename for a class export.
     * 
     * @param schemaClass The schema class to export
     * @return Suggested filename
     */
    public String suggestClassFilename(DOSchemaClass schemaClass) {
        String simpleName = schemaClass.getSourceName();
        return simpleName + ".xml";
    }

    /**
     * Gets the suggested output filename for a module export.
     * 
     * @param moduleId   The module ID
     * @param moduleName The module name
     * @return Suggested filename
     */
    public String suggestModuleFilename(String moduleId, String moduleName) {
        String safeId = moduleId != null && !moduleId.trim().isEmpty() ? moduleId : moduleName;
        return safeId + ".xml";
    }

    /**
     * Gets the suggested output directory name for a structured module export.
     * 
     * @param moduleId   The module ID
     * @param moduleName The module name
     * @return Suggested directory name
     */
    public String suggestModuleDirectoryName(String moduleId, String moduleName) {
        String safeId = moduleId != null && !moduleId.trim().isEmpty() ? moduleId : moduleName;
        return safeId;
    }

    /**
     * Gets the default output directory for exports.
     * 
     * @return Default output directory path
     */
    public String getDefaultOutputDirectory() {
        return "output/migration";
    }

    /**
     * Result of validation checks.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String errorTitle;

        private ValidationResult(boolean valid, String errorMessage, String errorTitle) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.errorTitle = errorTitle;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(String message, String title) {
            return new ValidationResult(false, message, title);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorTitle() {
            return errorTitle;
        }
    }

    /**
     * Combines multiple export results into a single summary result.
     * 
     * @param results     the list of individual export results
     * @param moduleNames the names of the modules that were exported
     * @param outputPath  the output directory path
     * @return combined export result
     */
    public ExportResult combineExportResults(List<ExportResult> results, List<String> moduleNames, String outputPath) {
        List<ExportResult.ExportError> allErrors = new ArrayList<>();
        List<ExportResult.SchemaWarning> allWarnings = new ArrayList<>();
        Map<String, Integer> allClassCounts = new java.util.HashMap<>();
        int totalObjectsAttempted = 0;
        int totalObjectsSucceeded = 0;

        for (ExportResult result : results) {
            allErrors.addAll(result.errors);
            allWarnings.addAll(result.schemaWarnings);
            allClassCounts.putAll(result.exportedClassCounts);
            totalObjectsAttempted += result.objectsAttempted;
            totalObjectsSucceeded += result.objectsSucceeded;
        }

        return new ExportResult(
                "Bulk Export", outputPath, totalObjectsAttempted, totalObjectsSucceeded,
                allErrors, allWarnings, allClassCounts);
    }

    /**
     * Saves module export operation to history.
     * 
     * @param moduleNames        the names of exported modules
     * @param exportedClasses    the list of exported class names
     * @param outputPath         the output directory path
     * @param maxObjectsPerClass optional limit on objects per class
     */
    public void saveModuleExportHistory(List<String> moduleNames, List<String> exportedClasses,
            String outputPath, Integer maxObjectsPerClass) {

        String targetName = moduleNames.size() == 1
                ? moduleNames.get(0)
                : moduleNames.size() + " modules";

        ExportHistory.saveExport(
                ExportHistory.ExportType.MODULE,
                targetName,
                outputPath,
                exportedClasses,
                moduleNames,
                maxObjectsPerClass);
    }
}
