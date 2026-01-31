package migration4o.database.processors;

import java.util.ArrayList;
import java.util.List;

import com.db4o.ext.StoredClass;

import migration4o.database.DODatabaseContext;
import migration4o.database.DODatabaseMonitor;
import migration4o.models.schema.DOSchemaClass;

/**
 * Converter for transforming DB4O StoredClass arrays to DOSchemaClass arrays.
 * Provides static methods for batch class conversion without requiring
 * instantiation.
 */
public class DOClassesConverter {

    /**
     * Private constructor to prevent instantiation.
     */
    private DOClassesConverter() {
    }

    /**
     * Converts an array of StoredClass objects to an array of DOSchemaClass
     * objects.
     * 
     * @param storedClasses The array of DB4O stored classes to convert
     * @param context       The database context containing container and stored
     *                      class map
     * @return Array of converted schema classes
     */
    public static DOSchemaClass[] convertStoredClassesToSchemaClasses(
            StoredClass[] storedClasses,
            DODatabaseContext context) {
        return convertStoredClassesToSchemaClasses(storedClasses, context, null);
    }

    /**
     * Converts an array of StoredClass objects to an array of DOSchemaClass
     * objects.
     * 
     * @param storedClasses The array of DB4O stored classes to convert
     * @param context       The database context containing container and stored
     *                      class map
     * @param monitor       Optional monitor for progress feedback
     * @return Array of converted schema classes
     */
    public static DOSchemaClass[] convertStoredClassesToSchemaClasses(
            StoredClass[] storedClasses,
            DODatabaseContext context,
            DODatabaseMonitor monitor) {

        List<DOSchemaClass> schemaClasses = new ArrayList<>();

        for (int i = 0; i < storedClasses.length; i++) {
            StoredClass storedClass = storedClasses[i];
            try {
                if (monitor != null) {
                    monitor.onConvertingClass(storedClass.getName(), i + 1, storedClasses.length);
                }

                DOSchemaClass schemaClass = DOClassConverter.convertStoredClassToSchemaClass(storedClass, context,
                        monitor);
                schemaClasses.add(schemaClass);

                if (monitor != null) {
                    monitor.onClassConverted(storedClass.getName(),
                            schemaClass.fields != null ? schemaClass.fields.length : 0);
                }
            } catch (Exception e) {
                String errorMsg = "Could not convert stored class '" +
                        storedClass.getName() + "' to schema class: " + e.getMessage();
                if (monitor != null) {
                    monitor.onClassConversionWarning(storedClass.getName(), e.getMessage());
                } else {
                    System.out.println("Warning: " + errorMsg);
                }
            }
        }

        return schemaClasses.toArray(new DOSchemaClass[0]);
    }
}
