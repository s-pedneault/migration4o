package dataobjects.api.report;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Interface for generating comprehensive HTML reachability analysis reports.
 * These reports help identify object reachability patterns and navigation paths
 * through the database structure.
 */
public interface DOReachabilityReportGenerator {

    /**
     * Generate the reachability report with the default filename and location.
     * 
     * @param engine The fully-loaded DOEngine instance
     * @throws IOException if there's an error writing the report file
     */
    public void generateDefaultReport(DOEngine engine) throws IOException;

    /**
     * Generate the reachability report to a specific path.
     * 
     * @param engine     The fully-loaded DOEngine instance
     * @param outputPath The path where the HTML report should be generated
     * @throws IOException if there's an error writing the report file
     */
    public void generateReport(DOEngine engine, String outputPath) throws IOException;
}