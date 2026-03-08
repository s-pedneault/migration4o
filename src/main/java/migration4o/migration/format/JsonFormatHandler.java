package migration4o.migration.format;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import migration4o.migration.ExportFormat;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterJSON;

/**
 * Format handler for JSON output. Uses all base-class hook defaults; exists
 * as a dedicated class so any future JSON-specific behaviour has a clear home.
 */
public class JsonFormatHandler extends FormatHandler {

    public JsonFormatHandler() {
        super(ExportFormat.JSON);
    }

    @Override
    public String extension() { return ".json"; }

    @Override
    public String displayName() { return "JSON"; }

    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        return new StructuredWriter(new StructuredWriterJSON(), new FileWriter(filePath.toFile()), filePath);
    }
}
