package migration4o.migration.tasks;

import java.io.IOException;
import java.nio.file.Path;

import migration4o.migration.ExportOperation;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.JsViewerHtmlGenerator;
import migration4o.util.XmlViewerHtmlGenerator;

/**
 * Generates the HTML/JS viewer file next to an exported data file.
 * <p>
 * Delegates to either {@link JsViewerHtmlGenerator} (for JS-format output) or
 * {@link XmlViewerHtmlGenerator} (for XML-format output), and does nothing when
 * HTML viewer generation is disabled on the operation.
 */
public class HtmlViewerTask {

    private final ExportOperation operation;

    public HtmlViewerTask(ExportOperation operation) {
        this.operation = operation;
    }

    /**
     * Generates an HTML viewer for {@code outputPath} if the operation has HTML
     * viewer generation enabled. Safe to call even when the feature is disabled
     * — it will simply be a no-op.
     */
    public void generateIfNeeded(Path outputPath, DOSchemaClass schemaClass, ClassExportConfig config) {
        if (!operation.generateHtmlViewer || outputPath == null) {
            return;
        }
        try {
            if ("JS".equalsIgnoreCase(operation.getStructuredWriterAPI().getName())) {
                String baseHref = computeBaseHref();
                String layoutJson = (config != null && config.hasLayout()) ? config.getLayout().toJson() : "null";
                JsViewerHtmlGenerator.writeViewerForJs(outputPath, schemaClass, operation.cachedNavJson, baseHref, layoutJson);
            } else {
                if (schemaClass != null) {
                    DOSchema refSchema = migration4o.schema.DOSchemaService.getInstance().getReferenceSchema();
                    XmlViewerHtmlGenerator.writeViewerForXml(outputPath, schemaClass, refSchema);
                } else {
                    XmlViewerHtmlGenerator.writeViewerForXml(outputPath);
                }
            }
        } catch (IOException e) {
            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Warning: Failed to generate HTML viewer for " + outputPath.getFileName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Computes the base href for the current file — a relative path from the
     * file's directory back to the db export root (e.g. "../../" for depth 2),
     * derived from the current module stack size.
     */
    private String computeBaseHref() {
        int levels = operation.moduleStack.size();
        if (levels == 0) {
            return "./";
        }
        StringBuilder bh = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            bh.append("../");
        }
        return bh.toString();
    }
}
