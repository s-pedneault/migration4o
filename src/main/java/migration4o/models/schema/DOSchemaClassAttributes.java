package migration4o.models.schema;

public class DOSchemaClassAttributes {

    public String source;
    public String destinationName;
    public String parentClassName;
    public boolean migrate;
    public String schemaNotes;
    public String title;
    public String description;
    public String summary;
    public String pointsTo; // For IDEntite classes: the target class name this points to
    public String pointsToFilter; // e.g. "mCode=E2" — additional field=value filter to disambiguate pointsTo target
    public boolean isStatic;
    public String preview; // Optional: preview generator keyword, e.g. "FILE(path/[ID])"
    public boolean alwaysExportAll; // Bypass export limits and export all objects of this class (use with caution, as this will make demo exports more complete)

}
