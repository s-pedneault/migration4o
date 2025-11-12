package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Provides detailed information about a specific type stored polymorphically.
 */
public interface DOPolymorphicTypeInfo {

    /**
     * Get the type that is stored.
     * 
     * @return The stored type
     */
    DOClass getStoredType();

    /**
     * Get the number of objects of this type.
     * 
     * @return Object count
     */
    long getObjectCount();

    /**
     * Get the inheritance path from the stored type to the storage class.
     * 
     * @return Array of classes in the inheritance path
     */
    DOClass[] getInheritancePath();

    /**
     * Check if this type has a schema mapping.
     * 
     * @return true if mapped in schema
     */
    boolean hasSchemaMapping();

    /**
     * Get the inheritance depth from storage class.
     * 
     * @return Inheritance depth
     */
    int getInheritanceDepth();

    /**
     * Get the migration impact description for this type.
     * 
     * @return Impact description
     */
    String getMigrationImpact();
}
