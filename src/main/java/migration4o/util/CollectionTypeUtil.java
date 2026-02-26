package migration4o.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Utility class for detecting and working with collection types.
 * Provides centralized logic for identifying collections, arrays, and their
 * content types.
 */
public class CollectionTypeUtil {

    /**
     * Set of type name substrings that indicate a collection type.
     */
    private static final Set<String> COLLECTION_TYPE_NAMES = new HashSet<>(Arrays.asList("Vector", "ArrayList", "LinkedList", "HashSet", "TreeSet", "LinkedHashSet", "HashMap", "TreeMap", "Stack", "Queue", "Collection", "List", "Set", "Map", "VectRechID" // Project-specific collection type
    ));

    // /**
    // * Determines if a field represents a collection (array, list, set, map,
    // etc.).
    // *
    // * @param field The field to check
    // * @return true if the field is a collection type
    // */
    // public static boolean isCollection(DOSDatabaseField field) {
    // if (field == null) {
    // return false;
    // }

    // return field.isArray() || isCollectionType(field.getTypeName());
    // }

    /**
     * Determines if a type name represents a collection type.
     * 
     * @param typeName The type name to check
     * @return true if the type name represents a collection
     */
    public static boolean isCollectionType(String typeName) {
        if (typeName == null) {
            return false;
        }

        // Check for array notation
        if (typeName.endsWith("[]")) {
            return true;
        }

        // Check if the type name contains any of the collection type names
        for (String collectionType : COLLECTION_TYPE_NAMES) {
            if (typeName.contains(collectionType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Well-known collection base classes. If a class in the schema has one of these
     * as an ancestor (via parentClassName chain), it is a collection type regardless
     * of its own name.
     */
    private static final Set<String> COLLECTION_BASE_CLASSES = new HashSet<>(Arrays.asList("java.util.Vector", "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.TreeSet", "java.util.LinkedHashSet", "java.util.AbstractList", "java.util.AbstractCollection", "java.util.AbstractSet"));

    /**
     * Determines if a type name represents a collection type by walking the class
     * hierarchy in the reference schema. This catches custom classes that extend
     * Vector/List/etc. but whose names don't contain recognizable collection keywords.
     *
     * @param typeName The type name to check
     * @param schemas  The reference and/or database schemas to search for class hierarchy
     * @return true if the type inherits from a known collection base class
     */
    public static boolean isCollectionByAncestry(String typeName, DOSchema[] schemas) {
        if (typeName == null || schemas == null) {
            return false;
        }

        // Fast path: already recognized by name
        if (isCollectionType(typeName)) {
            return true;
        }

        // Walk the parentClassName chain in the schema
        String currentClassName = typeName;
        Set<String> visited = new HashSet<>();
        while (currentClassName != null && !visited.contains(currentClassName)) {
            visited.add(currentClassName);

            if (COLLECTION_BASE_CLASSES.contains(currentClassName)) {
                return true;
            }

            // Look up class in schemas to find its parent
            DOSchemaClass schemaClass = findClassInSchemas(currentClassName, schemas);
            if (schemaClass == null) {
                break;
            }
            currentClassName = schemaClass.parentClassName;
        }

        return false;
    }

    /**
     * Finds a class definition across multiple schemas.
     */
    private static DOSchemaClass findClassInSchemas(String className, DOSchema[] schemas) {
        for (DOSchema schema : schemas) {
            if (schema == null || schema.classes == null)
                continue;
            for (DOSchemaClass cls : schema.classes) {
                if (cls != null && className.equals(cls.source)) {
                    return cls;
                }
            }
        }
        return null;
    }

    // /**
    // * Gets the content type of a collection field.
    // * For arrays, returns the component type.
    // * For generic collections, extracts the type parameter.
    // *
    // * @param field The collection field
    // * @return The content type name, or null if it cannot be determined
    // */
    // public static String getCollectionContentType(DODatabaseField field) {
    // if (field == null || !isCollection(field)) {
    // return null;
    // }

    // System.out.println("DEBUG getCollectionContentType for field: " +
    // field.getName()
    // + ", typeName=" + field.getTypeName()
    // + ", isArray=" + field.isArray()
    // + ", contentTypeClass=" + field.getContentTypeClass()
    // + ", contentTypeName=" + field.getContentTypeName());

    // // First, check if there's a content type class (most reliable)
    // if (field.getContentTypeClass() != null) {
    // String result = field.getContentTypeClass().getAbsoluteName();
    // System.out.println(" -> Using contentTypeClass: " + result);
    // return result;
    // }

    // // If the field already has a content type name, use it
    // String contentTypeName = field.getContentTypeName();
    // if (contentTypeName != null && !contentTypeName.isEmpty()) {
    // System.out.println(" -> Using contentTypeName: " + contentTypeName);
    // return contentTypeName;
    // }

    // // Try to determine from the type name
    // String result = extractContentTypeFromTypeName(field.getTypeName(),
    // field.isArray());
    // System.out.println(" -> Extracted from type name: " + result);
    // return result;
    // }

    /**
     * Extracts the content type from a type name.
     * 
     * @param typeName The type name to analyze
     * @param isArray  Whether this is explicitly marked as an array
     * @return The extracted content type, or "java.lang.Object" as fallback
     */
    public static String extractContentTypeFromTypeName(String typeName, boolean isArray) {
        if (typeName == null) {
            return null;
        }

        if (isArray) {
            // For arrays, try to get the component type
            if (typeName.endsWith("[]")) {
                return typeName.substring(0, typeName.length() - 2);
            }
            // For primitive arrays
            if (typeName.equals("int[]"))
                return "int";
            if (typeName.equals("long[]"))
                return "long";
            if (typeName.equals("double[]"))
                return "double";
            if (typeName.equals("float[]"))
                return "float";
            if (typeName.equals("boolean[]"))
                return "boolean";
            if (typeName.equals("byte[]"))
                return "byte";
            if (typeName.equals("char[]"))
                return "char";
            if (typeName.equals("short[]"))
                return "short";
        }

        // For generic collections, try to extract type parameters
        if (typeName.contains("<") && typeName.contains(">")) {
            int start = typeName.indexOf('<');
            int end = typeName.lastIndexOf('>');
            if (start < end) {
                String genericPart = typeName.substring(start + 1, end);
                // Handle simple case: List<String> -> String
                if (!genericPart.contains(",") && !genericPart.contains("<")) {
                    return genericPart.trim();
                }
                // For Map<K,V> types, return the value type
                if (genericPart.contains(",")) {
                    String[] parts = genericPart.split(",");
                    if (parts.length >= 2) {
                        return parts[1].trim(); // Return value type for maps
                    }
                }
            }
        }

        // For non-generic collections, default to Object
        if (isCollectionType(typeName)) {
            return "java.lang.Object";
        }

        // If we can't determine the content type, return null
        return null;
    }
}
