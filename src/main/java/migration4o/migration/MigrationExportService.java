package migration4o.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import migration4o.migration.format.FormatHandler;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaModule;
import migration4o.schema.DOSchemaService;
import migration4o.ui.common.DOExportMonitor;

/**
 * Service for coordinating XML export operations. Handles validation, export
 * execution, and history tracking.
 */
public class MigrationExportService {

    private final DOSchemaService schemaService = DOSchemaService.getInstance();

    public ValidationResult validateExportPrerequisites(migration4o.database.DODatabaseContext dbContext) {
        if (dbContext == null || !dbContext.isDatabaseOpen()) {
            return ValidationResult.error("No database is currently open. Please open a database first.", "No Database");
        }

        if (!schemaService.isSchemaLoaded()) {
            return ValidationResult.error("No reference schema loaded. Please load the schema first.", "No Schema");
        }

        return ValidationResult.success();
    }

    public ExportStatistics exportModules(migration4o.database.DODatabaseContext dbContext,
            List<DOSchemaModule> modules, List<String> modulePaths, String baseOutputPath,
            DOExportMonitor monitor, Integer maxObjectsPerClass, boolean exportNativeIds,
            List<migration4o.models.schema.DOSchemaField> selectedSkipOptions,
            List<String> outputOptions, boolean applyUserSelectedFieldExclusions,
            boolean applySkipWhenConditions, boolean applyExportCriteriaFilters,
            boolean skipObjectsWithoutExportableFields) throws Exception {

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = dbContext.databaseSchema;

        ExportEngine exporter = new ExportEngine(referenceSchema, databaseSchema,
                dbContext.databaseFilePath, dbContext);
        exporter.operation.maxObjectsPerClass = maxObjectsPerClass;
        exporter.operation.exportNativeIds = exportNativeIds;
        exporter.operation.selectedSkipUserOptions = selectedSkipOptions != null
                ? new ArrayList<>(selectedSkipOptions) : new ArrayList<>();
        exporter.operation.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
        exporter.operation.applySkipWhenConditions = applySkipWhenConditions;
        exporter.operation.applyExportCriteriaFilters = applyExportCriteriaFilters;
        exporter.operation.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
        exporter.operation.baseOutputPath = baseOutputPath;
        exporter.operation.monitor = monitor;

        List<FormatHandler> handlers = ExportOutputOption.toHandlers(outputOptions);
        return exporter.exportModules(modules, modulePaths, handlers);
    }

    public ExportStatistics repeatLastExport(DOExportMonitor monitor, migration4o.database.DODatabaseContext dbContext) throws Exception {
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();
        if (params == null) {
            return null;
        }

        File outputFile = new File(params.outputPath);
        String path = outputFile.getAbsolutePath();
        int outputIndex = path.lastIndexOf("/output");
        String baseOutput = outputIndex >= 0 ? path.substring(0, outputIndex + 7) : (outputFile.getParent() != null ? outputFile.getParent() : "output");

        if (params.type == ExportHistory.ExportType.CLASS) {
            throw new UnsupportedOperationException("Single-class export is no longer supported. Please export via modules instead.");
        }

        List<DOSchemaModule> modules = new ArrayList<>();
        List<String> modulePaths = new ArrayList<>();
        if (params.moduleNames != null && !params.moduleNames.isEmpty()) {
            for (String moduleName : params.moduleNames) {
                DOSchemaModule module = ExportUtil.findModuleByName(moduleName);
                if (module == null)
                    throw new IllegalStateException("Could not find module '" + moduleName + "'");
                modules.add(module);
                // Build full hierarchical path for the module
                modulePaths.add(ExportUtil.findModulePathByName(moduleName));
            }
        } else {
            DOSchemaModule module = ExportUtil.findModuleByName(params.targetName);
            if (module == null)
                throw new IllegalStateException("Could not find module '" + params.targetName + "'");
            modules.add(module);
            // Build full hierarchical path for the module
            modulePaths.add(ExportUtil.findModulePathByName(params.targetName));
        }
        return exportModules(dbContext, modules, modulePaths, baseOutput, monitor, params.maxObjectsPerClass, params.exportNativeIds, null, ExportOutputOption.parsePersistedOptions(params.outputFormat), params.applyUserSelectedFieldExclusions, params.applySkipWhenConditions, params.applyExportCriteriaFilters, params.skipObjectsWithoutExportableFields);
    }

}
