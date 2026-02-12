package migration4o.database.reach;

import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ObjectResolverUtil;

/**
 * Handles IDEntite relationship resolution during reach analysis.
 * IDEntite objects point to EntiteContientID objects via mID field matching.
 */
public class IDEntiteResolver {

    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final ExtObjectContainer container;

    public IDEntiteResolver(DOSchema referenceSchema, DOSchema databaseSchema, ExtObjectContainer container) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.container = container;
    }

    /**
     * Handles IDEntite relationships: marks the IDEntite as reached, then finds
     * the corresponding EntiteContientID object with matching mID and type.
     * 
     * @param idEntiteObj         The IDEntite object
     * @param idEntiteId          The IDEntite object ID
     * @param expectedType        The expected type of the target EntiteContientID
     *                            (optional)
     * @param reachedObjectIds    Set of all reached object IDs
     * @param traverser           The object traverser for recursive exploration
     * @param classProcessedCount Map tracking processed count per class
     * @param classTotalCount     Map tracking total count per class
     * @param progressCallback    Optional callback for progress updates
     */
    public void handleIDEntiteRelationship(
            Object idEntiteObj,
            long idEntiteId,
            String expectedType,
            Set<Long> reachedObjectIds,
            ObjectTraverser traverser,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount,
            ReachProgressCallback progressCallback) {

        // Mark the IDEntite object itself as reached
        if (!reachedObjectIds.contains(idEntiteId)) {
            reachedObjectIds.add(idEntiteId);
        }

        try {
            // Activate and extract the mID field
            ObjectResolverUtil.activateObjectShallow(container, idEntiteObj, idEntiteId);
            Long mID = extractMIDField(idEntiteObj);

            if (mID == null) {
                return; // No mID field found
            }

            // Find EntiteContientID objects with the same mID and matching type
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                if (schemaClass.isEntite(referenceSchema)) {
                    // Check if this class matches the expected type (if specified)
                    String simpleClassName = schemaClass.source;
                    if (simpleClassName.contains(".")) {
                        simpleClassName = simpleClassName.substring(simpleClassName.lastIndexOf('.') + 1);
                    }

                    // Only explore if type matches or no type specified
                    if (expectedType != null && !simpleClassName.equals(expectedType)) {
                        continue; // Skip classes that don't match the expected type
                    }

                    long[] objectIds = schemaClass.uniqueObjectIds;
                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            try {
                                Object obj = container.ext().getByID(objectId);
                                if (obj != null) {
                                    ObjectResolverUtil.activateObjectShallow(container, obj, objectId);
                                    Long objMID = extractMIDField(obj);

                                    // If mIDs match, explore this EntiteContientID object
                                    if (mID.equals(objMID)) {
                                        traverser.exploreObjectRecursively(
                                                objectId,
                                                reachedObjectIds,
                                                classProcessedCount,
                                                classTotalCount,
                                                progressCallback);
                                        // Only process the first matching object for this field
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // Skip objects that can't be processed
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling IDEntite relationship for object " + idEntiteId + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the mID field value from a GenericObject.
     * 
     * @param obj The object to extract mID from
     * @return The mID value, or null if not found
     */
    public Long extractMIDField(Object obj) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        StoredField[] fields = storedClass.getStoredFields();
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                } catch (Exception e) {
                    // Field access failed
                }
            }
        }

        return null;
    }
}
