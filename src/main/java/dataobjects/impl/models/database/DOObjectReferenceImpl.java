package dataobjects.impl.models.database;

import dataobjects.api.models.database.DOObjectReference;
import dataobjects.api.database.DOReferenceType;
import dataobjects.api.models.DOField;

public class DOObjectReferenceImpl implements DOObjectReference {
    private final Long sourceObjectId;
    private final Long targetObjectId;
    private final DOField field;
    private final DOReferenceType referenceType;

    public DOObjectReferenceImpl(Long sourceObjectId, Long targetObjectId,
            DOField field, DOReferenceType referenceType) {
        this.sourceObjectId = sourceObjectId;
        this.targetObjectId = targetObjectId;
        this.field = field;
        this.referenceType = referenceType;
    }

    @Override
    public Long getSourceObjectId() {
        return sourceObjectId;
    }

    @Override
    public Long getTargetObjectId() {
        return targetObjectId;
    }

    @Override
    public DOField getField() {
        return field;
    }

    @Override
    public DOReferenceType getReferenceType() {
        return referenceType;
    }
}