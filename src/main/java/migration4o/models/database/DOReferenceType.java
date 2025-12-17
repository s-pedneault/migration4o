package migration4o.models.database;

/**
 * Enumeration of reference types for object relationships.
 */
public enum DOReferenceType {
    /**
     * Direct field reference to another object.
     */
    DIRECT,

    /**
     * Reference through a collection item.
     */
    COLLECTION_ITEM,

    /**
     * Reference through a map key.
     */
    MAP_KEY,

    /**
     * Reference through a map value.
     */
    MAP_VALUE
}
