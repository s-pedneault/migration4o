package dataobjects.impl.engine.resolvers;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.engine.resolvers.DOFieldResolver;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.impl.models.DOFieldImpl;
import dataobjects.util.CollectionTypeUtil;

import java.util.HashMap;
import java.util.Map;

public class DOFieldResolverImpl implements DOFieldResolver {

    @Override
    public void resolveFieldTypes(DOEngine engine) {
        if (engine == null) {
            return;
        }

        // Build a map of class name to class object for quick lookup
        Map<String, DOClass> classMap = new HashMap<>();

        // Add schema classes to the map
        DOSchema schema = engine.getSchema();
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass != null && schemaClass.getAbsoluteName() != null) {
                    classMap.put(schemaClass.getAbsoluteName(), schemaClass);
                }
            }
        }

        // Add database classes to the map
        DODatabase database = engine.getDatabase();
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass databaseClass : database.getClasses()) {
                if (databaseClass != null && databaseClass.getAbsoluteName() != null) {
                    // Prefer schema classes over database classes if both exist
                    classMap.putIfAbsent(databaseClass.getAbsoluteName(), databaseClass);
                }
            }
        }

        // Resolve field types for schema classes
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                resolveFieldTypesForClass(schemaClass, classMap);
            }
        }

        // Resolve field types for database classes
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass databaseClass : database.getClasses()) {
                resolveFieldTypesForClass(databaseClass, classMap);
            }
        }
    }

    private void resolveFieldTypesForClass(DOClass clazz, Map<String, DOClass> classMap) {
        if (clazz == null || clazz.getFields() == null) {
            return;
        }

        for (DOField field : clazz.getFields()) {
            if (field instanceof DOFieldImpl) {
                DOFieldImpl fieldImpl = (DOFieldImpl) field;

                // Resolve the main field type
                String typeName = field.getTypeName();
                if (typeName != null && !typeName.isEmpty()) {
                    DOClass typeClass = classMap.get(typeName);
                    if (typeClass != null) {
                        fieldImpl.setTypeClass(typeClass);
                    }
                }

                // Resolve the content type if it's a collection (array, list, set, map, etc.)
                if (CollectionTypeUtil.isCollection(field)) {
                    String contentTypeName = CollectionTypeUtil.getCollectionContentType(field);
                    if (contentTypeName != null && !contentTypeName.isEmpty()) {
                        DOClass contentTypeClass = classMap.get(contentTypeName);
                        if (contentTypeClass != null) {
                            fieldImpl.setContentTypeClass(contentTypeClass);
                            // System.out.println("DEBUG: Resolved content type class for " +
                            // field.getName() + " -> "
                            // + contentTypeName);
                        } else {
                            // System.out.println("DEBUG: Content type class NOT FOUND for " +
                            // field.getName() + " -> "
                            // + contentTypeName + " (available: " + classMap.keySet().size() + "
                            // classes)");
                        }
                    } else {
                        // System.out.println("DEBUG: No content type name for collection field " +
                        // field.getName());
                    }
                }
            }
        }
    }
}
