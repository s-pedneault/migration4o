package migration4o.migration;

/**
 * Identifies the export format. Each value maps to a {@link migration4o.migration.format.FormatHandler}
 * implementation that declares its own file extension and display name.
 */
public enum ExportFormat {
    XML,
    HTML,
    JSON,
    EXCEL
}
