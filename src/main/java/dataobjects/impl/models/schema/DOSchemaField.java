
package dataobjects.impl.models.schema;

import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOClass;

public class DOSchemaField extends DOField {
    private final String exportName;

    public DOSchemaField(String name, String description, String typeName, DOClass typeClass, boolean isPrimitive,
            boolean isArray,
            String contentTypeName, DOClass contentTypeClass, String exportName) {
        super(name, description, typeName, typeClass, isPrimitive, isArray, contentTypeName, contentTypeClass);
        this.exportName = exportName;
    }

    public String getExportName() {
        return exportName;
    }
}
