package dataobjects.api.migration.generic.v2;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Base class for tabular format handlers (Excel, CSV, etc.).
 * Provides common functionality for formats that organize data in rows and
 * columns.
 */
public abstract class TabularFormatHandler implements ExportFormatHandler {

    protected String outputDirectory;

    @Override
    public final OutputStructure getPreferredStructure() {
        return OutputStructure.TABULAR;
    }

    @Override
    public void initialize(String outputDirectory) throws IOException {
        this.outputDirectory = outputDirectory;

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
     * Create column headers for tabular output.
     * Subclasses can override to customize header creation.
     */
    protected String[] createHeaders(ClassExportContext context) {
        List<FormattedValue> columns = context.getColumns().stream()
                .map(col -> new FormattedValue(col.columnName, col))
                .collect(java.util.stream.Collectors.toList());

        return columns.stream()
                .map(FormattedValue::getStringValue)
                .toArray(String[]::new);
    }

    /**
     * Convert formatted values to row data suitable for tabular output.
     */
    protected Object[] createRowData(List<FormattedValue> values) {
        return values.stream()
                .map(this::convertValueForRow)
                .toArray();
    }

    /**
     * Convert a single formatted value for inclusion in a table row.
     * Subclasses can override to provide format-specific conversion.
     */
    protected Object convertValueForRow(FormattedValue value) {
        if (value.isEmpty()) {
            return null;
        }

        switch (value.getType()) {
            case BOOLEAN:
                return value.getBooleanValue();
            case INTEGER:
            case DOUBLE:
                return value.getNumberValue();
            case DATE:
                return value.getDateValue();
            default:
                return value.getStringValue();
        }
    }

    /**
     * Utility method to sanitize names for use in tabular formats.
     */
    protected String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unnamed";
        }
        return name.trim();
    }
}