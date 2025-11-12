package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents an export strategy for a specific class or set of classes.
 */
public interface DOExportStrategy {

    /**
     * Get the class this strategy applies to.
     * 
     * @return The target class
     */
    DOClass getTargetClass();

    /**
     * Get the strategy type.
     * 
     * @return Strategy type
     */
    DOStrategyType getStrategyType();

    /**
     * Get a description of the strategy.
     * 
     * @return Strategy description
     */
    String getDescription();

    /**
     * Get the estimated success rate for this strategy.
     * 
     * @return Success rate (0.0 to 1.0)
     */
    double getSuccessRate();

    /**
     * Get any prerequisites for this strategy.
     * 
     * @return Array of prerequisites
     */
    String[] getPrerequisites();
}
