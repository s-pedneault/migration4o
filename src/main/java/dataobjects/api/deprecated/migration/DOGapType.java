package dataobjects.api.deprecated.migration;

/**
 * Enumeration of schema gap types.
 */
public enum DOGapType {
    UNRESOLVED_FIELD("Unresolved Field", "Field content type cannot be resolved"),
    MISSING_SCHEMA_CLASS("Missing Schema Class", "Database class has no schema mapping"),
    TYPE_MISMATCH("Type Mismatch", "Schema and database types don't match"),
    BROKEN_REFERENCE("Broken Reference", "Object reference cannot be resolved"),
    INHERITANCE_GAP("Inheritance Gap", "Missing inheritance mapping"),
    COLLECTION_CONTENT_UNKNOWN("Collection Content Unknown", "Collection content type is unknown"),
    POLYMORPHIC_CONFLICT("Polymorphic Conflict", "Polymorphic storage creates conflicts");

    private final String name;
    private final String description;

    DOGapType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
