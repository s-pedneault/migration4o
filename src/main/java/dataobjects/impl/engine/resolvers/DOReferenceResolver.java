package dataobjects.impl.engine.resolvers;

import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;
import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.engine.DOEngine;
import dataobjects.impl.engine.resolvers.DOReferenceResolver;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.DOReference;
import dataobjects.util.CollectionTypeUtil;

public class DOReferenceResolver {

    public void resolveReferences(DOClass targetClass, DOEngine engine) {
        if (targetClass == null || engine == null) {
            return;
        }

        String targetClassName = targetClass.getAbsoluteName();
        if (targetClassName == null || targetClassName.isEmpty()) {
            return;
        }

        // Clear existing references before resolving
        targetClass.setReferences(new dataobjects.impl.models.DOReference[0]);

        // Check schema classes for references to the target class
        DOSchema schema = engine.getSchema();
        if (schema != null) {
            DOSchemaClass[] schemaClasses = schema.getClasses();
            if (schemaClasses != null) {
                for (DOSchemaClass schemaClass : schemaClasses) {
                    checkClassForReferences(targetClass, targetClassName, schemaClass);
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

    private void checkClassForReferences(DOClass targetClass, String targetClassName, DOClass sourceClass) {
        if (sourceClass == null) {
            return;
        }

        DOField[] fields = sourceClass.getFields();
        if (fields != null) {
            for (DOField field : fields) {
                checkFieldForReference(targetClass, targetClassName, sourceClass, field);
            }
        }
    }

    private void checkFieldForReference(DOClass targetClass, String targetClassName, DOClass sourceClass,
            DOField field) {
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
