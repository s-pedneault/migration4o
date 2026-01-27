package migration4o.models.schema;

/**
 * Model class representing a reference to a field within a schema class.
 * Pairs a schema class with one of its fields.
 */
public class DOSchemaFieldReference {
    public DOSchemaClass schemaClass;
    public DOSchemaField field;

    public DOSchemaFieldReference(DOSchemaClass schemaClass, DOSchemaField field) {
        this.schemaClass = schemaClass;
        this.field = field;
    }
}
