package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;

/**
 * Provides inheritance relationship mapping and analysis.
 */
public interface DOInheritanceMapping {

    /**
     * Get all root classes (classes with no parent).
     * 
     * @return Array of root classes
     */
    DOClass[] getRootClasses();

    /**
     * Get all ancestor classes for the given class.
     * 
     * @param clazz The class to get ancestors for
     * @return Array of ancestor classes
     */
    DOClass[] getAncestors(DOClass clazz);

    /**
     * Get all descendant classes for the given class.
     * 
     * @param clazz The class to get descendants for
     * @return Array of descendant classes
     */
    DOClass[] getDescendants(DOClass clazz);

    /**
     * Get all leaf classes (classes with no children) for the given class.
     * 
     * @param clazz The class to get leaf classes for
     * @return Array of leaf classes
     */
    DOClass[] getLeafClasses(DOClass clazz);

    /**
     * Check if the given class has polymorphic storage.
     * 
     * @param clazz The class to check
     * @return true if the class stores objects polymorphically
     */
    boolean isPolymorphic(DOClass clazz);

    /**
     * Get the inheritance depth of the given class.
     * 
     * @param clazz The class to get depth for
     * @return Inheritance depth (0 for root classes)
     */
    int getInheritanceDepth(DOClass clazz);

    /**
     * Get the direct parent of the given class.
     * 
     * @param clazz The class to get parent for
     * @return Parent class, or null if root class
     */
    DOClass getParent(DOClass clazz);

    /**
     * Get direct children of the given class.
     * 
     * @param clazz The class to get children for
     * @return Array of direct child classes
     */
    DOClass[] getChildren(DOClass clazz);
}
