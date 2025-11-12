package dataobjects.api.deprecated.migration;

/**
 * Enumeration of export strategy types.
 */
public enum DOStrategyType {
    DIRECT_EXPORT("Direct Export", "Export objects directly from their own class"),
    INHERITANCE_EXPORT("Inheritance Export", "Export objects from ancestor class"),
    POLYMORPHIC_EXPORT("Polymorphic Export", "Handle polymorphic storage specially"),
    CUSTOM_RESOLVER("Custom Resolver", "Requires custom migration logic"),
    SKIP_CLASS("Skip Class", "Skip this class during migration");

    private final String name;
    private final String description;

    DOStrategyType(String name, String description) {
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
