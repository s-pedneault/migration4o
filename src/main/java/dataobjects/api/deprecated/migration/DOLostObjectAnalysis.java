package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Analysis of objects that would be lost during migration.
 * Uses ID-based analysis to identify orphaned objects that aren't part of
 * inheritance chains or collection references.
 */
public interface DOLostObjectAnalysis {

    /**
     * Get all object IDs that would potentially be lost during migration.
     * These are objects that are not:
     * - Part of a leaf object's inheritance chain
     * - Referenced in any collection
     * 
     * @return Array of object IDs that would be lost
     */
    String[] getPotentiallyLostObjectIds();

    /**
     * Get lost objects grouped by their stored class.
     * 
     * @return Array of lost object information by class
     */
    DOLostObjectsByClass[] getLostObjectsByClass();

    /**
     * Get detailed analysis of why certain objects would be lost.
     * 
     * @return Array of reasons for object loss
     */
    DOLostObjectReason[] getLostObjectReasons();

    /**
     * Get total count of potentially lost objects.
     * 
     * @return Number of objects that would be lost
     */
    int getTotalLostObjectCount();

    /**
     * Get percentage of objects that would be lost.
     * 
     * @return Percentage (0.0 to 100.0) of objects that would be lost
     */
    double getLostObjectPercentage();

    /**
     * Check if migration would result in data loss.
     * 
     * @return true if objects would be lost, false otherwise
     */
    boolean hasDataLossRisk();

    /**
     * Get all object IDs that would be preserved during migration.
     * These include leaf objects, their inheritance chains, and collection
     * references.
     * 
     * @return Array of object IDs that would be preserved
     */
    String[] getPreservedObjectIds();

    /**
     * Get total count of all objects in the database.
     * 
     * @return Total number of objects across all classes
     */
    int getTotalObjectCount();
}
