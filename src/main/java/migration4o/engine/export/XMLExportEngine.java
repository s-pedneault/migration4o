package migration4o.engine.export;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseOpener;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.engine.export.monitoring.ExportStatistics;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

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
     * Exports objects of a specific class to XML file.
     */
    public ExportResult exportClass(String className, String outputPath) throws Exception {
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

            // Create XML writer
            fileWriter = new FileWriter(outputPath);
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
            String xsdPath = outputPath.replace(".xml", ".xsd");
            xsdBuilder.writeXSD(xsdPath);
            System.out.println("Generated XSD schema: " + xsdPath);

            // Print summary and create result
            statistics.printSummary(outputPath, className);
            return statistics.createResult(className, outputPath);

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
     * Exports all objects in a module to XML file.
     */
    public ExportResult exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        return exportModule(classNames, moduleName, outputPath, null);
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
