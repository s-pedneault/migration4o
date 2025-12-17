package dataobjects.impl.models.database;

import dataobjects.impl.models.database.DOObjectReference;
import dataobjects.impl.models.DOField;

public class DOObjectReference {
    private final Long sourceObjectId;
    private final Long targetObjectId;
    private final DOField field;
    private final DOReferenceType referenceType;

    public DOObjectReference(Long sourceObjectId, Long targetObjectId,
            DOField field, DOReferenceType referenceType) {
        this.sourceObjectId = sourceObjectId;
        this.targetObjectId = targetObjectId;
        this.field = field;
        this.referenceType = referenceType;
    }

    public Long getSourceObjectId() {
        return sourceObjectId;
    }

    public Long getTargetObjectId() {
        return targetObjectId;
    }

    public DOField getField() {
        return field;
    }

    public DOReferenceType getReferenceType() {
        return referenceType;
    }
}