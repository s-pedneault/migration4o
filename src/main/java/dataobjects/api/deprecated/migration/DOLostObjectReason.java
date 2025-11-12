package dataobjects.api.deprecated.migration;

/**
 * Enumeration of reasons why objects might be lost during migration.
 * Each reason represents a different type of migration issue.
 */
public enum DOLostObjectReason {

    /**
     * Class has no schema mapping at all.
     * Objects of this class cannot be migrated because there's no schema
     * definition.
     */
    NO_SCHEMA_MAPPING("No schema mapping defined for this class"),

    /**
     * Objects are orphaned - not referenced in any collection or inheritance chain.
     * These objects exist in the database but aren't reachable through normal
     * migration paths.
     */
    ORPHANED_OBJECTS("Objects not referenced in collections or inheritance chains"),

    /**
     * Objects are part of an incomplete inheritance hierarchy.
     * Some levels of the inheritance chain have schema mappings, but not all.
     */
    INCOMPLETE_INHERITANCE_CHAIN("Part of incomplete inheritance chain mapping"),

    /**
     * Objects are referenced in unresolved collection fields.
     * The collection field exists but its content type cannot be determined.
     */
    UNRESOLVED_COLLECTION_REFERENCE("Referenced in collections with unresolved content types"),

    /**
     * Objects are stored in intermediate inheritance levels that won't be exported.
     * These objects exist at middle levels of inheritance hierarchies that aren't
     * leaf nodes.
     */
    INTERMEDIATE_INHERITANCE_LEVEL("Stored at intermediate inheritance level, not leaf or root"),

    /**
     * Objects belong to classes that have inheritance conflicts.
     * Multiple inheritance paths or ambiguous relationships prevent proper
     * migration.
     */
    INHERITANCE_CONFLICT("Class has conflicting inheritance relationships"),

    /**
     * Objects are in classes marked as excluded from migration.
     * These classes have been explicitly marked as not suitable for migration.
     */
    MIGRATION_EXCLUDED("Class excluded from migration by configuration");

    private final String description;

    DOLostObjectReason(final String description) {
        this.description = description;
    }

    /**
     * Get a human-readable description of this loss reason.
     * 
     * @return Description of why objects would be lost
     */
    public String getDescription() {
        return description;
    }
}
