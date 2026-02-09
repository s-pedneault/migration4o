package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Anomaly indicating that a field pointing to an object with only one reference
 * has embedContents=false, when it should be embedded for efficiency.
 */
public class DOSchemaShouldBeEmbeddedAnomaly extends DOSchemaEmbeddingAnomaly {

    public DOSchemaShouldBeEmbeddedAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String explanation) {
        super(schemaClass, schemaField, explanation);
    }
}
