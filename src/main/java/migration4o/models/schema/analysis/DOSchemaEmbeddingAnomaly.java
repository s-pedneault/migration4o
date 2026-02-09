package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Schema anomaly for incorrect embedContents configuration.
 * Generated when DOEmbeddingDetector finds embedContents mismatches based on
 * reference counts.
 */
public class DOSchemaEmbeddingAnomaly extends DOSchemaAnomaly {

    public DOSchemaEmbeddingAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField, String explanation) {
        super(schemaClass, schemaField, explanation);
    }
}
