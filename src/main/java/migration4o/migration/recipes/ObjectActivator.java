package migration4o.migration.recipes;

import com.db4o.ext.ExtObjectContainer;

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
     * @param container DB4O container
     * @param objectId  Object ID to retrieve
     * @return ActivationResult with object and class name, or null if object not
     *         found
     */
    public static ActivationResult getAndActivate(ExtObjectContainer container, long objectId) {
        try {
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return null;
            }

            String className = ClassUtil.getClassName(obj);
            ObjectResolverUtil.activateObject(container, obj, objectId);

            return new ActivationResult(obj, className);
        } catch (Exception e) {
            return null;
        }
    }
}
