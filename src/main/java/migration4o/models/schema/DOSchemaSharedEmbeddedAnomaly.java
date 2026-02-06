package migration4o.models.schema;

/**
 * Anomaly indicating that a field pointing to a shared object (multiple
 * references) has embedContents=true, which would cause duplication.
 */
public class DOSchemaSharedEmbeddedAnomaly extends DOSchemaEmbeddingAnomaly {

    public DOSchemaSharedEmbeddedAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String explanation) {
        super(schemaClass, schemaField, explanation);
    }
}
