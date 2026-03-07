package migration4o.migration.tasks;

import java.nio.file.Path;

import migration4o.migration.ExportOperation;

/**
 * Writes a per-class XSD schema file after the data file has been generated.
 * <p>
 * Only acts when all three conditions are met:
 * <ol>
 * <li>The output format is XML.
 * <li>No shared XSD builder is in use (shared builds write one comprehensive
 * schema at the end instead).
 * <li>A non-null {@code xsdPath} is provided.
 * </ol>
 * Progress callbacks ({@code onXSDGenerationStart} /
 * {@code onXSDGenerationComplete}) are fired automatically when the XSD is
 * written.
 */
public class ClassXsdWriter {

    private final ExportOperation operation;

    public ClassXsdWriter(ExportOperation operation) {
        this.operation = operation;
    }

    /**
     * Writes the XSD for the class that was just exported, if applicable.
     *
     * @param xsdPath Destination path for the per-class XSD; may be
     * {@code null} (in which case this is a no-op)
     */
    public void writeIfNeeded(Path xsdPath) throws Exception {
        if (!operation.isXMLFormat() || operation.sharedXSDBuilder != null || xsdPath == null) {
            return;
        }
        if (operation.monitor != null) {
            operation.monitor.onXSDGenerationStart(xsdPath.toString());
        }
        operation.xsdBuilder.writeXSD(xsdPath.toString());
        if (operation.monitor != null) {
            operation.monitor.onXSDGenerationComplete(xsdPath.toString());
        }
    }
}
