package migration4o.database.processors;

import java.util.HashMap;
import java.util.Map;

import com.db4o.ext.StoredClass;

import migration4o.database.DODatabaseContext;
import migration4o.database.DODatabaseMonitor;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.DatabaseUtil;

/**
 * Converter for transforming DB4O StoredClass objects to DOSchemaClass objects. Provides static methods for class conversion without requiring instantiation.
 * @deprecated Part of the old DODatabaseReader pipeline. Use {@link migration4o.database.DODatabaseLoader} instead.
 */
@Deprecated
public class DOClassConverter {

    /**
     * Converts a StoredClass directly to a DOSchemaClass.
     * 
     * @param storedClass The DB4O stored class to convert
     * @param context The database context containing container and stored class map
     * @return A DOSchemaClass representing the stored class
     */
    public static DOSchemaClass convertStoredClassToSchemaClass(StoredClass storedClass, DODatabaseContext context) {
        return convertStoredClassToSchemaClass(storedClass, context, null, null);
    }

    /**
     * Converts a StoredClass directly to a DOSchemaClass.
     * 
     * @param storedClass The DB4O stored class to convert
     * @param context The database context containing container and stored class map
     * @param monitor Optional monitor for progress feedback
     * @return A DOSchemaClass representing the stored class
     */
    public static DOSchemaClass convertStoredClassToSchemaClass(StoredClass storedClass, DODatabaseContext context, DODatabaseMonitor monitor) {
        return convertStoredClassToSchemaClass(storedClass, context, monitor, null);
    }

    public static DOSchemaClass convertStoredClassToSchemaClass(StoredClass storedClass, DODatabaseContext context, DODatabaseMonitor monitor, DOSchema schema) {

        String absoluteName = storedClass.getName();
        String simpleName = DatabaseUtil.getSimpleClassName(absoluteName);
        int objectCount = storedClass.instanceCount();
        // String description = buildDescription(storedClass, objectCount);
        // String title = simpleName;

        // Get parent class name
        String parentClassName = null;
        StoredClass parentStoredClass = storedClass.getParentStoredClass();
        if (parentStoredClass != null) {
            parentClassName = parentStoredClass.getName();
        }

        // Create schema class first so it can be referenced by its fields
        DOSchemaClass newClass = new DOSchemaClass(schema);
        newClass.attributes.source = absoluteName;
        newClass.attributes.destinationName = simpleName;
        newClass.attributes.parentClassName = parentClassName;

        // Convert fields, linking them to their parent class
        DOSchemaField[] schemaFields = DOFieldsConverter.convertStoredFieldsToSchemaFields(storedClass, context, monitor, schema, newClass);

        // Get object IDs - this can be very expensive for classes with many objects
        if (monitor != null) {
            monitor.onRetrievingObjectIds(absoluteName, objectCount);
        }

        long[] objectIds = storedClass.getIDs();
        if (objectIds == null) {
            objectIds = new long[0];
        }

        if (monitor != null) {
            monitor.onObjectIdsRetrieved(absoluteName, objectIds.length);
        }

        // Create schema class - all database classes are marked as migrate=true
        newClass.setFields(schemaFields);
        newClass.schemaReferences = null; // Will be resolved later if needed
        newClass.attributes.migrate = true; // All database classes are migratable
        newClass.objectIds = objectIds;
        newClass.attributes.pointsTo = null;

        return newClass;
    }

    /**
     * Builds a description from stored class information.
     * 
     * @param storedClass The stored class
     * @param objectCount The number of objects in the class
     * @return A description string
     */
    public static String buildDescription(StoredClass storedClass, int objectCount) {
        StringBuilder desc = new StringBuilder();
        desc.append("Inferred from database");

        if (objectCount > 0) {
            desc.append(" (").append(objectCount).append(" objects)");
        }

        return desc.toString();
    }

    /**
     * Creates a map of stored classes by name for quick lookup.
     * 
     * @param storedClasses The array of stored classes
     * @return A map with class names as keys and stored classes as values
     */
    public static Map<String, StoredClass> createStoredClassMap(StoredClass[] storedClasses) {
        Map<String, StoredClass> map = new HashMap<>();

        for (StoredClass storedClass : storedClasses) {
            map.put(storedClass.getName(), storedClass);
            // Also map by short name for convenience
            String shortName = DatabaseUtil.getSimpleClassName(storedClass.getName());
            if (!shortName.equals(storedClass.getName())) {
                map.put(shortName, storedClass);
            }
        }

        return map;
    }

}
