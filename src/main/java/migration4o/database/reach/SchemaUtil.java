package migration4o.database.reach;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Utility methods for reach analysis.
 */
public class SchemaUtil {

    /**
     * Finds a class by name in a schema array.
     * 
     * @param schema    The schema to search
     * @param className The class name to find
     * @return The schema class, or null if not found
     */
    public static DOSchemaClass findClassInSchemaByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
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
