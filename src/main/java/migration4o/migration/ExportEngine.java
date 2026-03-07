package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import migration4o.database.DODatabaseContext;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.tasks.ClassFileExporter;
import migration4o.migration.tasks.HtmlViewerTask;
import migration4o.migration.tasks.ModuleExporter;
import migration4o.migration.tasks.ModulePathUtil;
import migration4o.migration.tasks.NavTreeBuilder;
import migration4o.migration.tasks.ReferencedClassesExporter;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.SchemaUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterMetadata;

/**
 * Orchestrates XML export operations by coordinating specialized task classes.
 * <p>
 * Responsibilities are delegated to:
 * <ul>
 * <li>{@link NavTreeBuilder} – HTML viewer sidebar nav tree
 * <li>{@link ModuleExporter} – recursive module/folder traversal
 * <li>{@link ClassFileExporter} – single class → data file
 * <li>{@link ReferencedClassesExporter} – referenced-class clean-up pass
 * <li>{@link HtmlViewerTask} – per-file HTML viewer generation
 * <li>{@link ModulePathUtil} – path / module-name utilities
 * </ul>
 */
public class ExportEngine {
    public final ExportOperation operation;

    /**
     * Creates export engine using the shared in-memory database from
     * DODatabaseService.
     * 
     * @param schema The reference schema
     * @param databaseSchema The database schema
     * @param databasePath The database file path (for naming output folders)
     * @param dbContext The database context
     */
    public ExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath, DODatabaseContext dbContext) {
        this.operation = new ExportOperation();
        this.operation.referenceSchema = schema;
        this.operation.databaseSchema = databaseSchema;
        this.operation.databasePath = databasePath;
        this.operation.dbContext = dbContext;
        this.operation.maxObjectsPerClass = null;
        this.operation.exportNativeIds = false;
        this.operation.outputFormat = "XML";
        this.operation.applyUserSelectedFieldExclusions = true;
        this.operation.applySkipWhenConditions = true;
        this.operation.applyExportCriteriaFilters = true;
        this.operation.skipObjectsWithoutExportableFields = true;
        this.operation.availableSkipUserOptions = SchemaUtil.collectSkipUserOptions(schema);
        this.operation.selectedSkipUserOptions = new ArrayList<>();
        this.operation.exportedObjectIds = new HashSet<>();
        this.operation.useSharedTracking = false;
        this.operation.sharedXSDBuilder = null;
        this.operation.exportedXMLFiles = null;

        // Get the shared in-memory container from the context
        this.operation.container = dbContext != null ? dbContext.container : null;

        if (operation.container == null) {
            throw new IllegalStateException("No database is open.");
        }
    }

    /**
     * Pre-builds the hierarchical nav tree for the HTML viewer sidebar. Must be
     * called before export starts. The tree mirrors the module structure:
     * path-prefix groups → module groups → class leaves (recursively).
     */
    public void setModuleNavData(List<DOSchemaModule> modules, List<String> modulePaths, String baseOutputDir) {
        new NavTreeBuilder(operation).build(modules, modulePaths, baseOutputDir);
    }

    /**
     * Writes the comprehensive XSD schema file. Should be called after all
     * exports are complete when using shared tracking.
     * 
     * @param baseOutputPath The base output directory
     * @throws IOException if writing fails
     */
    public void writeComprehensiveXSD(String baseOutputPath) throws IOException {
        if (operation.sharedXSDBuilder == null) {
            throw new IllegalStateException("Shared XSD builder not initialized. Call initializeSharedTracking() first.");
        }

        Path xsdPath = ModulePathUtil.getComprehensiveSchemaPath(baseOutputPath, operation);
        Files.createDirectories(xsdPath.getParent());
        operation.sharedXSDBuilder.writeXSD(xsdPath.toString());
    }

    /**
     * Exports all objects in a module to XML file (legacy flat structure).
     * 
     * @deprecated Use exportModuleStructured for folder-based export
     */
    @Deprecated
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        return exportModule(classNames, moduleName, outputPath, null);
    }

    /**
     * Exports a module with folder structure, using provided hierarchical path.
     * This is used when exporting a child module standalone to preserve the
     * parent folder structure.
     * 
     * @param module The module to export (including child modules)
     * @param modulePath The full hierarchical path for this module (e.g.,
     * "Activités/Intervention")
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor Progress monitor (can be null)
     * @param sharedTracker Shared reference tracker for bulk exports (can be
     * null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(DOSchemaModule module, String modulePath) throws Exception {
        System.out.println("DEBUG exportModuleStructured: module=" + module.name + ", modulePath=" + modulePath);

        ModuleExporter moduleExporter = new ModuleExporter(operation);
        int totalClasses = moduleExporter.countTotalClasses(module);

        if (operation.monitor != null) {
            operation.monitor.onExportStart(module.name, totalClasses);
        }

        operation.statistics = new ExportStatistics(operation.monitor);
        if (operation.referencedClassTracker == null) {
            operation.referencedClassTracker = new ReferencedClassTracker();
        }

        try {
            Path dbBasePath = operation.getBaseOutputPath(operation.baseOutputPath);
            Files.createDirectories(dbBasePath);

            moduleExporter.registerModuleClasses(module, operation.referencedClassTracker);

            Path fullModulePath = dbBasePath;
            if (modulePath != null && !modulePath.isEmpty()) {
                for (String part : modulePath.split("/")) {
                    fullModulePath = fullModulePath.resolve(part);
                }
            }

            Path moduleBasePath = fullModulePath.getParent();
            if (moduleBasePath == null) {
                moduleBasePath = dbBasePath;
            }

            moduleExporter.exportModuleRecursive(module, moduleBasePath, 0);

            // For local (non-shared) trackers, handle referenced classes
            // immediately
            if (operation.referencedClassTracker != null && !operation.useSharedTracking) {
                Set<String> referencedClasses = operation.referencedClassTracker.getReferencedClasses();
                if (!referencedClasses.isEmpty() && operation.monitor != null) {
                    operation.monitor.onStatusMessage("Discovered " + referencedClasses.size() + " referenced classes not in export request");
                }
                if (!referencedClasses.isEmpty()) {
                    new ReferencedClassesExporter(operation).exportReferencedClasses(referencedClasses, dbBasePath);
                }
            }

            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo(module.name, dbBasePath.toString());

            if (operation.monitor != null) {
                operation.monitor.onExportComplete(module.name, operation.statistics.objectsSucceeded, operation.statistics.schemaWarnings.size());
            }

            return operation.statistics;

        } catch (Exception e) {
            if (operation.monitor != null) {
                operation.monitor.onExportError(module.name, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Exports a module with folder structure matching the module hierarchy.
     * Uses module ID as path (falls back to name if ID is blank).
     * <p>
     * Reads {@link ExportOperation#baseOutputPath},
     * {@link ExportOperation#monitor} and
     * {@link ExportOperation#referencedClassTracker} from the operation – set
     * them before calling.
     * 
     * @param module The module to export (including child modules)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(DOSchemaModule module) throws Exception {
        return exportModuleStructured(module, ModulePathUtil.moduleId(module));
    }

    /**
     * Exports referenced classes that were discovered during bulk export.
     * Should be called after all modules have been exported with a shared
     * tracker.
     * <p>
     * Reads {@link ExportOperation#baseOutputPath},
     * {@link ExportOperation#monitor} and
     * {@link ExportOperation#referencedClassTracker} from the operation – set
     * them before calling.
     * 
     * @throws Exception if export fails
     */
    public ExportStatistics exportReferencedClasses() throws Exception {

        operation.statistics = new ExportStatistics(operation.monitor);

        Set<String> referencedClasses = operation.referencedClassTracker != null ? operation.referencedClassTracker.getReferencedClasses() : java.util.Collections.emptySet();
        if (referencedClasses.isEmpty()) {
            operation.statistics.setExportInfo("Referenced", operation.getBaseOutputPath(operation.baseOutputPath).resolve("Referenced").toString());
            return operation.statistics;
        }

        if (operation.monitor != null) {
            operation.monitor.onStatusMessage("Exporting " + referencedClasses.size() + " referenced classes");
        }

        try {
            Path dbBasePath = operation.getBaseOutputPath(operation.baseOutputPath);

            new ReferencedClassesExporter(operation).exportReferencedClasses(referencedClasses, dbBasePath);
            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo("Referenced", dbBasePath.resolve("Referenced").toString());
            return operation.statistics;

        } catch (Exception e) {
            if (operation.monitor != null) {
                operation.monitor.onExportError("Referenced", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Exports objects that were not reached during the normal module export.
     * <p>
     * Reads {@link ExportOperation#baseOutputPath} and
     * {@link ExportOperation#monitor} from the operation – set them before
     * calling.
     *
     * @param unreachedObjectIds The set of DB4O object IDs to export
     * @throws Exception if export fails
     */
    public ExportStatistics exportUnreachedObjects(Set<Long> unreachedObjectIds) throws Exception {
        operation.statistics = new ExportStatistics(operation.monitor);

        Path dbBasePath = operation.getBaseOutputPath(operation.baseOutputPath);
        Path migrationPath = dbBasePath.resolve("_Migration");
        Files.createDirectories(migrationPath);

        Path xmlPath = migrationPath.resolve("Extra" + operation.getOutputFileExtension());
        if (operation.isXMLFormat() && operation.exportedXMLFiles != null) {
            operation.exportedXMLFiles.add(xmlPath.toString());
        }

        if (operation.monitor != null) {
            int unreachedCount = unreachedObjectIds != null ? unreachedObjectIds.size() : 0;
            operation.monitor.onModuleStart("_Migration", unreachedCount, 0);
        }

        Writer outputWriter = null;
        ClassExportConfig previousExportConfig = operation.exportConfig;
        Set<Long> previousAllowedObjectIds = operation.allowedObjectIds;

        try {
            operation.xsdBuilder = operation.sharedXSDBuilder != null ? operation.sharedXSDBuilder : new XSDBuilder(operation.dbContext);
            if (operation.sharedXSDBuilder == null) {
                operation.xsdBuilder.startExportRoot();
            }

            outputWriter = new FileWriter(xmlPath.toFile());
            operation.xmlWriter = new StructuredWriter(operation.getStructuredWriterAPI(), outputWriter, xmlPath);

            operation.exportNativeIds = operation.shouldExportNativeIdsForCurrentFormat();
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            operation.exportConfig = null;
            operation.allowedObjectIds = unreachedObjectIds != null ? new HashSet<>(unreachedObjectIds) : Collections.emptySet();

            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            if (operation.isXMLFormat()) {
                operation.xmlWriter.openRootStructure("export", ModulePathUtil.getSchemaLocationForXml(xmlPath, operation.baseOutputPath, operation));
            } else {
                operation.xmlWriter.openStructure("export");
            }
            operation.xmlWriter.metadata(getMetadata("_Migration", "Extra", unreachedObjectIds != null ? unreachedObjectIds.size() : 0));
            operation.xmlWriter.openStructure("objects");

            if (unreachedObjectIds != null && !unreachedObjectIds.isEmpty()) {
                List<Long> sortedIds = new ArrayList<>(unreachedObjectIds);
                Collections.sort(sortedIds);
                operation.statistics.setCurrentClass("_Migration.Extra", sortedIds.size());

                for (Long objectId : sortedIds) {
                    if (objectId == null || objectId <= 0) {
                        continue;
                    }
                    if (operation.monitor != null && operation.monitor.isCancelled()) {
                        break;
                    }
                    objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                }
            }

            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }
            // Extra is exported to XML only — no HTML viewer generated.

            if (operation.isXMLFormat() && operation.sharedXSDBuilder == null) {
                Path xsdPath = ModulePathUtil.getComprehensiveSchemaPath(operation.baseOutputPath, operation);
                Files.createDirectories(xsdPath.getParent());
                operation.xsdBuilder.writeXSD(xsdPath.toString());
            }

            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo("_Migration/Extra", xmlPath.toString());

            if (operation.monitor != null) {
                operation.monitor.onModuleComplete("_Migration");
            }

            return operation.statistics;

        } finally {
            operation.exportConfig = previousExportConfig;
            operation.allowedObjectIds = previousAllowedObjectIds;

            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Exports all objects in a module to XML file with custom XSD path.
     */
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath, String xsdOutputPath) throws Exception {

        String safeModuleName = ModulePathUtil.sanitizeModuleName(moduleName);

        // Initialize components
        operation.classNames = classNames;
        operation.monitor = null;
        operation.statistics = new ExportStatistics();
        operation.xsdBuilder = new XSDBuilder(operation.dbContext);
        operation.xsdBuilder.startExportRoot();
        operation.referencedClassTracker = null;

        Writer outputWriter = null;

        try {
            // Use the shared in-memory container (already opened by
            // DODatabaseService)
            // No need to open database here - saves time and memory

            // Create structured writer
            outputWriter = new FileWriter(outputPath);
            operation.xmlWriter = new StructuredWriter(operation.getStructuredWriterAPI(), outputWriter, Paths.get(outputPath));

            // Create export operation
            operation.baseOutputPath = outputPath;
            operation.exportNativeIds = operation.shouldExportNativeIdsForCurrentFormat();
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            // Write XML header and metadata
            // xmlWriter.writeExportHeader(moduleName, "module",
            // classNames.size(), null);
            if (operation.isXMLFormat()) {
                operation.xmlWriter.openRootStructure("export", null);
            } else {
                operation.xmlWriter.openStructure("export");
            }
            operation.xmlWriter.metadata(getMetadata(safeModuleName, null, classNames.size()));
            operation.xmlWriter.openStructure("objects");

            // Export all classes in the module
            for (String className : classNames) {
                DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
                if (dbSchemaClass != null) {
                    operation.xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
                    long[] objectIds = dbSchemaClass.objectIds;

                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                        }
                    }
                }
            }

            // Write XML footer
            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }
            new HtmlViewerTask(operation).generateIfNeeded(Paths.get(outputPath), null, null);

            // Generate XSD schema
            if (operation.isXMLFormat()) {
                String xsdPath = xsdOutputPath;
                if (xsdPath == null) {
                    xsdPath = outputPath.replace(".xml", ".xsd");
                }
                operation.xsdBuilder.writeXSD(xsdPath);
                System.out.println("Generated XSD schema: " + xsdPath);
            }

            // Generate duplicate warnings and set export info
            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo(safeModuleName, outputPath);

            return operation.statistics;

        } finally {
            // Note: We do NOT close the container here as it's a shared
            // resource managed by
            // DODatabaseService
            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public static StructuredWriterMetadata getMetadata(String module, String type, int objects) {
        StructuredWriterMetadata metadata = new StructuredWriterMetadata();
        metadata.generator = "Migration4o";
        metadata.provider = "Gestion Technologies";
        metadata.module = ModulePathUtil.sanitizeModuleName(module);
        metadata.type = type;
        metadata.objects = objects >= 0 ? String.valueOf(objects) : "";
        metadata.date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return metadata;
    }

}
