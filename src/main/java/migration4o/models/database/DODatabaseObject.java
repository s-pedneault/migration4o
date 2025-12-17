package migration4o.models.database;

import migration4o.models.DOClass;

public class DODatabaseObject {
    private final Long objectId;
    private final DOClass mostSpecificClass;
    private final DOClass[] allClasses;
    private final DOObjectReference[] references;
    private final DOCollectionReference[] collections;
    private boolean reachable;

    public DODatabaseObject(Long objectId, DOClass mostSpecificClass, DOClass[] allClasses,
            DOObjectReference[] references, DOCollectionReference[] collections) {
        this.objectId = objectId;
        this.mostSpecificClass = mostSpecificClass;
        this.allClasses = allClasses != null ? allClasses : new DOClass[0];
        this.references = references != null ? references : new DOObjectReference[0];
        this.collections = collections != null ? collections : new DOCollectionReference[0];
        this.reachable = false;
    }

    public Long getObjectId() {
        return objectId;
    }

    public DOClass getMostSpecificClass() {
        return mostSpecificClass;
    }

    public DOClass[] getAllClasses() {
        return allClasses;
    }

    public DOObjectReference[] getReferences() {
        return references;
    }

    public DOCollectionReference[] getCollections() {
        return collections;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }
}