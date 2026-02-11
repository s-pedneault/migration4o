package migration4o.migration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.MigrationModule;
import migration4o.schema.DOSchemaService;
import migration4o.ui.common.DOExportMonitor;

/**
 * Service for coordinating XML export operations.
 * Handles validation, export execution, and history tracking.
 */
public class MigrationExportService {

    private final DODatabaseService databaseService = DODatabaseService.getInstance();
    private final DOSchemaService schemaService = DOSchemaService.getInstance();

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

    public ExportStatistics exportModules(List<MigrationModule> modules, String baseOutputPath,
            DOExportMonitor monitor, Integer maxObjectsPerClass, boolean exportNativeIds) throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(maxObjectsPerClass);
        exporter.setExportNativeIds(exportNativeIds);

        if (modules.size() == 1) {
            MigrationModule module = modules.get(0);
            ExportStatistics result = exporter.exportModuleStructured(module, baseOutputPath, monitor);
            if (result.errors.isEmpty()) {
                ExportHistory.saveExport(ExportHistory.ExportType.MODULE, module.getName(),
                        baseOutputPath, module.getClassNames(), null, maxObjectsPerClass, exportNativeIds);
            }
            return result;
        }

        exporter.initializeSharedTracking();
        ReferencedClassTracker tracker = new ReferencedClassTracker();
        for (MigrationModule module : modules) {
            ExportUtil.registerAllModuleClasses(module, tracker);
        }

        List<ExportStatistics> results = new ArrayList<>();
        for (MigrationModule module : modules) {
            results.add(exporter.exportModuleStructured(module, baseOutputPath, monitor, tracker));
        }
        exporter.exportReferencedClasses(baseOutputPath, monitor, tracker);

        // Write comprehensive XSD after all exports are complete
        try {
            if (monitor != null) {
                monitor.onStatusMessage("Generating comprehensive XSD schema...");
            }
            exporter.writeComprehensiveXSD(baseOutputPath);
            if (monitor != null) {
                monitor.onStatusMessage("Comprehensive XSD schema generated: migration-schema.xsd");
            }
        } catch (Exception e) {
            if (monitor != null) {
                monitor.onStatusMessage("Warning: Failed to generate comprehensive XSD: " + e.getMessage());
            }
            e.printStackTrace(); // Log the full stack trace
        }

        // Validate exported XML files against the comprehensive XSD
        try {
            java.util.Set<String> xmlFiles = exporter.getExportedXMLFiles();
            if (xmlFiles != null && !xmlFiles.isEmpty()) {
                if (monitor != null) {
                    monitor.onStatusMessage("Validating " + xmlFiles.size() + " XML files against schema...");
                }

                Path dbBasePath = exporter.getBaseOutputPath(baseOutputPath);
                Path xsdPath = dbBasePath.resolve("schema.xsd");

                migration4o.util.XMLValidator.ValidationResult validationResult = migration4o.util.XMLValidator
                        .validateMultiple(
                                new java.util.ArrayList<>(xmlFiles),
                                xsdPath.toString());

                // Print final summary
                System.out.println();
                if (validationResult.allValid()) {
                    System.out.println(
                            "=== OVERALL VALIDATION: PASS (" + validationResult.getTotalCount() + " files) ===");
                } else {
                    System.out.println("=== OVERALL VALIDATION: FAIL (" + validationResult.successCount + " passed, " +
                            validationResult.failedFiles.size() + " failed) ===");
                }
                System.out.println();

                if (monitor != null) {
                    if (validationResult.allValid()) {
                        monitor.onStatusMessage(
                                "✓ All " + validationResult.getTotalCount() + " XML files validated successfully");
                    } else {
                        monitor.onStatusMessage("⚠ Validation: " + validationResult.successCount + " passed, " +
                                validationResult.failedFiles.size() + " failed");
                        for (String failedFile : validationResult.failedFiles) {
                            String fileName = new java.io.File(failedFile).getName();
                            monitor.onStatusMessage("  ✗ " + fileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (monitor != null) {
                monitor.onStatusMessage("Warning: XML validation failed: " + e.getMessage());
            }
        }

        exporter.resetSharedTracking();

        return ExportUtil.combineResults(results, baseOutputPath);
    }

    public ExportStatistics repeatLastExport(DOExportMonitor monitor) throws Exception {
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();
        if (params == null) {
            return null;
        }

        File outputFile = new File(params.outputPath);
        String path = outputFile.getAbsolutePath();
        int outputIndex = path.lastIndexOf("/output");
        String baseOutput = outputIndex >= 0 ? path.substring(0, outputIndex + 7)
                : (outputFile.getParent() != null ? outputFile.getParent() : "output");

        if (params.type == ExportHistory.ExportType.CLASS) {
            throw new UnsupportedOperationException(
                    "Single-class export is no longer supported. Please export via modules instead.");
        }

        List<MigrationModule> modules = new ArrayList<>();
        if (params.moduleNames != null && !params.moduleNames.isEmpty()) {
            for (String moduleName : params.moduleNames) {
                MigrationModule module = ExportUtil.findModuleByName(moduleName);
                if (module == null)
                    throw new IllegalStateException("Could not find module '" + moduleName + "'");
                modules.add(module);
            }
        } else {
            MigrationModule module = ExportUtil.findModuleByName(params.targetName);
            if (module == null)
                throw new IllegalStateException("Could not find module '" + params.targetName + "'");
            modules.add(module);
        }
        return exportModules(modules, baseOutput, monitor, params.maxObjectsPerClass, params.exportNativeIds);
    }
}
