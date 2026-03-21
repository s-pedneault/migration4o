package migration4o.util;

import migration4o.database.DODatabaseDelegate;

/**
 * Utility class for object resolution operations. Provides efficient static methods for common object resolution tasks.
 */
public class ObjectResolverUtil {

    /**
     * Activate object with minimal depth. Uses shallow activation (depth 1) matching the proven UI pattern. Vectors and collections are activated specifically when accessed via CollectionUtil.
     */
    public static void activateObjectShallow(DODatabaseDelegate delegate, Object obj, Long objectId) {
        try {
            // Shallow activation is sufficient - avoids cascading retries and exception
            // spam
            delegate.activate(obj, 1);
        } catch (Exception e) {
            // Silently ignore - object is still usable with lazy activation
        }
    }

    /**
     * Get object ID from delegate
     */
    public static Long getObjectId(DODatabaseDelegate delegate, Object obj) {
        try {
            long id = delegate.getID(obj);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

}