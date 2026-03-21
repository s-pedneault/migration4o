package migration4o.migration.recipes;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.ClassUtil;
import migration4o.util.ObjectResolverUtil;

/**
 * Recipe for retrieving and activating DB4O objects.
 * Handles object retrieval, activation, and class name extraction.
 */
public class ObjectActivator {

    /**
     * Result of object activation.
     */
    public static class ActivationResult {
        public final Object object;
        public final String className;

        public ActivationResult(Object object, String className) {
            this.object = object;
            this.className = className;
        }
    }

    /**
     * Gets and activates an object from the database.
     * 
     * @param delegate DB4O delegate
     * @param objectId Object ID to retrieve
     * @return ActivationResult with object and class name, or null if object not
     *         found
     */
    public static ActivationResult getAndActivate(DODatabaseDelegate delegate, long objectId) {
        try {
            Object obj = delegate.getByID(objectId);
            if (obj == null) {
                return null;
            }

            String className = ClassUtil.getClassName(obj);
            ObjectResolverUtil.activateObjectShallow(delegate, obj, objectId);

            return new ActivationResult(obj, className);
        } catch (Exception e) {
            return null;
        }
    }
}
