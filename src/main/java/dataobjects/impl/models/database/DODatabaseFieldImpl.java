package dataobjects.impl.models.database;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.database.DODatabaseField;
import dataobjects.impl.models.DOFieldImpl;

public class DODatabaseFieldImpl extends DOFieldImpl implements DODatabaseField {
    public DODatabaseFieldImpl(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass) {
        super(name, description, typeName, typeClass, isPrimitive, isArray, contentTypeName, contentTypeClass);
    }
}
