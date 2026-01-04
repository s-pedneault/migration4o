package migration4o.models;

import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseField;

public class DOReference {
    private final DODatabaseClass referencedClass;
    private final DODatabaseField referencedField;

    public DOReference(DODatabaseClass referencedClass, DODatabaseField referencedField) {
        this.referencedClass = referencedClass;
        this.referencedField = referencedField;
    }

    public DODatabaseClass getReferencedClass() {
        return referencedClass;
    }

    public DODatabaseField getReferencedField() {
        return referencedField;
    }
}
