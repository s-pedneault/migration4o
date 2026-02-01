package migration4o.engine.export;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        XSDBuilder xsdBuilder = new XSDBuilder();
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
            if (container != null) {
                container.close();
            }
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
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor)
            throws Exception {
        // Count total classes for progress reporting
        int totalClasses = countTotalClasses(module);

        if (monitor != null) {
            monitor.onExportStart(module.getName(), totalClasses);
        }

        // Initialize components
        ExportStatistics statistics = new ExportStatistics(monitor);

        try {
            // Use the in-memory container (already open)

            // Create database-specific output directory: output/<db-folder>/
            Path dbBasePath = getBaseOutputPath(baseOutputPath);
            Path dataPath = dbBasePath.resolve("Data");
            Path defsPath = dbBasePath.resolve("Definitions");

            Files.createDirectories(dataPath);
            Files.createDirectories(defsPath);

            // Export module recursively
            exportModuleRecursive(container, module, dataPath, defsPath, statistics, monitor, 0);

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
            ExportStatistics statistics, DOExportMonitor monitor, int depth) throws Exception {

        if (monitor != null) {
            monitor.onModuleStart(module.getName(), module.getClassNames().size(), depth);
        }

        // Create folder for this module in both Data and Definitions
        // Use module name (not ID) to preserve proper casing
        Path moduleDataPath = currentDataPath.resolve(sanitizeFolderName(module.getName()));
        Path moduleDefsPath = currentDefsPath.resolve(sanitizeFolderName(module.getName()));
        Files.createDirectories(moduleDataPath);
        Files.createDirectories(moduleDefsPath);

        // Export each class in this module to its own file
        for (String className : module.getClassNames()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }

            DOSchemaClass schemaClass = schema.findClassByName(className);
            if (schemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via monitor
            }

            DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via monitor
            }

            // Generate file name from destination class name
            String fileName = schemaClass.destinationName + ".xml";
            String xsdFileName = schemaClass.destinationName + ".xsd";
            Path xmlPath = moduleDataPath.resolve(fileName);
            Path xsdPath = moduleDefsPath.resolve(xsdFileName);

            // Export this class
            exportClassToFile(container, schemaClass, dbSchemaClass, xmlPath, xsdPath, statistics, monitor);
        }

        // Recursively export child modules
        for (MigrationModule childModule : module.getChildModules()) {
            if (monitor != null && monitor.isCancelled()) {
                break;
            }
            exportModuleRecursive(container, childModule, moduleDataPath, moduleDefsPath, statistics, monitor,
                    depth + 1);
        }

        if (monitor != null) {
            monitor.onModuleComplete(module.getName());
        }
    }

    /**
     * Exports a single class to the specified file paths.
     */
    private void exportClassToFile(ExtObjectContainer container, DOSchemaClass schemaClass,
            DOSchemaClass dbSchemaClass, Path xmlPath, Path xsdPath,
            ExportStatistics statistics, DOExportMonitor monitor) throws Exception {

        XSDBuilder xsdBuilder = new XSDBuilder();
        xsdBuilder.startExportRoot();

        FileWriter fileWriter = null;

        try {
            // Create XML writer
            fileWriter = new FileWriter(xmlPath.toFile());
            XMLWriter xmlWriter = new XMLWriter(fileWriter);

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(schema, databaseSchema, xmlWriter,
                    xsdBuilder, statistics);
            objectExporter.reset();

            // Write XML header and metadata
            xmlWriter.writeXMLHeader();
            xmlWriter.writeExportHeader(schemaClass.source);

            // Export all objects of this class
            xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
            long[] objectIds = dbSchemaClass.objectIds;
            int objectCount = (objectIds != null ? objectIds.length : 0);

            if (monitor != null) {
                monitor.onClassStart(schemaClass.source, schemaClass.destinationName, objectCount);
            }

            if (objectIds != null) {
                statistics.setCurrentClass(schemaClass.source, objectIds.length);

                for (long objectId : objectIds) {
                    if (monitor != null && monitor.isCancelled()) {
                        break;
                    }
                    objectExporter.exportObjectRecursively(container, objectId, 2);
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
     * Sanitizes a folder name by removing or replacing invalid characters.
     */
    private String sanitizeFolderName(String name) {
        if (name == null) {
            return "unnamed";
        }
        // Replace invalid characters with underscores
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Exports all objects in a module to XML file with custom XSD path.
     */
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath,
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
            if (container != null) {
                container.close();
            }
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
