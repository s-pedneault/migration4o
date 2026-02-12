package migration4o.util;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
     * Converts a field to use a common field definition reference if a matching
     * common field exists in the schema.
     * 
     * @param field  the field to potentially convert
     * @param schema the schema containing common field definitions
     * @return a new field using the common definition if found, or the original
     *         field
     */
    public static DOSchemaField convertToCommonFieldIfExists(DOSchemaField field, DOSchema schema) {
        if (field == null || schema == null || schema.sharedFields == null || schema.sharedFields.isEmpty()) {
            return field;
        }

        // Check if a common field definition exists for this field's source name
        DOSchemaField commonField = schema.sharedFields.get(field.source);
        if (commonField != null) {
            // Create a reference to the common field
            DOSchemaField refField = new DOSchemaField();
            refField.source = field.source;
            refField.definitionId = field.source; // Mark as reference
            // Copy properties from common field
            refField.destinationName = commonField.destinationName;
            refField.type = commonField.type;
            refField.isExported = commonField.isExported;
            refField.skipWhen = commonField.skipWhen;
            refField.isCollection = commonField.isCollection;
            refField.embedContents = commonField.embedContents;
            refField.childrenType = commonField.childrenType;
            refField.title = commonField.title;
            refField.description = commonField.description;
            refField.pointsTo = commonField.pointsTo;
            return refField;
        }

        return field;
    }

    /**
     * Adds a class to a schema, inserting it alphabetically by source name.
     * 
     * @param schema   the schema to add the class to
     * @param newClass the class to add
     */
    public static void addClass(DOSchema schema, DOSchemaClass newClass) {
        if (schema == null || newClass == null) {
            return;
        }

        DOSchemaClass[] existing = schema.classes;
        DOSchemaClass[] newArray = new DOSchemaClass[existing.length + 1];

        // Find insertion point (alphabetically by source)
        int insertIndex = 0;
        for (int i = 0; i < existing.length; i++) {
            if (existing[i].source.compareTo(newClass.source) < 0) {
                insertIndex = i + 1;
            }
        }

        // Copy elements before insertion point
        System.arraycopy(existing, 0, newArray, 0, insertIndex);

        // Insert new class
        newArray[insertIndex] = newClass;

        // Copy elements after insertion point
        if (insertIndex < existing.length) {
            System.arraycopy(existing, insertIndex, newArray, insertIndex + 1, existing.length - insertIndex);
        }

        schema.classes = newArray;
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
     * If not found, falls back to searching by simple name (class name without
     * package).
     * 
     * @param className the absolute class name to find (or simple name as fallback)
     * @param schema    the reference schema
     * @return the schema class, or null if not found
     */
    public static DOSchemaClass findClassByName(String className, DOSchema schema) {
        if (className == null || schema == null || schema.getClasses() == null) {
            return null;
        }

        // First, try exact match by absolute name
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
        }

        // Fallback: try matching by simple name (class name without package)
        String searchSimpleName = ClassUtil.getSimpleName(className);
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String schemaSimpleName = ClassUtil.getSimpleName(schemaClass.source);
            if (schemaSimpleName.equals(searchSimpleName)) {
                return schemaClass;
            }
        }

        return null;
    }

    /**
     * Finds a schema class by its absolute name.
     */
    public static DOSchemaClass findSchemaClassByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (className.equals(schemaClass.source)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name within a schema class.
     */
    public static DOSchemaField findSchemaFieldByName(DOSchemaClass schemaClass, String fieldName) {
        if (schemaClass == null || schemaClass.fields == null) {
            return null;
        }

        for (DOSchemaField field : schemaClass.fields) {
            if (fieldName.equals(field.source)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Checks if the given class is a superclass of other Entite-type classes in the
     * schema.
     * 
     * @param schema      the schema to search
     * @param targetClass the class to check
     * @return true if at least one other class in the schema has this class as a
     *         parent
     */
    public static boolean hasSubclasses(DOSchema schema, DOSchemaClass targetClass) {
        if (schema == null || schema.getClasses() == null || targetClass == null) {
            return false;
        }

        String targetClassName = targetClass.source;

        // Check if any class has this class as its parent
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.parentClassName != null && schemaClass.parentClassName.equals(targetClassName)) {
                // Found a direct subclass - now verify it's an Entite type
                if (isDescendantOf(schemaClass, "gest.gen.Entite", schema)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Given a set of class names that represent the same object (due to
     * inheritance),
     * finds the leaf (most derived) class using the reference schema.
     * 
     * @param classNames set of class names for the same object
     * @return the leaf class name, or the first class name if schema lookup fails
     */
    public static String findLeafClass(Set<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return "Unknown";
        }
        if (classNames.size() == 1) {
            return classNames.iterator().next();
        }

        // Get reference schema
        DOSchema schema = migration4o.schema.DOSchemaService.getInstance().getReferenceSchema();
        if (schema == null) {
            return classNames.iterator().next();
        }

        // Build a map of class -> parent for all classes in the set
        Map<String, String> parentMap = new HashMap<>();
        for (String className : classNames) {
            DOSchemaClass schemaClass = findClassByName(className, schema);
            if (schemaClass != null && schemaClass.parentClassName != null) {
                parentMap.put(className, schemaClass.parentClassName);
            }
        }

        // Find the class that is NOT a parent of any other class in the set
        // (i.e., the leaf class)
        Set<String> parentClasses = new HashSet<>(parentMap.values());
        for (String className : classNames) {
            // If this class is not a parent of any other class in our set,
            // it must be the leaf
            if (!parentClasses.contains(className)) {
                return className;
            }
        }

        // Fallback: return first class
        return classNames.iterator().next();
    }
}
