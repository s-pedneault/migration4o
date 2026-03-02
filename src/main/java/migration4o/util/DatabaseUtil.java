package migration4o.util;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for database-related operations and conversions.
 */
public class DatabaseUtil {

    /**
     * Counts total objects across all stored classes.
     */
    public static int countTotalObjects(StoredClass[] storedClasses) {
        int total = 0;
        for (StoredClass storedClass : storedClasses) {
            try {
                total += storedClass.instanceCount();
            } catch (Exception e) {
                System.out.println("Warning: Could not get instance count for class: " + storedClass.getName());
            }
        }
        return total;
    }

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
            if (className.equals(schemaClass.source)) {
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
            if (fieldName.equals(field.source)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name, searching the schema class and all its
     * ancestor classes.
     * This is CRITICAL for proper field lookup - fields may be defined in parent
     * classes.
     * 
     * @param schemaClass the schema class to start searching from
     * @param fieldName   the field name to find
     * @param schema      the schema containing all class definitions
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
        if (schemaClass.parentClassName != null && !schemaClass.parentClassName.isEmpty() && schema != null) {
            DOSchemaClass parentClass = SchemaUtil.findClassByName(schemaClass.parentClassName, schema);
            if (parentClass != null) {
                return findSchemaFieldByNameIncludingAncestors(parentClass, fieldName, schema);
            }
        }

        return null;
    }

    /**
     * Finds a schema field by its <em>destination name</em> within a schema
     * class and all its ancestor classes.
     *
     * @param schemaClass the class to start from
     * @param destName    the {@code destinationName} to look for
     * @param schema      schema for ancestor resolution
     * @return the matching field, or {@code null} if not found
     */
    public static DOSchemaField findSchemaFieldByDestinationNameIncludingAncestors(DOSchemaClass schemaClass, String destName, DOSchema schema) {
        if (schemaClass == null || destName == null) {
            return null;
        }
        if (schemaClass.fields != null) {
            for (DOSchemaField field : schemaClass.fields) {
                if (field != null && destName.equals(field.destinationName)) {
                    return field;
                }
            }
        }
        if (schemaClass.parentClassName != null && !schemaClass.parentClassName.isEmpty() && schema != null) {
            DOSchemaClass parent = SchemaUtil.findClassByName(schemaClass.parentClassName, schema);
            if (parent != null) {
                return findSchemaFieldByDestinationNameIncludingAncestors(parent, destName, schema);
            }
        }
        return null;
    }

    /**
     * Returns all fields declared on {@code schemaClass} and every ancestor
     * class, in declaration order (most-derived class first). Fields with the
     * same {@code destinationName} are deduplicated (the most-derived
     * definition wins).
     *
     * @param schemaClass starting class
     * @param schema      schema for ancestor resolution
     * @return ordered, deduplicated list of all fields
     */
    public static java.util.List<DOSchemaField> getAllSchemaFieldsIncludingAncestors(DOSchemaClass schemaClass, DOSchema schema) {
        java.util.List<DOSchemaField> result = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        DOSchemaClass current = schemaClass;
        while (current != null) {
            if (current.fields != null) {
                for (DOSchemaField field : current.fields) {
                    if (field != null && field.destinationName != null && !field.destinationName.isEmpty() && seen.add(field.destinationName)) {
                        result.add(field);
                    }
                }
            }
            current = (current.parentClassName != null && !current.parentClassName.isEmpty() && schema != null) ? SchemaUtil.findClassByName(current.parentClassName, schema) : null;
        }
        return result;
    }

    /**
     * Normalizes a database field name to XML-friendly camelCase format.
     * Replicates the Python transform_destination_name() logic:
     * - Removes leading 'm' prefix if followed by uppercase letter
     * - Converts 'ID' prefix at start to lowercase 'id'
     * - Lowercases the first letter
     * 
     * Examples:
     * - mNom → nom
     * - mDateCreation → dateCreation
     * - mID → id
     * - mIDSSI → idssi
     * - IDDossPrev → idDossPrev
     * - Name → name
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

    // /**
    // * Converts a StoredField to a DODatabaseField with schema enhancement.
    // */
    // public static DODatabaseField convertStoredFieldToDOField(StoredField
    // storedField, DOSchemaField schemaField) {
    // String fieldName = storedField.getName();
    // String typeName = storedField.getStoredType().getName();
    // String description = "";
    // boolean isPrimitive = TypeUtil.isPrimitiveType(typeName);
    // boolean isArray = storedField.isArray();
    // boolean isCollection = isArray ||
    // CollectionTypeUtil.isCollectionType(typeName);

    // String contentTypeName = null;
    // if (isCollection) {
    // if (schemaField != null && schemaField.childrenType != null) {
    // contentTypeName = schemaField.childrenType;
    // System.out.println("Enhanced field " + fieldName + " with schema content
    // type: " + contentTypeName);
    // } else {
    // contentTypeName = CollectionTypeUtil.extractContentTypeFromTypeName(typeName,
    // isArray);
    // }
    // }

    // return new DODatabaseField(fieldName, description, typeName, null,
    // isPrimitive, isCollection, contentTypeName,
    // null);
    // }

    // /**
    // * Extracts class name from a resolved object.
    // */
    // public static String getClassNameFromObject(DODatabaseObject obj) {
    // if (obj.getMostSpecificClass() != null) {
    // return obj.getMostSpecificClass().getAbsoluteName();
    // }

    // DODatabaseClass[] allClasses = obj.getAllClasses();
    // if (allClasses != null && allClasses.length > 0) {
    // return allClasses[0].getAbsoluteName();
    // }

    // return null;
    // }

    // /**
    // * Finds resolved objects for a database class using multiple name lookup
    // * strategies.
    // */
    // public static java.util.List<DODatabaseObject> findResolvedObjectsForClass(
    // DODatabaseClass databaseClass,
    // java.util.Map<String, java.util.List<DODatabaseObject>> classToObjectsMap) {

    // // Try absolute name first
    // java.util.List<DODatabaseObject> objects =
    // classToObjectsMap.get(databaseClass.getAbsoluteName());
    // if (objects != null) {
    // return objects;
    // }

    // // Try short name
    // objects = classToObjectsMap.get(databaseClass.getShortName());
    // if (objects != null) {
    // return objects;
    // }

    // // Try simple class name as fallback
    // objects =
    // classToObjectsMap.get(getSimpleClassName(databaseClass.getAbsoluteName()));
    // if (objects != null) {
    // return objects;
    // }

    // return new java.util.ArrayList<>();
    // }

    // /**
    // * Creates a single database class from a stored class and schema information.
    // */
    // public static DODatabaseClass createDatabaseClass(StoredClass storedClass,
    // DOSchema schema) {
    // String className = storedClass.getName();
    // int objectCount = storedClass.instanceCount();

    // String superClassName = null;
    // StoredClass parentStoredClass = storedClass.getParentStoredClass();
    // if (parentStoredClass != null) {
    // superClassName = parentStoredClass.getName();
    // }

    // DOSchemaClass matchingSchemaClass = findSchemaClassByName(schema, className);
    // DODatabaseField[] fields = extractFieldsFromStoredClass(storedClass,
    // matchingSchemaClass);

    // // Get object IDs while database is still open
    // long[] objectIds = storedClass.getIDs();
    // if (objectIds == null) {
    // objectIds = new long[0];
    // }

    // return new DODatabaseClass(
    // className,
    // getSimpleClassName(className),
    // "Class from database",
    // getSimpleClassName(className),
    // superClassName,
    // fields,
    // objectCount,
    // 0,
    // objectIds);
    // }

    // /**
    // * Extracts fields from a stored class with schema enhancement.
    // * Deduplicates fields with the same name (keeps array version if both exist).
    // */
    // public static DODatabaseField[] extractFieldsFromStoredClass(StoredClass
    // storedClass, DOSchemaClass schemaClass) {
    // try {
    // StoredField[] storedFields = storedClass.getStoredFields();

    // // Use a map to deduplicate fields by name
    // // Key: field name, Value: StoredField
    // java.util.Map<String, StoredField> fieldMap = new
    // java.util.LinkedHashMap<>();

    // for (StoredField sf : storedFields) {
    // String fieldName = sf.getName();
    // StoredField existing = fieldMap.get(fieldName);

    // if (existing == null) {
    // // First occurrence of this field name
    // fieldMap.put(fieldName, sf);
    // } else {
    // // Duplicate field name - prefer array version
    // if (sf.isArray() && !existing.isArray()) {
    // // New field is array, existing is not - replace with array version
    // fieldMap.put(fieldName, sf);
    // }
    // // else keep existing (either both are arrays, both are non-arrays, or
    // existing
    // // is already array)
    // }
    // }

    // // Convert deduplicated fields to DODatabaseField array
    // DODatabaseField[] fields = new DODatabaseField[fieldMap.size()];
    // int index = 0;
    // for (StoredField sf : fieldMap.values()) {
    // DOSchemaField matchingSchemaField = findSchemaFieldByName(schemaClass,
    // sf.getName());
    // fields[index++] = convertStoredFieldToDOField(sf, matchingSchemaField);
    // }

    // return fields;
    // } catch (Exception e) {
    // System.out.println(
    // "Warning: Could not extract fields for class " + storedClass.getName() + ": "
    // + e.getMessage());
    // return new DODatabaseField[0];
    // }
    // }

    /**
     * Reads a single stored field value from a DB4O {@link GenericObject} by
     * source field name. Returns {@code null} if the field is not found or the
     * object is not a {@link GenericObject}.
     */
    public static Object getStoredFieldValue(ExtObjectContainer container, Object obj, String fieldName) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }
        StoredClass storedClass = container.ext().storedClass(obj);
        if (storedClass == null) {
            return null;
        }
        StoredField field = storedClass.storedField(fieldName, null);
        if (field == null) {
            return null;
        }
        return field.get(obj);
    }

    /**
     * Traverses a dotted <em>source</em> field path (e.g. {@code "mAdresse.mRue"})
     * through a chain of DB4O {@link GenericObject}s and returns the leaf value,
     * or {@code null} if any step fails. Each path segment must be the raw DB4O
     * field name (i.e. the {@code source} attribute in the schema, not the
     * {@code destinationName}).
     *
     * @param container DB4O container
     * @param obj       starting object
     * @param fieldPath dot-separated source field path
     * @return value at the end of the path, or {@code null} on any failure
     */
    public static Object getFieldValueByPath(ExtObjectContainer container, Object obj, String fieldPath) {
        if (obj == null || fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }
        String[] segments = fieldPath.split("\\.");
        Object current = obj;
        for (String segment : segments) {
            if (current == null)
                return null;
            current = getStoredFieldValue(container, current, segment);
        }
        return current;
    }

    /**
     * Checks if a stored class should be included in database analysis.
     * Filters out db4o internal classes and empty classes.
     */
    public static boolean isValidDatabaseClass(StoredClass storedClass) {
        String className = storedClass.getName();
        long instanceCount = storedClass.instanceCount();
        return !className.startsWith("com.db4o.") && instanceCount > 0;
    }

    /**
     * Safely gets stored classes from container with error handling.
     */
    public static StoredClass[] getStoredClassesSafely(com.db4o.ext.ExtObjectContainer container) {
        try {
            return container.storedClasses();
        } catch (Exception e) {
            System.out.println("Warning: Could not enumerate stored classes: " + e.getMessage());
            return new StoredClass[0];
        }
    }

    // /**
    // * Creates a fallback empty database when there are errors.
    // */
    // public static DODatabase createEmptyDatabase(com.db4o.ext.ExtObjectContainer
    // container,
    // DODatabaseEncoding encoding,
    // String databaseSize) {
    // return new DODatabase(container, encoding, 0, 0, databaseSize,
    // new DODatabaseClass[0]);
    // }

    /**
     * Gets all fields from a StoredClass including fields from all ancestor
     * classes.
     * This is CRITICAL for proper object export - DB4O's getStoredFields() only
     * returns
     * fields declared in that specific class, missing inherited fields.
     * 
     * @param storedClass the stored class to get fields from
     * @return array of all fields including those from ancestors
     */
    public static com.db4o.ext.StoredField[] getAllFieldsIncludingAncestors(StoredClass storedClass) {
        java.util.List<com.db4o.ext.StoredField> allFields = new java.util.ArrayList<>();

        // Traverse up the inheritance hierarchy
        StoredClass currentClass = storedClass;
        while (currentClass != null) {
            com.db4o.ext.StoredField[] classFields = currentClass.getStoredFields();
            if (classFields != null) {
                for (com.db4o.ext.StoredField field : classFields) {
                    allFields.add(field);
                }
            }
            currentClass = currentClass.getParentStoredClass();
        }

        return allFields.toArray(new com.db4o.ext.StoredField[0]);
    }
}