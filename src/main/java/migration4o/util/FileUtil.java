package migration4o.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utility methods for file operations and formatting.
 */
public class FileUtil {

    /**
     * Formats a file size in bytes into a human-readable string with appropriate units.
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
     * Sanitize a name for use in XML (remove invalid characters). Converts accentuated characters to their non-accentuated equivalents (e.g., é→e, à→a, ñ→n) and preserves single quotes.
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

    /**
     * Sanitizes a name for use as a filesystem path component.
     * Removes accents, strips characters invalid on any OS ({@code / \ : * ? " < > |}),
     * collapses consecutive underscores, and trims leading/trailing underscores.
     * Returns {@code "unnamed"} for null or blank input.
     */
    public static String sanitizeForPath(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }

        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{M}", "");

        // Strip characters that are invalid in file/folder names on Windows, macOS, or Linux
        String safe = withoutAccents.replaceAll("[/\\\\:*?\"<>|]", "_");

        // Collapse consecutive underscores and trim
        safe = safe.replaceAll("_+", "_").replaceAll("^_|_$", "");

        return safe.isBlank() ? "unnamed" : safe;
    }

    public static void createBackup(String filePath, String destinationFile) throws IOException {
        File originalFile = new File(filePath);
        if (!originalFile.exists()) {
            return; // No need to backup if file doesn't exist yet
        }

        // Find next available backup number
        int backupNumber = 1;
        File backupFile;
        do {
            String backupPath = destinationFile + "." + String.format("%04d", backupNumber) + ".bak";
            backupFile = new File(backupPath);
            backupNumber++;
        } while (backupFile.exists());

        // Create the backup
        Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        // System.out.println("Created backup: " + backupFile.getName());
    }

    /**
     * Returns the extension of a file name (excluding the dot), or an empty string if there is no extension. For example, {@code "photo.jpg"} → {@code "jpg"}, {@code "archive.tar.gz"} → {@code "gz"}, {@code "README"} → {@code ""}.
     *
     * @param fileName the file name (or full path — only the last component is examined)
     * @return the extension, never {@code null}
     */
    public static String getExtension(String fileName) {
        return getExtension(fileName, "");
    }

    /**
     * Returns the extension of a file name (excluding the dot), or {@code defaultExtension} if the file name has no valid extension.
     *
     * @param fileName the file name or full path
     * @param defaultExtension value to return when no extension is found
     * @return the extension, or {@code defaultExtension}
     */
    public static String getExtension(String fileName, String defaultExtension) {
        if (fileName == null) {
            return defaultExtension;
        }
        String name = new File(fileName).getName();
        int dot = name.lastIndexOf('.');
        if (dot < 1 || dot == name.length() - 1) {
            return defaultExtension;
        }
        return name.substring(dot + 1);
    }

    /**
     * Copies {@code source} to {@code destination}, creating parent directories as needed. If {@code destination} already exists it is overwritten.
     *
     * @param source the file to copy; must exist and be a regular file
     * @param destination the target file path
     */
    public static void copyFile(File source, File destination) {
        try {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[FileUtil] Failed to copy " + source + " → " + destination + ": " + e.getMessage());
        }
    }

}