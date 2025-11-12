package dataobjects.impl.models;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.DOReference;

public class DOReferenceImpl implements DOReference {
    private final DOClass referencedClass;
    private final DOField referencedField;

    public DOReferenceImpl(DOClass referencedClass, DOField referencedField) {
        this.referencedClass = referencedClass;
        this.referencedField = referencedField;
    }

    @Override
    public DOClass getReferencedClass() {
        return referencedClass;
    }

    @Override
    public DOField getReferencedField() {
        return referencedField;
    }
}
