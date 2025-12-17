package migration4o.models;

import migration4o.models.DOClass;
import migration4o.models.DOField;
import migration4o.models.DOReference;

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
