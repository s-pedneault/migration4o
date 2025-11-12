package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents class-specific analysis results.
 */
public interface DOClassAnalysis {

    /**
     * Get the class this analysis applies to.
     * 
     * @return The class
     */
    DOClass getClazz();

    /**
     * Get the coverage information for this class.
     * 
     * @return Coverage information
     */
    DOClassCoverage getCoverage();

    /**
     * Get schema gaps specific to this class.
     * 
     * @return Array of class-specific gaps
     */
    DOSchemaGap[] getClassGaps();

    /**
     * Get inheritance information for this class.
     * 
     * @return Inheritance analysis
     */
    DOClassInheritance getInheritanceInfo();

    /**
     * Get the recommended export strategy for this class.
     * 
     * @return Export strategy
     */
    DOExportStrategy getRecommendedStrategy();
}
