package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents a data loss risk identified during migration analysis.
 */
public interface DODataLossRisk {

    /**
     * Get the class where data loss risk was identified.
     * 
     * @return The affected class
     */
    DOClass getAffectedClass();

    /**
     * Get the risk level.
     * 
     * @return Risk level
     */
    DORiskLevel getRiskLevel();

    /**
     * Get a description of the risk.
     * 
     * @return Risk description
     */
    String getDescription();

    /**
     * Get the estimated number of objects at risk.
     * 
     * @return Object count at risk
     */
    long getObjectsAtRisk();

    /**
     * Get mitigation strategies for this risk.
     * 
     * @return Array of mitigation strategies
     */
    String[] getMitigationStrategies();

    /**
     * Check if this risk is acceptable.
     * 
     * @return true if risk is acceptable
     */
    boolean isAcceptableRisk();
}
