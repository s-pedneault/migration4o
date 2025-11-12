package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Provides migration coverage analysis results.
 */
public interface DOMigrationCoverage {

    /**
     * Get the coverage status for a specific class.
     * 
     * @param clazz The class to get coverage for
     * @return Coverage status
     */
    DOCoverageStatus getCoverageStatus(DOClass clazz);

    /**
     * Get all unresolved fields across all classes.
     * 
     * @return Array of unresolved fields
     */
    DOUnresolvedField[] getUnresolvedFields();

    /**
     * Get all polymorphic storage points that affect migration.
     * 
     * @return Array of polymorphic storage points
     */
    DOPolymorphicStorage[] getPolymorphicStoragePoints();

    /**
     * Get the overall coverage percentage for the migration.
     * 
     * @return Coverage percentage (0.0 to 1.0)
     */
    double getOverallCoveragePercentage();

    /**
     * Get coverage statistics for all classes.
     * 
     * @return Array of class coverage statistics
     */
    DOClassCoverage[] getClassCoverageStats();

    /**
     * Check if the migration has sufficient coverage to proceed.
     * 
     * @return true if coverage is sufficient
     */
    boolean hasSufficientCoverage();
}
