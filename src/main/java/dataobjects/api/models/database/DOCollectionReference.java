package dataobjects.api.models.database;

import dataobjects.api.models.DOField;

/**
 * Represents a collection reference pointing to objects contained in a collection.
 */
public interface DOCollectionReference {

    /**
     * Get the source object ID that contains the collection.
     */
    Long getSourceObjectId();

    /**
     * Get the field that contains this collection.
     */
    DOField getField();

    /**
     * Get all object IDs contained in this collection.
     * Returns empty array if collection is empty or contains only primitives.
     */
    Long[] getContainedObjectIds();

    /**
     * Get the resolved content type of the collection.
     * This is the actual type of objects stored in the collection.
     */
    String getResolvedContentType();

    /**
     * Get the size of the collection.
     */
    int getSize();
}