package dataobjects.impl.models.database;

import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;

public class DODatabaseField extends DOField {
    public DODatabaseField(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass) {
        super(name, description, typeName, typeClass, isPrimitive, isArray, contentTypeName, contentTypeClass);
    }
}
