package migration4o.database.processors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseMonitor;

/**
 * Processor for deduplicating object IDs across inheritance hierarchies.
 * Provides static methods for object ID deduplication without requiring
 * instantiation.
 * 
 * DB4O stores each object at every level of its inheritance chain, so the same
 * object ID appears in the parent class, grandparent class, etc. This processor
 * removes duplicate IDs by keeping them only in the most derived (leaf) class.
 */
public class DOObjectDeduplicator {

    // ===== DODatabase methods (new) =====

    /**
     * Deduplicates object IDs across inheritance hierarchies in a DODatabase.
     * 
     * @param database The database with potentially duplicate object IDs
     */
    public static void deduplicateObjectIds(DODatabase database) {
        deduplicateObjectIds(database, null);
    }

    /**
     * Deduplicates object IDs across inheritance hierarchies in a DODatabase.
     * 
     * Algorithm:
     * 1. Find all leaf classes (classes with no subclasses)
     * 2. For each leaf class, get its object IDs
     * 3. For each object ID, walk up the parent chain and remove it from ancestors
     * 
     * @param database The database with potentially duplicate object IDs
     * @param monitor  Optional monitor for progress feedback
     */
    public static void deduplicateObjectIds(DODatabase database, DODatabaseMonitor monitor) {
        if (database == null || database.getClasses() == null || database.getClasses().length == 0) {
            return;
        }

        // Create a map for quick class lookup
        Map<String, DODatabaseClass> classMap = new HashMap<>();
        for (DODatabaseClass cls : database.getClasses()) {
            classMap.put(cls.attributes.source, cls);
        }

        // Find leaf classes (classes with no subclasses)
        List<DODatabaseClass> leafClasses = findLeafDatabaseClasses(database, classMap);

        if (monitor != null) {
            monitor.onStartingDeduplication(leafClasses.size());
        } else {
            System.out.println("Deduplicating object IDs across inheritance hierarchies...");
        }

        // Track which IDs should be removed from each class
        Map<String, java.util.Set<Long>> idsToRemove = new HashMap<>();

        // For each leaf class, mark its object IDs for removal from all ancestor classes
        int leafIndex = 0;
        for (DODatabaseClass leafClass : leafClasses) {
            leafIndex++;

            if (monitor != null) {
                monitor.onProcessingLeafClass(leafClass.attributes.source, leafIndex, leafClasses.size());
            }

            if (leafClass.objects.objectIds == null || leafClass.objects.objectIds.length == 0) {
                continue;
            }

            // Walk up the inheritance chain
            String parentClassName = leafClass.attributes.parentClassName;
            while (parentClassName != null) {
                DODatabaseClass parentClass = classMap.get(parentClassName);
                if (parentClass == null) {
                    break;
                }

                // Mark leaf's object IDs for removal from this parent
                java.util.Set<Long> toRemove = idsToRemove.computeIfAbsent(parentClass.attributes.source, k -> new java.util.HashSet<>());
                for (long id : leafClass.objects.objectIds) {
                    toRemove.add(id);
                }

                // Move to next ancestor
                parentClassName = parentClass.attributes.parentClassName;
            }
        }

        // Now update uniqueObjectIds for all classes (keep objectIds unchanged)
        int deduplicatedCount = 0;
        int totalRemoved = 0;
        for (DODatabaseClass cls : database.getClasses()) {
            java.util.Set<Long> toRemove = idsToRemove.get(cls.attributes.source);

            if (toRemove != null && !toRemove.isEmpty() && cls.objects.objectIds != null) {
                // Filter out the IDs that belong to derived classes
                long[] uniqueIds = removeObjectIds(cls.objects.objectIds, toRemove);
                cls.objects.uniqueObjectIds = uniqueIds;

                int removedCount = cls.objects.objectIds.length - uniqueIds.length;
                if (removedCount > 0) {
                    deduplicatedCount++;
                    totalRemoved += removedCount;
                    if (monitor != null) {
                        monitor.onClassDeduplicated(cls.attributes.source, removedCount, uniqueIds.length);
                    } else {
                        System.out.println("Deduplicated " + removedCount + " object IDs from " + cls.attributes.source + " (" + cls.objects.objectIds.length + " -> " + uniqueIds.length + ")");
                    }
                }
            } else if (cls.objects.objectIds != null) {
                // No deduplication needed - copy objectIds to uniqueObjectIds
                cls.objects.uniqueObjectIds = cls.objects.objectIds;
            }
        }

        if (monitor != null) {
            monitor.onDeduplicationComplete(leafClasses.size(), totalRemoved);
        } else {
            System.out.println("Object ID deduplication complete: " + leafClasses.size() + " leaf classes, " + deduplicatedCount + " classes deduplicated");
        }
    }

    /**
     * Finds all leaf classes (classes with no subclasses) in a DODatabase.
     */
    private static List<DODatabaseClass> findLeafDatabaseClasses(DODatabase database, Map<String, DODatabaseClass> classMap) {
        List<DODatabaseClass> leafClasses = new ArrayList<>();

        for (DODatabaseClass cls : database.getClasses()) {
            boolean isLeaf = true;

            for (DODatabaseClass potentialChild : database.getClasses()) {
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
