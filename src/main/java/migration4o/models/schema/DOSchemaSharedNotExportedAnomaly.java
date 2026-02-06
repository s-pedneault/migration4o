package migration4o.models.schema;

/**
 * Anomaly indicating that a class with multiple references (shared object)
 * is not listed in any module and therefore won't be properly exported.
 */
public class DOSchemaSharedNotExportedAnomaly extends DOSchemaEmbeddingAnomaly {

    public DOSchemaSharedNotExportedAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            String explanation) {
        super(schemaClass, schemaField, explanation);
    }
}
