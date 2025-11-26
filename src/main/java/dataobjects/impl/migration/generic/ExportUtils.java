package dataobjects.impl.migration.generic;

import dataobjects.api.models.DOField;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for common export operations.
 * Contains shared helper methods used by various export components.
 */
public class ExportUtils {

    /**
     * Priority fields to export first (in this order) if they exist.
     */
    public static final String[] PRIORITY_FIELDS = {
            "mID",
            "mIDSSI",
            "mIDSSIConso",
    };

    /**
     * Sort fields according to export priority:
     * 1. Priority fields (as defined in PRIORITY_FIELDS)
     * 2. Non-collection fields
     * 3. Collection fields
     */
    public static List<DOField> sortFieldsForExport(List<DOField> fields) {
        List<DOField> priorityFields = new ArrayList<>();
        List<DOField> nonCollectionFields = new ArrayList<>();
        List<DOField> collectionFields = new ArrayList<>();

        // Categorize fields
        for (DOField field : fields) {
            boolean isPriority = false;
            for (String priorityName : PRIORITY_FIELDS) {
                if (priorityName.equals(field.getName())) {
                    isPriority = true;
                    break;
                }
            }

            if (isPriority) {
                priorityFields.add(field);
            } else if (isCollectionField(field)) {
                collectionFields.add(field);
            } else {
                nonCollectionFields.add(field);
            }
        }

        // Sort priority fields in the order defined in PRIORITY_FIELDS
        priorityFields.sort((f1, f2) -> {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < PRIORITY_FIELDS.length; i++) {
                if (PRIORITY_FIELDS[i].equals(f1.getName())) {
                    index1 = i;
                }
                if (PRIORITY_FIELDS[i].equals(f2.getName())) {
                    index2 = i;
                }
            }
            return Integer.compare(index1, index2);
        });

        // Combine: priority fields first, then non-collection, then collection
        List<DOField> sortedFields = new ArrayList<>();
        sortedFields.addAll(priorityFields);
        sortedFields.addAll(nonCollectionFields);
        sortedFields.addAll(collectionFields);

        return sortedFields;
    }

    /**
     * Check if a field is a collection type.
     */
    private static boolean isCollectionField(DOField field) {
        if (field == null) {
            return false;
        }
        String typeName = field.getTypeName();
        if (typeName == null) {
            return false;
        }
        // Check for common collection types
        return typeName.contains("Vector") ||
                typeName.contains("ArrayList") ||
                typeName.contains("List") ||
                typeName.contains("Set") ||
                typeName.contains("Collection") ||
                typeName.endsWith("[]");
    }

    /**
     * Remove accents from text by normalizing and removing diacritical marks.
     */
    public static String removeAccents(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Sanitize a module name for use as a file name.
     * Removes or replaces characters that are not valid in file names.
     */
    public static String sanitizeModuleName(String moduleName) {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            return "unnamed_module";
        }

        // Remove accents first
        String withoutAccents = removeAccents(moduleName.trim());

        // Replace spaces and special characters with underscores
        String sanitized = withoutAccents
                .replaceAll("[\\s\\-\\.]", "_")
                .replaceAll("[^a-zA-Z0-9_]", "");

        // Ensure it's not empty
        if (sanitized.isEmpty()) {
            sanitized = "module";
        }

        return sanitized;
    }
}