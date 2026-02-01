package migration4o.database.reach;

import java.util.Set;

/**
 * Callback interface for receiving progress updates during reach analysis.
 */
public interface ReachProgressCallback {

    /**
     * Called when the status message should be updated.
     * 
     * @param message The status message
     */
    void onStatusUpdate(String message);

    /**
     * Called when an object is being processed.
     * 
     * @param className The class name of the object
     * @param objectId  The object ID
     * @param processed Number of objects processed for this class
     * @param total     Total objects for this class
     */
    default void onObjectProcessed(String className, long objectId, int processed, int total) {
        // Optional override
    }

    /**
     * Called when an important object is found that should be highlighted.
     * 
     * @param parentLabel The parent context label
     * @param childLabel  The child object label
     */
    default void onImportantObjectFound(String parentLabel, String childLabel) {
        // Optional override
    }
}
