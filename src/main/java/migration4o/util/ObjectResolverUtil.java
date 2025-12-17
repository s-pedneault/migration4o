package migration4o.util;

import migration4o.models.DOClass;
import migration4o.models.DOField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.schema.DOSchema;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import java.util.*;
import java.util.Arrays;

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
     * Check if an object is any type of collection
     */
    /**
     * Create a HashMap for quick class lookups by name
     */
    public static Map<String, DODatabaseClass> createDatabaseClassMap(DODatabase database) {
        Map<String, DODatabaseClass> classMap = new HashMap<>();
        for (DODatabaseClass dbClass : database.getClasses()) {
            classMap.put(dbClass.getAbsoluteName(), dbClass);
        }
        return classMap;
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
     * Sort classes by inheritance specificity (most specific first)
     * This ensures we process subclasses before their superclasses
     */
    public static List<String> sortClassesBySpecificity(Set<String> classNames,
            Map<String, DODatabaseClass> classMap) {
        List<String> sortedClasses = new ArrayList<>(classNames);

        // Sort by inheritance depth (descending - most specific first)
        sortedClasses.sort((a, b) -> {
            int depthA = classMap.get(a) != null ? classMap.get(a).getInheritanceDepth() : 0;
            int depthB = classMap.get(b) != null ? classMap.get(b).getInheritanceDepth() : 0;
            return Integer.compare(depthB, depthA); // Descending order
        });

        return sortedClasses;
    }

    /**
     * Find class definition by name in schema or database
     */
    public static DOClass findClassDefinition(String className, DOSchema schema, DODatabase database) {
        // Try schema first
        if (schema != null) {
            for (DOClass schemaClass : schema.getClasses()) {
                if (className.equals(schemaClass.getAbsoluteName())) {
                    return schemaClass;
                }
            }
        }

        // Try database
        if (database != null) {
            for (DOClass databaseClass : database.getClasses()) {
                if (className.equals(databaseClass.getAbsoluteName())) {
                    return databaseClass;
                }
            }
        }

        return null;
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
     * Activate object with safe error handling
     */
    public static void activateObject(ExtObjectContainer container, Object obj, Long objectId) {
        try {
            container.activate(obj, Integer.MAX_VALUE);
        } catch (StackOverflowError e) {
            // Stack overflow - use shallow activation
            try {
                container.activate(obj, 10);
            } catch (Exception e2) {
                // Shallow activation also failed - skip
            }
        } catch (Exception e) {
            // Activation failed - skip
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
     * Build complete inheritance chain for an object
     */
    public static DOClass[] buildInheritanceChain(DOClass mostSpecificClass, DODatabaseClass databaseClass,
            DOSchema schema, DODatabase database) {
        List<DOClass> inheritanceChain = new ArrayList<>();

        // Always include the most specific class first
        inheritanceChain.add(mostSpecificClass);

        if (databaseClass != null && !databaseClass.getInheritanceChain().isEmpty()) {
            // Add parent classes from inheritance chain
            for (DODatabaseClass parentDatabaseClass : databaseClass.getInheritanceChain()) {
                DOClass parentClass = findClassDefinition(parentDatabaseClass.getAbsoluteName(), schema, database);
                if (parentClass != null) {
                    inheritanceChain.add(parentClass);
                }
            }
        }

        return inheritanceChain.toArray(new DOClass[0]);
    }

    /**
     * Exclude an object from all its superclasses to avoid duplicate processing
     */
    public static void excludeObjectFromSuperclasses(Long objectId, DODatabaseClass databaseClass,
            Set<String> excludedClassObjectPairs) {
        if (databaseClass == null || databaseClass.getInheritanceChain().isEmpty()) {
            return;
        }

        // Exclude this object from all superclasses in the inheritance chain
        for (DODatabaseClass superClass : databaseClass.getInheritanceChain()) {
            String excludeKey = superClass.getAbsoluteName() + ":" + objectId;
            excludedClassObjectPairs.add(excludeKey);
        }
    }

    /**
     * Find the most specific class among multiple class names using inheritance
     * depth
     */
    public static String findMostSpecificClass(DODatabase database, DOSchema schema, String[] classNames) {
        if (classNames.length == 0)
            return null;
        if (classNames.length == 1)
            return classNames[0];

        Map<String, DODatabaseClass> classMap = createDatabaseClassMap(database);
        String mostSpecific = classNames[0];
        int maxDepth = -1;

        for (String className : classNames) {
            DODatabaseClass dbClass = classMap.get(className);
            int depth = dbClass != null ? dbClass.getInheritanceDepth() : 0;
            if (depth > maxDepth) {
                maxDepth = depth;
                mostSpecific = className;
            }
        }

        return mostSpecific;
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
     * Get field value using db4o APIs
     */
    public static Object getFieldValue(ExtObjectContainer container, Object obj, DOField field) {
        try {
            String className = getObjectClassName(container, obj);
            StoredClass storedClass = container.ext().storedClass(className);
            if (storedClass != null) {
                StoredField storedField = storedClass.storedField(field.getName(), null);
                if (storedField != null) {
                    return storedField.get(obj);
                }
            }
        } catch (Exception e) {
            // Field not accessible
        }
        return null;
    }

    /**
     * Universal collection content extraction that works for all collection types
     * (Vector, ArrayList, Arrays, etc.)
     */
    public static CollectionExtractionResult extractUniversalCollectionContents(
            ExtObjectContainer container, Object collectionObj, Long objectId, DOField field) {
        try {
            List<Long> containedIds = new ArrayList<>();
            String contentType = determineContentType(field, collectionObj);
            int totalSize = 0;

            // Handle different collection types uniformly
            Iterable<?> iterable = convertToIterable(collectionObj);
            if (iterable == null) {
                return null;
            }

            // Count size
            if (collectionObj instanceof Collection) {
                totalSize = ((Collection<?>) collectionObj).size();
            } else if (collectionObj instanceof Object[]) {
                totalSize = ((Object[]) collectionObj).length;
            } else {
                // For other iterables, count manually
                for (@SuppressWarnings("unused")
                Object item : iterable) {
                    totalSize++;
                }
            }

            if (totalSize == 0) {
                return null; // Skip empty collections
            }

            // Extract object IDs and determine content type
            for (Object item : iterable) {
                if (item != null) {
                    Long itemId = getObjectId(container, item);
                    if (itemId != null) {
                        containedIds.add(itemId);
                    }

                    // Determine content type from first element if not already known
                    if (contentType.equals("java.lang.Object")) {
                        contentType = getObjectClassName(container, item);
                    }
                }
            }

            if (!containedIds.isEmpty()) {
                return new CollectionExtractionResult(
                        containedIds.toArray(new Long[0]),
                        contentType,
                        totalSize);
            }

        } catch (Exception e) {
            // Return null for problematic collections
        }

        return null;
    }

    /**
     * Convert any collection-like object to an Iterable for uniform processing
     */
    private static Iterable<?> convertToIterable(Object obj) {
        if (obj instanceof Iterable) {
            return (Iterable<?>) obj;
        } else if (obj instanceof Object[]) {
            return Arrays.asList((Object[]) obj);
        }
        return null;
    }

    /**
     * Determine the content type of a collection
     */
    private static String determineContentType(DOField field, Object collectionObj) {
        // First try to get from field definition
        if (field != null && field.getContentTypeName() != null) {
            return field.getContentTypeName();
        }

        // For standalone collections (like Vector objects), default to Object
        return "java.lang.Object";
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

    /**
     * Result of primitive field extraction
     */
    public static class PrimitiveFieldValue {
        public final DOField field;
        public final Object value;

        public PrimitiveFieldValue(DOField field, Object value) {
            this.field = field;
            this.value = value;
        }
    }

    /**
     * Extract all primitive field values for an object on-demand.
     * This method accesses the database to retrieve current field values.
     * 
     * @param container  The database container
     * @param objectId   The ID of the object to extract fields from
     * @param allClasses The complete inheritance chain for the object
     * @return Map of field name to PrimitiveFieldValue, or empty map if extraction
     *         fails
     */
    public static Map<String, PrimitiveFieldValue> extractPrimitiveFieldValues(
            ExtObjectContainer container,
            Long objectId,
            DOClass[] allClasses) {
        Map<String, PrimitiveFieldValue> fieldValues = new LinkedHashMap<>();

        try {
            // Get the actual object from the database
            Object actualObj = container.getByID(objectId);
            if (actualObj == null) {
                return fieldValues;
            }

            // Activate the object
            activateObject(container, actualObj, objectId);

            // Collect primitive fields from the entire class hierarchy
            if (allClasses != null) {
                for (DOClass dbClass : allClasses) {
                    DOField[] fields = dbClass.getFields();
                    if (fields != null) {
                        for (DOField field : fields) {
                            // Only include primitive types
                            if (TypeUtil.isPrimitiveType(field)) {
                                try {
                                    Object value = getFieldValue(container, actualObj, field);
                                    fieldValues.put(field.getName(), new PrimitiveFieldValue(field, value));
                                } catch (Exception e) {
                                    // Skip fields that can't be read
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map on failure
        }

        return fieldValues;
    }
}