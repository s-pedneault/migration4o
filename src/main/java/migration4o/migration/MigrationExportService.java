package migration4o.migration;

import java.io.File;
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

    public ExportStatistics exportClasses(List<String> classNames, String outputPath,
            DOExportMonitor monitor, Integer maxObjectsPerClass) throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(maxObjectsPerClass);

        if (classNames.size() == 1) {
            String className = classNames.get(0);
            ClassExportConfig config = ExportUtil.findClassConfig(className);
            ExportStatistics result = exporter.exportClass(className, outputPath, monitor, config);
            if (result.errors.isEmpty()) {
                ExportHistory.saveExport(ExportHistory.ExportType.CLASS, className,
                        outputPath, null, null, maxObjectsPerClass);
            }
            return result;
        }

        List<ExportStatistics> results = new ArrayList<>();
        for (String className : classNames) {
            ClassExportConfig config = ExportUtil.findClassConfig(className);
            results.add(exporter.exportClass(className, outputPath, monitor, config));
        }
        return ExportUtil.combineResults(results, outputPath);
    }

    public ExportStatistics exportModules(List<MigrationModule> modules, String baseOutputPath,
            DOExportMonitor monitor, Integer maxObjectsPerClass) throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = databaseService.getDatabaseSchema();
        String databasePath = databaseService.getCurrentDatabasePath();

        XMLExportEngine exporter = new XMLExportEngine(referenceSchema, databaseSchema, databasePath);
        exporter.setMaxObjectsPerClass(maxObjectsPerClass);

        if (modules.size() == 1) {
            MigrationModule module = modules.get(0);
            ExportStatistics result = exporter.exportModuleStructured(module, baseOutputPath, monitor);
            if (result.errors.isEmpty()) {
                ExportHistory.saveExport(ExportHistory.ExportType.MODULE, module.getName(),
                        baseOutputPath, module.getClassNames());
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
            List<String> classNames = new ArrayList<>();
            classNames.add(params.targetName);
            return exportClasses(classNames, baseOutput, monitor, params.maxObjectsPerClass);
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
        return exportModules(modules, baseOutput, monitor, params.maxObjectsPerClass);
    }
}
