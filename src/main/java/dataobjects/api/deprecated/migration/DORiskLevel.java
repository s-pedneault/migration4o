package dataobjects.api.deprecated.migration;

/**
 * Enumeration of risk levels for migration.
 */
public enum DORiskLevel {
    LOW("Low Risk", "Migration can proceed with confidence"),
    MEDIUM("Medium Risk", "Migration should proceed with caution"),
    HIGH("High Risk", "Migration has significant risks"),
    CRITICAL("Critical Risk", "Migration should not proceed without resolution");

    private final String name;
    private final String description;

    DORiskLevel(String name, String description) {
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
