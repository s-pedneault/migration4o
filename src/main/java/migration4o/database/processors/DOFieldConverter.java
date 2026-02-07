package migration4o.database.processors;

import java.util.HashMap;
import java.util.Map;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.DODatabaseContext;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;
import migration4o.util.DatabaseUtil;

/**
 * Converter for transforming DB4O StoredField objects to DOSchemaField objects.
 * Provides static methods for field conversion without requiring instantiation.
 */
public class DOFieldConverter {

    /**
     * Type normalization map - converts fully qualified types to their canonical
     * form.
     */
    private static final Map<String, String> TYPE_NORMALIZATION_MAP = new HashMap<String, String>() {
        {
            put("java.lang.String", "string");
            put("java.util.Date", "date");
            put("java.lang.Object", "object");
            put("java.lang.Integer", "int");
            put("java.lang.Long", "long");
            put("java.lang.Boolean", "boolean");
            put("java.lang.Double", "double");
            put("java.lang.Float", "float");
        }
    };

    /**
     * Converts a single StoredField to a DOSchemaField.
     * 
     * @param storedField The DB4O stored field to convert
     * @param context     The database context containing container and stored class
     *                    map
     * @return A DOSchemaField representing the stored field
     */
    public static DOSchemaField convertStoredFieldToSchemaField(
            StoredField storedField,
            DODatabaseContext context) {

        String source = storedField.getName();
        String destination = DatabaseUtil.normalizeFieldName(source);
        String typeName = storedField.getStoredType().getName();
        boolean isArray = storedField.isArray();

        // Determine field type
        String type = determineFieldType(typeName, isArray);

        // Determine if this is a collection
        boolean isCollection = isArray || CollectionTypeUtil.isCollectionType(typeName);

        // Determine children type for collections
        String childrenType = determineChildrenType(typeName, isArray, context.storedClassMap);

        // Create schema field
        DOSchemaField field = new DOSchemaField();
        field.source = source;
        field.destinationName = destination;
        field.type = type;
        field.isExported = true; // Assume all database fields are exported
        field.skipWhen = "DEFAULT"; // Default behavior
        field.isCollection = isCollection;
        field.embedContents = false; // Default - don't embed
        field.childrenType = childrenType;
        field.title = null;
        field.description = null;
        field.pointsTo = null;
        field.childrenSchemaClass = null; // Will be linked later if needed

        return field;
    }

    /**
     * Determines the type of a field from the stored field information.
     * 
     * @param typeName The type name from the stored field
     * @param isArray  Whether the field is an array
     * @return The normalized type name
     */
    public static String determineFieldType(String typeName, boolean isArray) {
        if (typeName == null || typeName.isEmpty()) {
            return "java.lang.Object";
        }

        // For arrays, get the component type
        if (isArray && typeName.endsWith("[]")) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        // Normalize the type name
        return normalizeTypeName(typeName);
    }

    /**
     * Normalizes a type name to match schema conventions.
     * 
     * @param typeName The type name to normalize
     * @return The normalized type name
     */
    public static String normalizeTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return typeName;
        }

        // Check if we have a normalization rule for this type
        String normalized = TYPE_NORMALIZATION_MAP.get(typeName);
        return normalized != null ? normalized : typeName;
    }

    /**
     * Determines the children type for collection fields.
     * 
     * @param typeName       The type name from the stored field
     * @param isArray        Whether the field is an array
     * @param storedClassMap Map of stored classes for reference lookups
     * @return The children type name, or empty string if not a collection
     */
    public static String determineChildrenType(String typeName, boolean isArray,
            Map<String, StoredClass> storedClassMap) {

        if (!isArray && !CollectionTypeUtil.isCollectionType(typeName)) {
            return ""; // Not a collection
        }

        // For arrays, try to get the component type
        if (isArray && typeName.endsWith("[]")) {
            return typeName.substring(0, typeName.length() - 2);
        }

        // For primitive arrays
        if (isArray) {
            if (typeName.equals("int"))
                return "int";
            if (typeName.equals("long"))
                return "long";
            if (typeName.equals("double"))
                return "double";
            if (typeName.equals("float"))
                return "float";
            if (typeName.equals("boolean"))
                return "boolean";
            if (typeName.equals("byte"))
                return "byte";
            if (typeName.equals("char"))
                return "char";
            if (typeName.equals("short"))
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

        // Default to Object for non-generic collections
        return "java.lang.Object";
    }

}
