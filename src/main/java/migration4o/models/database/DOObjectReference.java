package migration4o.models.database;

import migration4o.models.database.DOObjectReference;

public class DOObjectReference {
    private final Long sourceObjectId;
    private final Long targetObjectId;
    private final DODatabaseField field;
    private final DOReferenceType referenceType;

    public DOObjectReference(Long sourceObjectId, Long targetObjectId,
            DODatabaseField field, DOReferenceType referenceType) {
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

    public DODatabaseField getField() {
        return field;
    }

    public DOReferenceType getReferenceType() {
        return referenceType;
    }
}