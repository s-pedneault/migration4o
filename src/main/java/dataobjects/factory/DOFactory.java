package dataobjects.factory;

import dataobjects.impl.report.DOStructureReportGenerator;
import dataobjects.impl.report.DOObjectTreeReportGenerator;
import dataobjects.impl.report.reachability.ReachabilityReportGenerator;
import dataobjects.impl.migration.excel.ExcelExportEngine;
import dataobjects.impl.migration.xml.XMLMigrationEngine;

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
     * Create a new instance of ExcelExportEngine.
     * 
     * @return A new ExcelExportEngine instance
     */
    public static ExcelExportEngine createExcelExportEngine() {
        return new ExcelExportEngine();
    }

    /**
     * Create a new instance of XMLMigrationEngine.
     * 
     * @return A new XMLMigrationEngine instance
     */
    public static XMLMigrationEngine createXMLMigrationEngine() {
        return new XMLMigrationEngine();
    }

    // Note: DOGenericExportEngine uses static methods, no factory method needed
}
