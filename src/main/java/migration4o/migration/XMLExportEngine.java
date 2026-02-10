package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.FileUtil;

/**
 * Orchestrates XML export operations by coordinating specialized components.
 * This class has been refactored to delegate to:
 * - XMLWriter: XML file generation
 * - ExportStatistics: tracking metrics and errors
 * - ObjectExporter: object graph traversal
 * - XSDBuilder: schema generation
 */
public class XMLExportEngine {
    private final DOSchema schema;
    private final DOSchema databaseSchema;
    private final String databasePath;
    private final ExtObjectContainer container;
    private Integer maxObjectsPerClass = null; // null = all objects
    private Set<Long> sharedExportedObjectIds = null; // Shared across module exports to prevent duplicate counting
    private XSDBuilder sharedXSDBuilder = null; // Shared XSD builder for comprehensive schema generation
    private Set<String> exportedXMLFiles = null; // Track XML files for validation

    /**
     * Creates export engine using the shared in-memory database from
     * DODatabaseService.
     * 
     * @param schema         The reference schema
     * @param databaseSchema The database schema
     * @param databasePath   The database file path (for naming output folders)
     */
    public XMLExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;

        // Get the shared in-memory container from the service
        this.container = DODatabaseService.getInstance().getContainer();

        if (container == null) {
            throw new IllegalStateException(
                    "No database is open. Please open a database first using DODatabaseService.");
        }
    }

    /**
     * Sets the maximum number of objects to export per class.
     * 
     * @param maxObjectsPerClass Maximum objects to export per class, or null for
     *                           all objects
     */
    public void setMaxObjectsPerClass(Integer maxObjectsPerClass) {
        this.maxObjectsPerClass = maxObjectsPerClass;
    }

    /**
     * Initializes shared object tracking and XSD builder for multi-module exports.
     * Call this before exporting multiple modules to ensure objects are only
     * counted once and a single comprehensive XSD is generated.
     */
    public void initializeSharedTracking() {
        this.sharedExportedObjectIds = new HashSet<>();
        this.sharedXSDBuilder = new XSDBuilder();
        this.sharedXSDBuilder.startExportRoot();
        this.exportedXMLFiles = new HashSet<>();
    }

    /**
     * Resets shared object tracking and XSD builder.
     * Call this to clear tracking state between different export sessions.
     */
    public void resetSharedTracking() {
        this.sharedExportedObjectIds = null;
        this.sharedXSDBuilder = null;
        this.exportedXMLFiles = null;
    }

    /**
     * Gets the list of exported XML files.
     * Only available when using shared tracking.
     * 
     * @return Set of XML file paths, or null if not using shared tracking
     */
    public Set<String> getExportedXMLFiles() {
        return exportedXMLFiles;
    }

    /**
     * Writes the comprehensive XSD schema file.
     * Should be called after all exports are complete when using shared tracking.
     * 
     * @param baseOutputPath The base output directory
     * @throws IOException if writing fails
     */
    public void writeComprehensiveXSD(String baseOutputPath) throws IOException {
        if (sharedXSDBuilder == null) {
            throw new IllegalStateException(
                    "Shared XSD builder not initialized. Call initializeSharedTracking() first.");
        }

        Path dbBasePath = getBaseOutputPath(baseOutputPath);
        Files.createDirectories(dbBasePath);

        Path xsdPath = dbBasePath.resolve("schema.xsd");
        sharedXSDBuilder.writeXSD(xsdPath.toString());
    }

    /**
     * Extracts the database folder name from the database path.
     * For example: "local/54060/BackupManuel.dat" -> "54060"
     */
    private String getDatabaseFolderName() {
        if (databasePath == null) {
            return "default";
        }

        Path path = Paths.get(databasePath);
        Path parent = path.getParent();

        if (parent != null) {
            return parent.getFileName().toString();
        }

        return "default";
    }

    /**
     * Gets the base output directory for the current database.
     * Returns: output/<database-folder>/
     */
    public Path getBaseOutputPath(String baseOutputDir) {
        String dbFolder = getDatabaseFolderName();
        return Paths.get(baseOutputDir).resolve(dbFolder);
    }

    /**
     * Exports all objects in a module to XML file (legacy flat structure).
     * 
     * @deprecated Use exportModuleStructured for folder-based export
     */
    @Deprecated
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath)
            throws Exception {
        return exportModule(classNames, moduleName, outputPath, null);
    }

    /**
     * Exports a module with folder structure matching the module hierarchy.
     * Each class is exported to its own XML/XSD file within the module folders.
     * Files are written to output/<db-folder>/
     * 
     * Automatically tracks and exports referenced classes that are not in the
     * module structure.
     * Referenced classes are exported to a virtual "Referenced" module.
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param sharedTracker  Shared reference tracker for bulk exports (can be null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(MigrationModule module, String baseOutputPath,
            DOExportMonitor monitor,
            ReferencedClassTracker sharedTracker)
            throws Exception {
        // Count total classes for progress reporting
        int totalClasses = countTotalClasses(module);

        if (monitor != null) {
            monitor.onExportStart(module.getName(), totalClasses);
        }

        // Initialize components
        ExportStatistics statistics = new ExportStatistics(monitor);

        // Use shared tracker if provided, otherwise create new one
        ReferencedClassTracker referencedClassTracker = sharedTracker != null
                ? sharedTracker
                : new ReferencedClassTracker();

        try {
            // Use the in-memory container (already open)

            // Create database-specific output directory: output/<db-folder>/
            Path dbBasePath = getBaseOutputPath(baseOutputPath);
            Files.createDirectories(dbBasePath);

            // Register all modules and their classes with the tracker
            registerModuleClasses(module, referencedClassTracker);

            // Export module recursively
            exportModuleRecursive(container, module, dbBasePath, statistics, monitor, 0,
                    referencedClassTracker);

            // Only export referenced classes if we created the tracker (not shared)
            // For shared trackers, the caller will handle referenced classes
            if (sharedTracker == null) {
                // After exporting requested modules, check for referenced classes
                Set<String> referencedClasses = referencedClassTracker.getReferencedClasses();
                if (!referencedClasses.isEmpty() && monitor != null) {
                    monitor.onStatusMessage(
                            "Discovered " + referencedClasses.size() + " referenced classes not in export request");
                }

                // Export referenced classes to a "Referenced" module
                if (!referencedClasses.isEmpty()) {
                    exportReferencedClasses(container, referencedClasses, dbBasePath,
                            statistics, monitor, referencedClassTracker);
                }
            }

            // Generate duplicate warnings and set export info
            String fullOutputPath = dbBasePath.toString();
            statistics.schemaWarnings.clear();
            statistics.schemaWarnings.addAll(statistics.duplicationDetector.generateDuplicateWarnings());
            statistics.setExportInfo(module.getName(), fullOutputPath);

            if (monitor != null) {
                monitor.onExportComplete(module.getName(), statistics.objectsSucceeded,
                        statistics.schemaWarnings.size());
            }

            return statistics;

        } catch (Exception e) {
            if (monitor != null) {
                monitor.onExportError(module.getName(), e.getMessage());
            }
            throw e;
        } finally {
            // Don't close container - it's shared and managed by MainWindow
        }
    }

    /**
     * Exports a module with folder structure (convenience method without shared
     * tracker).
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(MigrationModule module, String baseOutputPath,
            DOExportMonitor monitor)
            throws Exception {
        return exportModuleStructured(module, baseOutputPath, monitor, null);
    }

    /**
     * Exports referenced classes that were discovered during bulk export.
     * Should be called after all modules have been exported with a shared tracker.
     * 
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param tracker        The shared reference tracker from bulk export
     * @throws Exception if export fails
     */
    public void exportReferencedClasses(String baseOutputPath, DOExportMonitor monitor,
            ReferencedClassTracker tracker) throws Exception {

        Set<String> referencedClasses = tracker.getReferencedClasses();
        if (referencedClasses.isEmpty()) {
            return;
        }

        ExportStatistics statistics = new ExportStatistics(monitor);

        if (monitor != null) {
            monitor.onStatusMessage("Exporting " + referencedClasses.size() + " referenced classes");
        }

        try {
            Path dbBasePath = getBaseOutputPath(baseOutputPath);

            exportReferencedClasses(container, referencedClasses, dbBasePath,
                    statistics, monitor, tracker);

        } catch (Exception e) {
            if (monitor != null) {
                monitor.onExportError("Referenced", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Counts total number of classes in a module and all its children.
     */
    private int countTotalClasses(MigrationModule module) {
        int count = module.getClassNames().size();
        for (MigrationModule child : module.getChildModules()) {
            count += countTotalClasses(child);
        }
        return count;
    }

    /**
     * Recursively exports a module and its children to folder structure.
     */
    private void exportModuleRecursive(ExtObjectContainer container, MigrationModule module,
            Path currentBasePath,
            ExportStatistics statistics, DOExportMonitor monitor, int depth,
            ReferencedClassTracker referencedClassTracker) throws Exception {

        if (monitor != null) {
            monitor.onModuleStart(module.getName(), module.getClassConfigs().size(), depth);
        }

        // Create folder for this module (use module name to preserve proper casing)
        Path modulePath = currentBasePath.resolve(module.getName());
        Files.createDirectories(modulePath);

        // Export each class configuration in this module
        // System.out.println("DEBUG exportModuleRecursive: Module '" + module.getName()
        // + "' has "
        // + module.getClassConfigs().size() + " class configs");
        for (ClassExportConfig config : module.getClassConfigs()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }

            String className = config.getClassName();
            // System.out.println("DEBUG exportModuleRecursive: Processing class " +
            // className + ", hasCriteria="
            // + config.hasCriteria() + ", criteria count=" + config.getCriteria().size());

            // DEBUG: Log if this class has criteria
            if (config.hasCriteria()) {
                System.out.println("DEBUG: Exporting " + className + " with " + config.getCriteria().size()
                        + " criteria: " + config.getCriteria());
            }

            DOSchemaClass schemaClass = schema.findClassByName(className);
            if (schemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via monitor
            }

            DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via monitor
            }

            // Use the destination file name from config (defaults to class name if not set)
            String fileName = config.getDestinationFileName() + ".xml";
            String xsdFileName = config.getDestinationFileName() + ".xsd";
            Path xmlPath = modulePath.resolve(fileName);
            Path xsdPath = modulePath.resolve(xsdFileName);

            // Track XML file for validation if using shared tracking
            if (exportedXMLFiles != null) {
                exportedXMLFiles.add(xmlPath.toString());
            }

            // Export this class with criteria filtering
            exportClassToFile(container, schemaClass, dbSchemaClass, xmlPath, xsdPath, statistics, monitor,
                    referencedClassTracker, config);
        }

        // Recursively export child modules
        for (MigrationModule childModule : module.getChildModules()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }
            exportModuleRecursive(container, childModule, modulePath, statistics, monitor,
                    depth + 1, referencedClassTracker);
        }

        if (monitor != null) {
            monitor.onModuleComplete(module.getName());
        }
    }

    /**
     * Exports a single class to the specified file paths.
     * 
     * @param config Optional export configuration with criteria filtering. If null,
     *               exports all objects.
     */
    private void exportClassToFile(ExtObjectContainer container, DOSchemaClass schemaClass,
            DOSchemaClass dbSchemaClass, Path xmlPath, Path xsdPath,
            ExportStatistics statistics, DOExportMonitor monitor,
            ReferencedClassTracker referencedClassTracker, migration4o.models.ui.ClassExportConfig config)
            throws Exception {

        // Use shared XSD builder if available, otherwise create a new one
        XSDBuilder xsdBuilder = sharedXSDBuilder != null ? sharedXSDBuilder : new XSDBuilder();
        if (sharedXSDBuilder == null) {
            xsdBuilder.startExportRoot();
        }

        FileWriter fileWriter = null;

        try {
            // Create XML writer
            fileWriter = new FileWriter(xmlPath.toFile());
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create export operation
            ExportOperation operation = new ExportOperation();
            operation.referenceSchema = schema;
            operation.databaseSchema = databaseSchema;
            operation.databasePath = databasePath;
            operation.baseOutputPath = xmlPath.getParent().getParent().toString();
            operation.monitor = monitor;
            operation.maxObjectsPerClass = maxObjectsPerClass;
            operation.statistics = statistics;
            operation.exportConfig = config;
            operation.exportedObjectIds = sharedExportedObjectIds != null ? sharedExportedObjectIds : new HashSet<>();
            operation.useSharedTracking = (sharedExportedObjectIds != null);
            operation.referencedClassTracker = referencedClassTracker;

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(operation, xmlWriter, xsdBuilder);
            objectExporter.reset();

            // Copy the tracker state if we have a tracker
            if (referencedClassTracker != null) {
                for (String className : referencedClassTracker.getReferencedClasses()) {
                    operation.referencedClassTracker.registerReferencedClass(className);
                }
            }

            // Write XML header and metadata
            if (sharedXSDBuilder != null) {
                // Using shared XSD - calculate relative path from XML file to schema.xsd
                Path xmlDir = xmlPath.getParent();
                Path baseDir = xmlPath.getRoot() != null
                        ? xmlPath.getParent().getParent().getParent()
                        : Paths.get(operation.baseOutputPath).resolve(getDatabaseFolderName());

                // Calculate depth: count directories between XML file and database folder
                int slashCount = 0;
                Path current = xmlDir;
                while (current != null && !current.equals(baseDir)) {
                    slashCount++;
                    current = current.getParent();
                }

                // Build relative path
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i < slashCount; i++) {
                    pathBuilder.append("../");
                }
                pathBuilder.append("schema.xsd");
                String relativeSchemaPath = pathBuilder.toString();

                xmlWriter.writeXMLHeader();
                xmlWriter.writeExportHeaderWithSchema(schemaClass.source, relativeSchemaPath);
            } else {
                // Individual XSD - no schema reference needed (pass null for schemaLocation)
                xmlWriter.writeXMLHeader();
                xmlWriter.writeExportHeaderWithSchema(schemaClass.source, null);
            }

            // Export all objects of this class
            xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
            long[] objectIds = dbSchemaClass.objectIds;
            int objectCount = (objectIds != null ? objectIds.length : 0);

            // Apply object limit if set
            int actualCount = objectCount;
            if (maxObjectsPerClass != null && objectCount > maxObjectsPerClass) {
                actualCount = maxObjectsPerClass;
            }

            if (monitor != null) {
                monitor.onClassStart(schemaClass.source, schemaClass.destinationName, actualCount);
            }

            if (objectIds != null) {
                statistics.setCurrentClass(schemaClass.source, actualCount);

                // Export up to maxObjectsPerClass objects that match criteria
                int exportedCount = 0;
                for (long objectId : objectIds) {
                    if (monitor != null && monitor.isCancelled()) {
                        break;
                    }

                    if (maxObjectsPerClass != null && exportedCount >= maxObjectsPerClass) {
                        break; // Stop at limit
                    }

                    objectExporter.exportObjectRecursively(container, objectId, 2);
                    exportedCount++;
                }
            }

            // Merge discovered references back to main tracker
            if (referencedClassTracker != null) {
                for (String className : objectExporter.getReferencedClassTracker().getReferencedClasses()) {
                    referencedClassTracker.registerReferencedClass(className);
                }
            }

            // Write XML footer
            xmlWriter.writeExportFooter();

            // Only generate individual XSD if not using shared builder
            if (sharedXSDBuilder == null) {
                if (monitor != null) {
                    monitor.onXSDGenerationStart(xsdPath.toString());
                }

                // Generate XSD schema
                xsdBuilder.writeXSD(xsdPath.toString());

                if (monitor != null) {
                    monitor.onXSDGenerationComplete(xsdPath.toString());
                }
            }

            if (monitor != null) {
                int exportedCount = statistics.objectsSucceeded;
                monitor.onClassComplete(schemaClass.source, exportedCount);
            }

        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Recursively registers all modules and their classes with the reference
     * tracker.
     */
    private void registerModuleClasses(MigrationModule module, ReferencedClassTracker tracker) {
        Set<String> classNames = new HashSet<>(module.getClassNames());
        tracker.registerModule(module.getName(), classNames);

        for (MigrationModule childModule : module.getChildModules()) {
            registerModuleClasses(childModule, tracker);
        }
    }

    /**
     * Exports referenced classes that were discovered during export but not in the
     * original request.
     * Creates a "Referenced" module to hold these classes.
     */
    private void exportReferencedClasses(ExtObjectContainer container, Set<String> referencedClasses,
            Path basePath, ExportStatistics statistics, DOExportMonitor monitor,
            ReferencedClassTracker referencedClassTracker) throws Exception {

        if (monitor != null) {
            monitor.onModuleStart("Referenced", referencedClasses.size(), 0);
        }

        // Create "Referenced" module folder
        Path referencedPath = basePath.resolve("Referenced");
        Files.createDirectories(referencedPath);

        for (String className : referencedClasses) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }

            // Skip if already exported as a referenced class
            if (referencedClassTracker.isReferencedClassExported(className)) {
                continue;
            }

            DOSchemaClass schemaClass = schema.findClassByName(className);
            if (schemaClass == null) {
                if (monitor != null) {
                    monitor.onStatusMessage("Referenced class not found in schema: " + className);
                }
                continue;
            }

            DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                if (monitor != null) {
                    monitor.onStatusMessage("Referenced class not found in database: " + className);
                }
                continue;
            }

            // Generate file name from destination class name
            String fileName = schemaClass.destinationName + ".xml";
            String xsdFileName = schemaClass.destinationName + ".xsd";
            Path xmlPath = referencedPath.resolve(fileName);
            Path xsdPath = referencedPath.resolve(xsdFileName);

            // Export this referenced class (without further reference tracking to avoid
            // infinite loops, and without criteria filtering)
            exportClassToFile(container, schemaClass, dbSchemaClass, xmlPath, xsdPath, statistics, monitor, null, null);

            // Mark as exported
            referencedClassTracker.markReferencedClassAsExported(className);

            if (monitor != null) {
                monitor.onStatusMessage("Exported referenced class: " + schemaClass.destinationName);
            }
        }

        if (monitor != null) {
            monitor.onModuleComplete("Referenced");
        }
    }

    /**
     * Exports all objects in a module to XML file with custom XSD path.
     */
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath,
            String xsdOutputPath) throws Exception {

        // Initialize components
        ExportStatistics statistics = new ExportStatistics();
        XSDBuilder xsdBuilder = new XSDBuilder();
        xsdBuilder.startExportRoot();

        FileWriter fileWriter = null;

        try {
            // Use the shared in-memory container (already opened by DODatabaseService)
            // No need to open database here - saves time and memory

            // Create XML writer
            fileWriter = new FileWriter(outputPath);
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create export operation
            ExportOperation operation = new ExportOperation();
            operation.referenceSchema = schema;
            operation.databaseSchema = databaseSchema;
            operation.databasePath = databasePath;
            operation.baseOutputPath = outputPath;
            operation.maxObjectsPerClass = maxObjectsPerClass;
            operation.statistics = statistics;
            operation.exportedObjectIds = sharedExportedObjectIds != null ? sharedExportedObjectIds : new HashSet<>();
            operation.useSharedTracking = (sharedExportedObjectIds != null);

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(operation, xmlWriter, xsdBuilder);
            objectExporter.reset();

            // Write XML header and metadata
            xmlWriter.writeXMLHeader();
            xmlWriter.writeModuleHeader(moduleName, classNames.size());

            // Export all classes in the module
            for (String className : classNames) {
                DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
                if (dbSchemaClass != null) {
                    xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
                    long[] objectIds = dbSchemaClass.objectIds;

                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            objectExporter.exportObjectRecursively(container, objectId, 2);
                        }
                    }
                }
            }

            // Write XML footer
            xmlWriter.writeExportFooter();

            // Generate XSD schema
            String xsdPath = xsdOutputPath;
            if (xsdPath == null) {
                xsdPath = outputPath.replace(".xml", ".xsd");
            }
            xsdBuilder.writeXSD(xsdPath);
            System.out.println("Generated XSD schema: " + xsdPath);

            // Generate duplicate warnings and set export info
            statistics.schemaWarnings.clear();
            statistics.schemaWarnings.addAll(statistics.duplicationDetector.generateDuplicateWarnings());
            statistics.setExportInfo(moduleName, outputPath);

            return statistics;

        } finally {
            // Note: We do NOT close the container here as it's a shared resource managed by
            // DODatabaseService
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

}
