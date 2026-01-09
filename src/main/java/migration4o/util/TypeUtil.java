package migration4o.util;

import migration4o.models.database.DODatabaseField;

public class TypeUtil {

    public static boolean isPrimitiveType(DODatabaseField field) {
        String typeName = field.getTypeName();
        return isPrimitiveType(typeName);
    }

    public static boolean isPrimitiveType(String typeName) {
        if (typeName == null) {
            return false;
        }

        // Strip array notation (e.g., "int[]" -> "int", "String[][]" -> "String")
        String baseTypeName = typeName.replaceAll("\\[\\]", "").trim();

        String lowerTypeName = baseTypeName.toLowerCase();

        // Java primitive types
        if (lowerTypeName.equals("boolean") || lowerTypeName.equals("byte") || lowerTypeName.equals("char") ||
                lowerTypeName.equals("short") || lowerTypeName.equals("int") || lowerTypeName.equals("long") ||
                lowerTypeName.equals("float") || lowerTypeName.equals("double")) {
            return true;
        }

        // Common Java standard library types that are considered "primitive" for our
        // purposes
        // Check both fully qualified names and simple class names (case-insensitive)
        return matchesType(lowerTypeName, "java.lang.object") ||
                matchesType(lowerTypeName, "java.lang.string") ||
                matchesType(lowerTypeName, "java.lang.integer") ||
                matchesType(lowerTypeName, "java.lang.long") ||
                matchesType(lowerTypeName, "java.lang.double") ||
                matchesType(lowerTypeName, "java.lang.float") ||
                matchesType(lowerTypeName, "java.lang.boolean") ||
                matchesType(lowerTypeName, "java.lang.character") ||
                matchesType(lowerTypeName, "java.lang.byte") ||
                matchesType(lowerTypeName, "java.lang.short") ||
                matchesType(lowerTypeName, "java.math.bigdecimal") ||
                matchesType(lowerTypeName, "java.math.biginteger") ||
                matchesType(lowerTypeName, "java.util.date") ||
                matchesType(lowerTypeName, "java.sql.date") ||
                matchesType(lowerTypeName, "java.sql.time") ||
                matchesType(lowerTypeName, "java.sql.timestamp") ||
                matchesType(lowerTypeName, "java.time.localdate") ||
                matchesType(lowerTypeName, "java.time.localtime") ||
                matchesType(lowerTypeName, "java.time.localdatetime") ||
                matchesType(lowerTypeName, "java.time.zoneddatetime") ||
                matchesType(lowerTypeName, "java.util.uuid");
    }

    /**
     * Check if typeName matches either the full qualified name or the simple class
     * name.
     * Both parameters should be in lowercase.
     */
    private static boolean matchesType(String lowerTypeName, String lowerFullName) {
        if (lowerTypeName.equals(lowerFullName)) {
            return true;
        }
        // Extract simple name (everything after the last dot)
        int lastDot = lowerFullName.lastIndexOf('.');
        if (lastDot >= 0) {
            String simpleName = lowerFullName.substring(lastDot + 1);
            return lowerTypeName.equals(simpleName);
        }
        return false;
    }

}
