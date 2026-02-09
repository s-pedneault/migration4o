package migration4o.models.schema.analysis;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Base class for schema anomalies detected during schema loading and
 * validation.
 * Lean data class with no accessor methods.
 */
public abstract class DOSchemaAnomaly {
    public final DOSchemaClass schemaClass;
    public final DOSchemaField schemaField;
    public final String explanation;

    public DOSchemaAnomaly(DOSchemaClass schemaClass, DOSchemaField schemaField, String explanation) {
        this.schemaClass = schemaClass;
        this.schemaField = schemaField;
        this.explanation = explanation;
    }
}
