package dataobjects.api.migration.generic.v2;

import dataobjects.api.migration.generic.ExportColumn;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A formatted value ready for export.
 * Encapsulates the original raw value, its type, and pre-formatted string
 * representation.
 * This moves all value formatting logic out of format handlers and into the
 * engine.
 */
public class FormattedValue {
    private final Object rawValue;
    private final ExportColumn column;
    private final ValueType type;
    private final String stringValue;
    private final boolean isEmpty;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Create a formatted value from a raw value and its column.
     */
    public FormattedValue(Object rawValue, ExportColumn column) {
        this.rawValue = rawValue;
        this.column = column;
        this.isEmpty = determineIfEmpty(rawValue, column);
        this.type = determineType(rawValue);
        this.stringValue = formatToString(rawValue, type);
    }

    /**
     * Get the original raw value.
     */
    public Object getRawValue() {
        return rawValue;
    }

    /**
     * Get the export column this value belongs to.
     */
    public ExportColumn getColumn() {
        return column;
    }

    /**
     * Get the column name for this value.
     */
    public String getColumnName() {
        return column.columnName;
    }

    /**
     * Get the field name for this value.
     */
    public String getFieldName() {
        return column.field != null ? column.field.getName() : column.columnName;
    }

    /**
     * Get the type of this value.
     */
    public ValueType getType() {
        return type;
    }

    /**
     * Get the formatted string representation.
     */
    public String getStringValue() {
        return stringValue;
    }

    /**
     * Check if this value is considered empty and should be skipped.
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    /**
     * Check if this represents an ID field.
     */
    public boolean isIDField() {
        if (column.isFlattened) {
            return false; // Flattened fields are not ID fields
        }
        String typeName = column.field != null ? column.field.getTypeName() : null;
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Get the value as a boolean, handling French boolean strings.
     */
    public Boolean getBooleanValue() {
        if (rawValue instanceof Boolean) {
            return (Boolean) rawValue;
        }
        if (rawValue instanceof String) {
            String strValue = (String) rawValue;
            if ("VRAI".equalsIgnoreCase(strValue) || "true".equalsIgnoreCase(strValue)) {
                return true;
            }
            if ("FAUX".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                return false;
            }
        }
        return null;
    }

    /**
     * Get the value as a number.
     */
    public Number getNumberValue() {
        if (rawValue instanceof Number) {
            return (Number) rawValue;
        }
        return null;
    }

    /**
     * Get the value as a date.
     */
    public Date getDateValue() {
        if (rawValue instanceof Date) {
            return (Date) rawValue;
        }
        return null;
    }

    /**
     * Determine if a value should be considered empty.
     */
    private boolean determineIfEmpty(Object value, ExportColumn column) {
        if (value == null) {
            return true;
        }

        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return true;
        }

        // For ID fields, -1 typically means "no reference"
        if (value instanceof Number && isIDTypeField(column) && ((Number) value).intValue() == -1) {
            return true;
        }

        return false;
    }

    /**
     * Determine the type of a value.
     */
    private ValueType determineType(Object value) {
        if (value == null) {
            return ValueType.EMPTY;
        }

        if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        }

        if (value instanceof Integer || value instanceof Long) {
            return ValueType.INTEGER;
        }

        if (value instanceof Double || value instanceof Float) {
            return ValueType.DOUBLE;
        }

        if (value instanceof Date) {
            return ValueType.DATE;
        }

        if (value instanceof String) {
            String strValue = (String) value;
            // Check if it's a French boolean string
            if ("VRAI".equalsIgnoreCase(strValue) || "FAUX".equalsIgnoreCase(strValue) ||
                    "true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                return ValueType.BOOLEAN;
            }
        }

        return ValueType.STRING;
    }

    /**
     * Format a value to its string representation.
     */
    private String formatToString(Object value, ValueType type) {
        if (value == null) {
            return "";
        }

        switch (type) {
            case DATE:
                return DATE_FORMAT.format((Date) value);

            case BOOLEAN:
                if (value instanceof String) {
                    String strValue = (String) value;
                    if ("VRAI".equalsIgnoreCase(strValue))
                        return "true";
                    if ("FAUX".equalsIgnoreCase(strValue))
                        return "false";
                }
                return String.valueOf(value);

            default:
                return String.valueOf(value);
        }
    }

    /**
     * Check if a column represents an ID field.
     */
    private boolean isIDTypeField(ExportColumn column) {
        if (column.isFlattened) {
            return false;
        }
        String typeName = column.field != null ? column.field.getTypeName() : null;
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    @Override
    public String toString() {
        return "FormattedValue{" +
                "column='" + getColumnName() + "'" +
                ", type=" + type +
                ", value='" + stringValue + "'" +
                ", isEmpty=" + isEmpty +
                '}';
    }
}