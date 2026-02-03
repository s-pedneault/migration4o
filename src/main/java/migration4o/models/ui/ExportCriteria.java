package migration4o.models.ui;

/**
 * Represents a single export filter criteria based on field values.
 * For example: "export only objects where mIDDossPrevOld == -1"
 */
public class ExportCriteria {

    public enum Operator {
        EQUALS("=="),
        NOT_EQUALS("!="),
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        IS_NULL("is null"),
        IS_NOT_NULL("is not null");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public static Operator fromSymbol(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            return EQUALS; // Default
        }
    }

    private final String fieldName;
    private final Operator operator;
    private final String value; // String representation, will be converted based on field type

    public ExportCriteria(String fieldName, Operator operator, String value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Operator getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    /**
     * Evaluates if the given field value matches this criteria.
     */
    public boolean matches(Object fieldValue) {
        if (operator == Operator.IS_NULL) {
            return fieldValue == null;
        }

        if (operator == Operator.IS_NOT_NULL) {
            return fieldValue != null;
        }

        if (fieldValue == null) {
            return false; // Can't compare null with non-null operators
        }

        // Convert field value to string for comparison
        String fieldValueStr = fieldValue.toString();

        // For numeric comparisons
        if (fieldValue instanceof Number && value != null) {
            try {
                double fieldNum = ((Number) fieldValue).doubleValue();
                double criteriaNum = Double.parseDouble(value);

                switch (operator) {
                    case EQUALS:
                        return Math.abs(fieldNum - criteriaNum) < 0.0001;
                    case NOT_EQUALS:
                        return Math.abs(fieldNum - criteriaNum) >= 0.0001;
                    case GREATER_THAN:
                        return fieldNum > criteriaNum;
                    case LESS_THAN:
                        return fieldNum < criteriaNum;
                    case GREATER_OR_EQUAL:
                        return fieldNum >= criteriaNum;
                    case LESS_OR_EQUAL:
                        return fieldNum <= criteriaNum;
                    case IS_NULL:
                    case IS_NOT_NULL:
                        // These cases are handled above, should never reach here
                        return false;
                }
            } catch (NumberFormatException e) {
                // Fall through to string comparison
            }
        }

        // String comparison
        switch (operator) {
            case EQUALS:
                return fieldValueStr.equals(value);
            case NOT_EQUALS:
                return !fieldValueStr.equals(value);
            default:
                return false; // Can't do > < on strings
        }
    }

    @Override
    public String toString() {
        if (operator == Operator.IS_NULL || operator == Operator.IS_NOT_NULL) {
            return fieldName + " " + operator.getSymbol();
        }
        return fieldName + " " + operator.getSymbol() + " " + value;
    }
}
