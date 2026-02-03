package migration4o.engine.export;

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
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.engine.export.monitoring.ExportStatistics;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;

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
    private Path getBaseOutputPath(String baseOutputDir) {
        String dbFolder = getDatabaseFolderName();
        return Paths.get(baseOutputDir).resolve(dbFolder);
    }

    /**
     * Exports objects of a specific class to XML file in Data/Definitions
     * structure.
     * The file will be saved to output/<db-folder>/Data/ and
     * output/<db-folder>/Definitions/
     * 
     * @param className     The class name to export
     * @param baseOutputDir The base output directory (typically "output")
     * @param monitor       Progress monitor (can be null)
     * @return Export result
     * @throws Exception if export fails
     */
    public ExportResult exportClass(String className, String baseOutputDir, DOExportMonitor monitor) throws Exception {
        if (monitor != null) {
            monitor.onExportStart(className, 1); // 1 class to export
        }

        DOSchemaClass schemaClass = schema.findClassByName(className);
        if (schemaClass == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }

        // Initialize components
        ExportStatistics statistics = new ExportStatistics(monitor);
        XSDBuilder xsdBuilder = new XSDBuilder(schema, databaseSchema);
        xsdBuilder.startExportRoot();

        FileWriter fileWriter = null;

        try {
            // Use the shared in-memory container (already opened by DODatabaseService)
            // No need to open database here - saves time and memory

            // Create database-specific output paths
            Path dbBasePath = getBaseOutputPath(baseOutputDir);
            Path dataPath = dbBasePath.resolve("Data");
            Path defsPath = dbBasePath.resolve("Definitions");
            Files.createDirectories(dataPath);
            Files.createDirectories(defsPath);

            // Create file paths for XML and XSD
            String fileName = schemaClass.destinationName + ".xml";
            String xsdFileName = schemaClass.destinationName + ".xsd";
            Path xmlPath = dataPath.resolve(fileName);
            Path xsdPath = defsPath.resolve(xsdFileName);

            // Create XML writer
            fileWriter = new FileWriter(xmlPath.toFile());
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(schema, databaseSchema, xmlWriter,
                    xsdBuilder, statistics);
            objectExporter.reset();

            // Write XML header and metadata
            xmlWriter.writeXMLHeader();
            xmlWriter.writeExportHeader(className);

            // Export all objects of this class
            DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
            if (dbSchemaClass != null) {
                xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
                long[] objectIds = dbSchemaClass.objectIds;
                int objectCount = (objectIds != null ? objectIds.length : 0);

                if (monitor != null) {
                    monitor.onClassStart(className, schemaClass.destinationName, objectCount);
                }

                if (objectIds != null) {
                    statistics.setCurrentClass(className, objectIds.length);

                    for (long objectId : objectIds) {
                        if (monitor != null && monitor.isCancelled()) {
                            break;
                        }
                        objectExporter.exportObjectRecursively(container, objectId, 2);
                    }
                }
            }
            // If class not in database schema, just continue with empty export

            // Write XML footer
            xmlWriter.writeExportFooter();

            if (monitor != null) {
                monitor.onXSDGenerationStart(xsdPath.toString());
            }

            // Generate XSD schema
            xsdBuilder.writeXSD(xsdPath.toString());

            if (monitor != null) {
                monitor.onXSDGenerationComplete(xsdPath.toString());
                int exportedCount = statistics.getObjectsSucceeded();
                monitor.onClassComplete(className, exportedCount);
            }

            // Print summary and create result
            String fullOutputPath = dbBasePath.toString();
            statistics.printSummary(fullOutputPath, className);

            ExportResult result = statistics.createResult(className, fullOutputPath);

            if (monitor != null) {
                monitor.onExportComplete(className, statistics.getObjectsSucceeded(),
                        statistics.getSchemaWarnings().size());
            }

            return result;

        } catch (Exception e) {
            if (monitor != null) {
                monitor.onExportError(className, e.getMessage());
            }
            throw e;
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

    /**
     * Exports all objects in a module to XML file (legacy flat structure).
     * 
     * @deprecated Use exportModuleStructured for folder-based export
     */
    @Deprecated
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        return exportModule(classNames, moduleName, outputPath, null);
    }

    /**
     * Exports a module with folder structure matching the module hierarchy.
     * Each class is exported to its own XML/XSD file within the module folders.
     * XML files go to output/<db-folder>/Data/, XSD files go to
     * output/<db-folder>/Definitions/
     * 
     * Automatically tracks and exports referenced classes that are not in the
     * module structure.
     * Referenced classes are exported to a virtual "Referenced" module.
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param sharedTracker  Shared reference tracker for bulk exports (can be null)
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor,
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
            Path dataPath = dbBasePath.resolve("Data");
            Path defsPath = dbBasePath.resolve("Definitions");

            Files.createDirectories(dataPath);
            Files.createDirectories(defsPath);

            // Register all modules and their classes with the tracker
            registerModuleClasses(module, referencedClassTracker);

            // Export module recursively
            exportModuleRecursive(container, module, dataPath, defsPath, statistics, monitor, 0,
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
                    exportReferencedClasses(container, referencedClasses, dataPath, defsPath,
                            statistics, monitor, referencedClassTracker);
                }
            }

            // Print summary and create result
            String fullOutputPath = dbBasePath.toString();
            statistics.printSummary(fullOutputPath, module.getName());

            ExportResult result = statistics.createResult(module.getName(), fullOutputPath);

            if (monitor != null) {
                monitor.onExportComplete(module.getName(), statistics.getObjectsSucceeded(),
                        statistics.getSchemaWarnings().size());
            }

            return result;

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
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor)
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
            Path dataPath = dbBasePath.resolve("Data");
            Path defsPath = dbBasePath.resolve("Definitions");

            exportReferencedClasses(container, referencedClasses, dataPath, defsPath,
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
     * Creates parallel folder hierarchy in both Data and Definitions paths.
     */
    private void exportModuleRecursive(ExtObjectContainer container, MigrationModule module,
            Path currentDataPath, Path currentDefsPath,
            ExportStatistics statistics, DOExportMonitor monitor, int depth,
            ReferencedClassTracker referencedClassTracker) throws Exception {

        if (monitor != null) {
            monitor.onModuleStart(module.getName(), module.getClassConfigs().size(), depth);
        }

        // Create folder for this module in both Data and Definitions
        // Use module name (not ID) to preserve proper casing
        Path moduleDataPath = currentDataPath.resolve(migration4o.util.FileUtil.sanitizeName(module.getName()));
        Path moduleDefsPath = currentDefsPath.resolve(migration4o.util.FileUtil.sanitizeName(module.getName()));
        Files.createDirectories(moduleDataPath);
        Files.createDirectories(moduleDefsPath);

        // Export each class configuration in this module
        for (migration4o.models.ui.ClassExportConfig config : module.getClassConfigs()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }

            String className = config.getClassName();
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
            Path xmlPath = moduleDataPath.resolve(fileName);
            Path xsdPath = moduleDefsPath.resolve(xsdFileName);

            // Export this class with criteria filtering
            exportClassToFile(container, schemaClass, dbSchemaClass, xmlPath, xsdPath, statistics, monitor,
                    referencedClassTracker, config);
        }

        // Recursively export child modules
        for (MigrationModule childModule : module.getChildModules()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }
            exportModuleRecursive(container, childModule, moduleDataPath, moduleDefsPath, statistics, monitor,
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

        XSDBuilder xsdBuilder = new XSDBuilder(schema, databaseSchema);
        xsdBuilder.startExportRoot();

        FileWriter fileWriter = null;

        try {
            // Create XML writer
            fileWriter = new FileWriter(xmlPath.toFile());
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create object exporter with export configuration for filtering
            ObjectExporter objectExporter = new ObjectExporter(schema, databaseSchema, xmlWriter,
                    xsdBuilder, statistics, config);
            objectExporter.reset();

            // Enable reference tracking if we have a tracker
            if (referencedClassTracker != null) {
                objectExporter.setReferenceTracking(true);
                // Share the same tracker instance
                objectExporter.getReferencedClassTracker().reset();
                // Copy the tracker state
                for (String className : referencedClassTracker.getReferencedClasses()) {
                    objectExporter.getReferencedClassTracker().registerReferencedClass(className);
                }
            }

            // Write XML header and metadata
            xmlWriter.writeXMLHeader();
            xmlWriter.writeExportHeader(schemaClass.source);

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

            if (monitor != null) {
                monitor.onXSDGenerationStart(xsdPath.toString());
            }

            // Generate XSD schema
            xsdBuilder.writeXSD(xsdPath.toString());

            if (monitor != null) {
                monitor.onXSDGenerationComplete(xsdPath.toString());
            }

            if (monitor != null) {
                int exportedCount = statistics.getObjectsSucceeded();
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
            Path dataPath, Path defsPath, ExportStatistics statistics, DOExportMonitor monitor,
            ReferencedClassTracker referencedClassTracker) throws Exception {

        if (monitor != null) {
            monitor.onModuleStart("Referenced", referencedClasses.size(), 0);
        }

        // Create "Referenced" module folder
        Path referencedDataPath = dataPath.resolve("Referenced");
        Path referencedDefsPath = defsPath.resolve("Referenced");
        Files.createDirectories(referencedDataPath);
        Files.createDirectories(referencedDefsPath);

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
            Path xmlPath = referencedDataPath.resolve(fileName);
            Path xsdPath = referencedDefsPath.resolve(xsdFileName);

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
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath,
            String xsdOutputPath) throws Exception {

        // Initialize components
        ExportStatistics statistics = new ExportStatistics();
        XSDBuilder xsdBuilder = new XSDBuilder(schema, databaseSchema);
        xsdBuilder.startExportRoot();

        FileWriter fileWriter = null;

        try {
            // Use the shared in-memory container (already opened by DODatabaseService)
            // No need to open database here - saves time and memory

            // Create XML writer
            fileWriter = new FileWriter(outputPath);
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(schema, databaseSchema, xmlWriter,
                    xsdBuilder, statistics);
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

            // Print summary and create result
            statistics.printSummary(outputPath, moduleName);
            return statistics.createResult(moduleName, outputPath);

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
