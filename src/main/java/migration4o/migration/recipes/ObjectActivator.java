package migration4o.migration.recipes;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.ClassUtil;
import migration4o.util.ObjectResolverUtil;

/**
 * Recipe for retrieving and activating DB4O objects. Handles object retrieval, activation, and class name extraction.
 */
public class ObjectActivator {

    /**
     * Result of object activation, including the delegate that owns the object.
     */
    public static class ActivationResult {
        public final Object object;
        public final String className;
        public final DODatabaseDelegate delegate;

        public ActivationResult(Object object, String className, DODatabaseDelegate delegate) {
            this.object = object;
            this.className = className;
            this.delegate = delegate;
        }
    }

    /**
     * Gets and activates an object from a single delegate.
     * 
     * @param delegate DB4O delegate
     * @param objectId Object ID to retrieve
     * @return ActivationResult with object and class name, or null if object not found
     */
    public static ActivationResult getAndActivate(DODatabaseDelegate delegate, long objectId) {
        try {
            // Preserve any outer context (e.g. class name set by exportUnreachedObjects).
            // Only set a fallback when no context is already active.
            boolean outerContext = migration4o.database.Db4oReadContext.get() != null;
            if (!outerContext) {
                migration4o.database.Db4oReadContext.set("getByID objectId=" + objectId);
            }
            Object obj;
            try {
                obj = delegate.getByID(objectId);
            } finally {
                if (!outerContext) {
                    migration4o.database.Db4oReadContext.clear();
                }
            }
            if (obj == null) {
                return null;
            }

            String className = ClassUtil.getClassName(obj);
            migration4o.database.Db4oReadContext.set("activate " + className + " objectId=" + objectId);
            try {
                ObjectResolverUtil.activateObjectShallow(delegate, obj, objectId);
            } finally {
                migration4o.database.Db4oReadContext.clear();
            }

            return new ActivationResult(obj, className, delegate);
        } catch (Exception e) {
            return null;
        }
    }

}
