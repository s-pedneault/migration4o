package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;

/**
 * Represents a schema gap identified during migration analysis.
 */
public interface DOSchemaGap {

    /**
     * Get the type of gap identified.
     * 
     * @return Gap type
     */
    DOGapType getGapType();

    /**
     * Get the source class where the gap was identified.
     * 
     * @return Source class
     */
    DOClass getSourceClass();

    /**
     * Get the specific field where the gap was identified.
     * 
     * @return Source field, or null if gap affects entire class
     */
    DOField getSourceField();

    /**
     * Get a human-readable description of the gap.
     * 
     * @return Gap description
     */
    String getDescription();

    /**
     * Get the severity level of this gap.
     * 
     * @return Severity level
     */
    DOSeverityLevel getSeverity();

    /**
     * Get recommended actions to resolve this gap.
     * 
     * @return Array of recommendations
     */
    String[] getRecommendations();

    /**
     * Check if this gap would cause data loss.
     * 
     * @return true if gap causes data loss
     */
    boolean causesDataLoss();
}
