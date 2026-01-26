package migration4o.util;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

public class SchemaUtil {
    /**
     * Checks if a schema class is a descendant of a given ancestor class.
     * 
     * @param schemaClass       the class to check
     * @param ancestorClassName the name of the ancestor class
     * @param schema            the schema containing all classes
     * @return true if schemaClass is a descendant of ancestorClassName
     */
    public static boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName, DOSchema schema) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.source;
        if (currentClassName.equals(ancestorClassName)) {
            return true;
        }

        String parentClassName = schemaClass.parentClassName;
        if (parentClassName == null || parentClassName.isEmpty()) {
            return false;
        }

        if (parentClassName.equals(ancestorClassName)) {
            return true;
        }

        // Look up parent class and recurse
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass candidate : schema.getClasses()) {
                if (candidate.source.equals(parentClassName)) {
                    return isDescendantOf(candidate, ancestorClassName, schema);
                }
            }
        }

        return false;
    }

    /**
     * Finds a class in the schemas by its absolute name.
     * Searches the schemas in reverse order, and returns the first class found.
     * 
     * @param className the absolute class name to find
     * @param schemas   the array of schemas to search
     * @return the schema class, or null if not found
     */
    public static DOSchemaClass findClassByName(String className, DOSchema[] schemas) {
        if (schemas == null || className == null) {
            return null;
        }
        for (int i = schemas.length - 1; i >= 0; i--) {
            DOSchemaClass foundClass = findClassByName(className, schemas[i]);
            if (foundClass != null) {
                return foundClass;
            }
        }
        return null;
    }

    /**
     * Finds a class in the schema by its absolute name.
     * 
     * @param className the absolute class name to find
     * @param schema    the reference schema
     * @return the schema class, or null if not found
     */
    public static DOSchemaClass findClassByName(String className, DOSchema schema) {
        if (className == null || schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }
}
