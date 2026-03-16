package migration4o.migration.tasks;

import java.nio.file.Path;

import migration4o.migration.ExportRequest;
import migration4o.models.schema.DOSchemaModule;

/**
 * Static helpers for module names, paths, and XSD schema locations.
 */
public final class ModulePathUtil {

    private ModulePathUtil() {
    }

    /**
     * Returns the folder identifier for a module: the module's ID when
     * non-blank, otherwise falls back to the module's display name.
     */
    public static String moduleId(DOSchemaModule m) {
        String id = m.id;
        return (id != null && !id.isBlank()) ? id : m.name;
    }

    /**
     * Sanitizes a raw module name or path for use as a module identifier in
     * metadata. Strips absolute-path prefixes and trailing slashes.
     */
    public static String sanitizeModuleName(String moduleName) {
        if (moduleName == null) {
            return "";
        }
        String normalized = moduleName.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return "";
        }

        boolean looksAbsolutePath = normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*");
        if (!looksAbsolutePath) {
            return normalized;
        }

        String marker = "/output/";
        int outputMarkerIndex = normalized.indexOf(marker);
        if (outputMarkerIndex >= 0) {
            String afterOutput = normalized.substring(outputMarkerIndex + marker.length());
            int dbSeparatorIndex = afterOutput.indexOf('/');
            if (dbSeparatorIndex >= 0 && dbSeparatorIndex + 1 < afterOutput.length()) {
                return afterOutput.substring(dbSeparatorIndex + 1);
            }
        }

        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < normalized.length()) {
            return normalized.substring(lastSlash + 1);
        }

        return normalized;
    }

    /**
     * Derives a module name suitable for embedding in XML metadata from the
     * given output file path, using the operation's base output path as a
     * reference.
     */
    public static String getModuleNameForXml(Path xmlPath, ExportRequest operation) {
        if (xmlPath == null) {
            return "";
        }
        Path parent = xmlPath.getParent();
        if (parent == null) {
            return "";
        }
        try {
            if (operation.baseOutputPath != null && !operation.baseOutputPath.isBlank()) {
                Path dbBasePath = operation.getBaseOutputPath(operation.baseOutputPath);
                if (dbBasePath != null && parent.startsWith(dbBasePath)) {
                    String relativeModule = dbBasePath.relativize(parent).toString().replace('\\', '/');
                    if (!relativeModule.isBlank()) {
                        return sanitizeModuleName(relativeModule);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to path sanitization
        }
        return sanitizeModuleName(parent.toString());
    }

    /**
     * Returns the path of the comprehensive XSD schema file for the current
     * export (relative to the db-specific output directory).
     */
    public static Path getComprehensiveSchemaPath(String baseOutputPath, ExportRequest operation) {
        Path dbBasePath = operation.getBaseOutputPath(baseOutputPath);
        return dbBasePath.resolve("_Migration").resolve("Schema.xsd");
    }

    /**
     * Returns the schema location string to embed in an XML file, as a relative
     * path from the file's directory to the comprehensive schema. Returns
     * {@code null} when the operation format is not XML.
     */
    public static String getSchemaLocationForXml(Path xmlPath, String baseOutputPath, ExportRequest operation) {
        Path schemaPath = getComprehensiveSchemaPath(baseOutputPath, operation);
        Path xmlDir = xmlPath.getParent();
        if (xmlDir == null) {
            return "_Migration/Schema.xsd";
        }
        try {
            return xmlDir.relativize(schemaPath).toString().replace('\\', '/');
        } catch (Exception e) {
            return "_Migration/Schema.xsd";
        }
    }
}
