package migration4o.engine.resolvers;

import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.engine.DOEngine;
import migration4o.engine.resolvers.DOGenericObjectResolver;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import com.db4o.reflect.generic.GenericObject;

public class DOGenericObjectResolver {

    public DOSchemaClass resolveClass(GenericObject genericObject, DOSchema schema) {
        if (genericObject == null || schema == null) {
            return null;
        }

        String className = getClassName(genericObject);
        if (className == null) {
            return null;
        }

        // Search in schema classes
        DOSchemaClass[] schemaClasses = schema.getClasses();
        for (DOSchemaClass schemaClass : schemaClasses) {
            if (className.equals(schemaClass.source)) {
                return schemaClass;
            }
        }

        return null;
    }

    public DODatabaseClass resolveClass(GenericObject genericObject, DODatabase database) {
        if (genericObject == null || database == null) {
            return null;
        }

        String className = getClassName(genericObject);
        if (className == null) {
            return null;
        }

        // Search in database classes
        DODatabaseClass[] databaseClasses = database.getClasses();
        for (DODatabaseClass databaseClass : databaseClasses) {
            if (className.equals(databaseClass.getAbsoluteName())) {
                return databaseClass;
            }
        }

        return null;
    }

    public DODatabaseClass resolveClass(GenericObject genericObject, DOEngine engine) {
        if (genericObject == null || engine == null) {
            return null;
        }

        // Try schema first
        DOSchema schema = engine.getSchema();
        if (schema != null) {
            DOSchemaClass schemaClass = resolveClass(genericObject, schema);
            if (schemaClass != null && schemaClass.databaseClass != null) {
                return schemaClass.databaseClass;
            }
        }

        // If not found in schema, try database
        DODatabase database = engine.getDatabase();
        if (database != null) {
            DODatabaseClass databaseClass = resolveClass(genericObject, database);
            if (databaseClass != null) {
                return databaseClass;
            }
        }

        return null;
    }

    /**
     * Extract the class name from a GenericObject.
     * Uses the GenericClass name which should contain the full class name.
     */
    private String getClassName(GenericObject genericObject) {
        try {
            if (genericObject.getGenericClass() != null) {
                return genericObject.getGenericClass().getName();
            }
        } catch (Exception e) {
            // If there's any issue accessing the class information, return null
        }
        return null;
    }
}
