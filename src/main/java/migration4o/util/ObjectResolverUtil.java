package migration4o.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

/**
 * Utility class for object resolution operations.
 * Provides efficient static methods for common object resolution tasks.
 */
public class ObjectResolverUtil {

    /**
     * Check if a class name represents a known collection type for GenericObject
     * conversion
     */
    public static boolean isCollectionClassName(String className) {
        return className.equals("java.util.Vector") ||
                className.equals("java.util.ArrayList") ||
                className.equals("java.util.LinkedList") ||
                className.contains("VectRechID"); // Project-specific
    }

    /**
     * Get all stored classes and their object IDs efficiently
     */
    public static Map<String, Set<Long>> getAllClassObjectIds(ExtObjectContainer container) {
        Map<String, Set<Long>> allClassObjectIds = new HashMap<>();
        StoredClass[] storedClasses = container.storedClasses();

        for (StoredClass sc : storedClasses) {
            String className = sc.getName();
            long[] objectIds = sc.getIDs();

            Set<Long> idSet = new HashSet<>();
            for (long id : objectIds) {
                idSet.add(id);
            }

            allClassObjectIds.put(className, idSet);
        }

        return allClassObjectIds;
    }

    /**
     * Get object class name using db4o APIs
     */
    public static String getObjectClassName(ExtObjectContainer container, Object obj) {
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            return storedClass != null ? storedClass.getName() : obj.getClass().getName();
        } catch (Exception e) {
            return obj.getClass().getName();
        }
    }

    /**
     * Get GenericObject class name
     */
    public static String getGenericObjectClassName(GenericObject genericObj) {
        try {
            return genericObj.getGenericClass().getName();
        } catch (Exception e) {
            return "GenericObject";
        }
    }

    /**
     * Activate object with minimal depth.
     * Uses shallow activation (depth 1) matching the proven UI pattern.
     * Vectors and collections are activated specifically when accessed via
     * CollectionUtil.
     */
    public static void activateObject(ExtObjectContainer container, Object obj, Long objectId) {
        try {
            // Shallow activation is sufficient - avoids cascading retries and exception
            // spam
            container.activate(obj, 1);
        } catch (Exception e) {
            // Silently ignore - object is still usable with lazy activation
        }
    }

    /**
     * Get object ID from container
     */
    public static Long getObjectId(ExtObjectContainer container, Object obj) {
        try {
            long id = container.getID(obj);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load objects in batch to minimize disk seeks
     */
    public static Map<Long, Object> loadObjectsBatch(ExtObjectContainer container, List<Long> objectIds) {
        Map<Long, Object> loadedObjects = new HashMap<>();

        for (Long objectId : objectIds) {
            try {
                Object obj = container.getByID(objectId);
                if (obj != null) {
                    loadedObjects.put(objectId, obj);
                }
            } catch (Exception e) {
                // Skip problematic objects
            }
        }

        // Activate all loaded objects in batch
        Iterator<Map.Entry<Long, Object>> iterator = loadedObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Object> entry = iterator.next();
            try {
                activateObject(container, entry.getValue(), entry.getKey());
            } catch (Exception e) {
                // Remove problematic objects from processing
                iterator.remove();
            }
        }

        return loadedObjects;
    }

    /**
     * Result of collection content extraction
     */
    public static class CollectionExtractionResult {
        public final Long[] containedIds;
        public final String contentType;
        public final int totalSize;

        public CollectionExtractionResult(Long[] containedIds, String contentType, int totalSize) {
            this.containedIds = containedIds;
            this.contentType = contentType;
            this.totalSize = totalSize;
        }
    }

    /**
     * Check if an object is any type of collection at runtime
     */
    public static boolean isAnyCollectionType(Object obj) {
        return obj instanceof Collection ||
                obj instanceof Object[] ||
                obj instanceof Map;
    }

}