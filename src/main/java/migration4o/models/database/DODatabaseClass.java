package migration4o.models.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import migration4o.models.DOReference;

public class DODatabaseClass {
    // Base class attributes
    private final String absoluteName;
    private final String shortName;
    private final String description;
    private final String title;
    private final String superClassAbsoluteName;
    private final DODatabaseField[] fields;
    private final List<DOReference> referenceList;

    // Database-specific attributes
    private int totalObjectCount;
    private int migratedObjectCount;
    private DODatabaseObject[] resolvedObjects;
    private final long[] objectIds;

    // Direct inheritance references
    private DODatabaseClass parentClass;
    private Vector<DODatabaseClass> directSubclasses;
    private Vector<DODatabaseClass> allSubclasses;
    private Vector<DODatabaseClass> inheritanceChain;

    // Reference tracking for ID-type fields
    private int referenceCount = 0;

    public DODatabaseClass(String absoluteName, String shortName, String description, String title,
            String superClassAbsoluteName,
            DODatabaseField[] fields, int totalObjectCount, int migratedObjectCount, long[] objectIds) {
        this.absoluteName = absoluteName;
        this.shortName = shortName;
        this.description = description;
        this.title = title;
        this.superClassAbsoluteName = superClassAbsoluteName;
        this.fields = fields != null ? fields : new DODatabaseField[0];
        this.referenceList = new ArrayList<>();

        this.totalObjectCount = totalObjectCount;
        this.migratedObjectCount = migratedObjectCount;
        this.resolvedObjects = new DODatabaseObject[0];
        this.objectIds = objectIds != null ? objectIds : new long[0];

        // Initialize inheritance collections
        this.directSubclasses = new Vector<DODatabaseClass>();
        this.allSubclasses = new Vector<DODatabaseClass>();
        this.inheritanceChain = new Vector<DODatabaseClass>();
    }

    // Base class getters
    public String getAbsoluteName() {
        return absoluteName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }

    public String getTitle() {
        return title;
    }

    public String getSuperClassAbsoluteName() {
        return superClassAbsoluteName;
    }

    public DODatabaseField[] getFields() {
        return fields;
    }

    public DOReference[] getReferences() {
        return referenceList.toArray(new DOReference[0]);
    }

    public void setReferences(DOReference[] references) {
        referenceList.clear();
        if (references != null) {
            for (DOReference ref : references) {
                if (ref != null) {
                    referenceList.add(ref);
                }
            }
        }
    }

    public void addReference(DOReference reference) {
        if (reference != null) {
            referenceList.add(reference);
        }
    }

    // Database-specific getters and setters
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

    public long[] getObjectIds() {
        return objectIds != null ? objectIds.clone() : new long[0];
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
