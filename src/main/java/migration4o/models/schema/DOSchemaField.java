
package migration4o.models.schema;

import migration4o.models.database.DODatabaseClass;

public class DOSchemaField {
    public String source;
    public String destinationName;
    public String type;
    public boolean isExported;
    public boolean skipIfEmpty;
    public boolean isCollection;
    public boolean embedContents;
    public String childrenType;
    public String title;
    public String description;
    public String pointsTo;

    public DODatabaseClass databaseClass;
    public DOSchemaClass childrenSchemaClass;

    public DOSchemaField() {
    }
}