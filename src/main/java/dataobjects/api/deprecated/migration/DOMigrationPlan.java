package dataobjects.api.deprecated.migration;

/**
 * Represents a comprehensive migration plan with strategies and
 * recommendations.
 */
public interface DOMigrationPlan {

    /**
     * Get recommended export strategies for each class.
     * 
     * @return Array of export strategies
     */
    DOExportStrategy[] getExportStrategies();

    /**
     * Get all identified data loss risks.
     * 
     * @return Array of data loss risks
     */
    DODataLossRisk[] getDataLossRisks();

    /**
     * Get all recommendations for improving migration success.
     * 
     * @return Array of recommendations
     */
    DORecommendation[] getRecommendations();

    /**
     * Check if the migration is ready to proceed.
     * 
     * @return true if migration is ready
     */
    boolean isReadyForMigration();

    /**
     * Get the overall risk level of the migration.
     * 
     * @return Risk level
     */
    DORiskLevel getOverallRiskLevel();

    /**
     * Get estimated migration time in milliseconds.
     * 
     * @return Estimated migration time
     */
    long getEstimatedMigrationTime();

    /**
     * Get a summary of the migration plan.
     * 
     * @return Migration plan summary
     */
    String getPlanSummary();
}
