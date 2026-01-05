package migration4o.engine.migration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import migration4o.models.database.DODatabaseField;

/**
 * Utility methods for XML export operations.
 * Contains shared helper methods for formatting, validation, and data
 * transformation.
 */
public class XMLExportUtils {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Sanitize a name for use in XML (remove invalid characters).
     */
    public static String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    /**
     * Clean field names by removing 'm' prefix and converting to camelCase.
     */
    public static String cleanFieldName(String fieldName) {
        if (fieldName == null) {
            return fieldName;
        }

        String cleaned = fieldName;

        // Remove leading 'm' from mXxx pattern
        if (cleaned.length() > 1 && cleaned.startsWith("m") && Character.isUpperCase(cleaned.charAt(1))) {
            cleaned = cleaned.substring(1);
        }

        // Special handling for ID fields
        if (cleaned.equals("ID")) {
            return "id";
        }
        if (cleaned.equals("IDSSI")) {
            return "idssi";
        }

        // Handle IDPrefix patterns (e.g., IDDossPrev -> idDossPrev)
        if (cleaned.startsWith("ID") && cleaned.length() > 2 && Character.isUpperCase(cleaned.charAt(2))) {
            cleaned = "id" + cleaned.substring(2);
        }

        // Convert to camelCase (first letter lowercase)
        if (cleaned.length() > 0) {
            cleaned = Character.toLowerCase(cleaned.charAt(0)) + cleaned.substring(1);
        }

        return cleaned;
    }

    /**
     * Get the XML type for a field.
     */
    public static String getFieldType(DODatabaseField field) {
        if (field.isPrimitive()) {
            String typeName = field.getTypeName();
            if (typeName == null)
                return "string";

            if (typeName.equals("int") || typeName.equals("java.lang.Integer"))
                return "int";
            if (typeName.equals("long") || typeName.equals("java.lang.Long"))
                return "long";
            if (typeName.equals("double") || typeName.equals("java.lang.Double"))
                return "double";
            if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean"))
                return "boolean";
            if (typeName.equals("java.util.Date"))
                return "date";

            return "string";
        }
        return "reference";
    }

    /**
     * Format field value for XML output.
     */
    public static String formatFieldValue(Object value, DODatabaseField field) {
        if (value == null)
            return "";

        if (value instanceof Date && getFieldType(field).equals("date")) {
            return DATE_FORMAT.format((Date) value);
        }

        return String.valueOf(value);
    }

    /**
     * Get simple type name from full class name.
     */
    public static String getSimpleTypeName(String className) {
        if (className == null)
            return "Unknown";

        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * Check if a value is empty or meaningless for export.
     */
    public static boolean isEmptyValue(Object value, DODatabaseField field) {
        if (value == null) {
            return true;
        }

        if (value instanceof String) {
            String strValue = ((String) value).trim();
            return strValue.isEmpty();
        }

        if (value instanceof Number) {
            Number numValue = (Number) value;
            double doubleValue = numValue.doubleValue();

            // Skip -1 values for ID fields (indicates no reference)
            if (isIDTypeField(field) && numValue.intValue() == -1) {
                return true;
            }

            // Skip 0 values for specific field types that are likely meaningless when zero
            if (isZeroMeaninglessField(field) && doubleValue == 0.0) {
                return true;
            }

            // Skip SSI fields with -1 (they indicate no reference)
            String fieldName = field.getName().toLowerCase();
            if (fieldName.contains("ssi") && numValue.intValue() == -1) {
                return true;
            }
        }

        if (value instanceof Date) {
            Date dateValue = (Date) value;
            // Skip default dates like 1900-01-01 which are often placeholders
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateValue);
            if (cal.get(Calendar.YEAR) <= 1900) {
                return true;
            }
        }

        if (value instanceof Boolean) {
            // Keep all boolean values as they are meaningful
            return false;
        }

        return false;
    }

    /**
     * Check if this is an ID-type field.
     */
    public static boolean isIDTypeField(DODatabaseField field) {
        String typeName = field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Determine if zero values for this field are likely meaningless.
     */
    public static boolean isZeroMeaninglessField(DODatabaseField field) {
        String fieldName = field.getName().toLowerCase();
        return fieldName.contains("annee") || // Year fields
                fieldName.contains("year") ||
                fieldName.contains("nbr") || // Count fields
                fieldName.contains("count") ||
                fieldName.contains("numero") || // Number fields
                fieldName.contains("number");
    }
}
