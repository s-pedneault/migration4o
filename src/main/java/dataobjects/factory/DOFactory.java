package dataobjects.factory;

import dataobjects.api.report.DOStructureReportGenerator;
import dataobjects.api.report.DOObjectTreeReportGenerator;
import dataobjects.api.report.DOReachabilityReportGenerator;
import dataobjects.api.migration.excel.DOExcelExportEngine;
import dataobjects.api.migration.xml.DOXMLMigrationEngine;
import dataobjects.api.migration.generic.DOGenericExportEngine;
import dataobjects.impl.report.DOStructureReportGeneratorImpl;
import dataobjects.impl.report.DOObjectTreeReportGeneratorImpl;
import dataobjects.impl.report.reachability.ReachabilityReportGenerator;
import dataobjects.impl.migration.excel.ExcelExportEngineImpl;
import dataobjects.impl.migration.xml.XMLMigrationEngineImpl;
import dataobjects.impl.migration.generic.GenericExportEngineImpl;

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
        return new DOStructureReportGeneratorImpl();
    }

    /**
     * Create a new instance of DOObjectTreeReportGenerator.
     * 
     * @return A new DOObjectTreeReportGenerator instance
     */
    public static DOObjectTreeReportGenerator createTreeReportGenerator() {
        return new DOObjectTreeReportGeneratorImpl();
    }

    /**
     * Create a new instance of DOReachabilityReportGenerator.
     * 
     * @return A new DOReachabilityReportGenerator instance
     */
    public static DOReachabilityReportGenerator createReachabilityReportGenerator() {
        return new ReachabilityReportGenerator();
    }

    /**
     * Create a new instance of DOExcelExportEngine.
     * 
     * @return A new DOExcelExportEngine instance
     */
    public static DOExcelExportEngine createExcelExportEngine() {
        return new ExcelExportEngineImpl();
    }

    /**
     * Create a new instance of DOXMLMigrationEngine.
     * 
     * @return A new DOXMLMigrationEngine instance
     */
    public static DOXMLMigrationEngine createXMLMigrationEngine() {
        return new XMLMigrationEngineImpl();
    }

    /**
     * Create a new instance of DOGenericExportEngine.
     * 
     * @return A new DOGenericExportEngine instance
     */
    public static DOGenericExportEngine createGenericExportEngine() {
        return new GenericExportEngineImpl();
    }
}
