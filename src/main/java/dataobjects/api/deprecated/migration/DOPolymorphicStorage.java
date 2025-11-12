package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents a polymorphic storage point that affects migration.
 */
public interface DOPolymorphicStorage {

    /**
     * Get the storage class where objects are stored.
     * 
     * @return Storage class
     */
    DOClass getStorageClass();

    /**
     * Get the actual object types stored in this class.
     * 
     * @return Array of object types
     */
    DOClass[] getStoredObjectTypes();

    /**
     * Get the number of objects stored polymorphically.
     * 
     * @return Object count
     */
    long getObjectCount();

    /**
     * Check if this polymorphic storage affects migration.
     * 
     * @return true if it affects migration
     */
    boolean affectsMigration();

    /**
     * Get detailed explanation of the polymorphic storage issue.
     * 
     * @return Detailed explanation
     */
    String getDetailedExplanation();

    /**
     * Get storage breakdown by type.
     * 
     * @return Array of type-specific storage information
     */
    DOPolymorphicTypeInfo[] getTypeBreakdown();
}
