
package migration4o.models.schema;

import migration4o.models.database.DODatabaseClass;

public class DOSchemaField {
    private String name;
    private String description;
    private String typeName;
    private DODatabaseClass typeClass;
    private boolean isArray;
    private boolean isPrimitive;
    private String contentTypeName;
    private DODatabaseClass contentTypeClass;
    private final String exportName;

    public DOSchemaField(String name, String description, String typeName, DODatabaseClass typeClass,
            boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DODatabaseClass contentTypeClass, String exportName) {
        this.name = name;
        this.description = description;
        this.typeName = typeName;
        this.typeClass = typeClass;
        this.isPrimitive = isPrimitive;
        this.isArray = isArray;
        this.contentTypeName = contentTypeName;
        this.contentTypeClass = contentTypeClass;
        this.exportName = exportName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public DODatabaseClass getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(DODatabaseClass typeClass) {
        this.typeClass = typeClass;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean array) {
        isArray = array;
    }

    public String getContentTypeName() {
        return contentTypeName;
    }

    public void setContentTypeName(String contentTypeName) {
        this.contentTypeName = contentTypeName;
    }

    public DODatabaseClass getContentTypeClass() {
        return contentTypeClass;
    }

    public void setContentTypeClass(DODatabaseClass contentTypeClass) {
        this.contentTypeClass = contentTypeClass;
    }

    public String getExportName() {
        return exportName;
    }
}
