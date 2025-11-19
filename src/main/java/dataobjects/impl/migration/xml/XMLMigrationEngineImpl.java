package dataobjects.impl.migration.xml;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.migration.xml.DOXMLMigrationEngine;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaModule;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implementation of XML migration engine.
 * Orchestrates schema generation, data export, and report generation.
 */
public class XMLMigrationEngineImpl implements DOXMLMigrationEngine {

    private static final String DEFAULT_OUTPUT_DIR = "output/migration";
    private static final String DATA_DIR = "data";
    private static final String SCHEMA_NAMESPACE = "migration4o";

    @Override
    public void exportToXML(DOEngine engine) throws IOException {
        exportToXML(engine, DEFAULT_OUTPUT_DIR);
    }

    @Override
    public void exportToXML(DOEngine engine, String outputDirectory) throws IOException {
        System.out.println("=== Starting XML Migration Export ===");
        System.out.println("Output directory: " + outputDirectory);
        System.out.println("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        // Create output directories
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File dataDir = new File(outputDirectory, DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        DOSchema schema = engine.getSchema();
        if (schema == null || schema.getModules() == null) {
            throw new IOException("Schema or modules not available");
        }

        // Step 1: Generate XSD schema
        System.out.println("\n--- Step 1: Generating XML Schema ---");
        XMLSchemaGenerator schemaGenerator = new XMLSchemaGenerator(engine, SCHEMA_NAMESPACE);
        String schemaFilePath = outputDirectory + "/premligne-schema.xml";
        schemaGenerator.generateSchema(schemaFilePath);
        System.out.println("Schema generated: " + schemaFilePath);

        // Step 2: Export data files
        System.out.println("\n--- Step 2: Exporting Data Files ---");
        XMLDataExporter dataExporter = new XMLDataExporter(engine, SCHEMA_NAMESPACE);
        
        // Export one file per module
        for (DOSchemaModule module : schema.getModules()) {
            String moduleFilePath = dataDir.getAbsolutePath() + "/" + sanitizeFileName(module.getName()) + ".xml";
            dataExporter.exportModule(module, moduleFilePath);
            System.out.println("Module exported: " + module.getName());
        }

        // Export unreached objects
        String unreachedFilePath = dataDir.getAbsolutePath() + "/unreached.xml";
        dataExporter.exportUnreachedObjects(unreachedFilePath);
        System.out.println("Unreached objects exported");

        // Step 3: Generate migration report
        System.out.println("\n--- Step 3: Generating Migration Report ---");
        XMLReportGenerator reportGenerator = new XMLReportGenerator(engine);
        String reportFilePath = outputDirectory + "/premligne-report.xml";
        reportGenerator.generateReport(reportFilePath);
        System.out.println("Report generated: " + reportFilePath);

        System.out.println("\n=== XML Migration Export Complete ===");
    }

    /**
     * Sanitize a filename by replacing problematic characters.
     */
    private String sanitizeFileName(String name) {
        if (name == null) {
            return "unnamed";
        }
        // Replace any non-alphanumeric chars (except dots and dashes) with underscores
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
