package migration4o.migration.monitoring;

/**
 * Represents a single reference to an exported object.
 * Pure data class that records WHERE an object was referenced from.
 */
public class ObjectReference {
    public enum ReferenceType {
        MODULE_EXPORT, // Exported as top-level module class
        EMBEDDED_FIELD // Embedded within a parent object's field
    }

    public final long objectId;
    public final String className;
    public final ReferenceType type;

    // For EMBEDDED_FIELD references:
    public final Long parentObjectId;
    public final String sourceContainingClass;
    public final String sourceFieldName;

    /**
     * Constructor for embedded field reference.
     */
    public ObjectReference(long objectId, String className, long parentObjectId,
            String sourceContainingClass, String sourceFieldName) {
        this.objectId = objectId;
        this.className = className;
        this.type = ReferenceType.EMBEDDED_FIELD;
        this.parentObjectId = parentObjectId;
        this.sourceContainingClass = sourceContainingClass;
        this.sourceFieldName = sourceFieldName;
    }

    /**
     * Constructor for module export reference.
     */
    public ObjectReference(long objectId, String className) {
        this.objectId = objectId;
        this.className = className;
        this.type = ReferenceType.MODULE_EXPORT;
        this.parentObjectId = null;
        this.sourceContainingClass = null;
        this.sourceFieldName = null;
    }

    public boolean isModuleExport() {
        return type == ReferenceType.MODULE_EXPORT;
    }

    public boolean isEmbeddedField() {
        return type == ReferenceType.EMBEDDED_FIELD;
    }
}
