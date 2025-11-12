package dataobjects.api.deprecated.migration;

/**
 * Enumeration of recommendation types.
 */
public enum DORecommendationType {
    SCHEMA_IMPROVEMENT("Schema Improvement", "Improve schema coverage"),
    CUSTOM_RESOLVER("Custom Resolver", "Implement custom migration logic"),
    DATA_CLEANUP("Data Cleanup", "Clean up database before migration"),
    INHERITANCE_MAPPING("Inheritance Mapping", "Improve inheritance mappings"),
    FIELD_MAPPING("Field Mapping", "Add missing field mappings"),
    REFERENCE_RESOLUTION("Reference Resolution", "Resolve broken references"),
    PERFORMANCE_OPTIMIZATION("Performance Optimization", "Optimize migration performance");

    private final String name;
    private final String description;

    DORecommendationType(String name, String description) {
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
