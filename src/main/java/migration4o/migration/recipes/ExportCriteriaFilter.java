package migration4o.migration.recipes;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.models.ui.ClassExportConfig;

/**
 * Recipe for filtering objects based on export criteria.
 * Applies criteria matching to determine if an object should be exported.
 */
public class ExportCriteriaFilter {

    /**
     * Checks if an object matches export criteria.
     * Only applies to top-level (non-embedded) GenericObjects.
     * 
     * @param container    DB4O container
     * @param obj          The object to check
     * @param className    The object's class name
     * @param isEmbedded   Whether this is an embedded object
     * @param exportConfig Export configuration with criteria
     * @param statistics   Statistics tracker (optional, for counting filtered
     *                     objects)
     * @return true if object should be exported, false if filtered out
     */
    public static boolean shouldExport(ExtObjectContainer container, Object obj, String className, boolean isEmbedded, boolean isRootObject, ClassExportConfig exportConfig, ExportStatistics statistics) {

        // Only apply criteria filtering to root objects of the exported class
        // References discovered during traversal should not be filtered by the
        // root class criteria.
        if (isEmbedded || !isRootObject) {
            return true;
        }

        // Only apply criteria if configured
        if (exportConfig == null || !exportConfig.hasCriteria()) {
            return true;
        }

        // Only apply criteria if object is a GenericObject
        if (!(obj instanceof GenericObject)) {
            System.out.println("DEBUG: Cannot apply criteria to non-GenericObject: " + className);
            return true;
        }

        // Check if object matches all criteria
        if (!exportConfig.matchesAllCriteria(container, (GenericObject) obj)) {
            // Object doesn't match criteria, skip export
            if (statistics != null) {
                statistics.incrementFiltered(); // Track filtered objects
                try {
                    long objectId = container.ext().getID(obj);
                    statistics.recordReachedOnly(className, objectId);
                    statistics.recordObjectDecision(objectId, className, "filtered out by export criteria: " + exportConfig);
                } catch (Exception ignored) {
                    // Best-effort diagnostics only
                }
            }
            return false;
        }

        return true;
    }
}
