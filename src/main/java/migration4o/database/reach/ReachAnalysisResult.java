package migration4o.database.reach;

import java.util.Map;
import java.util.Set;

/**
 * Result of a reach analysis operation.
 * Contains all objects that were reached during traversal, organized by class.
 */
public class ReachAnalysisResult {

    private final Set<Long> allReachedObjectIds;
    private final Map<String, Set<Long>> reachedObjectsByClass;

    /**
     * Creates a new reach analysis result.
     * 
     * @param allReachedObjectIds   All reached object IDs
     * @param reachedObjectsByClass Reached object IDs organized by class name
     */
    public ReachAnalysisResult(Set<Long> allReachedObjectIds, Map<String, Set<Long>> reachedObjectsByClass) {
        this.allReachedObjectIds = allReachedObjectIds;
        this.reachedObjectsByClass = reachedObjectsByClass;
    }

    /**
     * Gets all reached object IDs.
     * 
     * @return Set of all reached object IDs
     */
    public Set<Long> getAllReachedObjectIds() {
        return allReachedObjectIds;
    }

    /**
     * Gets reached object IDs organized by class name.
     * 
     * @return Map of class name to set of reached object IDs
     */
    public Map<String, Set<Long>> getReachedObjectsByClass() {
        return reachedObjectsByClass;
    }

    /**
     * Gets the total number of reached objects.
     * 
     * @return Total count of reached objects
     */
    public int getTotalReachedCount() {
        return allReachedObjectIds.size();
    }

    /**
     * Checks if a specific object ID was reached.
     * 
     * @param objectId The object ID to check
     * @return true if the object was reached, false otherwise
     */
    public boolean isObjectReached(long objectId) {
        return allReachedObjectIds.contains(objectId);
    }

    /**
     * Gets the number of reached objects for a specific class.
     * 
     * @param className The class name
     * @return Number of reached objects for that class, or 0 if none
     */
    public int getReachedCountForClass(String className) {
        Set<Long> classObjects = reachedObjectsByClass.get(className);
        return classObjects != null ? classObjects.size() : 0;
    }
}
