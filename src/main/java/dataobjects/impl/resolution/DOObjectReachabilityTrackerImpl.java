package dataobjects.impl.resolution;

import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.resolution.DOObjectReachabilityTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the object reachability tracker.
 * Uses concurrent data structures to support potential future parallelization.
 */
public class DOObjectReachabilityTrackerImpl implements DOObjectReachabilityTracker {

    // Master list: all object IDs in database, grouped by database class
    private final Map<DODatabaseClass, Set<Long>> allObjectIdsByClass;

    // Tracking: which object IDs have been marked as reached, grouped by database
    // class
    private final Map<DODatabaseClass, Set<Long>> reachedObjectIdsByClass;

    // Quick lookup: all reached object IDs (for faster isObjectReached checks)
    private final Set<Long> allReachedObjectIds;

    // Tracking: the most specific class for each reached object ID
    private final Map<Long, DODatabaseClass> mostSpecificClassByObjectId;

    public DOObjectReachabilityTrackerImpl() {
        this.allObjectIdsByClass = new ConcurrentHashMap<>();
        this.reachedObjectIdsByClass = new ConcurrentHashMap<>();
        this.allReachedObjectIds = ConcurrentHashMap.newKeySet();
        this.mostSpecificClassByObjectId = new ConcurrentHashMap<>();
    }

    @Override
    public void initializeFromDatabase(Map<DODatabaseClass, Set<Long>> allObjectIdsByClass) {
        this.allObjectIdsByClass.clear();
        this.reachedObjectIdsByClass.clear();
        this.allReachedObjectIds.clear();
        this.mostSpecificClassByObjectId.clear();

        // Deep copy the input map to avoid external modifications
        for (Map.Entry<DODatabaseClass, Set<Long>> entry : allObjectIdsByClass.entrySet()) {
            DODatabaseClass dbClass = entry.getKey();
            Set<Long> objectIds = new HashSet<>(entry.getValue());

            this.allObjectIdsByClass.put(dbClass, objectIds);
            // Initialize empty sets for reached objects
            this.reachedObjectIdsByClass.put(dbClass, ConcurrentHashMap.newKeySet());
        }
    }

    @Override
    public void markObjectAsReached(Long objectId, DODatabaseClass[] classesInChain) {
        if (objectId == null || classesInChain == null || classesInChain.length == 0) {
            return;
        }

        // Mark as reached globally
        allReachedObjectIds.add(objectId);

        // Store the most specific class (first in the chain)
        mostSpecificClassByObjectId.putIfAbsent(objectId, classesInChain[0]);

        // Mark as reached for each class in the inheritance chain
        for (DODatabaseClass dbClass : classesInChain) {
            Set<Long> reachedSet = reachedObjectIdsByClass.get(dbClass);
            if (reachedSet != null) {
                reachedSet.add(objectId);
            } else {
                // Class not in master list - add it
                Set<Long> newSet = ConcurrentHashMap.newKeySet();
                newSet.add(objectId);
                reachedObjectIdsByClass.put(dbClass, newSet);
            }
        }
    }

    @Override
    public Map<DODatabaseClass, Set<Long>> getReachedObjectsByClass() {
        // Return a copy to prevent external modifications
        Map<DODatabaseClass, Set<Long>> result = new HashMap<>();
        for (Map.Entry<DODatabaseClass, Set<Long>> entry : reachedObjectIdsByClass.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
    }

    @Override
    public Map<DODatabaseClass, Set<Long>> getReachedObjectsByMostSpecificClass() {
        // Group objects by their most specific class
        Map<DODatabaseClass, Set<Long>> result = new HashMap<>();

        for (Map.Entry<Long, DODatabaseClass> entry : mostSpecificClassByObjectId.entrySet()) {
            Long objectId = entry.getKey();
            DODatabaseClass mostSpecificClass = entry.getValue();

            Set<Long> objectIds = result.get(mostSpecificClass);
            if (objectIds == null) {
                objectIds = new HashSet<>();
                result.put(mostSpecificClass, objectIds);
            }
            objectIds.add(objectId);
        }

        return result;
    }

    @Override
    public Map<DODatabaseClass, Set<Long>> getUnreachedObjectsByClass() {
        Map<DODatabaseClass, Set<Long>> unreached = new HashMap<>();

        for (Map.Entry<DODatabaseClass, Set<Long>> entry : allObjectIdsByClass.entrySet()) {
            DODatabaseClass dbClass = entry.getKey();
            Set<Long> allIds = entry.getValue();
            Set<Long> reachedIds = reachedObjectIdsByClass.get(dbClass);

            // Calculate unreached: all IDs minus reached IDs
            Set<Long> unreachedIds = new HashSet<>(allIds);
            if (reachedIds != null) {
                unreachedIds.removeAll(reachedIds);
            }

            if (!unreachedIds.isEmpty()) {
                unreached.put(dbClass, unreachedIds);
            }
        }

        return unreached;
    }

    @Override
    public boolean isObjectReached(Long objectId) {
        return allReachedObjectIds.contains(objectId);
    }

    @Override
    public int getTotalObjectCount() {
        return allObjectIdsByClass.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Override
    public int getReachedObjectCount() {
        return allReachedObjectIds.size();
    }

    @Override
    public int getUnreachedObjectCount() {
        return getTotalObjectCount() - getReachedObjectCount();
    }

    @Override
    public long getObjectCountByClass(DODatabaseClass dbClass) {
        Set<Long> objectIds = allObjectIdsByClass.get(dbClass);
        return objectIds != null ? objectIds.size() : 0;
    }

    @Override
    public long getReachedObjectCountByClass(DODatabaseClass dbClass) {
        Set<Long> reachedIds = reachedObjectIdsByClass.get(dbClass);
        return reachedIds != null ? reachedIds.size() : 0;
    }

    @Override
    public long getUnreachedObjectCountByClass(DODatabaseClass dbClass) {
        Set<Long> allIds = allObjectIdsByClass.get(dbClass);
        Set<Long> reachedIds = reachedObjectIdsByClass.get(dbClass);

        if (allIds == null) {
            return 0;
        }

        long totalCount = allIds.size();
        long reachedCount = reachedIds != null ? reachedIds.size() : 0;

        return totalCount - reachedCount;
    }
}
