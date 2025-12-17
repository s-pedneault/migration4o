package migration4o.database;

import migration4o.database.DODatabaseEncoding;

public class DODatabaseEncoding {
    private final String description;
    private final boolean unicodeEnabled;
    private final boolean internStringsEnabled;
    private final boolean dotnetSupportEnabled;

    public DODatabaseEncoding(String description, boolean unicodeEnabled, boolean internStringsEnabled,
            boolean dotnetSupportEnabled) {
        this.description = description;
        this.unicodeEnabled = unicodeEnabled;
        this.internStringsEnabled = internStringsEnabled;
        this.dotnetSupportEnabled = dotnetSupportEnabled;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUnicodeEnabled() {
        return unicodeEnabled;
    }

    public boolean isInternStringsEnabled() {
        return internStringsEnabled;
    }

    public boolean isDotnetSupportEnabled() {
        return dotnetSupportEnabled;
    }

    public String toString() {
        return description;
    }
}
