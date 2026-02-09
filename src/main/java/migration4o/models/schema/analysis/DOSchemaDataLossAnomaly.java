package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * CRITICAL schema anomaly for fields that will result in data loss during
 * export.
 * Generated when a collection field has embedContents=false but childrenType is
 * not an IDEntite,
 * meaning the collection items will neither be exported as IDs nor as embedded
 * objects.
 */
public class DOSchemaDataLossAnomaly extends DOSchemaAnomaly {
    public final String childrenTypeName;

    public DOSchemaDataLossAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String childrenTypeName, String explanation) {
        super(schemaClass, schemaField, explanation);
        this.childrenTypeName = childrenTypeName;
    }
}
