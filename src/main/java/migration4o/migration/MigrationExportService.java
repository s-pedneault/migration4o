package migration4o.migration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaModule;
import migration4o.schema.DOSchemaService;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.HtmlNavPostProcessor;

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

    public ExportStatistics exportModules(migration4o.database.DODatabaseContext dbContext, List<DOSchemaModule> modules, List<String> modulePaths, String baseOutputPath, DOExportMonitor monitor, Integer maxObjectsPerClass, boolean exportNativeIds, List<migration4o.models.schema.DOSchemaField> selectedSkipOptions, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) throws Exception {
        List<String> normalizedOptions = ExportOutputOption.normalize(outputOptions);
        List<ExportStatistics> allFormatResults = new ArrayList<>();

        for (String outputOption : normalizedOptions) {
            String writerFormat = ExportOutputOption.toWriterFormat(outputOption);
            boolean generateHtmlViewer = ExportOutputOption.generatesHtmlViewer(outputOption);
            boolean generateXsd = ExportOutputOption.generatesXsd(outputOption);

            if (monitor != null) {
                monitor.onStatusMessage("Running export option: " + outputOption + " (writer=" + writerFormat + ")");
            }

            allFormatResults.add(exportModulesSingleFormat(dbContext, modules, modulePaths, baseOutputPath, monitor, maxObjectsPerClass, exportNativeIds, selectedSkipOptions, writerFormat, generateHtmlViewer, generateXsd, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields));
        }

        return ExportUtil.combineResults(allFormatResults, baseOutputPath);
    }

    private ExportStatistics exportModulesSingleFormat(migration4o.database.DODatabaseContext dbContext, List<DOSchemaModule> modules, List<String> modulePaths, String baseOutputPath, DOExportMonitor monitor, Integer maxObjectsPerClass, boolean exportNativeIds, List<migration4o.models.schema.DOSchemaField> selectedSkipOptions, String outputFormat, boolean generateHtmlViewer, boolean generateXsd, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) throws Exception {
        DOSchema referenceSchema = schemaService.getReferenceSchema();
        DOSchema databaseSchema = dbContext.databaseSchema;
        String databasePath = dbContext.databaseFilePath;

        ExportEngine exporter = new ExportEngine(referenceSchema, databaseSchema, databasePath, dbContext);
        exporter.operation.maxObjectsPerClass = maxObjectsPerClass;
        exporter.operation.exportNativeIds = exportNativeIds;
        exporter.operation.selectedSkipUserOptions = selectedSkipOptions != null ? new java.util.ArrayList<>(selectedSkipOptions) : new java.util.ArrayList<>();
        exporter.operation.outputFormat = (outputFormat != null && !outputFormat.isBlank()) ? outputFormat : "XML";
        exporter.operation.generateHtmlViewer = generateHtmlViewer;
        exporter.operation.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
        exporter.operation.applySkipWhenConditions = applySkipWhenConditions;
        exporter.operation.applyExportCriteriaFilters = applyExportCriteriaFilters;
        exporter.operation.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
        if (generateHtmlViewer) {
            exporter.setModuleNavData(modules, modulePaths, baseOutputPath);
        }

        // CRITICAL FIX: Always use shared tracking for module exports to avoid
        // generating
        // individual XSD files per class. Generate comprehensive XSD at the end
        // instead.
        exporter.initializeSharedTracking();
        ReferencedClassTracker tracker = new ReferencedClassTracker();
        for (DOSchemaModule module : modules) {
            ExportUtil.registerAllModuleClasses(module, tracker);
        }

        List<ExportStatistics> results = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            DOSchemaModule module = modules.get(i);
            String modulePath = (modulePaths != null && i < modulePaths.size()) ? modulePaths.get(i) : (module.id != null && !module.id.isBlank() ? module.id : module.name);
            results.add(exporter.exportModuleStructured(module, modulePath, baseOutputPath, monitor, tracker));
        }
        results.add(exporter.exportReferencedClasses(baseOutputPath, monitor, tracker));

        Set<Long> reachedObjectIds = collectReachedObjectIds(results);
        Set<Long> unreachedObjectIds = collectUnreachedObjectIds(databaseSchema, reachedObjectIds);
        if (exporter.operation.isXMLFormat() && !unreachedObjectIds.isEmpty()) {
            if (monitor != null) {
                monitor.onStatusMessage("Exporting " + unreachedObjectIds.size() + " unreached objects to _Migration/Extra.xml...");
            }
            results.add(exporter.exportUnreachedObjects(baseOutputPath, unreachedObjectIds, monitor));
        } else if (monitor != null) {
            monitor.onStatusMessage("No unreached objects detected.");
        }

        // Write comprehensive XSD after all exports are complete
        try {
            if (exporter.operation.isXMLFormat() && generateXsd) {
                if (monitor != null) {
                    monitor.onStatusMessage("Generating comprehensive XSD schema...");
                }
                exporter.writeComprehensiveXSD(baseOutputPath);
                if (monitor != null) {
                    monitor.onStatusMessage("Comprehensive XSD schema generated: _Migration/Schema.xsd");
                }
            }
        } catch (Exception e) {
            if (monitor != null) {
                monitor.onStatusMessage("Warning: Failed to generate comprehensive XSD: " + e.getMessage());
            }
            e.printStackTrace(); // Log the full stack trace
        }

        // Validate exported XML files against the comprehensive XSD
        try {
            java.util.Set<String> xmlFiles = exporter.operation.exportedXMLFiles;
            if (exporter.operation.isXMLFormat() && generateXsd && xmlFiles != null && !xmlFiles.isEmpty()) {
                if (monitor != null) {
                    monitor.onStatusMessage("Validating " + xmlFiles.size() + " XML files against schema...");
                }

                Path dbBasePath = exporter.operation.getBaseOutputPath(baseOutputPath);
                Path xsdPath = dbBasePath.resolve("_Migration").resolve("Schema.xsd");

                migration4o.util.XMLValidator.ValidationResult validationResult = migration4o.util.XMLValidator.validateMultiple(new java.util.ArrayList<>(xmlFiles), xsdPath.toString());

                // Print final summary
                System.out.println();
                if (validationResult.allValid()) {
                    System.out.println("=== OVERALL VALIDATION: PASS (" + validationResult.getTotalCount() + " files) ===");
                } else {
                    System.out.println("=== OVERALL VALIDATION: FAIL (" + validationResult.successCount + " passed, " + validationResult.failedFiles.size() + " failed) ===");
                }
                System.out.println();

                if (monitor != null) {
                    if (validationResult.allValid()) {
                        monitor.onStatusMessage("✓ All " + validationResult.getTotalCount() + " XML files validated successfully");
                    } else {
                        monitor.onStatusMessage("⚠ Validation: " + validationResult.successCount + " passed, " + validationResult.failedFiles.size() + " failed");
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

        // Navigation sidebar is embedded per-file at generation time via
        // setModuleNavData()

        exporter.resetSharedTracking();

        return ExportUtil.combineResults(results, baseOutputPath);
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

    private Set<Long> collectReachedObjectIds(List<ExportStatistics> results) {
        Set<Long> reached = new HashSet<>();
        if (results == null || results.isEmpty()) {
            return reached;
        }

        for (ExportStatistics result : results) {
            if (result == null || result.exportedObjectIds == null || result.exportedObjectIds.isEmpty()) {
                continue;
            }

            for (List<Long> ids : result.exportedObjectIds.values()) {
                if (ids != null) {
                    reached.addAll(ids);
                }
            }
        }

        return reached;
    }

    private Set<Long> collectUnreachedObjectIds(DOSchema databaseSchema, Set<Long> reachedObjectIds) {
        Set<Long> allObjectIds = new HashSet<>();
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return allObjectIds;
        }

        for (migration4o.models.schema.DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            long[] ids = (schemaClass.uniqueObjectIds != null && schemaClass.uniqueObjectIds.length > 0) ? schemaClass.uniqueObjectIds : schemaClass.objectIds;
            if (ids == null) {
                continue;
            }

            for (long id : ids) {
                if (id > 0) {
                    allObjectIds.add(id);
                }
            }
        }

        if (reachedObjectIds != null && !reachedObjectIds.isEmpty()) {
            allObjectIds.removeAll(reachedObjectIds);
        }

        return allObjectIds;
    }
}
