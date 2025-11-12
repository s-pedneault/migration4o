package dataobjects.api.deprecated.migration;

/**
 * Enumeration of coverage status levels.
 */
public enum DOCoverageStatus {
    FULL_COVERAGE("Full Coverage", "All fields are properly mapped"),
    PARTIAL_COVERAGE("Partial Coverage", "Some fields are unmapped"),
    NO_COVERAGE("No Coverage", "No schema mapping exists"),
    INHERITANCE_COVERAGE("Inheritance Coverage", "Covered through inheritance"),
    CUSTOM_RESOLVER_NEEDED("Custom Resolver Needed", "Requires custom migration logic");

    private final String name;
    private final String description;

    DOCoverageStatus(String name, String description) {
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
