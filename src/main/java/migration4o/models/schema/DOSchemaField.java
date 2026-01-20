
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

    public String getSource() {
        return source;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getType() {
        return type;
    }

    public boolean isExported() {
        return isExported;
    }

    public boolean isSkipIfEmpty() {
        return skipIfEmpty;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public boolean isEmbedContents() {
        return embedContents;
    }

    public String getChildrenType() {
        return childrenType;
    }

    public String getPointsTo() {
        return pointsTo;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public DODatabaseClass getDatabaseClass() {
        return databaseClass;
    }

    public void setDatabaseClass(DODatabaseClass databaseClass) {
        this.databaseClass = databaseClass;
    }

    public DOSchemaClass getChildrenSchemaClass() {
        return childrenSchemaClass;
    }

    public void setChildrenSchemaClass(DOSchemaClass childrenSchemaClass) {
        this.childrenSchemaClass = childrenSchemaClass;
    }
}