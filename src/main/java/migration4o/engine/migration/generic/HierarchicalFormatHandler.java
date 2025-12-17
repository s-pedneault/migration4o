package migration4o.engine.migration.generic;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for hierarchical format handlers (XML, JSON, etc.).
 * Provides common functionality for formats that organize data in nested
 * structures.
 */
public abstract class HierarchicalFormatHandler extends ExportFormatHandler {

    protected String outputDirectory;
    protected ReferenceTracker tracker;

    @Override
    public final OutputStructure getPreferredStructure() {
        return OutputStructure.HIERARCHICAL;
    }

    @Override
    public void initialize(String outputDirectory, ReferenceTracker tracker) throws IOException {
        this.outputDirectory = outputDirectory;
        this.tracker = tracker;

        // Create output directory
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Allow subclasses to perform additional initialization
        initializeFormat(outputDirectory);
    }

    /**
     * Hook for subclasses to perform format-specific initialization.
     */
    protected void initializeFormat(String outputDirectory) throws IOException {
        // Default: no additional initialization
    }

    /**
     * Filter out empty values if desired.
     * Many hierarchical formats benefit from omitting empty/null values.
     */
    protected List<FormattedValue> filterValues(List<FormattedValue> values, boolean includeEmpty) {
        if (includeEmpty) {
            return values;
        }

        return values.stream()
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Get non-empty values (common case for hierarchical formats).
     */
    protected List<FormattedValue> getNonEmptyValues(List<FormattedValue> values) {
        return filterValues(values, false);
    }

    /**
     * Generate a file name for a module.
     * Subclasses can override to customize naming.
     */
    protected String generateModuleFileName(ModuleExportContext context, String extension) {
        return context.getSanitizedModuleName() + extension;
    }

    /**
     * Utility method to escape text for use in hierarchical formats.
     * Default implementation handles common XML escaping.
     */
    protected String escapeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Utility method to create valid element/attribute names.
     * Removes or replaces characters that are not valid in hierarchical formats.
     */
    protected String sanitizeElementName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed";
        }

        // Replace spaces and special characters with underscores
        String sanitized = name.trim()
                .replaceAll("[\\s\\-\\.]", "_")
                .replaceAll("[^a-zA-Z0-9_]", "");

        // Ensure it starts with a letter or underscore
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "item_" + sanitized;
        }

        return sanitized.toLowerCase();
    }

    /**
     * Utility method to format attribute values.
     */
    protected String formatAttributeValue(FormattedValue value) {
        if (value.isEmpty()) {
            return "";
        }
        return escapeText(value.getStringValue());
    }

    /**
     * Utility method to format text content.
     */
    protected String formatTextContent(FormattedValue value) {
        if (value.isEmpty()) {
            return "";
        }
        return escapeText(value.getStringValue());
    }
}