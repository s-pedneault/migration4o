
package dataobjects.impl.models.schema;

import dataobjects.api.models.schema.DOSchemaField;
import dataobjects.impl.models.DOFieldImpl;
import dataobjects.api.models.DOClass;

public class DOSchemaFieldImpl extends DOFieldImpl implements DOSchemaField {
    private final String exportName;

    public DOSchemaFieldImpl(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass, String exportName) {
        super(name, description, typeName, typeClass, isPrimitive, isArray, contentTypeName, contentTypeClass);
        this.exportName = exportName;
    }

    @Override
    public String getExportName() {
        return exportName;
    }
}
