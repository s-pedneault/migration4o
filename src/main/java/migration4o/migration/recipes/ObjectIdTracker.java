package migration4o.migration.recipes;

import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.util.ClassUtil;

/**
 * Recipe for tracking exported object IDs and detecting duplicates. Manages the
 * deduplication set and statistics recording.
 */
public class ObjectIdTracker {

    /**
     * Records an object reference and returns whether it's a duplicate. Root and
     * embedded objects always pass through (not checked for global duplicates).
     * 
     * @param container                 DB4O container
     * @param objectId                  The object ID to track
     * @param isRootObject              Whether this is a root object (from
     *                                  objectIds array)
     * @param isEmbedded                Whether this object is being exported as an
     *                                  embedded/value object
     * @param exportedObjectIds         Set of already exported object IDs
     * @param statistics                Statistics tracker (optional)
     * @param parentObjectId            Parent object ID for reference tracking
     * @param sourceContainingClassName Source class name for reference tracking
     * @param sourceFieldName           Source field name for reference tracking
     * @return true if object should be exported, false if it's a duplicate
     */
    public static boolean shouldExport(ExtObjectContainer container, long objectId, boolean isRootObject, boolean isEmbedded, Set<Long> exportedObjectIds, ExportStatistics statistics, Long parentObjectId, String sourceContainingClassName, String sourceFieldName) {

        // Get className for statistics recording
        String className = null;
        try {
            Object obj = container.ext().getByID(objectId);
            if (obj != null) {
                className = ClassUtil.getClassName(obj);
            }
        } catch (Exception e) {
            // If we can't get class name, use a placeholder but still track
            className = "Unknown_" + objectId;
        }

        // Always record the reference, even if we can't get perfect info
        if (className != null && statistics != null) {
            statistics.duplicationDetector.recordObjectReference(objectId, className, parentObjectId, sourceContainingClassName, sourceFieldName);
        }

        // Check if object was already exported. Skip check for:
        // - root objects: allows multiple criteria-based exports of same class
        // - embedded objects: value objects can be reused and must be exported inline
        if (!isRootObject && !isEmbedded && !exportedObjectIds.add(objectId)) {
            // Object already exported - it's a duplicate
            return false;
        }

        return true;
    }

    /**
     * Extracts class name from an object ID, with fallback.
     * 
     * @param container DB4O container
     * @param objectId  Object ID
     * @return Class name or "Unknown_" + objectId
     */
    public static String getClassName(ExtObjectContainer container, long objectId) {
        try {
            Object obj = container.ext().getByID(objectId);
            if (obj != null) {
                return ClassUtil.getClassName(obj);
            }
        } catch (Exception e) {
            // Fallback to placeholder
        }
        return "Unknown_" + objectId;
    }
}
