package migration4o.database.processors;

import java.util.ArrayList;
import java.util.List;

import com.db4o.ext.StoredClass;

import migration4o.database.DODatabaseContext;
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

        List<DOSchemaClass> schemaClasses = new ArrayList<>();

        for (StoredClass storedClass : storedClasses) {
            try {
                DOSchemaClass schemaClass = DOClassConverter.convertStoredClassToSchemaClass(storedClass, context);
                schemaClasses.add(schemaClass);
            } catch (Exception e) {
                System.out.println("Warning: Could not convert stored class '" +
                        storedClass.getName() + "' to schema class: " + e.getMessage());
            }
        }

        return schemaClasses.toArray(new DOSchemaClass[0]);
    }
}
