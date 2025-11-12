package dataobjects.impl.database;

import dataobjects.api.database.DODatabaseEncoding;

public class DODatabaseEncodingImpl implements DODatabaseEncoding {
    private final String description;
    private final boolean unicodeEnabled;
    private final boolean internStringsEnabled;
    private final boolean dotnetSupportEnabled;

    public DODatabaseEncodingImpl(String description, boolean unicodeEnabled, boolean internStringsEnabled,
            boolean dotnetSupportEnabled) {
        this.description = description;
        this.unicodeEnabled = unicodeEnabled;
        this.internStringsEnabled = internStringsEnabled;
        this.dotnetSupportEnabled = dotnetSupportEnabled;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isUnicodeEnabled() {
        return unicodeEnabled;
    }

    @Override
    public boolean isInternStringsEnabled() {
        return internStringsEnabled;
    }

    @Override
    public boolean isDotnetSupportEnabled() {
        return dotnetSupportEnabled;
    }

    @Override
    public String toString() {
        return description;
    }
}
