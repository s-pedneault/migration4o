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

        // Cache the schema for use in helper methods
        DOSchema schema = engine.getSchema();
        this.cachedSchema = schema;

        // Check schema classes for references to the target class
        if (schema != null) {
            DOSchemaClass[] schemaClasses = schema.getClasses();
            if (schemaClasses != null) {
                for (DOSchemaClass schemaClass : schemaClasses) {
                    if (schemaClass.databaseClass != null) {
                        checkClassForReferences(targetClass, targetClassName, schemaClass.databaseClass);
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

        // If the field is a collection (array, list, set, map, etc.), check the content
        // type
        if (CollectionTypeUtil.isCollection(field)) {
            String contentTypeName = CollectionTypeUtil.getCollectionContentType(field);

            // Also check the schema field's childrenType attribute
            String childrenType = getSchemaChildrenType(sourceClass, field);

            // Check direct content type match
            if (targetClassName.equals(contentTypeName)) {
                targetClass.addReference(new DOReference(sourceClass, field));
                return;
            }

            // Check if the collection content type is an IDEntite pointing to the target
            // class
            if (isIDEntitePointingTo(contentTypeName, targetClassName)) {
                System.out.println("DEBUG DOReferenceResolver: Found IDEntite collection reference: "
                        + sourceClass.getAbsoluteName() + "." + field.getName() +
                        " (contentType=" + contentTypeName + ", pointsTo=" + targetClassName + ")");
                targetClass.addReference(new DOReference(sourceClass, field));
                return;
            }

            // Check if the schema's childrenType is an IDEntite pointing to the target
            // class
            if (childrenType != null && isIDEntitePointingTo(childrenType, targetClassName)) {
                System.out.println(
                        "DEBUG DOReferenceResolver: Found IDEntite collection reference via schema childrenType: "
                                + sourceClass.getAbsoluteName() + "." + field.getName() +
                                " (childrenType=" + childrenType + ", pointsTo=" + targetClassName + ")");
                targetClass.addReference(new DOReference(sourceClass, field));
            }
        } else {
            // For non-collection fields, check direct type match
            if (targetClassName.equals(fieldTypeName)) {
                targetClass.addReference(new DOReference(sourceClass, field));
                return;
            }

            // Check if the field type is an IDEntite that points to the target class
            if (isIDEntitePointingTo(fieldTypeName, targetClassName)) {
                System.out.println("DEBUG DOReferenceResolver: Found IDEntite field reference: "
                        + sourceClass.getAbsoluteName() + "." + field.getName() +
                        " (type=" + fieldTypeName + ", pointsTo=" + targetClassName + ")");
                targetClass.addReference(new DOReference(sourceClass, field));
            }
        }
    }

    /**
     * Get the childrenType attribute from the schema field definition.
     */
    private String getSchemaChildrenType(DODatabaseClass databaseClass, DODatabaseField databaseField) {
        if (cachedSchema == null || databaseClass == null || databaseField == null) {
            return null;
        }

        // Find the schema class that corresponds to this database class
        DOSchemaClass schemaClass = findSchemaClassByDatabaseClass(databaseClass);
        if (schemaClass == null || schemaClass.fields == null) {
            System.out.println("DEBUG getSchemaChildrenType: No schema class found for "
                    + databaseClass.getAbsoluteName() + "." + databaseField.getName());
            return null;
        }

        // Find the schema field by name
        for (int i = 0; i < schemaClass.fields.length; i++) {
            if (schemaClass.fields[i].source != null && schemaClass.fields[i].source.equals(databaseField.getName())) {
                String childrenType = schemaClass.fields[i].childrenType;
                if (childrenType != null) {
                    System.out.println("DEBUG getSchemaChildrenType: Found childrenType for "
                            + databaseClass.getAbsoluteName() + "." + databaseField.getName() + " = " + childrenType);
                }
                return childrenType;
            }
        }

        System.out.println("DEBUG getSchemaChildrenType: No schema field found for " + databaseClass.getAbsoluteName()
                + "." + databaseField.getName());
        return null;
    }

    /**
     * Find schema class by its linked database class.
     */
    private DOSchemaClass findSchemaClassByDatabaseClass(DODatabaseClass databaseClass) {
        if (cachedSchema == null || databaseClass == null) {
            return null;
        }

        DOSchemaClass[] classes = cachedSchema.getClasses();
        if (classes != null) {
            for (DOSchemaClass cls : classes) {
                if (cls.databaseClass == databaseClass) {
                    return cls;
                }
            }
        }
        return null;
    }

    /**
     * Check if the given class name is an IDEntite that points to the target class.
     */
    private boolean isIDEntitePointingTo(String className, String targetClassName) {
        if (className == null || targetClassName == null) {
            return false;
        }

        DOSchemaClass schemaClass = findSchemaClass(className);
        if (schemaClass == null) {
            System.out.println("DEBUG isIDEntitePointingTo: Schema class not found for className=" + className);
            return false;
        }

        // Check if this is an IDEntite and if it points to the target class
        boolean result = schemaClass.pointsTo != null && targetClassName.equals(schemaClass.pointsTo);
        if (result) {
            System.out.println("DEBUG isIDEntitePointingTo: MATCH! className=" + className
                    + " points to targetClassName=" + targetClassName);
        } else if (schemaClass.pointsTo != null) {
            System.out.println("DEBUG isIDEntitePointingTo: className=" + className + " points to "
                    + schemaClass.pointsTo + " (not " + targetClassName + ")");
        }
        return result;
    }

    /**
     * Find a schema class by name from the engine's schema.
     */
    private DOSchemaClass findSchemaClass(String className) {
        // We need access to the schema - store it during resolveReferences
        if (cachedSchema == null) {
            return null;
        }

        DOSchemaClass[] classes = cachedSchema.getClasses();
        if (classes != null) {
            for (DOSchemaClass cls : classes) {
                if (className.equals(cls.source)) {
                    return cls;
                }
            }
        }
        return null;
    }

    // Cache the schema during reference resolution
    private DOSchema cachedSchema;
}
