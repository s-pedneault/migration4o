package migration4o.models.schema;

/**
 * Represents a reference element in the schema XML.
 * Example:
 * <reference class="gest.vehicule.Vehicule" field="mVectCompartiment"/>
 */
public class DOSchemaReference {
    private final String className;
    private final String fieldName;

    public DOSchemaReference(String className, String fieldName) {
        this.className = className;
        this.fieldName = fieldName;
    }

    public String getClassName() {
        return className;
    }

    public String getFieldName() {
        return fieldName;
    }
}
