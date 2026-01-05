package migration4o.engine.migration;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;

import migration4o.engine.DOEngine;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaModule;

/**
 * Streamlined XML export engine.
 * Orchestrates schema generation, data export, and report generation for XML
 * format only.
 */
public class XMLMigrationEngine {

    private static final String DEFAULT_OUTPUT_DIR = "output/migration";
    private static final String DATA_DIR = "data";
    private static final String SCHEMA_NAMESPACE = "migration4o";

    public void exportToXML(DOEngine engine) throws IOException {
        exportToXML(engine, DEFAULT_OUTPUT_DIR);
    }

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
            String moduleFilePath = dataDir.getAbsolutePath() + "/"
                    + sanitizeModuleName(module.getName()) + ".xml";
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
     * Remove accents from text by normalizing and removing diacritical marks.
     */
    private static String removeAccents(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Sanitize a module name for use as a file name.
     * Removes or replaces characters that are not valid in file names.
     */
    private static String sanitizeModuleName(String moduleName) {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            return "unnamed_module";
        }

        // Remove accents first
        String withoutAccents = removeAccents(moduleName.trim());

        // Replace spaces and special characters with underscores
        String sanitized = withoutAccents
                .replaceAll("[\\s\\-\\.]", "_")
                .replaceAll("[^a-zA-Z0-9_]", "");

        // Ensure it's not empty
        if (sanitized.isEmpty()) {
            sanitized = "module";
        }

        return sanitized;
    }
}
