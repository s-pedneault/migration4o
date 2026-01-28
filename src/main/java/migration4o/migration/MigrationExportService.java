package migration4o.migration;

import java.util.List;

import migration4o.engine.export.ExportHistory;
import migration4o.engine.export.XMLExportEngine;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Service class for coordinating XML export operations.
 * 
 * This class handles the business logic for exporting classes and modules to
 * XML,
 * separate from the UI concerns. It coordinates validation, export execution,
 * and history tracking.
 */
public class MigrationExportService {

    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final String databasePath;

    /**
     * Creates a new migration export service.
     * 
     * @param referenceSchema The reference schema with class definitions
     * @param databaseSchema  The database schema with actual object IDs
     * @param databasePath    Path to the currently opened database
     */
    public MigrationExportService(DOSchema referenceSchema, DOSchema databaseSchema, String databasePath) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;
    }

    /**
     * Validates that export prerequisites are met.
     * 
     * @return ValidationResult indicating success or describing the validation
     *         error
     */
    public ValidationResult validateExportPrerequisites() {
        if (databasePath == null || databasePath.trim().isEmpty()) {
            return ValidationResult.error("No database is currently open. Please open a database first.",
                    "No Database");
        }

        if (databaseSchema == null) {
            return ValidationResult.error("No database schema available. Please open a database first.",
                    "No Database Schema");
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
     * 
     * @param classNames List of fully qualified class names to export
     * @param moduleName Name of the module being exported
     * @param outputPath The output file path
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        // Empty module validation is handled by UI
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
     * Repeats the last export operation if history exists.
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

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);

        if (params.type == ExportHistory.ExportType.CLASS) {
            return exporter.exportClass(params.targetName, params.outputPath);
        } else {
            return exporter.exportModule(params.classNames, params.targetName, params.outputPath);
        }
    }

    /**
     * Gets the suggested output filename for a class export.
     * 
     * @param schemaClass The schema class to export
     * @return Suggested filename
     */
    public String suggestClassFilename(DOSchemaClass schemaClass) {
        String simpleName = schemaClass.getSourceName();
        return simpleName + "_export.xml";
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
        return safeId + "_export.xml";
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
