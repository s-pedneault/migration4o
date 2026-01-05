package migration4o.engine;

import migration4o.engine.migration.XMLMigrationEngine;
import migration4o.engine.report.DOObjectTreeReportGenerator;
import migration4o.engine.report.DOStructureReportGenerator;
import migration4o.engine.report.reachability.ReachabilityReportGenerator;

/**
 * Factory class for creating instances of DataObject components.
 */
public class DOFactory {

    /**
     * Create a new instance of DOStructureReportGenerator.
     * 
     * @return A new DOStructureReportGenerator instance
     */
    public static DOStructureReportGenerator createReportGenerator() {
        return new DOStructureReportGenerator();
    }

    /**
     * Create a new instance of DOObjectTreeReportGenerator.
     * 
     * @return A new DOObjectTreeReportGenerator instance
     */
    public static DOObjectTreeReportGenerator createTreeReportGenerator() {
        return new DOObjectTreeReportGenerator();
    }

    /**
     * Create a new instance of ReachabilityReportGenerator.
     * 
     * @return A new ReachabilityReportGenerator instance
     */
    public static ReachabilityReportGenerator createReachabilityReportGenerator() {
        return new ReachabilityReportGenerator();
    }

    /**
     * Create a new instance of XMLMigrationEngine.
     * 
     * @return A new XMLMigrationEngine instance
     */
    public static XMLMigrationEngine createXMLMigrationEngine() {
        return new XMLMigrationEngine();
    }
}
