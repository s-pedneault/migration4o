package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Schema anomaly for fields whose type class is not found in the reference
 * schema.
 * Generated when a field references a class that doesn't exist in the schema.
 */
public class DOSchemaMissingFieldClass extends DOSchemaAnomaly {
    public final String missingClassName;

    public DOSchemaMissingFieldClass(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String missingClassName, String explanation) {
        super(schemaClass, schemaField, explanation);
        this.missingClassName = missingClassName;
    }
}
