package dataobjects.util;

import dataobjects.api.models.DOField;

/**
 * Utility class for detecting and working with collection types.
 * Provides centralized logic for identifying collections, arrays, and their
 * content types.
 */
public class CollectionTypeUtil {

    /**
     * Determines if a field represents a collection (array, list, set, map, etc.).
     * 
     * @param field The field to check
     * @return true if the field is a collection type
     */
    public static boolean isCollection(DOField field) {
        if (field == null) {
            return false;
        }

        return field.isArray() || isCollectionType(field.getTypeName());
    }

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

        // Check for common collection types
        return typeName.endsWith("[]") ||
                typeName.contains("Vector") ||
                typeName.contains("ArrayList") ||
                typeName.contains("LinkedList") ||
                typeName.contains("HashSet") ||
                typeName.contains("TreeSet") ||
                typeName.contains("LinkedHashSet") ||
                typeName.contains("HashMap") ||
                typeName.contains("TreeMap") ||
                typeName.contains("Stack") ||
                typeName.contains("Queue") ||
                typeName.contains("Collection") ||
                typeName.contains("List") ||
                typeName.contains("Set") ||
                typeName.contains("Map") ||
                typeName.contains("VectRechID"); // Project-specific collection type
    }

    /**
     * Gets the content type of a collection field.
     * For arrays, returns the component type.
     * For generic collections, extracts the type parameter.
     * 
     * @param field The collection field
     * @return The content type name, or null if it cannot be determined
     */
    public static String getCollectionContentType(DOField field) {
        if (field == null || !isCollection(field)) {
            return null;
        }

        // If the field already has a content type, use it
        String contentTypeName = field.getContentTypeName();
        if (contentTypeName != null && !contentTypeName.isEmpty()) {
            return contentTypeName;
        }

        // Try to determine from the type name
        return extractContentTypeFromTypeName(field.getTypeName(), field.isArray());
    }

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
