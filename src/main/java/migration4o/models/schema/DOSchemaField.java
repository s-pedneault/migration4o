
package migration4o.models.schema;

import migration4o.models.database.DODatabaseClass;
import migration4o.util.TypeUtil;

public class DOSchemaField {
    private final String source;
    private final String destinationName;
    private final String type;
    private final boolean isExported;
    private final boolean skipIfEmpty;
    private final boolean isCollection;
    private final boolean embedContents;
    private final String childrenClassName;

    private DODatabaseClass databaseClass;
    private DOSchemaClass childrenSchemaClass;

    public DOSchemaField(String source, String destinationName, String type, boolean isExported, boolean skipIfEmpty,
            boolean isCollection, boolean embedContents,
            String childrenClassName, DODatabaseClass databaseClass, DOSchemaClass childrenSchemaClass) {
        this.source = source;
        this.destinationName = destinationName;
        this.type = type;
        this.isExported = isExported;
        this.skipIfEmpty = skipIfEmpty;
        this.isCollection = isCollection;
        this.embedContents = embedContents;
        this.childrenClassName = childrenClassName;
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

    public String getChildrenClassName() {
        return childrenClassName;
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

    public boolean isPrimitive() {
        return TypeUtil.isPrimitiveType(source);
    }
}