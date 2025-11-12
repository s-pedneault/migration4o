package dataobjects.api.database;

public interface DODatabaseEncoding {
    String getDescription();

    boolean isUnicodeEnabled();

    boolean isInternStringsEnabled();

    boolean isDotnetSupportEnabled();
}
