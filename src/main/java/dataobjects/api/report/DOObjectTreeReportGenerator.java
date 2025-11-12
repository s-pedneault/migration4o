package dataobjects.api.report;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Interface for generating an interactive HTML report that displays the full
 * tree
 * of actual objects found in the database, using schema modules as starting
 * points.
 * This report helps identify reachable vs unreachable objects through tree
 * visualization.
 */
public interface DOObjectTreeReportGenerator {

    /**
     * Generate a comprehensive HTML tree report of actual database objects.
     * The report shows:
     * - A compact, expandable tree of objects starting from schema modules
     * - Object relationships and references in a hierarchical view
     * - A separate section for unreachable objects sorted by class
     * 
     * @param engine     The fully-loaded DOEngine instance containing schema and
     *                   database
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    void generateReport(DOEngine engine, String outputPath) throws IOException;

    /**
     * Generate the default tree report with standard filename in the output
     * directory.
     * Creates "Object Tree.html" in the tool's output folder.
     * 
     * @param engine The fully-loaded DOEngine instance containing schema and
     *               database
     * @throws IOException if there's an error writing the report file
     */
    void generateDefaultReport(DOEngine engine) throws IOException;

}