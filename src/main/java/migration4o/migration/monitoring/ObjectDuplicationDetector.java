package migration4o.migration.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized tracker for detecting and recording object duplication during
 * export.
 * Maintains references to all exported objects and identifies when objects are
 * exported multiple times.
 */
public class ObjectDuplicationDetector {
    public final Map<Long, List<ObjectReference>> objectReferences = new HashMap<>();

    /**
     * Records a reference to an exported object.
     * 
     * @param objectId              The ID of the exported object
     * @param className             The class name of the object
     * @param parentObjectId        The ID of the parent object (null for module
     *                              exports)
     * @param sourceContainingClass The class containing the field that references
     *                              this object
     * @param sourceFieldName       The field name that references this object
     */
    public void recordObjectReference(long objectId, String className, Long parentObjectId,
            String sourceContainingClass, String sourceFieldName) {
        ObjectReference ref = parentObjectId != null
                ? new ObjectReference(objectId, className, parentObjectId, sourceContainingClass, sourceFieldName)
                : new ObjectReference(objectId, className);
        objectReferences.computeIfAbsent(objectId, k -> new ArrayList<>()).add(ref);
    }

    /**
     * Generates warnings for all objects that were exported multiple times.
     * 
     * @return List of warnings for duplicate exports
     */
    public List<ExportWarning> generateDuplicateWarnings() {
        List<ExportWarning> warnings = new ArrayList<>();
        for (Map.Entry<Long, List<ObjectReference>> entry : objectReferences.entrySet()) {
            List<ObjectReference> refs = entry.getValue();
            if (refs.size() > 1) {
                warnings.add(new ExportWarning(entry.getKey(), refs.get(0).className, refs));
            }
        }
        return warnings;
    }

    /**
     * Checks if an object has been referenced.
     * 
     * @param objectId The object ID to check
     * @return true if the object has at least one reference
     */
    public boolean hasReference(long objectId) {
        return objectReferences.containsKey(objectId);
    }

    /**
     * Gets all references for a specific object.
     * 
     * @param objectId The object ID to get references for
     * @return List of references, or empty list if object not referenced
     */
    public List<ObjectReference> getReferences(long objectId) {
        return objectReferences.getOrDefault(objectId, new ArrayList<>());
    }

    /**
     * Gets the count of times an object was referenced.
     * 
     * @param objectId The object ID to check
     * @return Number of times the object was referenced
     */
    public int getReferenceCount(long objectId) {
        List<ObjectReference> refs = objectReferences.get(objectId);
        return refs != null ? refs.size() : 0;
    }

    /**
     * Clears all tracked references.
     */
    public void clear() {
        objectReferences.clear();
    }
}
