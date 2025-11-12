package dataobjects.impl.models.database;

import dataobjects.api.models.database.DOCollectionReference;
import dataobjects.api.models.DOField;

public class DOCollectionReferenceImpl implements DOCollectionReference {
    private final Long sourceObjectId;
    private final DOField field;
    private final Long[] containedObjectIds;
    private final String resolvedContentType;
    private final int size;

    public DOCollectionReferenceImpl(Long sourceObjectId, DOField field,
            Long[] containedObjectIds, String resolvedContentType, int size) {
        this.sourceObjectId = sourceObjectId;
        this.field = field;
        this.containedObjectIds = containedObjectIds != null ? containedObjectIds : new Long[0];
        this.resolvedContentType = resolvedContentType;
        this.size = size;
    }

    @Override
    public Long getSourceObjectId() {
        return sourceObjectId;
    }

    @Override
    public DOField getField() {
        return field;
    }

    @Override
    public Long[] getContainedObjectIds() {
        return containedObjectIds;
    }

    @Override
    public String getResolvedContentType() {
        return resolvedContentType;
    }

    @Override
    public int getSize() {
        return size;
    }
}