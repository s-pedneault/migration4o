package migration4o.database.reach;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Aggregates reach analysis results by class.
 * Organizes reached objects into per-class collections.
 */
public class ReachResultAggregator {

    private final DOSchema databaseSchema;

    public ReachResultAggregator(DOSchema databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    /**
     * Aggregates reached objects by their class.
     * 
     * @param reachedObjectIds Set of all reached object IDs
     * @param container        The database container
     * @return Map of class name to set of reached object IDs for that class
     */
    public Map<String, Set<Long>> aggregateReachedObjects(Set<Long> reachedObjectIds, ExtObjectContainer container) {
        Map<String, Set<Long>> reachedByClass = new HashMap<>();

        // For each reached object, determine its class and add to the appropriate set
        for (Long objectId : reachedObjectIds) {
            try {
                Object obj = container.ext().getByID(objectId);
                if (obj != null) {
                    String className = getClassName(obj);

                    // Add object ID to all classes in its inheritance hierarchy
                    addToClassHierarchy(className, objectId, reachedByClass);
                }
            } catch (Exception e) {
                System.err.println("Error aggregating object " + objectId + ": " + e.getMessage());
            }
        }

        return reachedByClass;
    }

    /**
     * Adds an object ID to all classes in its inheritance hierarchy.
     */
    private void addToClassHierarchy(String className, Long objectId, Map<String, Set<Long>> reachedByClass) {
        // Add to the object's actual class
        reachedByClass.computeIfAbsent(className, k -> new HashSet<>()).add(objectId);

        // Add to all parent classes in the hierarchy
        DOSchemaClass schemaClass = SchemaUtil.findClassInSchemaByName(databaseSchema, className);
        if (schemaClass != null) {
            String parentClassName = schemaClass.parentClassName;
            while (parentClassName != null && !parentClassName.isEmpty()) {
                reachedByClass.computeIfAbsent(parentClassName, k -> new HashSet<>()).add(objectId);

                // Move up to next parent
                DOSchemaClass parentClass = SchemaUtil.findClassInSchemaByName(databaseSchema, parentClassName);
                if (parentClass != null) {
                    parentClassName = parentClass.parentClassName;
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Adds reached object IDs to a schema class's reachedObjectIds array.
     * 
     * @param schemaClass The schema class to update
     * @param idsToAdd    The set of object IDs to add
     */
    public void addReachedIdsToClass(DOSchemaClass schemaClass, Set<Long> idsToAdd) {
        long[] currentReachedIds = schemaClass.reachedObjectIds;
        if (currentReachedIds == null) {
            currentReachedIds = new long[0];
        }

        // Create a set of existing reached IDs to avoid duplicates
        Set<Long> existingIds = new HashSet<>();
        for (long id : currentReachedIds) {
            existingIds.add(id);
        }

        // Add new IDs that aren't already in the list
        Set<Long> newIds = new HashSet<>();
        for (long id : idsToAdd) {
            if (!existingIds.contains(id)) {
                newIds.add(id);
            }
        }

        // Combine existing and new IDs
        if (!newIds.isEmpty()) {
            long[] combinedIds = new long[currentReachedIds.length + newIds.size()];
            System.arraycopy(currentReachedIds, 0, combinedIds, 0, currentReachedIds.length);
            int index = currentReachedIds.length;
            for (long id : newIds) {
                combinedIds[index++] = id;
            }
            schemaClass.reachedObjectIds = combinedIds;
            System.out.println("Added " + newIds.size() +
                    " reached objects to class " + schemaClass.source +
                    " (was " + currentReachedIds.length + ", now " + combinedIds.length + ")");
        }
    }

    /**
     * Gets the class name of an object (handles both GenericObject and regular
     * objects).
     */
    private String getClassName(Object obj) {
        if (obj instanceof com.db4o.reflect.generic.GenericObject) {
            com.db4o.reflect.generic.GenericObject genericObj = (com.db4o.reflect.generic.GenericObject) obj;
            return genericObj.getGenericClass().getName();
        } else {
            return obj.getClass().getName();
        }
    }
}
