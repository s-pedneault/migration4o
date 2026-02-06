package migration4o.models.schema;

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
