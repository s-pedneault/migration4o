package dataobjects.api.report;

import dataobjects.impl.report.DOStructureReportGeneratorImpl;

/**
 * Factory class for creating DOStructureReportGenerator instances.
 * Provides a simple way to get the default implementation.
 */
public class DOStructureReportGeneratorFactory {

    /**
     * Create a new instance of the default DOStructureReportGenerator
     * implementation.
     * 
     * @return A new DOStructureReportGenerator instance
     */
    public static DOStructureReportGenerator create() {
        return new DOStructureReportGeneratorImpl();
    }

    private DOStructureReportGeneratorFactory() {
        // Prevent instantiation
    }
}
