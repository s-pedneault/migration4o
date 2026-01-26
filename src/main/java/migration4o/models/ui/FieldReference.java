package migration4o.models.ui;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Model class representing a reference to a field within a schema class.
 * Pairs a schema class with one of its fields.
 */
public class FieldReference {
    public DOSchemaClass schemaClass;
    public DOSchemaField field;

    public FieldReference(DOSchemaClass schemaClass, DOSchemaField field) {
        this.schemaClass = schemaClass;
        this.field = field;
    }
}
