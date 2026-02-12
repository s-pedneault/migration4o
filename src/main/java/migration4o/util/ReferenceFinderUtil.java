package migration4o.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.query.Predicate;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.recipes.RecipeCollectionActivation;

/**
 * Utility for finding references to database objects.
 * Helps trace why objects are or aren't reached during migration.
 */
public class ReferenceFinderUtil {

    /**
     * Callback interface for progress reporting.
     */
    public interface ProgressCallback {
        void onProgress(String message);
    }

    /**
     * Result of a reference search.
     */
    public static class ReferenceResult {
        public final long referencingObjectId;
        public final String referencingClassName;
        public final String fieldName;

        public ReferenceResult(long referencingObjectId, String referencingClassName, String fieldName) {
            this.referencingObjectId = referencingObjectId;
            this.referencingClassName = referencingClassName;
            this.fieldName = fieldName;
        }

        @Override
        public String toString() {
            return String.format("Object ID %d (%s) field '%s'",
                    referencingObjectId, referencingClassName, fieldName);
        }
    }

    /**
     * Finds all objects that reference the specified target object.
     * 
     * @param container         DB4O container
     * @param targetObjectId    ID of the object to find references to
     * @param targetSchemaClass Schema definition of the target object
     * @param schema            Complete schema for finding referencing classes
     * @param progressCallback  Optional callback for progress updates
     * @return List of references found
     */
    public static List<ReferenceResult> findReferencesToObject(
            ExtObjectContainer container,
            long targetObjectId,
            DOSchemaClass targetSchemaClass,
            DOSchema schema,
            ProgressCallback progressCallback) {

        List<ReferenceResult> results = new ArrayList<>();

        if (schema == null || targetSchemaClass == null) {
            return results;
        }

        String targetClassName = targetSchemaClass.source;
        String targetShortName = getShortClassName(targetClassName);

        if (progressCallback != null) {
            progressCallback.onProgress("Searching for references to object ID " + targetObjectId +
                    " (" + targetShortName + ")...\n");
        }

        int totalClasses = schema.getClasses() != null ? schema.getClasses().length : 0;
        int currentClass = 0;

        // Find all classes that have fields referencing the target class
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            currentClass++;
            String searchClassName = getShortClassName(schemaClass.source);

            if (progressCallback != null) {
                progressCallback.onProgress(String.format("[%d/%d] Searching in %s...\n",
                        currentClass, totalClasses, searchClassName));
            }

            if (schemaClass.fields == null) {
                continue;
            }

            // Check each field to see if it references the target class
            for (DOSchemaField field : schemaClass.fields) {
                if (isReferenceToClass(field, targetClassName, schema)) {
                    // This field could reference our target object
                    // Search all instances of this class
                    int foundInClass = searchClassForReferences(container, schemaClass, field,
                            targetObjectId, results, progressCallback);

                    if (foundInClass > 0 && progressCallback != null) {
                        progressCallback.onProgress(String.format("  → Found %d reference(s) in field '%s'\n",
                                foundInClass, field.source));
                    }
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.onProgress(String.format("\nSearch complete. Found %d total reference(s).\n",
                    results.size()));
        }

        return results;
    }

    /**
     * Overload without progress callback for backward compatibility.
     */
    public static List<ReferenceResult> findReferencesToObject(
            ExtObjectContainer container,
            long targetObjectId,
            DOSchemaClass targetSchemaClass,
            DOSchema schema) {
        return findReferencesToObject(container, targetObjectId, targetSchemaClass, schema, null);
    }

    /**
     * Checks if a field references the specified class.
     */
    private static boolean isReferenceToClass(DOSchemaField field, String targetClassName, DOSchema schema) {
        if (field.type == null) {
            return false;
        }

        // Direct reference: field type matches target class
        if (field.type.equals(targetClassName)) {
            return true;
        }

        // Check if field type is the target class by short name
        String targetShortName = getShortClassName(targetClassName);
        String fieldShortType = getShortClassName(field.type);
        if (fieldShortType.equals(targetShortName)) {
            return true;
        }

        // For collections, check if the schema class has the target as a field type
        // This is a simplification - in real use we'd need to know collection element
        // types
        if (field.isCollection) {
            // We can't know the element type from schema alone, so we'll check at runtime
            return true; // Will check during search
        }

        return false;
    }

    /**
     * Searches all instances of a class for references to the target object.
     * Returns the number of references found in this class.
     */
    private static int searchClassForReferences(
            ExtObjectContainer container,
            DOSchemaClass schemaClass,
            DOSchemaField field,
            long targetObjectId,
            List<ReferenceResult> results,
            ProgressCallback progressCallback) {

        if (schemaClass.objectIds == null) {
            return 0;
        }

        String sourceFieldName = field.source;
        int foundCount = 0;
        int objectCount = schemaClass.objectIds.length;

        for (int i = 0; i < objectCount; i++) {
            long objectId = schemaClass.objectIds[i];

            // Report progress every 100 objects for large classes
            if (progressCallback != null && objectCount > 100 && i % 100 == 0) {
                progressCallback.onProgress(String.format("  Checking %d/%d objects in %s...\n",
                        i, objectCount, getShortClassName(schemaClass.source)));
            }

            try {
                Object obj = container.ext().getByID(objectId);
                if (obj == null || !(obj instanceof GenericObject)) {
                    continue;
                }

                GenericObject genericObj = (GenericObject) obj;
                Object fieldValue = getFieldValue(container, genericObj, sourceFieldName);

                if (fieldValue == null) {
                    continue;
                }

                // Check if this field references our target
                if (checkReference(container, fieldValue, targetObjectId)) {
                    results.add(new ReferenceResult(objectId, schemaClass.source, sourceFieldName));
                    foundCount++;
                }

            } catch (Exception e) {
                // Skip objects that can't be loaded
            }
        }

        return foundCount;
    }

    /**
     * Checks if a field value contains a reference to the target object.
     * Handles direct references and collections.
     */
    private static boolean checkReference(ExtObjectContainer container, Object fieldValue, long targetObjectId) {
        if (fieldValue == null) {
            return false;
        }

        // Direct object reference
        long fieldObjectId = container.ext().getID(fieldValue);
        if (fieldObjectId == targetObjectId) {
            return true;
        }

        // Collection reference
        if (fieldValue instanceof Collection) {
            Collection<?> collection = (Collection<?>) fieldValue;
            for (Object item : collection) {
                if (item != null) {
                    long itemId = container.ext().getID(item);
                    if (itemId == targetObjectId) {
                        return true;
                    }
                }
            }
        }

        // Array reference
        if (fieldValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(fieldValue);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(fieldValue, i);
                if (item != null) {
                    long itemId = container.ext().getID(item);
                    if (itemId == targetObjectId) {
                        return true;
                    }
                }
            }
        }

        // Check DB4O collection types (VectRechID, etc.)
        if (fieldValue instanceof GenericObject) {
            try {
                // Try to extract collection items
                Collection<?> items = RecipeCollectionActivation.getItems(container, fieldValue);
                if (items != null) {
                    for (Object item : items) {
                        if (item != null) {
                            long itemId = container.ext().getID(item);
                            if (itemId == targetObjectId) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Not a collection, skip
            }
        }

        return false;
    }

    /**
     * Gets a field value from a GenericObject, searching up the inheritance
     * hierarchy.
     */
    private static Object getFieldValue(ExtObjectContainer container, GenericObject obj, String fieldName) {
        try {
            StoredClass storedClass = container.ext().storedClass(obj);

            while (storedClass != null) {
                StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (StoredField field : fields) {
                        if (field.getName().equals(fieldName)) {
                            return field.get(obj);
                        }
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            // Field not found
        }
        return null;
    }

    /**
     * Gets the short class name from a fully qualified class name.
     */
    private static String getShortClassName(String className) {
        if (className == null) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
