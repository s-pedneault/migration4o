package migration4o.migration.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.migration.ExportFormat;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Abstract base class for format-specific export implementations.
 * <p>
 * The engine calls all hooks unconditionally; handlers override only what they
 * need. Subclasses must call {@code super} or replicate its schema-observation
 * callbacks (XSD registration) — all other handlers leave them as no-ops.
 */
public abstract class FormatHandler {

    // ── Public fields
    // ─────────────────────────────────────────────────────────

    /** The format this handler implements. */
    public final ExportFormat format;

    /**
     * Current output writer. Set by the engine before each {@code open} call.
     */
    public StructuredWriter writer;

    /**
     * Per-format set of exported object IDs — prevents writing the same object
     * twice within one format's output. Shared statistics and reference
     * tracking live on {@link ExportCurrentState} instead.
     */
    public final Set<Long> exportedIds = new HashSet<>();

    // ── Constructor
    // ───────────────────────────────────────────────────────────

    protected FormatHandler(ExportFormat format) {
        this.format = format;
    }

    // ── Abstract methods
    // ──────────────────────────────────────────────────────

    /**
     * Returns the output file extension including the leading dot, e.g.
     * {@code ".xml"}, {@code ".html"}, {@code ".json"}, {@code ".xlsx"}.
     */
    public abstract String extension();

    /**
     * Returns the human-readable display name for UI labels, e.g.
     * {@code "XML"}, {@code "HTML"}, {@code "JSON"}, {@code "Excel"}.
     */
    public abstract String displayName();

    /**
     * Creates a new {@link StructuredWriter} for the given output file. Each
     * implementation instantiates its own {@code StructuredWriterAPI} directly
     * (no {@code StructuredWriterProvider}).
     * <p>
     * Implementations should pass {@code filePath} as the third argument to the
     * {@code StructuredWriter} constructor so that {@code writer.outputPath} is
     * always set — even when the underlying {@code Writer} is in-memory.
     */
    protected abstract StructuredWriter createWriter(Path filePath) throws IOException;

    /**
     * Returns the format-specific output sub-folder name, e.g. {@code "xml"},
     * {@code "html"}, {@code "json"}, {@code "excel"}. Defaults to
     * {@code format.name().toLowerCase()}.
     */
    public String folderName() {
        return format.name().toLowerCase();
    }

    /**
     * Returns the root output directory for this format:
     * {@code ctx.basePath / folderName()}.
     */
    public Path formatBasePath(ExportCurrentState ctx) {
        return ctx.basePath.resolve(folderName());
    }

    /**
     * Public facade: creates a writer for {@code filePath} and assigns it to
     * {@link #writer}. Called by the engine before each {@code open} call.
     */
    public final void openWriter(Path filePath) throws IOException {
        this.writer = createWriter(filePath);
    }

    // ── Eight hooks
    // ───────────────────────────────────────────────────────────

    /**
     * Called once before any module is processed. Default: no-op.
     */
    public void init(ExportCurrentState ctx) throws Exception {
    }

    /**
     * Called at the start of each class data file, after {@code writer} has
     * been set by the engine. Default opens the standard two-level structure:
     * 
     * <pre>
     *   writer.openStructure("export")
     *   writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()))
     *   writer.openStructure("objects")
     * </pre>
     */
    public void open(ExportCurrentState ctx) throws Exception {
        writer.openStructure("export");
        if (ctx.schemaClass != null) {
            writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()));
        }
        writer.openStructure("objects");
    }

    /**
     * Called after {@code open}, before field export. Returns {@code true} if
     * the handler has fully written this object (field loop is skipped);
     * {@code false} to proceed with the default field pipeline.
     * <p>
     * Default opens the object element:
     * 
     * <pre>
     * Map&lt;String, String&gt; attrs = exportNativeIds ? Map.of("id", objectId) : null;
     * writer.openStructure(ctx.schemaClass.destinationName, attrs);
     * return false;
     * </pre>
     * 
     * When {@code false} is returned the engine calls
     * {@code writer.closeStructure(ctx.schemaClass.destinationName)} after the
     * field loop.
     */
    public boolean onObject(ExportCurrentState ctx) throws Exception {
        if (ctx.schemaClass == null)
            return true; // no schema match — skip object safely
        Map<String, String> attrs = null;
        if (ctx.request.exportNativeIds) {
            attrs = Map.of("id", String.valueOf(ctx.currentObject().objectId));
        }
        writer.openStructure(ctx.schemaClass.destinationName, attrs);
        return false;
    }

    /**
     * Called before the default field pipeline. Returns {@code true} if the
     * handler has fully written this field (default pipeline is skipped);
     * {@code false} to let the pipeline handle it. Default:
     * {@code return false}.
     */
    public boolean onField(ExportCurrentState ctx) throws Exception {
        return false;
    }

    /**
     * Called after the data file is fully written. Default closes the two
     * structures opened by {@code open} and flushes the writer:
     * 
     * <pre>
     *   writer.closeStructure("objects")
     *   writer.closeStructure("export")
     *   writer.writer.flush()
     * </pre>
     */
    public void close(ExportCurrentState ctx) throws Exception {
        writer.closeStructure("objects");
        writer.closeStructure("export");
        writer.writer.flush();
    }

    /**
     * Called once per handler after all modules finish, for final tasks.
     * Default: no-op.
     */
    public void done(ExportCurrentState ctx) throws Exception {
    }

    // ── Static factory
    // ────────────────────────────────────────────────────────

    /**
     * Creates handler instances for the requested formats.
     *
     * @param formats list of formats to export
     * @param generateXsd whether to generate XSD (passed to
     * {@link XmlFormatHandler})
     * @return one handler per format, in request order
     */
    public static List<FormatHandler> create(List<ExportFormat> formats, boolean generateXsd) {
        List<FormatHandler> handlers = new ArrayList<>();
        for (ExportFormat format : formats) {
            switch (format) {
            case XML -> handlers.add(new XmlFormatHandler(generateXsd));
            case HTML -> handlers.add(new HtmlFormatHandler());
            case JSON -> handlers.add(new JsonFormatHandler());
            case EXCEL -> handlers.add(new ExcelFormatHandler());
            }
        }
        return handlers;
    }
}
