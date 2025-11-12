package dataobjects.impl.models.database;

import dataobjects.api.models.database.*;
import dataobjects.api.models.DOClass;

public class DODatabaseObjectImpl implements DODatabaseObject {
    private final Long objectId;
    private final DOClass mostSpecificClass;
    private final DOClass[] allClasses;
    private final DOObjectReference[] references;
    private final DOCollectionReference[] collections;
    private boolean reachable;

    public DODatabaseObjectImpl(Long objectId, DOClass mostSpecificClass, DOClass[] allClasses,
            DOObjectReference[] references, DOCollectionReference[] collections) {
        this.objectId = objectId;
        this.mostSpecificClass = mostSpecificClass;
        this.allClasses = allClasses != null ? allClasses : new DOClass[0];
        this.references = references != null ? references : new DOObjectReference[0];
        this.collections = collections != null ? collections : new DOCollectionReference[0];
        this.reachable = false;
    }

    @Override
    public Long getObjectId() {
        return objectId;
    }

    @Override
    public DOClass getMostSpecificClass() {
        return mostSpecificClass;
    }

    @Override
    public DOClass[] getAllClasses() {
        return allClasses;
    }

    @Override
    public DOObjectReference[] getReferences() {
        return references;
    }

    @Override
    public DOCollectionReference[] getCollections() {
        return collections;
    }

    @Override
    public boolean isReachable() {
        return reachable;
    }

    @Override
    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }
}