package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Main interface for migration pre-analysis functionality.
 * Provides comprehensive analysis of migration requirements and risks.
 */
public interface DOPreAnalysis {

    /**
     * Get the inheritance mapping analysis for this migration.
     * 
     * @return Inheritance relationship mapping
     */
    DOInheritanceMapping getInheritanceMapping();

    /**
     * Get the migration coverage analysis.
     * 
     * @return Coverage analysis results
     */
    DOMigrationCoverage getCoverage();

    /**
     * Get the lost object analysis using ID-based detection.
     * Identifies objects that would be lost during migration.
     * 
     * @return Lost object analysis results
     */
    DOLostObjectAnalysis getLostObjectAnalysis();

    /**
     * Get all identified schema gaps.
     * 
     * @return Array of schema gaps
     */
    DOSchemaGap[] getSchemaGaps();

    /**
     * Generate a comprehensive migration plan based on the analysis.
     * 
     * @return Complete migration plan with strategies and recommendations
     */
    DOMigrationPlan generateMigrationPlan();

    /**
     * Print a comprehensive analysis report to the console.
     */
    void printAnalysisReport();

    /**
     * Get analysis for a specific class.
     * 
     * @param clazz The class to analyze
     * @return Class-specific analysis
     */
    DOClassAnalysis getClassAnalysis(DOClass clazz);
}
