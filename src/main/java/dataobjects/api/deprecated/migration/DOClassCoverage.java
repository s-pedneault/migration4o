package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents coverage statistics for a specific class.
 */
public interface DOClassCoverage {

    /**
     * Get the class this coverage applies to.
     * 
     * @return The class
     */
    DOClass getClazz();

    /**
     * Get the coverage status for this class.
     * 
     * @return Coverage status
     */
    DOCoverageStatus getStatus();

    /**
     * Get the percentage of fields covered (0.0 to 1.0).
     * 
     * @return Coverage percentage
     */
    double getCoveragePercentage();

    /**
     * Get the number of mapped fields.
     * 
     * @return Mapped field count
     */
    int getMappedFieldCount();

    /**
     * Get the total number of fields.
     * 
     * @return Total field count
     */
    int getTotalFieldCount();

    /**
     * Get the number of objects of this class in the database.
     * 
     * @return Object count
     */
    long getObjectCount();
}
