
package migration4o.models.schema;

import migration4o.models.DOField;
import migration4o.models.DOClass;

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
