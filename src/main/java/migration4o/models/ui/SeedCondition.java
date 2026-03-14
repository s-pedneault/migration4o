package migration4o.models.ui;

/**
 * A single condition within a seed query: field + operator + value.
 */
public class SeedCondition {

    public enum Operator {
        EQUALS, CONTAINS
    }

    private String fieldPath;
    private Operator operator;
    private String value;

    public SeedCondition() {
    }

    public SeedCondition(String fieldPath, Operator operator, String value) {
        this.fieldPath = fieldPath;
        this.operator = operator;
        this.value = value;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Evaluates this condition against a field value.
     */
    public boolean matches(Object fieldValue) {
        if (fieldValue == null) {
            return false;
        }
        String str = String.valueOf(fieldValue);
        switch (operator) {
        case EQUALS:
            return str.equalsIgnoreCase(value);
        case CONTAINS:
            return str.toLowerCase().contains(value.toLowerCase());
        default:
            return false;
        }
    }

    @Override
    public String toString() {
        return fieldPath + " " + operator.name() + " '" + value + "'";
    }
}
