package dataobjects.api.models.database;

import dataobjects.api.models.DOField;
import dataobjects.api.database.DOReferenceType;

/**
 * Represents a reference from one object to another.
 * Contains information about the source, target, and the field through which
 * the reference is made.
 */
public interface DOObjectReference {

    /**
     * Get the source object ID that contains the reference.
     */
    Long getSourceObjectId();

    /**
     * Get the target object ID being referenced.
     */
    Long getTargetObjectId();

    /**
     * Get the field through which this reference is made.
     */
    DOField getField();

    /**
     * Get the reference type (DIRECT, COLLECTION_ITEM, MAP_KEY, MAP_VALUE).
     */
    DOReferenceType getReferenceType();
}