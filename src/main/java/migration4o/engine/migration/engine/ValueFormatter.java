package migration4o.engine.migration.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for converting raw extracted values into formatted values.
 * This centralizes all value formatting logic that was previously duplicated
 * across different format handlers.
 */
public class ValueFormatter {

    /**
     * Format a list of raw values into FormattedValue objects.
     */
    public List<FormattedValue> formatValues(List<Object> rawValues, List<ExportColumn> columns) {
        List<FormattedValue> formattedValues = new ArrayList<>(rawValues.size());

        int minSize = Math.min(rawValues.size(), columns.size());
        for (int i = 0; i < minSize; i++) {
            Object rawValue = rawValues.get(i);
            ExportColumn column = columns.get(i);
            FormattedValue formatted = new FormattedValue(rawValue, column);
            formattedValues.add(formatted);
        }

        return formattedValues;
    }

    /**
     * Format a single raw value into a FormattedValue.
     */
    public FormattedValue formatValue(Object rawValue, ExportColumn column) {
        return new FormattedValue(rawValue, column);
    }

    /**
     * Filter formatted values to exclude empty ones (useful for hierarchical
     * formats).
     */
    public List<FormattedValue> filterNonEmpty(List<FormattedValue> values) {
        List<FormattedValue> nonEmpty = new ArrayList<>();

        for (FormattedValue value : values) {
            if (!value.isEmpty()) {
                nonEmpty.add(value);
            }
        }

        return nonEmpty;
    }

    /**
     * Get only the values for non-ID fields (useful for certain export scenarios).
     */
    public List<FormattedValue> filterNonIDFields(List<FormattedValue> values) {
        List<FormattedValue> nonIDFields = new ArrayList<>();

        for (FormattedValue value : values) {
            if (!value.isIDField()) {
                nonIDFields.add(value);
            }
        }

        return nonIDFields;
    }
}