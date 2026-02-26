package migration4o.database.reach;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.SchemaUtil;

/**
 * In-memory ID index used to track reached/exported objects from a single
 * notification point.
 *
 * The index is built from database schema object IDs and unique object IDs
 * (same source data as the Export IDs feature) and provides:
 * - Fast ID -> class hierarchy lookup
 * - Fast reached/unreached computation by class and by leaf class
 * - Synchronization back to DOSchemaClass.reachedObjectIds arrays
 */
public class ObjectExportTrackingIndex {

    private final DOSchema databaseSchema;
    private final Map<String, DOSchemaClass> classByName = new HashMap<>();

    // Export IDs-style indexes
    private final Map<String, Set<Long>> classToAllIds = new HashMap<>();
    private final Map<String, Set<Long>> classToUniqueIds = new HashMap<>();
    private final Map<Long, Set<String>> idToClasses = new HashMap<>();
    private final Map<Long, String> idToLeafClass = new HashMap<>();

    // Reached tracking
    private final Set<Long> reachedIds = new HashSet<>();
    private final Map<String, Set<Long>> reachedIdsByClass = new HashMap<>();

    public ObjectExportTrackingIndex(DOSchema databaseSchema) {
        this.databaseSchema = databaseSchema;
        rebuild();
    }

    public final void rebuild() {
        classByName.clear();
        classToAllIds.clear();
        classToUniqueIds.clear();
        idToClasses.clear();
        idToLeafClass.clear();
        reachedIds.clear();
        reachedIdsByClass.clear();

        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return;
        }

        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            classByName.put(schemaClass.source, schemaClass);

            Set<Long> allIds = new LinkedHashSet<>();
            if (schemaClass.objectIds != null) {
                for (long id : schemaClass.objectIds) {
                    allIds.add(id);
                    idToClasses.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(schemaClass.source);
                }
            }
            classToAllIds.put(schemaClass.source, allIds);

            Set<Long> uniqueIds = new LinkedHashSet<>();
            if (schemaClass.uniqueObjectIds != null) {
                for (long id : schemaClass.uniqueObjectIds) {
                    uniqueIds.add(id);
                    idToLeafClass.put(id, schemaClass.source);
                    idToClasses.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(schemaClass.source);
                }
            }
            classToUniqueIds.put(schemaClass.source, uniqueIds);
        }
    }

    public synchronized void resetReached() {
        reachedIds.clear();
        reachedIdsByClass.clear();
        syncSchemaReachedArrays();
    }

    public synchronized void markReached(long objectId) {
        if (!reachedIds.add(objectId)) {
            return;
        }

        Set<String> targetClasses = resolveClassesForObjectId(objectId);
        for (String className : targetClasses) {
            reachedIdsByClass.computeIfAbsent(className, ignored -> new LinkedHashSet<>()).add(objectId);
        }

        syncSchemaReachedArrays();
    }

    public synchronized void markReachedAll(Collection<Long> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Long objectId : objectIds) {
            if (objectId == null) {
                continue;
            }
            if (!reachedIds.add(objectId)) {
                continue;
            }
            Set<String> targetClasses = resolveClassesForObjectId(objectId);
            for (String className : targetClasses) {
                reachedIdsByClass.computeIfAbsent(className, ignored -> new LinkedHashSet<>()).add(objectId);
            }
            changed = true;
        }

        if (changed) {
            syncSchemaReachedArrays();
        }
    }

    public synchronized Map<String, List<Long>> getUnreachedByLeafClass() {
        Map<String, List<Long>> result = new TreeMap<>();
        for (Map.Entry<Long, String> entry : idToLeafClass.entrySet()) {
            Long objectId = entry.getKey();
            if (reachedIds.contains(objectId)) {
                continue;
            }
            String leafClass = entry.getValue();
            result.computeIfAbsent(leafClass, ignored -> new ArrayList<>()).add(objectId);
        }

        for (List<Long> ids : result.values()) {
            Collections.sort(ids);
        }
        return result;
    }

    public synchronized List<Long> getUnreachedForLeafClass(String leafClassName) {
        if (leafClassName == null || leafClassName.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uniqueIds = classToUniqueIds.getOrDefault(leafClassName, Collections.emptySet());
        List<Long> result = new ArrayList<>();
        for (Long objectId : uniqueIds) {
            if (!reachedIds.contains(objectId)) {
                result.add(objectId);
            }
        }
        Collections.sort(result);
        return result;
    }

    public synchronized Set<String> getClassHierarchyForObjectId(long objectId) {
        return resolveClassesForObjectId(objectId);
    }

    public synchronized String getLeafClassForObjectId(long objectId) {
        String leaf = idToLeafClass.get(objectId);
        if (leaf != null) {
            return leaf;
        }
        Set<String> classes = idToClasses.get(objectId);
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        return SchemaUtil.findLeafClass(classes);
    }

    private Set<String> resolveClassesForObjectId(long objectId) {
        Set<String> classes = idToClasses.get(objectId);
        if (classes != null && !classes.isEmpty()) {
            return classes;
        }

        String leafClass = idToLeafClass.get(objectId);
        if (leafClass == null || leafClass.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> hierarchy = new LinkedHashSet<>();
        String current = leafClass;
        while (current != null && !current.isEmpty()) {
            hierarchy.add(current);
            DOSchemaClass schemaClass = classByName.get(current);
            if (schemaClass == null) {
                break;
            }
            current = schemaClass.parentClassName;
        }
        return hierarchy;
    }

    private void syncSchemaReachedArrays() {
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return;
        }

        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            Set<Long> reachedForClass = reachedIdsByClass.get(schemaClass.source);
            if (reachedForClass == null || reachedForClass.isEmpty()) {
                schemaClass.reachedObjectIds = null;
                continue;
            }
            long[] ids = new long[reachedForClass.size()];
            int index = 0;
            for (Long id : reachedForClass) {
                ids[index++] = id;
            }
            schemaClass.reachedObjectIds = ids;
        }
    }

}
