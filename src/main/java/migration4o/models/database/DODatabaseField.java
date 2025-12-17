package migration4o.models.database;

import migration4o.models.DOClass;
import migration4o.models.DOField;

public class DODatabaseField extends DOField {
    public DODatabaseField(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass) {
        super(name, description, typeName, typeClass, isPrimitive, isArray, contentTypeName, contentTypeClass);
    }
}
