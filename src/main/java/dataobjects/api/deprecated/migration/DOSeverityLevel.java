package dataobjects.api.deprecated.migration;

/**
 * Enumeration of severity levels for schema gaps.
 */
public enum DOSeverityLevel {
    LOW("Low", "Minor issue, migration can proceed", 1),
    MEDIUM("Medium", "Moderate issue, attention required", 2),
    HIGH("High", "Serious issue, should be resolved", 3),
    CRITICAL("Critical", "Critical issue, migration should not proceed", 4);

    private final String name;
    private final String description;
    private final int priority;

    DOSeverityLevel(String name, String description, int priority) {
        this.name = name;
        this.description = description;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }
}
