package migration4o.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.formatters.FormatterContext;
import migration4o.util.formatters.ValueFormatter;

/**
 * Utility class for value-related operations, particularly for determining if values are considered "empty" for export purposes.
 */
public class ValueUtil {

    /**
     * Determines if a value is considered empty for export purposes.
     * 
     * A value is considered empty if: - It is null - It is a String that is empty or contains only whitespace - It is a Collection that is empty - It is an array that has zero length - It is an IDEntite field with value -1 (indicating an empty/null reference)
     * 
     * @param value The value to check
     * @param field The schema field (can be null for generic checks)
     * @param schema The schema to use for type checking (can be null if field is null or not checking IDEntite)
     * @return true if the value is considered empty, false otherwise
     */
    public static boolean isEmpty(Object value, DOSchemaField field, DOSchema schema) {
        if (value == null) {
            return true;
        }

        // Check for empty strings (including whitespace-only strings)
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }

        // Check for empty collections
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }

        // Check for empty maps
        if (value instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) value).isEmpty();
        }

        // Check for empty arrays
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }

        // All other values are considered non-empty
        return false;
    }

    /**
     * Determines if a value is considered empty or is a boolean with value false. This is useful for fields where both empty values and false booleans should be skipped.
     * 
     * @param value The value to check
     * @param field The schema field (can be null for generic checks)
     * @param schema The schema to use for type checking (can be null if field is null or not checking IDEntite)
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
     * Supported keywords: - NULL: value is null - ZERO: numeric value equals 0 - MINUS_ONE: numeric value equals -1 - EMPTY_STRING: string is null or empty (after trim) - EMPTY_COLLECTION: collection/array is null or empty - FALSE: boolean is false
     * 
     * @param value The value to check
     * @param skipWhen Comma-separated skip conditions (e.g., "NULL,ZERO,MINUS_ONE")
     * @param field The schema field (can be null)
     * @param schema The schema to use for type checking (can be null)
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
                if (value instanceof java.util.Map && ((java.util.Map<?, ?>) value).isEmpty()) {
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
            }
        }

        return false;
    }

    /**
     * Determines if a field should be skipped based on its skipWhen settings.
     * 
     * @param value The field value
     * @param field The schema field
     * @param schema The schema for type checking
     * @return true if the field should be skipped, false otherwise
     */
    public static boolean shouldSkipField(Object value, DOSchemaField field, DOSchema schema) {
        return shouldSkipField(value, field, schema, null);
    }

    /**
     * Determines if a field should be skipped based on its skipWhen settings and user-selected skip options.
     * 
     * @param value The field value
     * @param field The schema field
     * @param schema The schema for type checking
     * @param userSelectedSkipOptions List of fields that user has chosen to skip (can be null)
     * @return true if the field should be skipped, false otherwise
     */
    public static boolean shouldSkipField(Object value, DOSchemaField field, DOSchema schema, List<DOSchemaField> userSelectedSkipOptions) {
        return shouldSkipField(value, field, schema, userSelectedSkipOptions, true, true);
    }

    public static boolean shouldSkipField(Object value, DOSchemaField field, DOSchema schema, List<DOSchemaField> userSelectedSkipOptions, boolean applyUserSelectedSkipOptions, boolean applySkipWhenConditions) {
        if (field == null) {
            return false;
        }

        // Check if user has selected this field to be skipped
        if (applyUserSelectedSkipOptions && userSelectedSkipOptions != null && userSelectedSkipOptions.contains(field)) {
            return true;
        }

        // Check skipWhen conditions
        if (applySkipWhenConditions && field.attributes.skipWhen != null && !field.attributes.skipWhen.trim().isEmpty()) {
            return matchesSkipCondition(value, field.attributes.skipWhen, field, schema);
        }

        return false;
    }

    /**
     * Formats a field value according to DOSchemaField.format.
     *
     * Supported keywords (comma-separated): - TRIM - LOWERCASE - UPPERCASE
     *
     * Unknown keywords are ignored.
     *
     * @param value The value to format
     * @param field The schema field that may define format rules
     * @return Formatted value
     */
    public static String formatFieldValue(DODatabaseDelegate delegate, FormatterContext context, String value, DOSchemaField field) {
        if (value == null) {
            return null;
        }

        if (field == null || field.attributes.format == null || field.attributes.format.trim().isEmpty()) {
            return value;
        }

        String formattedValue = value;
        String[] formatKeywords = field.attributes.format.split(",");
        for (String keyword : formatKeywords) {
            formattedValue = ValueFormatter.formatValue(delegate, context, formattedValue, keyword.trim());
        }

        return formattedValue;
    }

    /**
     * Formats any object value according to DOSchemaField.format by converting it to string first.
     */
    public static String formatFieldValue(DODatabaseDelegate delegate, FormatterContext context, Object value, DOSchemaField field) {
        if (value == null) {
            return null;
        }
        return formatFieldValue(delegate, context, String.valueOf(value), field);
    }

    /**
     * Converts a Java array to a List.
     * 
     * @param arrayObj The array object
     * @return List containing the array elements
     */
    public static List<Object> arrayToList(Object arrayObj) {
        List<Object> list = new ArrayList<>();
        int length = java.lang.reflect.Array.getLength(arrayObj);

        for (int i = 0; i < length; i++) {
            Object item = java.lang.reflect.Array.get(arrayObj, i);
            if (item != null) {
                list.add(item);
            }
        }

        return list;
    }
}
