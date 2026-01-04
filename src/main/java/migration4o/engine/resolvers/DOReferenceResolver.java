package migration4o.engine.resolvers;

import migration4o.models.database.DODatabaseField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.engine.DOEngine;
import migration4o.engine.resolvers.DOReferenceResolver;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.DOReference;
import migration4o.util.CollectionTypeUtil;

public class DOReferenceResolver {

    public void resolveReferences(DODatabaseClass targetClass, DOEngine engine) {
        if (targetClass == null || engine == null) {
            return;
        }

        String targetClassName = targetClass.getAbsoluteName();
        if (targetClassName == null || targetClassName.isEmpty()) {
            return;
        }

        // Clear existing references before resolving
        targetClass.setReferences(new DOReference[0]);

        // Check schema classes for references to the target class
        DOSchema schema = engine.getSchema();
        if (schema != null) {
            DOSchemaClass[] schemaClasses = schema.getClasses();
            if (schemaClasses != null) {
                for (DOSchemaClass schemaClass : schemaClasses) {
                    if (schemaClass.getDatabaseClass() != null) {
                        checkClassForReferences(targetClass, targetClassName, schemaClass.getDatabaseClass());
                    }
                }
            }
        }

        // Check database classes for references to the target class
        DODatabase database = engine.getDatabase();
        if (database != null) {
            DODatabaseClass[] databaseClasses = database.getClasses();
            if (databaseClasses != null) {
                for (DODatabaseClass databaseClass : databaseClasses) {
                    checkClassForReferences(targetClass, targetClassName, databaseClass);
                }
            }
        }
    }

    private void checkClassForReferences(DODatabaseClass targetClass, String targetClassName,
            DODatabaseClass sourceClass) {
        if (sourceClass == null) {
            return;
        }

        DODatabaseField[] fields = sourceClass.getFields();
        if (fields != null) {
            for (DODatabaseField field : fields) {
                checkFieldForReference(targetClass, targetClassName, sourceClass, field);
            }
        }
    }

    private void checkFieldForReference(DODatabaseClass targetClass, String targetClassName,
            DODatabaseClass sourceClass,
            DODatabaseField field) {
        if (field == null) {
            return;
        }

        // Check if the field's type matches the target class
        String fieldTypeName = field.getTypeName();
        if (targetClassName.equals(fieldTypeName)) {
            targetClass.addReference(new DOReference(sourceClass, field));
            return;
        }

        // If the field is a collection (array, list, set, map, etc.), check the content
        // type
        if (CollectionTypeUtil.isCollection(field)) {
            String contentTypeName = CollectionTypeUtil.getCollectionContentType(field);
            if (targetClassName.equals(contentTypeName)) {
                targetClass.addReference(new DOReference(sourceClass, field));
            }
        }
    }
}
