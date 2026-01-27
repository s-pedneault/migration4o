package migration4o.models.schema.comparison;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents differences for a single class between two schemas.
 * Tracks fields that exist in only one schema and fields with different
 * properties.
 */
public class ClassDifference {
    private final String className;
    private final DOSchemaClass referenceClass;
    private final DOSchemaClass comparedClass;
    private final List<DOSchemaField> fieldsOnlyInReference = new ArrayList<>();
    private final List<DOSchemaField> fieldsOnlyInCompared = new ArrayList<>();
    private final Map<String, FieldPropertyDifference> fieldsWithDifferences = new HashMap<>();

    public ClassDifference(String className, DOSchemaClass referenceClass, DOSchemaClass comparedClass) {
        this.className = className;
        this.referenceClass = referenceClass;
        this.comparedClass = comparedClass;
    }

    public void addFieldOnlyInReference(DOSchemaField field) {
        fieldsOnlyInReference.add(field);
    }

    public void addFieldOnlyInCompared(DOSchemaField field) {
        fieldsOnlyInCompared.add(field);
    }

    public void addFieldWithDifferences(String fieldName, FieldPropertyDifference diff) {
        fieldsWithDifferences.put(fieldName, diff);
    }

    public boolean hasDifferences() {
        return referenceClass == null || comparedClass == null ||
                !fieldsOnlyInReference.isEmpty() ||
                !fieldsOnlyInCompared.isEmpty() ||
                !fieldsWithDifferences.isEmpty();
    }

    public boolean isOnlyInReference() {
        return referenceClass != null && comparedClass == null;
    }

    public boolean isOnlyInCompared() {
        return referenceClass == null && comparedClass != null;
    }

    public String getClassName() {
        return className;
    }

    public DOSchemaClass getReferenceClass() {
        return referenceClass;
    }

    public DOSchemaClass getComparedClass() {
        return comparedClass;
    }

    public List<DOSchemaField> getFieldsOnlyInReference() {
        return fieldsOnlyInReference;
    }

    public List<DOSchemaField> getFieldsOnlyInCompared() {
        return fieldsOnlyInCompared;
    }

    public Map<String, FieldPropertyDifference> getFieldsWithDifferences() {
        return fieldsWithDifferences;
    }
}
