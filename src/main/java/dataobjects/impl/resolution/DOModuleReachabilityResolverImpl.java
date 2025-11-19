package dataobjects.impl.resolution;

import dataobjects.api.resolution.DOModuleReachabilityResolver;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.schema.*;
import java.util.*;

/**
 * Implementation of module reachability resolver that handles determining
 * which objects are reachable from module root objects.
 */
public class DOModuleReachabilityResolverImpl implements DOModuleReachabilityResolver {

    @Override
    public Long[] findObjectsReachableFromModules(DODatabaseObject[] resolvedObjects, DOSchema schema) {
        // Find all objects reachable from module root objects
        Set<Long> discoverableObjects = new HashSet<>();

        try {
            // PERFORMANCE OPTIMIZATION: Pre-build index of object ID -> resolved object for
            // O(1) lookup
            // This eliminates O(n²) performance in findObjectsReferencedByObject calls
            Map<Long, DODatabaseObject> objectIndex = new HashMap<>();
            for (DODatabaseObject obj : resolvedObjects) {
                objectIndex.put(obj.getObjectId(), obj);
            }

            // Get all classes that are part of modules (root objects)
            String[] moduleClassNames = getModuleClassNames(schema);
            System.out.println("DEBUG: Found " + moduleClassNames.length + " classes in modules");

            // Create a map of class name to object IDs for quick lookup
            Map<String, Set<Long>> classToObjectIds = new HashMap<>();
            for (DODatabaseObject obj : resolvedObjects) {
                String className = obj.getMostSpecificClass().getAbsoluteName();
                classToObjectIds.computeIfAbsent(className, k -> new HashSet<>()).add(obj.getObjectId());
            }

            // Add all objects from module classes as starting points
            for (String moduleClassName : moduleClassNames) {
                Set<Long> moduleObjects = classToObjectIds.get(moduleClassName);
                if (moduleObjects != null) {
                    discoverableObjects.addAll(moduleObjects);
                    if (moduleClassName.contains("DossPrev")) {
                        System.out.println(
                                "DEBUG: Added " + moduleObjects.size() + " root objects from " + moduleClassName);
                    }
                } else {
                    // Try to find by simple class name
                    String simpleClassName = getSimpleClassName(moduleClassName);
                    moduleObjects = classToObjectIds.get(simpleClassName);
                    if (moduleObjects != null) {
                        discoverableObjects.addAll(moduleObjects);
                        if (moduleClassName.contains("DossPrev")) {
                            System.out.println("DEBUG: Added " + moduleObjects.size() + " root objects from "
                                    + moduleClassName + " (matched as " + simpleClassName + ")");
                        }
                    } else {
                        if (moduleClassName.contains("DossPrev")) {
                            System.out.println("DEBUG: No objects found for module class: " + moduleClassName
                                    + " (simple: " + simpleClassName + ")");
                        }
                    }
                }
            }

            // Follow reference chains to discover referenced objects
            Set<Long> newlyDiscovered;
            int iterations = 0;
            do {
                newlyDiscovered = new HashSet<>();
                iterations++;

                // For each currently discoverable object, find objects it references
                // OPTIMIZATION: Use array to avoid creating new HashSet copy each iteration
                Long[] currentObjects = discoverableObjects.toArray(new Long[0]);
                for (Long objectId : currentObjects) {
                    Set<Long> referencedObjects = findObjectsReferencedByObjectOptimized(objectId, objectIndex);
                    for (Long referencedId : referencedObjects) {
                        if (!discoverableObjects.contains(referencedId)) {
                            newlyDiscovered.add(referencedId);
                        }
                    }
                }

                discoverableObjects.addAll(newlyDiscovered);
                // System.out.println(
                // "DEBUG: Iteration " + iterations + ": discovered " + newlyDiscovered.size() +
                // " new objects");

            } while (!newlyDiscovered.isEmpty() && iterations < 50); // Prevent infinite loops

            if (iterations >= 50) {
                System.err.println(
                        "WARNING: Reference chain discovery stopped after 50 iterations to prevent infinite loop");
            }

        } catch (Exception e) {
            System.err.println("Error finding discoverable objects: " + e.getMessage());
            e.printStackTrace();
        }

        return discoverableObjects.toArray(new Long[0]);
    }

