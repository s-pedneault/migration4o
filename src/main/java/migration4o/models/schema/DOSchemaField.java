
package migration4o.models.schema;

public class DOSchemaField {
    public String source;
    public String destinationName;
    public String type;
    public boolean isExported;
    public String skipWhen; // Comma-separated skip conditions (NULL,ZERO,MINUS_ONE,etc.)
    public boolean isCollection;
    public boolean embedContents;
    public String childrenType;
    public String title;
    public String description;
    public String pointsTo;

    public DOSchemaClass childrenSchemaClass;

    public DOSchemaField() {
    }
}