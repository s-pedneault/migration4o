package migration4o.engine.export;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseOpener;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.engine.export.monitoring.ExportStatistics;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.MigrationModule;

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

    public XMLExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;
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
     * @return Export result
     * @throws Exception if export fails
     */
    public ExportResult exportClass(String className, String baseOutputDir) throws Exception {
        DOSchemaClass schemaClass = schema.findClassByName(className);
        if (schemaClass == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }

        // Initialize components
        ExportStatistics statistics = new ExportStatistics();
        XSDBuilder xsdBuilder = new XSDBuilder();
        xsdBuilder.startExportRoot();

        ExtObjectContainer container = null;
        FileWriter fileWriter = null;

        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

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
                System.out.println("DEBUG: Found " + (objectIds != null ? objectIds.length : 0)
                        + " objects for class " + className);

                if (objectIds != null) {
                    for (long objectId : objectIds) {
                        objectExporter.exportObjectRecursively(container, objectId, 2);
                    }
                }
            } else {
                System.err.println("Warning: Class not found in database schema: " + className);
            }

            // Write XML footer
            xmlWriter.writeExportFooter();

            // Generate XSD schema
            xsdBuilder.writeXSD(xsdPath.toString());
            System.out.println("Generated XSD schema: " + xsdPath);

            // Print summary and create result
            String fullOutputPath = dbBasePath.toString();
            statistics.printSummary(fullOutputPath, className);
            return statistics.createResult(className, fullOutputPath);

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
     * @return ExportResult with details about the export operation
     * @throws Exception if export fails
     */
    public ExportResult exportModuleStructured(MigrationModule module, String baseOutputPath) throws Exception {
        // Initialize components
        ExportStatistics statistics = new ExportStatistics();

        ExtObjectContainer container = null;

        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

            // Create database-specific output directory: output/<db-folder>/
            Path dbBasePath = getBaseOutputPath(baseOutputPath);
            Path dataPath = dbBasePath.resolve("Data");
            Path defsPath = dbBasePath.resolve("Definitions");

            Files.createDirectories(dataPath);
            Files.createDirectories(defsPath);

            // Export module recursively
            exportModuleRecursive(container, module, dataPath, defsPath, statistics);

            // Print summary and create result
            String fullOutputPath = dbBasePath.toString();
            statistics.printSummary(fullOutputPath, module.getName());
            return statistics.createResult(module.getName(), fullOutputPath);

        } finally {
            if (container != null) {
                container.close();
            }
        }
    }

    /**
     * Recursively exports a module and its children to folder structure.
     * Creates parallel folder hierarchy in both Data and Definitions paths.
     */
    private void exportModuleRecursive(ExtObjectContainer container, MigrationModule module,
            Path currentDataPath, Path currentDefsPath,
            ExportStatistics statistics) throws Exception {
        // Create folder for this module in both Data and Definitions
        // Use module name (not ID) to preserve proper casing
        Path moduleDataPath = currentDataPath.resolve(sanitizeFolderName(module.getName()));
        Path moduleDefsPath = currentDefsPath.resolve(sanitizeFolderName(module.getName()));
        Files.createDirectories(moduleDataPath);
        Files.createDirectories(moduleDefsPath);

        // Export each class in this module to its own file
        for (String className : module.getClassNames()) {
            DOSchemaClass schemaClass = schema.findClassByName(className);
            if (schemaClass == null) {
                System.err.println("Warning: Class not found in schema: " + className);
                continue;
            }

            DOSchemaClass dbSchemaClass = databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                System.err.println("Warning: Class not found in database schema: " + className);
                continue;
            }

            // Generate file name from destination class name
            String fileName = schemaClass.destinationName + ".xml";
            String xsdFileName = schemaClass.destinationName + ".xsd";
            Path xmlPath = moduleDataPath.resolve(fileName);
            Path xsdPath = moduleDefsPath.resolve(xsdFileName);

            // Export this class
            exportClassToFile(container, schemaClass, dbSchemaClass, xmlPath, xsdPath, statistics);
        }

        // Recursively export child modules
        for (MigrationModule childModule : module.getChildModules()) {
            exportModuleRecursive(container, childModule, moduleDataPath, moduleDefsPath, statistics);
        }
    }

    /**
     * Exports a single class to the specified file paths.
     */
    private void exportClassToFile(ExtObjectContainer container, DOSchemaClass schemaClass,
            DOSchemaClass dbSchemaClass, Path xmlPath, Path xsdPath,
            ExportStatistics statistics) throws Exception {
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

            if (objectIds != null) {
                for (long objectId : objectIds) {
                    objectExporter.exportObjectRecursively(container, objectId, 2);
                }
            }

            // Write XML footer
            xmlWriter.writeExportFooter();

            // Generate XSD schema
            xsdBuilder.writeXSD(xsdPath.toString());

            System.out.println("Exported " + schemaClass.source + " to " + xmlPath);

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

        ExtObjectContainer container = null;
        FileWriter fileWriter = null;

        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

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
                } else {
                    System.err.println("Warning: Class not found in database schema: " + className);
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
