package migration4o.models;

public class DOReference {
    private final DOClass referencedClass;
    private final DOField referencedField;

    public DOReference(DOClass referencedClass, DOField referencedField) {
        this.referencedClass = referencedClass;
        this.referencedField = referencedField;
    }

    public DOClass getReferencedClass() {
        return referencedClass;
    }

    public DOField getReferencedField() {
        return referencedField;
    }
}
