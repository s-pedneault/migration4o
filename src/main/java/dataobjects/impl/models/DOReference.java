package dataobjects.impl.models;

import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOReference;

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
