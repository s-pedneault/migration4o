package dataobjects.api.models.database;

import dataobjects.api.models.*;
import java.util.Vector;

public interface DODatabaseClass extends DOClass {

    public int getTotalObjectCount();

    public void setTotalObjectCount(int totalObjectCount);

    public int getMigratedObjectCount();

    public void setMigratedObjectCount(int migratedObjectCount);

    public void increaseMigratedObjectCount(int by);

    // New resolution methods
    /**
     * Get all resolved objects for this class (only most specific instances).
     * Objects are fully resolved with inheritance, references, and collections.
     */
    DODatabaseObject[] getResolvedObjects();

    /**
     * Set the resolved objects for this class.
     * Called during the database loading process.
     */
    void setResolvedObjects(DODatabaseObject[] resolvedObjects);

    // Direct inheritance references
    /**
     * Get the direct parent class in the inheritance hierarchy.
     * Returns null if this class has no parent (root class).
     */
    DODatabaseClass getParentClass();

    /**
     * Set the direct parent class.
     * Called during the inheritance resolution process.
     */
    void setParentClass(DODatabaseClass parentClass);

    /**
     * Get all direct subclasses (classes that directly extend this class).
     * Returns a Vector that can be modified during resolution.
     */
    Vector<DODatabaseClass> getDirectSubclasses();

    /**
     * Get all subclasses (direct and indirect) that extend from this class.
     * Returns a Vector that can be modified during resolution.
     */
    Vector<DODatabaseClass> getAllSubclasses();

    /**
     * Get the complete inheritance chain from this class to the root.
     * Returns a Vector with immediate parent first, then grandparent, etc.
     */
    Vector<DODatabaseClass> getInheritanceChain();

    /**
     * Get the inheritance depth (0 = no inheritance, 1 = direct inheritance, etc.).
     */
    int getInheritanceDepth();

    /**
     * Check if this class is a leaf class in the inheritance hierarchy.
     * Leaf classes have no subclasses extending from them.
     */
    boolean isLeafClass();

    /**
     * Get objects that are orphaned (not reachable from modules).
     * This is a filtered view of the resolved objects.
     */
    DODatabaseObject[] getOrphanedObjects();

    /**
     * Get objects that are reachable from module root objects.
     * This is a filtered view of the resolved objects.
     */
    DODatabaseObject[] getReachableObjects();

    /**
     * Get the count of how many different classes reference this class through
     * ID-type fields.
     * This is calculated during database loading and helps determine if ID objects
     * should be
     * flattened (single reference) or kept as IDs (multiple references) during
     * export.
     * 
     * @return Number of classes that have ID-type fields pointing to this class
     */
    int getReferenceCount();

    /**
     * Set the reference count. Called during database resolution.
     */
    void setReferenceCount(int count);

}
