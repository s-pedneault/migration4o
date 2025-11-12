
package dataobjects.impl.models;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;

public class DOFieldImpl implements DOField {

    private String name;
    private String description;
    private String typeName;
    private DOClass typeClass;
    private boolean isArray;
    private boolean isPrimitive;
    private String contentTypeName;
    private DOClass contentTypeClass;

    public DOFieldImpl() {
        this.isArray = false;
    }

    public DOFieldImpl(String name, String typeName) {
        this();
        this.name = name;
        this.typeName = typeName;
    }

    public DOFieldImpl(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
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

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public DOClass getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(DOClass typeClass) {
        this.typeClass = typeClass;
    }

    @Override
    public boolean isPrimitive() {
        return isPrimitive;
    }

    @Override
    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean array) {
        isArray = array;
    }

    @Override
    public String getContentTypeName() {
        return contentTypeName;
    }

    public void setContentTypeName(String contentTypeName) {
        this.contentTypeName = contentTypeName;
    }

    @Override
    public DOClass getContentTypeClass() {
        return contentTypeClass;
    }

    public void setContentTypeClass(DOClass contentTypeClass) {
        this.contentTypeClass = contentTypeClass;
    }
}
