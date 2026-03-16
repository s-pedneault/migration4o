package migration4o.migration.format;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import migration4o.migration.ExportFormat;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterExcel;

/**
 * Format handler for Excel (XLSX) output.
 * <p>
 * The Excel writer builds its workbook in memory and writes it to
 * {@code writer.outputPath} when the document is finalised (via
 * {@code onDocumentComplete}). A {@link StringWriter} is used as the backing
 * character writer since the Excel API does not use it.
 * <p>
 * Overrides one hook: {@code init} — sets {@code exportNativeIds = true}
 * because EXCEL always includes the native ID column.
 */
public class ExcelFormatHandler extends FormatHandler {

    public ExcelFormatHandler() {
        super(ExportFormat.EXCEL);
    }

    @Override
    public String extension() { return ".xlsx"; }

    @Override
    public String displayName() { return "Excel"; }

    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        // The Excel API writes to writer.outputPath via onDocumentComplete;
        // the StringWriter backing is never used directly.
        return new StructuredWriter(new StructuredWriterExcel(), new StringWriter(), filePath);
    }

    /** EXCEL always exports native IDs as its first column. */
    @Override
    public void init(ExportCurrentState ctx) throws Exception {
        ctx.request.exportNativeIds = true;
    }
}
