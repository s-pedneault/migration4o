package migration4o.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for detecting and working with collection types. Provides
 * centralized logic for identifying collections, arrays, and their content
 * types.
 *
 * <p>For ancestry-based detection (walking the parentClassName chain),
 * use {@link migration4o.models.schema.DOSchemaClass#isCollection()},
 * {@link migration4o.models.schema.DOSchemaClass#isMap()}, or
 * {@link migration4o.models.schema.DOSchemaClass#isCollectionOrMap()}.
 */
public class CollectionTypeUtil {

    /**
     * Set of type name substrings that indicate a collection type.
     */
    private static final Set<String> COLLECTION_TYPE_NAMES = new HashSet<>(Arrays.asList("Vector", "ArrayList", "LinkedList", "HashSet", "TreeSet", "LinkedHashSet", "HashMap", "TreeMap", "Hashtable", "Stack", "Queue", "Collection", "List", "Set", "Map", "VectRechID" // Project-specific
                                                                                                                                                                                                                                                                           // collection
                                                                                                                                                                                                                                                                           // type
    ));

    /**
     * Set of type name substrings that indicate a map type.
     */
    private static final Set<String> MAP_TYPE_NAMES = new HashSet<>(Arrays.asList("HashMap", "TreeMap", "Hashtable", "LinkedHashMap", "ConcurrentHashMap", "Map"));

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
     * Determines if a type name represents a map type (Hashtable, HashMap,
     * etc.).
     *
     * @param typeName The type name to check
     * @return true if the type name represents a map
     */
    public static boolean isMapType(String typeName) {
        if (typeName == null) {
            return false;
        }
        for (String mapType : MAP_TYPE_NAMES) {
            if (typeName.contains(mapType)) {
                return true;
            }
        }
        return false;
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
     * @param isArray Whether this is explicitly marked as an array
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
