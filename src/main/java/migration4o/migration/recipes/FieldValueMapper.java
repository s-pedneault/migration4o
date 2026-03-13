package migration4o.migration.recipes;

import migration4o.models.schema.DOSchemaField;

/**
 * Applies value mapping transformations based on schema field definitions.
 * Value maps allow converting raw database values to desired export values.
 */
public class FieldValueMapper {

    /**
     * Applies value mapping to a string value if defined in the schema field.
     * If no mapping exists for the value, returns the original value.
     * 
     * @param value       The value to map
     * @param schemaField The schema field containing the value map
     * @return The mapped value, or the original if no mapping exists
     */
    public static String applyMapping(String value, DOSchemaField schemaField) {
        if (value == null || schemaField == null) {
            return value;
        }

        if (schemaField.valueMap == null || schemaField.valueMap.isEmpty()) {
            return value;
        }

        String mappedValue = schemaField.valueMap.getMappedValue(value);
        return mappedValue != null ? mappedValue : value;
    }

    /**
     * Applies value mapping to an object by converting it to string first.
     * 
     * @param value       The value to map
     * @param schemaField The schema field containing the value map
     * @return The mapped value as string, or the original value's string
     *         representation
     */
    public static String applyMapping(Object value, DOSchemaField schemaField) {
        if (value == null) {
            return null;
        }

        return applyMapping(value.toString(), schemaField);
    }

    /**
     * Checks if a schema field has a value mapping defined.
     * 
     * @param schemaField The schema field to check
     * @return true if the field has value mappings, false otherwise
     */
    public static boolean hasMapping(DOSchemaField schemaField) {
        return schemaField != null
                && schemaField.valueMap != null
                && !schemaField.valueMap.isEmpty();
    }
}
