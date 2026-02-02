package migration4o.util;

import java.util.Collection;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for value-related operations, particularly for determining
 * if values are considered "empty" for export purposes.
 */
public class ValueUtil {

    /**
     * Determines if a value is considered empty for export purposes.
     * 
     * A value is considered empty if:
     * - It is null
     * - It is a String that is empty or contains only whitespace
     * - It is a Collection that is empty
     * - It is an array that has zero length
     * - It is an IDEntite field with value -1 (indicating an empty/null reference)
     * 
     * @param value  The value to check
     * @param field  The schema field (can be null for generic checks)
     * @param schema The schema to use for type checking (can be null if field is
     *               null or not checking IDEntite)
     * @return true if the value is considered empty, false otherwise
     */
    public static boolean isEmpty(Object value, DOSchemaField field, DOSchema schema) {
        if (value == null) {
            return true;
        }

        // Check for -1 ID in IDEntite fields (empty reference)
        if (field != null && schema != null && TypeUtil.isIDEntiteField(field, schema)) {
            if (value instanceof Number) {
                return ((Number) value).longValue() == -1;
            }
        }

        // Check for empty strings (including whitespace-only strings)
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }

        // Check for empty collections
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }

        // Check for empty arrays
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }

        // All other values are considered non-empty
        return false;
    }

    /**
     * Determines if a value is considered empty or is a boolean with value false.
     * This is useful for fields where both empty values and false booleans
     * should be skipped.
     * 
     * @param value  The value to check
     * @param field  The schema field (can be null for generic checks)
     * @param schema The schema to use for type checking (can be null if field is
     *               null or not checking IDEntite)
     * @return true if the value is empty or false, false otherwise
     */
    public static boolean isEmptyOrFalse(Object value, DOSchemaField field, DOSchema schema) {
        if (isEmpty(value, field, schema)) {
            return true;
        }

        // Check for boolean false
        if (value instanceof Boolean) {
            return !((Boolean) value);
        }

        return false;
    }

    /**
     * Evaluates whether a value matches skip conditions specified in skipWhen.
     * 
     * Supported keywords:
     * - NULL: value is null
     * - ZERO: numeric value equals 0
     * - MINUS_ONE: numeric value equals -1
     * - EMPTY_STRING: string is null or empty (after trim)
     * - EMPTY_COLLECTION: collection/array is null or empty
     * - FALSE: boolean is false
     * - DEFAULT: uses legacy isEmpty() logic
     * 
     * @param value    The value to check
     * @param skipWhen Comma-separated skip conditions (e.g., "NULL,ZERO,MINUS_ONE")
     * @param field    The schema field (can be null)
     * @param schema   The schema to use for type checking (can be null)
     * @return true if value matches any skip condition, false otherwise
     */
    public static boolean matchesSkipCondition(Object value, String skipWhen, DOSchemaField field, DOSchema schema) {
        if (skipWhen == null || skipWhen.trim().isEmpty()) {
            return false;
        }

        // Split by comma and check each condition
        String[] conditions = skipWhen.split(",");
        for (String condition : conditions) {
            condition = condition.trim().toUpperCase();

            switch (condition) {
                case "NULL":
                    if (value == null) {
                        return true;
                    }
                    break;

                case "ZERO":
                    if (value instanceof Number && ((Number) value).doubleValue() == 0.0) {
                        return true;
                    }
                    break;

                case "MINUS_ONE":
                    if (value instanceof Number && ((Number) value).longValue() == -1) {
                        return true;
                    }
                    break;

                case "EMPTY_STRING":
                    if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                        return true;
                    }
                    break;

                case "EMPTY_COLLECTION":
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
                        return true;
                    }
                    if (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) == 0) {
                        return true;
                    }
                    break;

                case "FALSE":
                    if (value instanceof Boolean && !((Boolean) value)) {
                        return true;
                    }
                    break;

                case "DEFAULT":
                    if (isEmpty(value, field, schema)) {
                        return true;
                    }
                    break;
            }
        }

        return false;
    }

    /**
     * Determines if a field should be skipped based on its skipWhen settings.
     * 
     * @param value  The field value
     * @param field  The schema field
     * @param schema The schema for type checking
     * @return true if the field should be skipped, false otherwise
     */
    public static boolean shouldSkipField(Object value, DOSchemaField field, DOSchema schema) {
        if (field == null) {
            return false;
        }

        // Check skipWhen conditions
        if (field.skipWhen != null && !field.skipWhen.trim().isEmpty()) {
            return matchesSkipCondition(value, field.skipWhen, field, schema);
        }

        return false;
    }
}
