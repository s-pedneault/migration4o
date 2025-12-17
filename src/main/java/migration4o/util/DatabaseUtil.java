package migration4o.util;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.DODatabaseEncoding;
import migration4o.models.DOClass;
import migration4o.models.DOField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseObject;
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
            if (className.equals(schemaClass.getAbsoluteName())) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name within a schema class.
     */
    public static DOSchemaField findSchemaFieldByName(DOSchemaClass schemaClass, String fieldName) {
        if (schemaClass == null || schemaClass.getFields() == null) {
            return null;
        }

        for (DOField field : schemaClass.getFields()) {
            if (fieldName.equals(field.getName()) && field instanceof DOSchemaField) {
                return (DOSchemaField) field;
            }
        }
        return null;
    }

    /**
     * Converts a StoredField to a DOField with schema enhancement.
     */
    public static DOField convertStoredFieldToDOField(StoredField storedField, DOSchemaField schemaField) {
        String fieldName = storedField.getName();
        String typeName = storedField.getStoredType().getName();
        String description = "";
        boolean isPrimitive = schemaField != null ? schemaField.isPrimitive() : false;
        boolean isArray = storedField.isArray();
        boolean isCollection = isArray || CollectionTypeUtil.isCollectionType(typeName);

        String contentTypeName = null;
        if (isCollection) {
            if (schemaField != null && schemaField.getContentTypeName() != null) {
                contentTypeName = schemaField.getContentTypeName();
                System.out.println("Enhanced field " + fieldName + " with schema content type: " + contentTypeName);
            } else {
                contentTypeName = CollectionTypeUtil.extractContentTypeFromTypeName(typeName, isArray);
            }
        }

        return new DOField(fieldName, description, typeName, null, isPrimitive, isCollection, contentTypeName,
                null);
    }

    /**
     * Extracts class name from a resolved object.
     */
    public static String getClassNameFromObject(DODatabaseObject obj) {
        if (obj.getMostSpecificClass() != null) {
            return obj.getMostSpecificClass().getAbsoluteName();
        }

        DOClass[] allClasses = obj.getAllClasses();
        if (allClasses != null && allClasses.length > 0) {
            return allClasses[0].getAbsoluteName();
        }

        return null;
    }

    /**
     * Finds resolved objects for a database class using multiple name lookup
     * strategies.
     */
    public static java.util.List<DODatabaseObject> findResolvedObjectsForClass(
            DODatabaseClass databaseClass,
            java.util.Map<String, java.util.List<DODatabaseObject>> classToObjectsMap) {

        // Try absolute name first
        java.util.List<DODatabaseObject> objects = classToObjectsMap.get(databaseClass.getAbsoluteName());
        if (objects != null) {
            return objects;
        }

        // Try short name
        objects = classToObjectsMap.get(databaseClass.getShortName());
        if (objects != null) {
            return objects;
        }

        // Try simple class name as fallback
        objects = classToObjectsMap.get(getSimpleClassName(databaseClass.getAbsoluteName()));
        if (objects != null) {
            return objects;
        }

        return new java.util.ArrayList<>();
    }

    /**
     * Creates a single database class from a stored class and schema information.
     */
    public static DODatabaseClass createDatabaseClass(StoredClass storedClass, DOSchema schema) {
        String className = storedClass.getName();
        int objectCount = storedClass.instanceCount();

        String superClassName = null;
        StoredClass parentStoredClass = storedClass.getParentStoredClass();
        if (parentStoredClass != null) {
            superClassName = parentStoredClass.getName();
        }

        DOSchemaClass matchingSchemaClass = findSchemaClassByName(schema, className);
        DOField[] fields = extractFieldsFromStoredClass(storedClass, matchingSchemaClass);

        return new DODatabaseClass(
                className,
                getSimpleClassName(className),
                "Class from database",
                getSimpleClassName(className),
                superClassName,
                fields,
                objectCount,
                0);
    }

    /**
     * Extracts fields from a stored class with schema enhancement.
     */
    public static DOField[] extractFieldsFromStoredClass(StoredClass storedClass, DOSchemaClass schemaClass) {
        try {
            StoredField[] storedFields = storedClass.getStoredFields();
            DOField[] fields = new DOField[storedFields.length];

            for (int i = 0; i < storedFields.length; i++) {
                DOSchemaField matchingSchemaField = findSchemaFieldByName(schemaClass, storedFields[i].getName());
                fields[i] = convertStoredFieldToDOField(storedFields[i], matchingSchemaField);
            }

            return fields;
        } catch (Exception e) {
            System.out.println(
                    "Warning: Could not extract fields for class " + storedClass.getName() + ": " + e.getMessage());
            return new DOField[0];
        }
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

    /**
     * Creates a fallback empty database when there are errors.
     */
    public static DODatabase createEmptyDatabase(com.db4o.ext.ExtObjectContainer container,
            DODatabaseEncoding encoding,
            String databaseSize) {
        return new DODatabase(container, encoding, 0, 0, databaseSize,
                new DODatabaseClass[0]);
    }
}