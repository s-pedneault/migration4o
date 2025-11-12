package dataobjects.impl.deprecated.migration;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.deprecated.migration.DOLostObjectAnalysis;
import dataobjects.api.deprecated.migration.DOLostObjectsByClass;
import dataobjects.api.deprecated.migration.DOLostObjectReason;
import dataobjects.api.deprecated.migration.DOInheritanceMapping;

import java.util.*;

/**
 * EXACT lost object analysis using reachability tracking.
 * No statistical estimates - only exact object counts based on graph traversal.
 */
public class DOLostObjectAnalysisImpl implements DOLostObjectAnalysis {

    private final DOEngine engine;
    private final int totalUniqueObjects;
    private final int reachedUniqueObjects;
    private final int unreachedUniqueObjects;
    private final Set<Long> allUniqueObjectIds;
    private final Set<Long> reachedObjectIds;
    private final Set<Long> unreachedObjectIds;

    public DOLostObjectAnalysisImpl(final DOEngine engine, final DOInheritanceMapping inheritanceMapping) {
        this.engine = engine;

        // Collect exact object counts
        this.allUniqueObjectIds = new HashSet<>();
        this.reachedObjectIds = new HashSet<>();
        this.unreachedObjectIds = new HashSet<>();

        analyzeExactReachability();

        this.totalUniqueObjects = allUniqueObjectIds.size();
        this.reachedUniqueObjects = reachedObjectIds.size();
        this.unreachedUniqueObjects = unreachedObjectIds.size();
    }

    /**
     * Analyze exact reachability by examining all resolved objects.
     */
    private void analyzeExactReachability() {
        final DODatabase database = engine.getDatabase();

        for (final DODatabaseClass dbClass : database.getClasses()) {
            DODatabaseObject[] resolvedObjects = dbClass.getResolvedObjects();
            if (resolvedObjects != null) {
                for (DODatabaseObject obj : resolvedObjects) {
                    Long objId = obj.getObjectId();
                    allUniqueObjectIds.add(objId);

                    if (obj.isReachable()) {
                        reachedObjectIds.add(objId);
                    } else {
                        unreachedObjectIds.add(objId);
                    }
                }
            }
        }
    }

    // ============ Interface Implementation ============

    @Override
    public String[] getPotentiallyLostObjectIds() {
        return unreachedObjectIds.stream()
                .map(Object::toString)
                .toArray(String[]::new);
    }

    @Override
    public DOLostObjectsByClass[] getLostObjectsByClass() {
        // Return empty array - detailed class breakdown not needed for exact tracking
        return new DOLostObjectsByClass[0];
    }

    @Override
    public DOLostObjectReason[] getLostObjectReasons() {
        // Return single reason - objects are orphaned (not reachable from modules)
        return new DOLostObjectReason[] { DOLostObjectReason.ORPHANED_OBJECTS };
    }

    @Override
    public int getTotalLostObjectCount() {
        return unreachedUniqueObjects;
    }

    @Override
    public double getLostObjectPercentage() {
        if (totalUniqueObjects == 0) {
            return 0.0;
        }
        return (unreachedUniqueObjects * 100.0) / totalUniqueObjects;
    }

    @Override
    public boolean hasDataLossRisk() {
        return unreachedUniqueObjects > 0;
    }

    @Override
    public String[] getPreservedObjectIds() {
        return reachedObjectIds.stream()
                .map(Object::toString)
                .toArray(String[]::new);
    }

    @Override
    public int getTotalObjectCount() {
        return totalUniqueObjects;
    }
}
