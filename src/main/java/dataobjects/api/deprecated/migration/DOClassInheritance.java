package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Represents inheritance information for a specific class.
 */
public interface DOClassInheritance {

    /**
     * Get the class this inheritance info applies to.
     * 
     * @return The class
     */
    DOClass getClazz();

    /**
     * Get the parent class.
     * 
     * @return Parent class, or null if root class
     */
    DOClass getParent();

    /**
     * Get direct children of this class.
     * 
     * @return Array of child classes
     */
    DOClass[] getChildren();

    /**
     * Get all ancestors of this class.
     * 
     * @return Array of ancestor classes
     */
    DOClass[] getAncestors();

    /**
     * Get all descendants of this class.
     * 
     * @return Array of descendant classes
     */
    DOClass[] getDescendants();

    /**
     * Get the inheritance depth.
     * 
     * @return Inheritance depth
     */
    int getDepth();

    /**
     * Check if this class stores objects polymorphically.
     * 
     * @return true if polymorphic storage
     */
    boolean isPolymorphic();
}
