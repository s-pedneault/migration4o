package migration4o.util;

/**
 * Utility methods for file operations and formatting.
 */
public class FileUtil {

    /**
     * Formats a file size in bytes into a human-readable string with appropriate
     * units.
     * 
     * @param bytes The file size in bytes
     * @return A formatted string with appropriate units (B, KB, MB, GB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Sanitize a name for use in XML (remove invalid characters).
     * Converts accentuated characters to their non-accentuated equivalents
     * (e.g., é→e, à→a, ñ→n) and preserves single quotes.
     */
    public static String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }

        // Normalize to NFD (decompose accented characters into base + diacritical
        // marks)
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);

        // Remove diacritical marks (accents) using Unicode category "Mark"
        String withoutAccents = normalized.replaceAll("\\p{M}", "");

        // Keep alphanumeric, underscore, dot, hyphen, and single quote
        // Replace everything else with underscore
        return withoutAccents.replaceAll("[^a-zA-Z0-9_.'-]", "_");
    }

}