package dataobjects.api.resolution;

import dataobjects.api.database.DOReferenceType;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.database.*;
import dataobjects.api.models.schema.DOSchema;
import com.db4o.ext.ExtObjectContainer;

/**
 * Service for resolving object relationships and inheritance.
 * This service encapsulates the complex logic for determining the most specific
 * class for objects and extracting all their references.
 * It also tracks object reachability through the engine's reachability tracker.
 */
public interface DOObjectResolver {

        /**
         * Resolve all objects with their most specific classes and references.
         * This method performs the core resolution logic and populates the
         * engine's reachability tracker with exact reachability data.
         * 
         * @param container The database container
         * @param database  The database structure (with inheritance already resolved)
         * @param schema    The schema for inheritance information
         * @param engine    The engine instance (provides reachability tracker)
         * @return Array of fully resolved objects
         */
        DODatabaseObject[] resolveAllObjects(ExtObjectContainer container,
                        DODatabase database,
                        DOSchema schema,
                        DOEngine engine);

        /**
         * Find the most specific class for an object among multiple classes.
         * This uses the existing analysis logic for inheritance depth calculation.
         * 
         * @param database   The database instance
         * @param schema     The schema for inheritance information
         * @param classNames Array of class names that contain the same object
         * @return The most specific class name
         */
        String findMostSpecificClass(DODatabase database,
                        DOSchema schema,
                        String[] classNames);

        /**
         * Extract all object references from a specific object.
         * This includes direct field references and collection contents.
         * 
         * @param container The database container
         * @param objectId  The object ID to examine
         * @param database  The database instance
         * @return Array of resolved references
         */
        DOObjectReference[] extractObjectReferences(ExtObjectContainer container,
                        Long objectId,
                        DODatabase database);

        /**
         * Extract collection references from a specific object.
         * 
         * @param container The database container
         * @param objectId  The object ID to examine
         * @param database  The database instance
         * @param schema    The schema instance for field definitions
         * @return Array of resolved collection references
         */
        DOCollectionReference[] extractCollectionReferences(ExtObjectContainer container,
                        Long objectId,
                        DODatabase database,
                        DOSchema schema);
}
