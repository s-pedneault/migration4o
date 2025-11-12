package dataobjects.api.resolution;

import dataobjects.api.models.database.DODatabaseClass;

import java.util.Map;
import java.util.Set;

/**
 * Tracks individual object reachability across all database classes.
 * Maintains a master list of all object IDs and marks which ones have been
 * reached
 * during the object processing phase.
 */
public interface DOObjectReachabilityTracker {

    /**
     * Initializes the tracker with all object IDs from all database classes.
     * This creates the master list that will be used to determine unreached
     * objects.
     * 
     * @param allObjectIdsByClass Map of database class to set of object IDs
     */
    void initializeFromDatabase(Map<DODatabaseClass, Set<Long>> allObjectIdsByClass);

    /**
     * Marks an object ID as reached for all classes in its inheritance chain.
     * This is called during object processing to track which objects have been
     * encountered while traversing the object graph.
     * 
     * @param objectId       The object ID to mark as reached
     * @param classesInChain Array of database classes in the object's inheritance
     *                       chain,
     *                       ordered from most specific (leaf) to least specific
     *                       (base)
     */
    void markObjectAsReached(Long objectId, DODatabaseClass[] classesInChain);

    /**
     * Returns all object IDs that have been marked as reached, grouped by database
     * class.
     * NOTE: Due to inheritance, the same object ID may appear in multiple classes.
     * 
     * @return Map of database class to set of reached object IDs
     */
    Map<DODatabaseClass, Set<Long>> getReachedObjectsByClass();

    /**
     * Returns reached object IDs grouped by their MOST SPECIFIC class only.
     * Each object ID appears only once, avoiding duplicates across inheritance
     * chains.
     * This is the preferred method for reporting reached objects.
     * 
     * @return Map of database class to set of object IDs (where each ID appears
     *         only
     *         once)
     */
    Map<DODatabaseClass, Set<Long>> getReachedObjectsByMostSpecificClass();

    /**
     * Returns all object IDs that have NOT been marked as reached, grouped by
     * database class.
     * This is calculated by comparing the master list with the reached objects.
     * 
     * @return Map of database class to set of unreached object IDs
     */
    Map<DODatabaseClass, Set<Long>> getUnreachedObjectsByClass();

    /**
     * Checks if a specific object ID has been marked as reached for any class.
     * 
     * @param objectId The object ID to check
     * @return true if the object has been reached, false otherwise
     */
    boolean isObjectReached(Long objectId);

    /**
     * Returns the total count of all object IDs in the database.
     * 
     * @return Total number of objects across all classes
     */
    int getTotalObjectCount();

    /**
     * Returns the count of objects that have been marked as reached.
     * 
     * @return Number of reached objects
     */
    int getReachedObjectCount();

    /**
     * Returns the count of objects that have not been reached.
     * 
     * @return Number of unreached objects
     */
    int getUnreachedObjectCount();

    /**
     * Returns the total count of unique object IDs for a specific class.
     * 
     * @param dbClass The database class
     * @return Number of unique objects in that class
     */
    long getObjectCountByClass(DODatabaseClass dbClass);

    /**
     * Returns the count of reached objects for a specific class.
     * 
     * @param dbClass The database class
     * @return Number of reached objects in that class
     */
    long getReachedObjectCountByClass(DODatabaseClass dbClass);

    /**
     * Returns the count of unreached objects for a specific class.
     * 
     * @param dbClass The database class
     * @return Number of unreached objects in that class
     */
    long getUnreachedObjectCountByClass(DODatabaseClass dbClass);
}
