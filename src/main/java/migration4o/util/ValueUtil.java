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
}
