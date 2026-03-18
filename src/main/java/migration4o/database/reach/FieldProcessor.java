package migration4o.database.reach;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.recipes.RecipeCollectionItems;
import migration4o.util.SchemaUtil;

/**
 * Processes fields of database objects during reach analysis.
 * Handles collections, arrays, and references to other objects.
 */
public class FieldProcessor {

    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final ExtObjectContainer container;

    private final IDEntiteResolver idEntiteResolver;

    public FieldProcessor(DOSchema referenceSchema, DOSchema databaseSchema, ExtObjectContainer container) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.container = container;
        this.idEntiteResolver = new IDEntiteResolver(referenceSchema, databaseSchema, container);
    }

    /**
     * Explores all fields of a GenericObject, following references recursively.
     */
    public void exploreAllFields(
            GenericObject obj,
            String parentClassName,
            Set<Long> reachedObjectIds,
            ObjectTraverser traverser,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount,
            ReachProgressCallback progressCallback) {

        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return;
            }

            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue == null) {
                        continue; // Skip null fields
                    }

                    // Try to extract as collection (handles both Collection and GenericObject)
                    Collection<?> extractedCollection = RecipeCollectionItems.getItems(container, fieldValue);
                    if (extractedCollection != null && !extractedCollection.isEmpty()) {
                        processCollectionField(
                                extractedCollection,
                                field.getName(),
                                parentClassName,
                                reachedObjectIds,
                                traverser,
                                classProcessedCount,
                                classTotalCount,
                                progressCallback);
                    }
                    // Handle arrays (excluding byte arrays which are primitives)
                    else if (fieldValue.getClass().isArray() && !(fieldValue instanceof byte[])) {
                        processArrayField(
                                fieldValue,
                                field.getName(),
                                parentClassName,
                                reachedObjectIds,
                                traverser,
                                classProcessedCount,
                                classTotalCount,
                                progressCallback);
                    }
                    // Handle single object references
                    else {
                        long refId = container.ext().getID(fieldValue);
                        if (refId > 0) {
                            processFieldReference(
                                    fieldValue,
                                    field.getName(),
                                    parentClassName,
                                    reachedObjectIds,
                                    traverser,
                                    classProcessedCount,
                                    classTotalCount,
                                    progressCallback);
                        }
                        // Primitives and non-persistent values are ignored
                    }
                } catch (Exception e) {
                    System.err.println("Error processing field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing fields: " + e.getMessage());
        }
    }

    /**
     * Processes a collection field, exploring all items.
     */
    private void processCollectionField(
            Collection<?> collection,
            String fieldName,
            String parentClassName,
            Set<Long> reachedObjectIds,
            ObjectTraverser traverser,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount,
            ReachProgressCallback progressCallback) {

        for (Object item : collection) {
            if (item != null) {
                processFieldReference(
                        item,
                        fieldName,
                        parentClassName,
                        reachedObjectIds,
                        traverser,
                        classProcessedCount,
                        classTotalCount,
                        progressCallback);
            }
        }
    }

    /**
     * Processes an array field, exploring all items.
     */
    private void processArrayField(
            Object fieldValue,
            String fieldName,
            String parentClassName,
            Set<Long> reachedObjectIds,
            ObjectTraverser traverser,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount,
            ReachProgressCallback progressCallback) {

        int length = java.lang.reflect.Array.getLength(fieldValue);
        for (int i = 0; i < length; i++) {
            Object item = java.lang.reflect.Array.get(fieldValue, i);
            if (item != null) {
                processFieldReference(
                        item,
                        fieldName,
                        parentClassName,
                        reachedObjectIds,
                        traverser,
                        classProcessedCount,
                        classTotalCount,
                        progressCallback);
            }
        }
    }

    /**
     * Processes a field value that might be a reference to another object.
     * Handles special IDEntite relationships with type matching.
     */
    private void processFieldReference(
            Object item,
            String fieldName,
            String parentClassName,
            Set<Long> reachedObjectIds,
            ObjectTraverser traverser,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount,
            ReachProgressCallback progressCallback) {

        long childId = container.ext().getID(item);
        if (childId <= 0) {
            return; // Not a persistent object
        }

        String className = getClassName(item);
        if (className == null) {
            return;
        }

        // Check if this is an IDEntite descendant
        DOSchemaClass itemClass = SchemaUtil.findClassInSchemaByName(databaseSchema, className);
        if (itemClass != null && itemClass.isIDEntite(referenceSchema)) {
            // This is an IDEntite - get target type from pointsTo or extract from field
            // name
            String expectedType = itemClass.attributes.pointsTo;
            if (expectedType == null) {
                // Fallback to name extraction
                expectedType = extractExpectedTypeFromFieldName(fieldName, className);
            }

            // Handle the special mID relationship with type filtering
            idEntiteResolver.handleIDEntiteRelationship(
                    item,
                    childId,
                    expectedType,
                    reachedObjectIds,
                    traverser,
                    classProcessedCount,
                    classTotalCount,
                    progressCallback);
        } else {
            // Regular object - explore it recursively
            traverser.exploreObjectRecursively(
                    childId,
                    reachedObjectIds,
                    classProcessedCount,
                    classTotalCount,
                    progressCallback);
        }
    }

    /**
     * Extracts expected EntiteContientID type from field name.
     * Example: "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
     */
    private String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        // If field name starts with "mID", extract the part after it
        if (fieldName.startsWith("mID")) {
            return fieldName.substring(3); // Remove "mID" prefix
        }
        // Otherwise try to extract from the ID class name
        // "IDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2); // Remove "ID" prefix
        }
        return null;
    }

    /**
     * Gets the class name of an object (handles both GenericObject and regular
     * objects).
     */
    private String getClassName(Object obj) {
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            return genericObj.getGenericClass().getName();
        } else {
            return obj.getClass().getName();
        }
    }
}
