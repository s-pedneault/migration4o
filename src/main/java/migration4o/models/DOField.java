
package migration4o.models;

import migration4o.models.DOClass;
import migration4o.models.DOField;

public class DOField {

    private String name;
    private String description;
    private String typeName;
    private DOClass typeClass;
    private boolean isArray;
    private boolean isPrimitive;
    private String contentTypeName;
    private DOClass contentTypeClass;

    public DOField() {
        this.isArray = false;
    }

    public DOField(String name, String typeName) {
        this();
        this.name = name;
        this.typeName = typeName;
    }

    public DOField(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass) {
        this.name = name;
        this.description = description;
        this.typeName = typeName;
        this.typeClass = typeClass;
        this.isPrimitive = isPrimitive;
        this.isArray = isArray;
        this.contentTypeName = contentTypeName;
        this.contentTypeClass = contentTypeClass;
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

    public DOClass getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(DOClass typeClass) {
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

    public DOClass getContentTypeClass() {
        return contentTypeClass;
    }

    public void setContentTypeClass(DOClass contentTypeClass) {
        this.contentTypeClass = contentTypeClass;
    }
}
