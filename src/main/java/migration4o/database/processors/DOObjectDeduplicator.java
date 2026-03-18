package migration4o.database.processors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import migration4o.database.DODatabaseMonitor;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Processor for deduplicating object IDs across inheritance hierarchies in
 * DOSchema.
 * Provides static methods for object ID deduplication without requiring
 * instantiation.
 * 
 * DB4O stores each object at every level of its inheritance chain, so the same
 * object ID appears in the parent class, grandparent class, etc. This processor
 * removes duplicate IDs by keeping them only in the most derived (leaf) class.
 */
public class DOObjectDeduplicator {

    /**
     * Deduplicates object IDs across inheritance hierarchies.
     * 
     * Algorithm:
     * 1. Find all leaf classes (classes with no subclasses)
     * 2. For each leaf class, get its object IDs
     * 3. For each object ID, walk up the parent chain and remove it from ancestors
     * 
     * @param schema The schema with potentially duplicate object IDs
     * @return A new schema with deduplicated object IDs
     */
    public static DOSchema deduplicateObjectIdsInInheritanceHierarchies(DOSchema schema) {
        return deduplicateObjectIdsInInheritanceHierarchies(schema, null);
    }

    /**
     * Deduplicates object IDs across inheritance hierarchies.
     * 
     * Algorithm:
     * 1. Find all leaf classes (classes with no subclasses)
     * 2. For each leaf class, get its object IDs
     * 3. For each object ID, walk up the parent chain and remove it from ancestors
     * 
     * @param schema  The schema with potentially duplicate object IDs
     * @param monitor Optional monitor for progress feedback
     * @return A new schema with deduplicated object IDs
     */
    public static DOSchema deduplicateObjectIdsInInheritanceHierarchies(DOSchema schema, DODatabaseMonitor monitor) {
        if (schema == null || schema.getClasses() == null || schema.getClasses().length == 0) {
            return schema;
        }

        // Create a map for quick class lookup
        Map<String, DOSchemaClass> classMap = new HashMap<>();
        for (DOSchemaClass cls : schema.getClasses()) {
            classMap.put(cls.attributes.source, cls);
        }

        // Find leaf classes (classes with no subclasses)
        List<DOSchemaClass> leafClasses = findLeafClasses(schema, classMap);

        if (monitor != null) {
            monitor.onStartingDeduplication(leafClasses.size());
        } else {
            System.out.println("Deduplicating object IDs across inheritance hierarchies...");
        }

        // Track which IDs should be removed from each class
        Map<String, java.util.Set<Long>> idsToRemove = new HashMap<>();

        // For each leaf class, mark its object IDs for removal from all ancestor
        // classes
        int leafIndex = 0;
        for (DOSchemaClass leafClass : leafClasses) {
            leafIndex++;

            if (monitor != null) {
                monitor.onProcessingLeafClass(leafClass.attributes.source, leafIndex, leafClasses.size());
            }

            if (leafClass.objectIds == null || leafClass.objectIds.length == 0) {
                continue;
            }

            // Walk up the inheritance chain
            String parentClassName = leafClass.attributes.parentClassName;
            while (parentClassName != null) {
                DOSchemaClass parentClass = classMap.get(parentClassName);
                if (parentClass == null) {
                    break;
                }

                // Mark leaf's object IDs for removal from this parent
                java.util.Set<Long> toRemove = idsToRemove.computeIfAbsent(parentClass.attributes.source,
                        k -> new java.util.HashSet<>());
                for (long id : leafClass.objectIds) {
                    toRemove.add(id);
                }

                // Move to next ancestor
                parentClassName = parentClass.attributes.parentClassName;
            }
        }

        // Now update uniqueObjectIds for all classes (keep objectIds unchanged)
        int deduplicatedCount = 0;
        int totalRemoved = 0;
        for (DOSchemaClass cls : schema.getClasses()) {
            java.util.Set<Long> toRemove = idsToRemove.get(cls.attributes.source);

            if (toRemove != null && !toRemove.isEmpty() && cls.objectIds != null) {
                // Filter out the IDs that belong to derived classes
                long[] uniqueIds = removeObjectIds(cls.objectIds, toRemove);
                cls.uniqueObjectIds = uniqueIds;

                int removedCount = cls.objectIds.length - uniqueIds.length;
                if (removedCount > 0) {
                    deduplicatedCount++;
                    totalRemoved += removedCount;
                    if (monitor != null) {
                        monitor.onClassDeduplicated(cls.attributes.source, removedCount, uniqueIds.length);
                    } else {
                        System.out.println("Deduplicated " + removedCount + " object IDs from " + cls.attributes.source +
                                " (" + cls.objectIds.length + " -> " + uniqueIds.length + ")");
                    }
                }
            } else if (cls.objectIds != null) {
                // No deduplication needed - copy objectIds to uniqueObjectIds
                cls.uniqueObjectIds = cls.objectIds;
            }
        }

        if (monitor != null) {
            monitor.onDeduplicationComplete(leafClasses.size(), totalRemoved);
        } else {
            System.out.println("Object ID deduplication complete: " + leafClasses.size() + " leaf classes, " +
                    deduplicatedCount + " classes deduplicated");
        }

        return schema;
    }

