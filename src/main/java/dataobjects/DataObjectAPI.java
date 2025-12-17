package dataobjects;

import java.io.FileNotFoundException;
import java.io.IOException;

import dataobjects.impl.engine.DOEngine;
// Deprecated migration analysis - moved to deprecated package
// import dataobjects.impl.deprecated.migration.DOPreAnalysis;
import dataobjects.impl.report.DOStructureReportGenerator;
import dataobjects.impl.report.DOObjectTreeReportGenerator;
import dataobjects.impl.report.reachability.ReachabilityReportGenerator;
import dataobjects.impl.migration.excel.ExcelExportEngine;
import dataobjects.impl.migration.xml.XMLMigrationEngine;
import dataobjects.impl.migration.generic.DOGenericExportEngine;
import dataobjects.impl.migration.excel.ExcelFormatHandler;
import dataobjects.factory.DOFactory;

public class DataObjectAPI {

    public static DOEngine newEngine(String schemaPath, String databasePath) throws IOException {
        return new DOEngine(schemaPath, databasePath);
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
     * return new DOPreAnalysis(engine);
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
        ReachabilityReportGenerator generator = DOFactory.createReachabilityReportGenerator();
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
        ReachabilityReportGenerator generator = DOFactory.createReachabilityReportGenerator();
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
        DOGenericExportEngine.export(engine, new ExcelFormatHandler());
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
        DOGenericExportEngine.export(engine, new ExcelFormatHandler(), outputDirectory);
    }

    /**
     * Export database contents to XML files organized by module.
     * Creates one XML data file per schema module in the "output/migration/data"
     * directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * Uses the modular export architecture for better performance and reliability.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the XML files
     */
    public static void exportToXML(DOEngine engine) throws IOException {
        dataobjects.impl.migration.generic.DOGenericExportEngine.export(
                engine, new dataobjects.impl.migration.xml.XMLFormatHandler());
    }

    /**
     * Export database contents to XML files organized by module.
     * Creates one XML data file per schema module in the specified directory.
     * Each file contains all objects for classes in that module with proper field
     * flattening.
     * Uses the modular export architecture for better performance and reliability.
     * 
     * @param engine          The fully-loaded DOEngine instance
     * @param outputDirectory The directory where XML files should be created
     * @throws IOException if there's an error writing the XML files
     */
    public static void exportToXML(DOEngine engine, String outputDirectory) throws IOException {
        dataobjects.impl.migration.generic.DOGenericExportEngine.export(
                engine, new dataobjects.impl.migration.xml.XMLFormatHandler(), outputDirectory);
    } /**
       * Note: This is the main XML export functionality.
       * The old v1 system and temporary v2 naming have been cleaned up.
       */

}
