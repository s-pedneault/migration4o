
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

    public DOSchemaField(String source, String destinationName, String type, boolean isExported, boolean skipIfEmpty,
            boolean isCollection, boolean embedContents,
            String childrenType,
            DODatabaseClass databaseClass, DOSchemaClass childrenSchemaClass) {
        this(source, destinationName, type, isExported, skipIfEmpty, isCollection, embedContents,
                childrenType, null, null, null, databaseClass, childrenSchemaClass);
    }

    public DOSchemaField(String source, String destinationName, String type, boolean isExported, boolean skipIfEmpty,
            boolean isCollection, boolean embedContents,
            String childrenType, String description,
            DODatabaseClass databaseClass, DOSchemaClass childrenSchemaClass) {
        this(source, destinationName, type, isExported, skipIfEmpty, isCollection, embedContents,
                childrenType, null, description, null, databaseClass, childrenSchemaClass);
    }

    public DOSchemaField(String source, String destinationName, String type, boolean isExported, boolean skipIfEmpty,
            boolean isCollection, boolean embedContents,
            String childrenType, String title, String description,
            DODatabaseClass databaseClass, DOSchemaClass childrenSchemaClass) {
        this(source, destinationName, type, isExported, skipIfEmpty, isCollection, embedContents,
                childrenType, title, description, null, databaseClass, childrenSchemaClass);
    }

    public DOSchemaField(String source, String destinationName, String type, boolean isExported, boolean skipIfEmpty,
            boolean isCollection, boolean embedContents,
            String childrenType, String title, String description, String pointsTo,
            DODatabaseClass databaseClass, DOSchemaClass childrenSchemaClass) {
        this.source = source;
        this.destinationName = destinationName;
        this.type = type;
        this.isExported = isExported;
        this.skipIfEmpty = skipIfEmpty;
        this.isCollection = isCollection;
        this.embedContents = embedContents;
        this.childrenType = childrenType;
        this.title = title;
        this.description = description;
        this.pointsTo = pointsTo;
        this.databaseClass = databaseClass;
        this.childrenSchemaClass = childrenSchemaClass;
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