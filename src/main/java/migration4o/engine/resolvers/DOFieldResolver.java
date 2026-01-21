package migration4o.engine.resolvers;

import java.util.HashMap;
import java.util.Map;

import migration4o.engine.DOEngine;
import migration4o.models.database.DODatabaseField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.CollectionTypeUtil;

public class DOFieldResolver {

    public void resolveFieldTypes(DOEngine engine) {
        if (engine == null) {
            return;
        }

        // Build a map of class name to class object for quick lookup
        Map<String, DODatabaseClass> classMap = new HashMap<>();

        // Add schema classes to the map
        DOSchema schema = engine.getSchema();
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass != null && schemaClass.source != null
                        && schemaClass.databaseClass != null) {
                    classMap.put(schemaClass.source, schemaClass.databaseClass);
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
                if (schemaClass.databaseClass != null) {
                    resolveFieldTypesForClass(schemaClass.databaseClass, classMap);
                }
            }
        }

        // Resolve field types for database classes
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass databaseClass : database.getClasses()) {
                resolveFieldTypesForClass(databaseClass, classMap);
            }
        }
    }

    private void resolveFieldTypesForClass(DODatabaseClass clazz, Map<String, DODatabaseClass> classMap) {
        if (clazz == null || clazz.getFields() == null) {
            return;
        }

        for (DODatabaseField field : clazz.getFields()) {
            if (field instanceof DODatabaseField) {
                DODatabaseField fieldImpl = (DODatabaseField) field;

                // Resolve the main field type
                String typeName = field.getTypeName();
                if (typeName != null && !typeName.isEmpty()) {
                    DODatabaseClass typeClass = classMap.get(typeName);
                    if (typeClass != null) {
                        fieldImpl.setTypeClass(typeClass);
                    }
                }

                // Resolve the content type if it's a collection (array, list, set, map, etc.)
                if (CollectionTypeUtil.isCollection(field)) {
                    String contentTypeName = CollectionTypeUtil.getCollectionContentType(field);
                    if (contentTypeName != null && !contentTypeName.isEmpty()) {
                        DODatabaseClass contentTypeClass = classMap.get(contentTypeName);
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
