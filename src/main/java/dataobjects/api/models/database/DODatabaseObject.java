package dataobjects.api.models.database;

import dataobjects.api.models.DOClass;

/**
 * Represents a fully resolved database object with all references and inheritance resolved.
 * This interface provides access to resolved inheritance information, direct references,
 * and collection contents without requiring post-processing.
 */
public interface DODatabaseObject {

    /**
     * Get the object's unique ID.
     */
    Long getObjectId();

    /**
     * Get the most specific class for this object (resolved through inheritance).
     * This is the deepest class in the inheritance hierarchy that contains this object.
     */
    DOClass getMostSpecificClass();

    /**
     * Get all classes this object appears in (inheritance chain).
     * Ordered from most specific to most general.
     */
    DOClass[] getAllClasses();

    /**
     * Get all objects this object references directly through its fields.
     */
    DOObjectReference[] getReferences();

    /**
     * Get all objects this object references through collections.
     */
    DOCollectionReference[] getCollections();

    /**
     * Check if this object is reachable from module root objects.
     * Objects not reachable from modules are considered orphaned.
     */
    boolean isReachable();

    /**
     * Set the reachability status from modules.
     * This is set during the resolution process.
     */
    void setReachable(boolean reachable);
}