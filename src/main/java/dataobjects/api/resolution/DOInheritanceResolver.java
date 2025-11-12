package dataobjects.api.resolution;

import dataobjects.api.database.*;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.database.DODatabase;

/**
 * Service for resolving inheritance relationships between classes.
 * This service builds direct references between classes instead of separate
 * inheritance info objects.
 */
public interface DOInheritanceResolver {

    /**
     * Resolve inheritance relationships for all classes in the database.
     * This builds direct references between parent and child classes.
     * 
     * @param database The database instance
     * @param schema   The schema for inheritance information
     */
    void resolveInheritance(DODatabase database, DOSchema schema);

    /**
     * Build the complete inheritance chain for a specific class.
     * Uses the existing analysis logic for schema traversal.
     * 
     * @param schema    The schema for inheritance information
     * @param className The class name to build chain for
     * @return Array of ancestor class names, from immediate parent to root
     */
    String[] buildInheritanceChain(DOSchema schema, String className);
}
