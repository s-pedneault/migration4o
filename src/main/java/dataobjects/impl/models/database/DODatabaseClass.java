package dataobjects.impl.models.database;

import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class DODatabaseClass extends DOClass {
    private int totalObjectCount;
    private int migratedObjectCount;
    private DODatabaseObject[] resolvedObjects;

    // Direct inheritance references
    private DODatabaseClass parentClass;
    private Vector<DODatabaseClass> directSubclasses;
    private Vector<DODatabaseClass> allSubclasses;
    private Vector<DODatabaseClass> inheritanceChain;

    // Reference tracking for ID-type fields
    private int referenceCount = 0;

    public DODatabaseClass(String absoluteName, String shortName, String description, String title,
            String superClassAbsoluteName,
            DOField[] fields, int totalObjectCount, int migratedObjectCount) {
        super(absoluteName, shortName, description, title, superClassAbsoluteName, fields);
        this.totalObjectCount = totalObjectCount;
        this.migratedObjectCount = migratedObjectCount;
        this.resolvedObjects = new DODatabaseObject[0];

        // Initialize inheritance collections
        this.directSubclasses = new Vector<DODatabaseClass>();
        this.allSubclasses = new Vector<DODatabaseClass>();
        this.inheritanceChain = new Vector<DODatabaseClass>();
    }

    public int getTotalObjectCount() {
        return totalObjectCount;
    }

    public void setTotalObjectCount(int totalObjectCount) {
        this.totalObjectCount = totalObjectCount;
    }

    public int getMigratedObjectCount() {
        return migratedObjectCount;
    }

    public void setMigratedObjectCount(int migratedObjectCount) {
        this.migratedObjectCount = migratedObjectCount;
    }

    public void increaseMigratedObjectCount(int by) {
        this.migratedObjectCount += by;
    }

    public DODatabaseObject[] getResolvedObjects() {
        return resolvedObjects != null ? resolvedObjects.clone() : new DODatabaseObject[0];
    }

    public void setResolvedObjects(DODatabaseObject[] resolvedObjects) {
        this.resolvedObjects = resolvedObjects != null ? resolvedObjects.clone() : new DODatabaseObject[0];
    }

    public DODatabaseClass getParentClass() {
        return parentClass;
    }

    public void setParentClass(DODatabaseClass parentClass) {
        this.parentClass = parentClass;
    }

    public Vector<DODatabaseClass> getDirectSubclasses() {
        return directSubclasses;
    }

    public Vector<DODatabaseClass> getAllSubclasses() {
        return allSubclasses;
    }

    public Vector<DODatabaseClass> getInheritanceChain() {
        return inheritanceChain;
    }

    public int getInheritanceDepth() {
        return inheritanceChain.size();
    }

    public boolean isLeafClass() {
        return directSubclasses.isEmpty();
    }

    public DODatabaseObject[] getOrphanedObjects() {
        if (resolvedObjects == null) {
            return new DODatabaseObject[0];
        }

        List<DODatabaseObject> orphaned = new ArrayList<>();
        for (DODatabaseObject obj : resolvedObjects) {
            if (!obj.isReachable()) {
                orphaned.add(obj);
            }
        }

        return orphaned.toArray(new DODatabaseObject[0]);
    }

    public DODatabaseObject[] getReachableObjects() {
        if (resolvedObjects == null) {
            return new DODatabaseObject[0];
        }

        List<DODatabaseObject> reachable = new ArrayList<>();
        for (DODatabaseObject obj : resolvedObjects) {
            if (obj.isReachable()) {
                reachable.add(obj);
            }
        }

        return reachable.toArray(new DODatabaseObject[0]);
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int count) {
        this.referenceCount = count;
    }
}
