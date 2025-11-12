package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Information about lost objects for a specific class.
 * Provides detailed breakdown of objects that would be lost during migration.
 */
public interface DOLostObjectsByClass {

    /**
     * The database class these objects are stored as.
     * 
     * @return The class where objects are stored
     */
    DOClass getStoredClass();

    /**
     * Object IDs that would be lost for this class.
     * 
     * @return Array of object IDs
     */
    String[] getLostObjectIds();

    /**
     * Count of lost objects for this class.
     * 
     * @return Number of objects that would be lost
     */
    int getLostObjectCount();

    /**
     * Total count of objects for this class (lost + preserved).
     * 
     * @return Total number of objects in this class
     */
    int getTotalObjectCount();

    /**
     * Percentage of this class's objects that would be lost.
     * 
     * @return Percentage (0.0 to 100.0) of objects lost
     */
    double getLossPercentage();

    /**
     * Whether this class has schema mapping.
     * 
     * @return true if class has schema mapping, false otherwise
     */
    boolean hasSchemaMapping();

    /**
     * Reason why these objects would be lost.
     * 
     * @return The reason for object loss
     */
    DOLostObjectReason getReason();
}
