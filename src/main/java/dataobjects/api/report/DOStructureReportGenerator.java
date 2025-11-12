package dataobjects.api.report;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Interface for generating comprehensive HTML reports of database structure and
 * contents.
 * The report includes schema information, inheritance hierarchies, field
 * descriptions,
 * and detailed object data including resolved collections and references.
 */
public interface DOStructureReportGenerator {

    /**
     * Generate a comprehensive HTML report of the database structure and contents.
     * 
     * @param engine     The fully-loaded DOEngine instance containing schema and
     *                   database
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    void generateReport(DOEngine engine, String outputPath) throws IOException;

    /**
     * Generate the default report with standard filename in the output directory.
     * Creates "Database contents.html" in the tool's output folder.
     * 
     * @param engine The fully-loaded DOEngine instance containing schema and
     *               database
     * @throws IOException if there's an error writing the report file
     */
    void generateDefaultReport(DOEngine engine) throws IOException;

}
