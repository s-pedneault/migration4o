package migration4o.migration.recipes;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Resolves ID classes for entity classes.
 * In the schema, ID classes (like EmployeID) point to entity classes (like
 * Employe)
 * via the pointsTo field.
 */
public class IDClassResolver {

    /**
     * Finds the ID class for a given entity class name.
     * For example, given "Employe", finds "EmployeID" by looking for classes
     * where pointsTo equals "Employe".
     * 
     * @param entityClassName The entity class name to find an ID class for
     * @param schema          The schema to search in
     * @return The ID class, or null if not found
     */
    public static DOSchemaClass findIDClass(String entityClassName, DOSchema schema) {
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.attributes.pointsTo != null && schemaClass.attributes.pointsTo.equals(entityClassName)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Finds the entity class that a given ID class points to.
     * This is the reverse operation - given an ID class, find what it references.
     * 
     * @param idClass The ID class
     * @return The entity class name from the pointsTo field, or null
     */
    public static String getEntityClassName(DOSchemaClass idClass) {
        return idClass != null ? idClass.attributes.pointsTo : null;
    }
}