    /**
     * Finds all leaf classes (classes with no subclasses).
     * 
     * @param schema   The schema to analyze
     * @param classMap Map of classes by name for quick lookup
     * @return List of leaf classes
     */
    public static List<DOSchemaClass> findLeafClasses(DOSchema schema, Map<String, DOSchemaClass> classMap) {
        List<DOSchemaClass> leafClasses = new ArrayList<>();

        for (DOSchemaClass cls : schema.getClasses()) {
            boolean isLeaf = true;

            // Check if any other class has this as a parent
            for (DOSchemaClass potentialChild : schema.getClasses()) {
                if (cls.attributes.source.equals(potentialChild.attributes.parentClassName)) {
                    isLeaf = false;
                    break;
                }
            }

            if (isLeaf) {
                leafClasses.add(cls);
            }
        }

        return leafClasses;
    }

    /**
     * Removes specified object IDs from an array.
     * 
     * @param sourceIds   The source array of object IDs
     * @param idsToRemove The IDs to remove from the source array (as a Set)
     * @return A new array with the specified IDs removed
     */
    public static long[] removeObjectIds(long[] sourceIds, java.util.Set<Long> idsToRemove) {
        if (sourceIds == null || sourceIds.length == 0) {
            return sourceIds;
        }

        if (idsToRemove == null || idsToRemove.isEmpty()) {
            return sourceIds;
        }

        // Filter out IDs that are in the remove set
        List<Long> result = new ArrayList<>();
        for (long id : sourceIds) {
            if (!idsToRemove.contains(id)) {
                result.add(id);
            }
        }

        // Convert back to array
        long[] resultArray = new long[result.size()];
        for (int i = 0; i < result.size(); i++) {
            resultArray[i] = result.get(i);
        }

        return resultArray;
    }

    /**
     * Removes specified object IDs from an array.
     * 
     * @param sourceIds   The source array of object IDs
     * @param idsToRemove The IDs to remove from the source array
     * @return A new array with the specified IDs removed
     */
    public static long[] removeObjectIds(long[] sourceIds, long[] idsToRemove) {
        if (sourceIds == null || sourceIds.length == 0) {
            return sourceIds;
        }

        if (idsToRemove == null || idsToRemove.length == 0) {
            return sourceIds;
        }

        // Convert idsToRemove to a set for fast lookup
        java.util.Set<Long> removeSet = new java.util.HashSet<>();
        for (long id : idsToRemove) {
            removeSet.add(id);
        }

        // Filter out IDs that are in the remove set
        List<Long> result = new ArrayList<>();
        for (long id : sourceIds) {
            if (!removeSet.contains(id)) {
                result.add(id);
            }
        }

        // Convert back to array
        long[] resultArray = new long[result.size()];
        for (int i = 0; i < result.size(); i++) {
            resultArray[i] = result.get(i);
        }

        return resultArray;
    }

}
