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
    public boolean isStatic;

}
