package migration4o.models.schema;

/**
 * Schema anomaly for dynamically added references.
 * Generated when DOReferenceDetector adds a missing reference to the schema.
 */
public class DOSchemaReferenceAnomaly extends DOSchemaAnomaly {
    public final DOSchemaReference schemaReference;

    public DOSchemaReferenceAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField,
            DOSchemaReference schemaReference, String explanation) {
        super(schemaClass, schemaField, explanation);
        this.schemaReference = schemaReference;
    }
}
