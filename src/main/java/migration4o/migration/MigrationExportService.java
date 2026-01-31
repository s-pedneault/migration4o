package migration4o.migration;

import java.io.File;
import java.util.List;

import migration4o.database.DODatabaseService;
import migration4o.schema.DOSchemaService;
import migration4o.engine.export.ExportHistory;
import migration4o.engine.export.XMLExportEngine;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.MigrationModule;
import migration4o.schema.modules.DOModuleStructureReader;

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
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportClass(DOSchemaClass schemaClass, String outputPath) throws Exception {
        String className = schemaClass.source;

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        ExportResult result = exporter.exportClass(className, outputPath);

        // Save to history if successful
        if (result.errors.isEmpty()) {
            ExportHistory.saveExport(
                    ExportHistory.ExportType.CLASS,
                    className,
                    outputPath,
                    null);
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
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath) throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        ExportResult result = exporter.exportModuleStructured(module, baseOutputPath);

        // Save to history if successful
        if (result.errors.isEmpty()) {
            ExportHistory.saveExport(
                    ExportHistory.ExportType.MODULE,
                    module.getName(),
                    baseOutputPath,
                    module.getClassNames());
        }

        return result;
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
    public ExportResult repeatLastExport() throws Exception {
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();

        if (params == null) {
            return null; // No history available
        }

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);

        if (params.type == ExportHistory.ExportType.CLASS) {
            // Class export - extract base directory (one level up from db folder)
            // Path is like "output/54060", we want "output"
            File outputFile = new File(params.outputPath);
            String baseOutput = "output"; // safe default

            if (outputFile.getParent() != null) {
                baseOutput = outputFile.getParent();
            }

            return exporter.exportClass(params.targetName, baseOutput);
        } else {
            // Module export - rebuild module from migration format
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

                return exporter.exportModuleStructured(module, baseOutput);
            } else {
                throw new IllegalStateException("Could not find module '" + params.targetName +
                        "' in migration structure. Please re-export manually.");
            }
        }
    }

    /**
     * Finds a module by name in the migration structure.
     */
    private MigrationModule findModuleByName(String moduleName) throws Exception {
        DOModuleStructureReader reader = new DOModuleStructureReader();
        List<MigrationModule> modules = reader.readMigrationFormat("schema/migration-format.xml");
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
}
