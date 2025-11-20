package dataobjects;

import java.io.FileNotFoundException;
import java.io.IOException;

import dataobjects.api.engine.DOEngine;
// Deprecated migration analysis - moved to deprecated package
// import dataobjects.api.deprecated.migration.DOPreAnalysis;
import dataobjects.api.report.DOStructureReportGenerator;
import dataobjects.api.report.DOObjectTreeReportGenerator;
import dataobjects.api.report.DOReachabilityReportGenerator;
import dataobjects.api.migration.excel.DOExcelExportEngine;
import dataobjects.api.migration.xml.DOXMLMigrationEngine;
import dataobjects.api.migration.generic.DOGenericExportEngine;
import dataobjects.impl.engine.DOEngineImpl;
// import dataobjects.impl.deprecated.migration.DOPreAnalysisImpl;
import dataobjects.impl.migration.excel.ExcelFormatHandler;
import dataobjects.factory.DOFactory;

public class DataObjectAPI {

    public static DOEngine newEngine(String schemaPath, String databasePath) throws IOException {
        return new DOEngineImpl(schemaPath, databasePath);
    }

    /**
     * Convenience method to print the complete hierarchy of a DOEngine.
     * Delegates to DOEnginePrintout for the actual printing logic.
     */
    public static void printEngineHierarchy(DOEngine engine) {
        DOEnginePrintout printout = new DOEnginePrintout();
        printout.printEngineHierarchy(engine);
    }

    // DEPRECATED METHODS - Commented out because deprecated package is not compiled
    // Uncomment if you need to use the old migration analysis (requires compiling
    // deprecated classes)

    /*
     * /**
     * Create a new migration pre-analysis for the given engine.
     * 
     * @param engine The engine to analyze
     * 
     * @return Migration pre-analysis instance
     * 
     * @deprecated Moved to deprecated package. Use Excel export instead.
     *
     * @Deprecated
     * public static DOPreAnalysis createPreAnalysis(DOEngine engine) {
     * return new DOPreAnalysisImpl(engine);
     * }
     * 
     * /**
     * Convenience method to run and print a complete migration analysis.
     * 
     * @param engine The engine to analyze
     * 
     * @deprecated Moved to deprecated package. Use Excel export instead.
     *
     * @Deprecated
     * public static void analyzeMigration(DOEngine engine) {
     * DOPreAnalysis analysis = createPreAnalysis(engine);
     * analysis.printAnalysisReport();
     * }
     */

    /**
     * Generate a comprehensive HTML report of database structure and contents.
     * Creates "Database contents.html" in the output directory.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the report file
     */
    public static void generateStructureReport(DOEngine engine) throws IOException {
        DOStructureReportGenerator generator = DOFactory.createReportGenerator();
        generator.generateDefaultReport(engine);
    }

    /**
     * Generate a comprehensive HTML report of database structure and contents.
     * 
     * @param engine     The fully-loaded DOEngine instance
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    public static void generateStructureReport(DOEngine engine, String outputPath) throws IOException {
        DOStructureReportGenerator generator = DOFactory.createReportGenerator();
        generator.generateReport(engine, outputPath);
    }

    /**
     * Generate an interactive HTML tree report of database objects.
     * Creates "Object Tree Report.html" in the output directory.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the report file
     */
    public static void generateTreeReport(DOEngine engine) throws IOException {
        DOObjectTreeReportGenerator generator = DOFactory.createTreeReportGenerator();
        generator.generateDefaultReport(engine);
    }

    /**
     * Generate an interactive HTML tree report of database objects.
     * 
     * @param engine     The fully-loaded DOEngine instance
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    public static void generateTreeReport(DOEngine engine, String outputPath) throws IOException {
        DOObjectTreeReportGenerator generator = DOFactory.createTreeReportGenerator();
        generator.generateReport(engine, outputPath);
    }

    /**
     * Generate a comprehensive HTML reachability analysis report.
     * Creates "Reachability Analysis.html" in the output directory.
     * Shows modules -> classes -> fields with navigation through references.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the report file
     */
    public static void generateReachabilityReport(DOEngine engine) throws IOException {
        DOReachabilityReportGenerator generator = DOFactory.createReachabilityReportGenerator();
        generator.generateDefaultReport(engine);
    }

