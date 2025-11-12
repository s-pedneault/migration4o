package dataobjects.impl.engine.resolvers;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.engine.resolvers.DOGenericObjectResolver;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import com.db4o.reflect.generic.GenericObject;

public class DOGenericObjectResolverImpl implements DOGenericObjectResolver {

    @Override
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
            if (className.equals(schemaClass.getAbsoluteName())) {
                return schemaClass;
            }
        }

        return null;
    }

    @Override
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

    @Override
    public DOClass resolveClass(GenericObject genericObject, DOEngine engine) {
        if (genericObject == null || engine == null) {
            return null;
        }

        // Try schema first
        DOSchema schema = engine.getSchema();
        if (schema != null) {
            DOSchemaClass schemaClass = resolveClass(genericObject, schema);
            if (schemaClass != null) {
                return schemaClass;
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
