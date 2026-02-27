package migration4o.database;

import migration4o.database.DODatabaseEncoding;

public class DODatabaseEncoding {

    public static DODatabaseEncoding[] encodings = new DODatabaseEncoding[] { new DODatabaseEncoding("UTF-8 (default)", true, true, true), new DODatabaseEncoding("Latin-1 (legacy)", false, true, true), new DODatabaseEncoding("UTF-8 no-intern", true, false, true), new DODatabaseEncoding("Latin-1 no-intern", false, false, true), new DODatabaseEncoding("UTF-8 no-dotnet", true, true, false), new DODatabaseEncoding("Latin-1 no-dotnet", false, true, false) };

    public final String description;
    public final boolean unicodeEnabled;
    public final boolean internStringsEnabled;
    public final boolean dotnetSupportEnabled;

    private DODatabaseEncoding(String description, boolean unicodeEnabled, boolean internStringsEnabled, boolean dotnetSupportEnabled) {
        this.description = description;
        this.unicodeEnabled = unicodeEnabled;
        this.internStringsEnabled = internStringsEnabled;
        this.dotnetSupportEnabled = dotnetSupportEnabled;
    }

    public String toString() {
        return description;
    }
}
