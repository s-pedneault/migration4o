package dataobjects.impl.migration.generic;

import java.text.Normalizer;

/**
 * Utility class for common export operations.
 * Contains shared helper methods used by various export components.
 */
public class ExportUtils {

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