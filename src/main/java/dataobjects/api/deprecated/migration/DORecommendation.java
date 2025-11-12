package dataobjects.api.deprecated.migration;

/**
 * Represents a recommendation for improving migration success.
 */
public interface DORecommendation {

    /**
     * Get the recommendation type.
     * 
     * @return Recommendation type
     */
    DORecommendationType getType();

    /**
     * Get the priority of this recommendation.
     * 
     * @return Priority level
     */
    DOSeverityLevel getPriority();

    /**
     * Get a description of the recommendation.
     * 
     * @return Recommendation description
     */
    String getDescription();

    /**
     * Get specific actions to implement this recommendation.
     * 
     * @return Array of action items
     */
    String[] getActionItems();

    /**
     * Get the expected impact of implementing this recommendation.
     * 
     * @return Impact description
     */
    String getExpectedImpact();
}
