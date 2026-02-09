package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Anomaly indicating that a class with only one reference is listed in a
 * module,
 * when it should be embedded instead of exported separately.
 */
public class DOSchemaShouldNotBeExportedAnomaly extends DOSchemaEmbeddingAnomaly {

    public DOSchemaShouldNotBeExportedAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String explanation) {
        super(schemaClass, schemaField, explanation);
    }
}
