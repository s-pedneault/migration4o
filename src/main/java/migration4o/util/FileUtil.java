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

    // Private constructor to prevent instantiation of utility class
    private FileUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}