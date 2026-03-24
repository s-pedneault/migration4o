package migration4o.util;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for database-related operations and conversions.
 */
public class DatabaseUtil {

    /**
     * Extracts simple class name from absolute class name.
     */
    public static String getSimpleClassName(String absoluteName) {
        if (absoluteName == null)
            return "Unknown";
        int lastDot = absoluteName.lastIndexOf('.');
        return lastDot >= 0 ? absoluteName.substring(lastDot + 1) : absoluteName;
    }

    /**
     * Finds a schema class by its absolute name.
     */
    public static DOSchemaClass findSchemaClassByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (className.equals(schemaClass.attributes.source)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name within a schema class.
     */
    public static DOSchemaField findSchemaFieldByName(DOSchemaClass schemaClass, String fieldName) {
        if (schemaClass == null || schemaClass.fields == null) {
            return null;
        }

        for (DOSchemaField field : schemaClass.fields) {
            if (fieldName.equals(field.attributes.source)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name, searching the schema class and all its ancestor classes. This is CRITICAL for proper field lookup - fields may be defined in parent classes.
     * 
     * @param schemaClass the schema class to start searching from
     * @param fieldName the field name to find
     * @param schema the schema containing all class definitions
     * @return the schema field definition, or null if not found
     */
    public static DOSchemaField findSchemaFieldByNameIncludingAncestors(DOSchemaClass schemaClass, String fieldName, DOSchema schema) {
        if (schemaClass == null || fieldName == null) {
            return null;
        }

        // Search in current class
        DOSchemaField field = findSchemaFieldByName(schemaClass, fieldName);
        if (field != null) {
            return field;
        }

        // Search in parent class if it exists
        if (schemaClass.attributes.parentClassName != null && !schemaClass.attributes.parentClassName.isEmpty() && schema != null) {
            DOSchemaClass parentClass = schema.findClassByName(schemaClass.attributes.parentClassName);
            if (parentClass != null) {
                return findSchemaFieldByNameIncludingAncestors(parentClass, fieldName, schema);
            }
        }

        return null;
    }

    /**
     * Finds a schema field by its <em>destination name</em> within a schema class and all its ancestor classes.
     *
     * @param schemaClass the class to start from
     * @param destName the {@code destinationName} to look for
     * @param schema schema for ancestor resolution
     * @return the matching field, or {@code null} if not found
     */
    public static DOSchemaField findSchemaFieldByDestinationNameIncludingAncestors(DOSchemaClass schemaClass, String destName, DOSchema schema) {
        if (schemaClass == null || destName == null) {
            return null;
        }
        if (schemaClass.fields != null) {
            for (DOSchemaField field : schemaClass.fields) {
                if (field != null && destName.equals(field.attributes.destinationName)) {
                    return field;
                }
            }
        }
        if (schemaClass.attributes.parentClassName != null && !schemaClass.attributes.parentClassName.isEmpty() && schema != null) {
            DOSchemaClass parent = schema.findClassByName(schemaClass.attributes.parentClassName);
            if (parent != null) {
                return findSchemaFieldByDestinationNameIncludingAncestors(parent, destName, schema);
            }
        }
        return null;
    }

    /**
     * Returns all fields declared on {@code schemaClass} and every ancestor class, in declaration order (most-derived class first). Fields with the same {@code destinationName} are deduplicated (the most-derived definition wins).
     *
     * @param schemaClass starting class
     * @param schema schema for ancestor resolution
     * @return ordered, deduplicated list of all fields
     */
    public static java.util.List<DOSchemaField> getAllSchemaFieldsIncludingAncestors(DOSchemaClass schemaClass, DOSchema schema) {
        java.util.List<DOSchemaField> result = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        DOSchemaClass current = schemaClass;
        while (current != null) {
            if (current.fields != null) {
                for (DOSchemaField field : current.fields) {
                    if (field != null && field.attributes.destinationName != null && !field.attributes.destinationName.isEmpty() && seen.add(field.attributes.destinationName)) {
                        result.add(field);
                    }
                }
            }
            current = (current.attributes.parentClassName != null && !current.attributes.parentClassName.isEmpty() && schema != null) ? schema.findClassByName(current.attributes.parentClassName) : null;
        }
        return result;
    }

    /**
     * Normalizes a database field name to XML-friendly camelCase format. Replicates the Python transform_destination_name() logic: - Removes leading 'm' prefix if followed by uppercase letter - Converts 'ID' prefix at start to lowercase 'id' - Lowercases the first letter
     * 
     * Examples: - mNom → nom - mDateCreation → dateCreation - mID → id - mIDSSI → idssi - IDDossPrev → idDossPrev - Name → name
     * 
     * @param sourceName The original field name from the database
     * @return The normalized field name
     */
    public static String normalizeFieldName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) {
            return sourceName;
        }

        String name = sourceName;

        // Remove leading 'm' if it's lowercase and followed by uppercase letter
        if (name.startsWith("m") && name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            name = name.substring(1);
        }

        // Handle ID prefix (e.g., "IDSomething" -> "idSomething")
        if (name.startsWith("ID") && name.length() > 2) {
            name = "id" + name.substring(2);
        }

        // Lowercase first letter
        if (name.length() > 0) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }

    /**
     * Reads a single stored field value from a DB4O object by source field
     * name. Delegates to {@link DODatabaseDelegate#getStoredFieldValue}.
     */
    public static Object getStoredFieldValue(DODatabaseDelegate delegate, Object obj, String fieldName) {
        return delegate.getStoredFieldValue(obj, fieldName);
    }

    /**
     * Traverses a dotted source field path through a chain of DB4O objects.
     * Delegates to {@link DODatabaseDelegate#getFieldValueByPath}.
     */
    public static Object getFieldValueByPath(DODatabaseDelegate delegate, Object obj, String fieldPath) {
        return delegate.getFieldValueByPath(obj, fieldPath);
    }

    /**
     * Safely gets stored classes from container with error handling.
     */
    public static StoredClass[] getStoredClassesSafely(DODatabaseDelegate delegate) {
        try {
            return delegate.storedClasses();
        } catch (Exception e) {
            System.out.println("Warning: Could not enumerate stored classes: " + e.getMessage());
            return new StoredClass[0];
        }
    }

    /**
     * Gets all fields from a StoredClass including ancestor fields.
     * Delegates to {@link DODatabaseDelegate#getAllFieldsIncludingAncestors(StoredClass)}.
     */
    public static StoredField[] getAllFieldsIncludingAncestors(DODatabaseDelegate delegate, StoredClass storedClass) {
        return delegate.getAllFieldsIncludingAncestors(storedClass);
    }

    /**
     * @deprecated Use {@link #getAllFieldsIncludingAncestors(DODatabaseDelegate, StoredClass)} instead.
     */
    @Deprecated
    public static StoredField[] getAllFieldsIncludingAncestors(StoredClass storedClass) {
        java.util.List<StoredField> allFields = new java.util.ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        StoredClass currentClass = storedClass;
        while (currentClass != null) {
            StoredField[] classFields = currentClass.getStoredFields();
            if (classFields != null) {
                for (StoredField field : classFields) {
                    if (seenNames.add(field.getName())) {
                        allFields.add(field);
                    }
                }
            }
            currentClass = currentClass.getParentStoredClass();
        }
        return allFields.toArray(new StoredField[0]);
    }
}