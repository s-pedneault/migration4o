package dataobjects.impl.models.database;

import dataobjects.api.models.DOField;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.impl.models.DOClassImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class DODatabaseClassImpl extends DOClassImpl implements DODatabaseClass {
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

    public DODatabaseClassImpl(String absoluteName, String shortName, String description, String title,
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

    @Override
    public int getTotalObjectCount() {
        return totalObjectCount;
    }

    @Override
    public void setTotalObjectCount(int totalObjectCount) {
        this.totalObjectCount = totalObjectCount;
    }

    @Override
    public int getMigratedObjectCount() {
        return migratedObjectCount;
    }

    @Override
    public void setMigratedObjectCount(int migratedObjectCount) {
        this.migratedObjectCount = migratedObjectCount;
    }

    @Override
    public void increaseMigratedObjectCount(int by) {
        this.migratedObjectCount += by;
    }

    @Override
    public DODatabaseObject[] getResolvedObjects() {
        return resolvedObjects != null ? resolvedObjects.clone() : new DODatabaseObject[0];
    }

    @Override
    public void setResolvedObjects(DODatabaseObject[] resolvedObjects) {
        this.resolvedObjects = resolvedObjects != null ? resolvedObjects.clone() : new DODatabaseObject[0];
    }

    @Override
    public DODatabaseClass getParentClass() {
        return parentClass;
    }

    @Override
    public void setParentClass(DODatabaseClass parentClass) {
        this.parentClass = parentClass;
    }

    @Override
    public Vector<DODatabaseClass> getDirectSubclasses() {
        return directSubclasses;
    }

    @Override
    public Vector<DODatabaseClass> getAllSubclasses() {
        return allSubclasses;
    }

    @Override
    public Vector<DODatabaseClass> getInheritanceChain() {
        return inheritanceChain;
    }

    @Override
    public int getInheritanceDepth() {
        return inheritanceChain.size();
    }

    @Override
    public boolean isLeafClass() {
        return directSubclasses.isEmpty();
    }

    @Override
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

    @Override
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

    @Override
    public int getReferenceCount() {
        return referenceCount;
    }

    @Override
    public void setReferenceCount(int count) {
        this.referenceCount = count;
    }
}
