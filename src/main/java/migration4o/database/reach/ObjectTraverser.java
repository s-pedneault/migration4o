package migration4o.database.reach;

import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ObjectResolverUtil;
import migration4o.util.SchemaUtil;

/**
 * Handles recursive traversal of the database object graph. Explores objects and their fields to determine reachability.
 */
public class ObjectTraverser {

    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final ExtObjectContainer container;

    private final FieldProcessor fieldProcessor;

    public ObjectTraverser(DOSchema referenceSchema, DOSchema databaseSchema, ExtObjectContainer container) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.container = container;
        this.fieldProcessor = new FieldProcessor(referenceSchema, databaseSchema, container);
    }

    /**
     * Recursively explores an object and all its field references.
     * 
     * @param objectId The object ID to explore
     * @param reachedObjectIds Set of all reached object IDs (updated during traversal)
     * @param classProcessedCount Map tracking processed count per class
     * @param classTotalCount Map tracking total count per class
     * @param progressCallback Optional callback for progress updates
     */
    public void exploreObjectRecursively(long objectId, Set<Long> reachedObjectIds, Map<String, Integer> classProcessedCount, Map<String, Integer> classTotalCount, ReachProgressCallback progressCallback) {

        // Avoid processing the same object twice - check and add atomically
        if (!reachedObjectIds.add(objectId)) {
            // Object was already processed, skip it
            return;
        }

        try {
            // Get and activate the object
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return;
            }

            String className = getClassName(obj);

            // Update processed count for this class
            int processed = classProcessedCount.getOrDefault(className, 0) + 1;
            classProcessedCount.put(className, processed);
            int total = classTotalCount.getOrDefault(className, 0);

            if (progressCallback != null) {
                progressCallback.onObjectProcessed(className, objectId, processed, total);
            }

            ObjectResolverUtil.activateObjectShallow(container, obj, objectId);

            // If it's a GenericObject, explore all its fields
            if (obj instanceof GenericObject) {
                GenericObject genericObj = (GenericObject) obj;

                StoredClass storedClass = container.ext().storedClass(genericObj);
                if (storedClass != null) {
                    fieldProcessor.exploreAllFields(genericObj, className, reachedObjectIds, this, classProcessedCount, classTotalCount, progressCallback);
                }
            }
        } catch (Exception e) {
            System.err.println("Error exploring object " + objectId + ": " + e.getMessage());
        }
    }

    /**
     * Gets the class name of an object (handles both GenericObject and regular objects).
     */
    private String getClassName(Object obj) {
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            return genericObj.getGenericClass().getName();
        } else {
            return obj.getClass().getName();
        }
    }

    /**
     * Checks if an object is important (descendant of EntiteContientID or IDEntite).
     */
    public boolean isImportantObject(Object obj) {
        if (obj == null) {
            return false;
        }

        String className = getClassName(obj);
        if (className == null) {
            return false;
        }

        DOSchemaClass objClass = databaseSchema.findClassByName(className);
        if (objClass != null) {
            return objClass.isEntite() || objClass.isIDEntite();
        }

        return false;
    }
}