    @Override
    public String[] getModuleClassNames(DOSchema schema) {
        // Get all class names that are part of modules
        Set<String> moduleClassNames = new HashSet<>();

        try {
            // Get all schema modules
            DOSchemaModule[] modules = schema.getModules();
            // System.out.println("DEBUG: Schema has " + modules.length + " modules");

            for (DOSchemaModule module : modules) {
                // Get all classes in this module
                DOSchemaClass[] moduleClasses = module.getClasses();
                if (moduleClasses != null) {
                    for (DOSchemaClass schemaClass : moduleClasses) {
                        moduleClassNames.add(schemaClass.getAbsoluteName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting module class names: " + e.getMessage());
            e.printStackTrace();
        }

        return moduleClassNames.toArray(new String[0]);
    }

    @Override
    public void markReachabilityStatus(DODatabaseObject[] resolvedObjects, Long[] reachableObjectIds) {
        // Convert array to set for faster lookup
        Set<Long> reachableSet = new HashSet<>(Arrays.asList(reachableObjectIds));

        // Mark each object with its reachability status
        for (DODatabaseObject obj : resolvedObjects) {
            boolean isReachable = reachableSet.contains(obj.getObjectId());
            obj.setReachable(isReachable);
        }

        // System.out.println("DEBUG: Marked " + reachableObjectIds.length + " objects
        // as reachable from modules");
        // System.out.println("DEBUG: " + (resolvedObjects.length -
        // reachableObjectIds.length) + " objects are orphaned");
    }

    /**
     * Find all objects referenced by a specific resolved object.
     * This combines both direct references and collection references.
     */
    private Set<Long> findObjectsReferencedByObject(Long objectId, DODatabaseObject[] resolvedObjects) {
        Set<Long> referencedObjects = new HashSet<>();

        // Find the resolved object with this ID
        for (DODatabaseObject obj : resolvedObjects) {
            if (obj.getObjectId().equals(objectId)) {
                // Add all direct references
                for (dataobjects.api.models.database.DOObjectReference ref : obj.getReferences()) {
                    referencedObjects.add(ref.getTargetObjectId());
                }

                // Add all collection references
                for (dataobjects.api.models.database.DOCollectionReference collRef : obj.getCollections()) {
                    Long[] containedIds = collRef.getContainedObjectIds();
                    if (containedIds != null && containedIds.length > 0) {
                        // System.out.println("DEBUG: Object " + objectId + " contains collection '"
                        // + collRef.getField().getName() + " with " + containedIds.length + "
                        // objects");
                        for (Long containedId : containedIds) {
                            referencedObjects.add(containedId);
                        }
                    } else {
                        // System.out.println("DEBUG: Object " + objectId + " has empty collection '"
                        // + collRef.getField().getName() + "'");
                    }
                }

                break;
            }
        }

        if (referencedObjects.size() > 0) {
            // System.out.println(
            // "DEBUG: Object " + objectId + " references " + referencedObjects.size() + "
            // other objects");
        }

        return referencedObjects;
    }

    /**
     * OPTIMIZED VERSION: Find all objects referenced by a specific resolved object
     * using O(1) lookup.
     * This eliminates the O(n) linear search through all resolved objects.
     */
    private Set<Long> findObjectsReferencedByObjectOptimized(Long objectId, Map<Long, DODatabaseObject> objectIndex) {
        Set<Long> referencedObjects = new HashSet<>();

        // O(1) lookup instead of O(n) linear search
        DODatabaseObject obj = objectIndex.get(objectId);
        if (obj != null) {
            // Add all direct references
            for (dataobjects.api.models.database.DOObjectReference ref : obj.getReferences()) {
                referencedObjects.add(ref.getTargetObjectId());
            }

            // Add all collection references
            for (dataobjects.api.models.database.DOCollectionReference collRef : obj.getCollections()) {
                Long[] containedIds = collRef.getContainedObjectIds();
                if (containedIds != null && containedIds.length > 0) {
                    for (Long containedId : containedIds) {
                        referencedObjects.add(containedId);
                    }
                }
            }
        }

        return referencedObjects;
    }

    /**
     * Extract simple class name from fully qualified name
     */
    private String getSimpleClassName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return null;
        }
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}
