package migration4o.models.database;

public class DODatabaseObject {
    private final Long objectId;
    private final DODatabaseClass mostSpecificClass;
    private final DODatabaseClass[] allClasses;
    private final DOObjectReference[] references;
    private final DOCollectionReference[] collections;
    private boolean reachable;

    public DODatabaseObject(Long objectId, DODatabaseClass mostSpecificClass, DODatabaseClass[] allClasses,
            DOObjectReference[] references, DOCollectionReference[] collections) {
        this.objectId = objectId;
        this.mostSpecificClass = mostSpecificClass;
        this.allClasses = allClasses != null ? allClasses : new DODatabaseClass[0];
        this.references = references != null ? references : new DOObjectReference[0];
        this.collections = collections != null ? collections : new DOCollectionReference[0];
        this.reachable = false;
    }

    public Long getObjectId() {
        return objectId;
    }

    public DODatabaseClass getMostSpecificClass() {
        return mostSpecificClass;
    }

    public DODatabaseClass[] getAllClasses() {
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