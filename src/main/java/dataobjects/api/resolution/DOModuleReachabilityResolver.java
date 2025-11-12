package dataobjects.api.resolution;

import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.schema.DOSchema;

/**
 * Service for determining module reachability of objects.
 * This service encapsulates the logic for finding objects reachable from module
 * root objects.
 */
public interface DOModuleReachabilityResolver {

    /**
     * Determine which objects are reachable from module root objects.
     * Uses the existing analysis logic for following reference chains.
     * 
     * @param resolvedObjects All resolved objects in the database
     * @param schema          The schema containing module information
     * @return Array of object IDs that are reachable from modules
     */
    Long[] findObjectsReachableFromModules(DODatabaseObject[] resolvedObjects, DOSchema schema);

    /**
     * Get all class names that are part of schema modules.
     * These are considered root objects for reachability analysis.
     * 
     * @param schema The schema containing module information
     * @return Set of module class names
     */
    String[] getModuleClassNames(DOSchema schema);

    /**
     * Mark all resolved objects with their reachability status.
     * This updates the reachability flag on each resolved object.
     * 
     * @param resolvedObjects    All resolved objects to mark
     * @param reachableObjectIds Object IDs that are reachable from modules
     */
    void markReachabilityStatus(DODatabaseObject[] resolvedObjects, Long[] reachableObjectIds);
}
