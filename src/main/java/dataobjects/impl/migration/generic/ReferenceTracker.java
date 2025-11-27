package dataobjects.impl.migration.generic;

import dataobjects.api.models.database.DODatabaseClass;
import java.util.*;

/**
 * Tracks object references during export to identify:
 * - Objects that are referenced but not exported
 * - Objects referenced by multiple modules (candidates for General.xml)
 * - Cross-module reference relationships
 */
public class ReferenceTracker {

    // Map: object ID -> set of module names that reference it
    private final Map<Long, Set<String>> objectReferences = new HashMap<>();

    // Map: object ID -> module name where object was exported
    private final Map<Long, String> exportedObjects = new HashMap<>();

    // Map: object ID -> database class definition (strongly-typed)
    private final Map<Long, DODatabaseClass> objectClasses = new HashMap<>();

    // Map: object ID -> actual object reference (for unexported objects)
    private final Map<Long, Object> referencedObjects = new HashMap<>();

    /**
     * Record that an object was exported in a specific module.
     */
    public void recordExportedObject(long objectId, String moduleName, DODatabaseClass databaseClass) {
        exportedObjects.put(objectId, moduleName);
        objectClasses.put(objectId, databaseClass);
    }

    /**
     * Record that an object is referenced by a specific module.
     * Now uses strongly-typed DODatabaseClass instead of string class names.
     */
    public void recordReference(long referencedId, String referencingModule, DODatabaseClass databaseClass,
            Object referencedObject) {
        objectReferences.computeIfAbsent(referencedId, k -> new HashSet<>()).add(referencingModule);

        // Also track the class if not already known
        if (!objectClasses.containsKey(referencedId) && databaseClass != null) {
            objectClasses.put(referencedId, databaseClass);
        }

        // Store the actual object reference if not already exported
        if (!exportedObjects.containsKey(referencedId) && referencedObject != null) {
            referencedObjects.put(referencedId, referencedObject);
        }
    }

    /**
     * Get objects that are referenced but were never exported.
     */
    public Set<Long> getUnexportedReferences() {
        Set<Long> unexported = new HashSet<>();
        for (Long objectId : objectReferences.keySet()) {
            if (!exportedObjects.containsKey(objectId)) {
                unexported.add(objectId);
            }
        }
        return unexported;
    }

    /**
     * Get unexported referenced objects grouped by database class.
     * Returns a map of DODatabaseClass -> list of actual object references.
     * This is strongly-typed - we pass actual DODatabaseClass objects, not strings.
     */
    public Map<DODatabaseClass, List<Object>> getUnexportedObjectsByClass() {
        Map<DODatabaseClass, List<Object>> objectsByClass = new HashMap<>();

        for (Long objectId : objectReferences.keySet()) {
            if (!exportedObjects.containsKey(objectId)) {
                DODatabaseClass dbClass = objectClasses.get(objectId);
                Object obj = referencedObjects.get(objectId);

                if (dbClass != null && obj != null) {
                    objectsByClass.computeIfAbsent(dbClass, k -> new ArrayList<>()).add(obj);
                }
            }
        }

        return objectsByClass;
    }

    /**
     * Get objects that are referenced by multiple modules.
     * These should go in General.xml.
     */
    public Map<Long, Set<String>> getMultiModuleReferences() {
        Map<Long, Set<String>> multiModule = new HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : objectReferences.entrySet()) {
            if (entry.getValue().size() > 1) {
                multiModule.put(entry.getKey(), entry.getValue());
            }
        }
        return multiModule;
    }

    /**
     * Get the module where an object was exported.
     * Returns null if object was not exported.
     */
    public String getObjectModule(long objectId) {
        return exportedObjects.get(objectId);
    }

    /**
     * Get the database class definition of an object.
     */
    public DODatabaseClass getObjectClass(long objectId) {
        return objectClasses.get(objectId);
    }

    /**
     * Check if an object should have a module attribute in its reference.
     * Returns the module name if different from the referencing module, null
     * otherwise.
     */
    public String getTargetModule(long referencedId, String referencingModule) {
        String targetModule = exportedObjects.get(referencedId);
        if (targetModule != null && !targetModule.equals(referencingModule)) {
            return targetModule;
        }
        return null;
    }

    /**
     * Get objects exported in a specific module.
     */
    public Set<Long> getObjectsInModule(String moduleName) {
        Set<Long> objects = new HashSet<>();
        for (Map.Entry<Long, String> entry : exportedObjects.entrySet()) {
            if (moduleName.equals(entry.getValue())) {
                objects.add(entry.getKey());
            }
        }
        return objects;
    }

    /**
     * Get statistics for reporting.
     */
    public ReferenceStatistics getStatistics() {
        return new ReferenceStatistics(
                exportedObjects.size(),
                objectReferences.size(),
                getUnexportedReferences().size(),
                getMultiModuleReferences().size());
    }

    /**
     * Statistics about references.
     */
    public static class ReferenceStatistics {
        public final int totalExported;
        public final int totalReferenced;
        public final int unexportedReferences;
        public final int multiModuleObjects;

        public ReferenceStatistics(int totalExported, int totalReferenced,
                int unexportedReferences, int multiModuleObjects) {
            this.totalExported = totalExported;
            this.totalReferenced = totalReferenced;
            this.unexportedReferences = unexportedReferences;
            this.multiModuleObjects = multiModuleObjects;
        }
    }
}
