package migration4o.migration;

import java.util.ArrayList;
import java.util.List;

import migration4o.migration.format.FormatHandler;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.SeedQuery;
import migration4o.schema.DOSchemaService;
import migration4o.ui.common.DOExportMonitor;

/**
 * Service for coordinating XML export operations. Handles validation and export
 * execution. All exports — whether triggered from the UI Export button or
 * {@code --repeat-export} — use the single {@link #exportModules} method.
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

    public ExportStatistics exportModules(migration4o.database.DODatabaseContext dbContext, List<DOSchemaModule> modules, List<String> modulePaths, String baseOutputPath, DOExportMonitor monitor, Integer maxObjectsPerClass, boolean exportNativeIds, List<migration4o.models.schema.DOSchemaField> selectedSkipOptions, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking, List<SeedQuery> seedQueries, String outputBranch) throws Exception {

        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = dbContext.databaseSchema;

        ExportEngine exporter = new ExportEngine(referenceSchema, databaseSchema, dbContext.databaseFilePath, dbContext);
        exporter.operation.maxObjectsPerClass = maxObjectsPerClass;
        exporter.operation.exportNativeIds = exportNativeIds;
        exporter.operation.selectedSkipUserOptions = selectedSkipOptions != null ? new ArrayList<>(selectedSkipOptions) : new ArrayList<>();
        exporter.operation.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
        exporter.operation.applySkipWhenConditions = applySkipWhenConditions;
        exporter.operation.applyExportCriteriaFilters = applyExportCriteriaFilters;
        exporter.operation.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
        exporter.operation.fullTracking = fullTracking;
        exporter.operation.baseOutputPath = baseOutputPath;
        exporter.operation.outputBranch = outputBranch;
        exporter.operation.monitor = monitor;
        if (seedQueries != null) {
            exporter.operation.seedQueries = new ArrayList<>(seedQueries);
        }

        List<FormatHandler> handlers = ExportOutputOption.toHandlers(outputOptions);
        return exporter.exportModules(modules, modulePaths, handlers);
    }

}