    /**
     * Generate a comprehensive HTML reachability analysis report.
     * 
     * @param engine     The fully-loaded DOEngine instance
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    public static void generateReachabilityReport(DOEngine engine, String outputPath) throws IOException {
        DOReachabilityReportGenerator generator = DOFactory.createReachabilityReportGenerator();
        generator.generateReport(engine, outputPath);
    }

    /**
     * Export database contents to Excel files.
     * Creates one Excel file per schema module in the "output/excel" directory.
     * Each file contains one sheet per class with rows for objects and columns for
     * fields.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the Excel files
     */
    public static void exportToExcel(DOEngine engine) throws IOException {
        DOGenericExportEngine exporter = DOFactory.createGenericExportEngine();
        exporter.export(engine, new ExcelFormatHandler());
    }

    /**
     * Export database contents to Excel files.
     * Creates one Excel file per schema module in the specified directory.
     * Each file contains one sheet per class with rows for objects and columns for
     * fields.
     * 
     * @param engine          The fully-loaded DOEngine instance
     * @param outputDirectory The directory where Excel files should be created
     * @throws IOException if there's an error writing the Excel files
     */
    public static void exportToExcel(DOEngine engine, String outputDirectory) throws IOException {
        DOGenericExportEngine exporter = DOFactory.createGenericExportEngine();
        exporter.export(engine, new ExcelFormatHandler(), outputDirectory);
    }

    /**
     * Export database contents to XML files organized by module using the enhanced
     * v2 system.
     * Creates one XML data file per schema module in the "output/migration/data"
     * directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * Uses the new modular export architecture for better performance and
     * reliability.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the XML files
     */
    public static void exportToXMLV2(DOEngine engine) throws IOException {
        dataobjects.api.migration.generic.v2.DOGenericExportEngineV2.export(
                engine, new dataobjects.impl.migration.xml.v2.XMLFormatHandler());
    }

    /**
     * Export database contents to XML files organized by module using the enhanced
     * v2 system.
     * Creates one XML data file per schema module in the specified directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * Uses the new modular export architecture for better performance and
     * reliability.
     * 
     * @param engine          The fully-loaded DOEngine instance
     * @param outputDirectory The directory where XML files should be created
     * @throws IOException if there's an error writing the XML files
     */
    public static void exportToXMLV2(DOEngine engine, String outputDirectory) throws IOException {
        dataobjects.api.migration.generic.v2.DOGenericExportEngineV2.export(
                engine, new dataobjects.impl.migration.xml.v2.XMLFormatHandler(), outputDirectory);
    }

    /**
     * Export database contents to XML files organized by module.
     * Creates one XML data file per schema module in the "output/migration/data"
     * directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the XML files
     */
    /*
     * public static void exportToXML(DOEngine engine) throws IOException {
     * DOGenericExportEngine exporter = DOFactory.createGenericExportEngine();
     * exporter.export(engine, new XMLFormatHandler());
     * }
     */

    /**
     * Export database contents to XML files organized by module.
     * Creates one XML data file per schema module in the specified directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * 
     * @param engine          The fully-loaded DOEngine instance
     * @param outputDirectory The directory where XML files should be created
     * @throws IOException if there's an error writing the XML files
     */
    /*
     * public static void exportToXML(DOEngine engine, String outputDirectory)
     * throws IOException {
     * DOGenericExportEngine exporter = DOFactory.createGenericExportEngine();
     * exporter.export(engine, new XMLFormatHandler(), outputDirectory);
     * }
     */

}
