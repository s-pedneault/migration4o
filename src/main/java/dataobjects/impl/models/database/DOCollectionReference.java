package dataobjects.impl.models.database;

import dataobjects.impl.models.database.DOCollectionReference;
import dataobjects.impl.models.DOField;

public class DOCollectionReference {
    private final Long sourceObjectId;
    private final DOField field;
    private final Long[] containedObjectIds;
    private final String resolvedContentType;
    private final int size;

    public DOCollectionReference(Long sourceObjectId, DOField field,
            Long[] containedObjectIds, String resolvedContentType, int size) {
        this.sourceObjectId = sourceObjectId;
        this.field = field;
        this.containedObjectIds = containedObjectIds != null ? containedObjectIds : new Long[0];
        this.resolvedContentType = resolvedContentType;
        this.size = size;
    }

    public Long getSourceObjectId() {
        return sourceObjectId;
    }

    public DOField getField() {
        return field;
    }

    public Long[] getContainedObjectIds() {
        return containedObjectIds;
    }

    public String getResolvedContentType() {
        return resolvedContentType;
    }

    public int getSize() {
        return size;
    }
}