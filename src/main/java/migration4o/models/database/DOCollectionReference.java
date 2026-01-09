package migration4o.models.database;

import migration4o.models.database.DOCollectionReference;

public class DOCollectionReference {
    private final Long sourceObjectId;
    private final DODatabaseField field;
    private final Long[] containedObjectIds;
    private final String resolvedContentType;
    private final int size;

    public DOCollectionReference(Long sourceObjectId, DODatabaseField field,
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

    public DODatabaseField getField() {
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