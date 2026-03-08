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
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.tasks.ObjectExportLoop;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Abstract base class for format-specific export implementations.
 * <p>
 * The engine calls all eight hooks unconditionally; handlers override only what
 * they need. Two hooks ({@code observeObject}, {@code observeField}) serve as
 * schema-observation callbacks (XSD registration) — all other handlers leave
 * them as no-ops. {@code onReferencedClasses} has a default implementation
 * that replaces the old {@code ReferencedClassesExporter}.
 */
public abstract class FormatHandler {

    // ── Public fields ─────────────────────────────────────────────────────────

    /** The format this handler implements. */
    public final ExportFormat format;

    /** Current output writer. Set by the engine before each {@code open} call. */
    public StructuredWriter writer;

    /**
     * Per-format set of exported object IDs — prevents writing the same object
     * twice within one format's output. Shared statistics and reference
     * tracking live on {@link ExportContext} instead.
     */
    public final Set<Long> exportedIds = new HashSet<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    protected FormatHandler(ExportFormat format) {
        this.format = format;
    }

    // ── Abstract methods ──────────────────────────────────────────────────────

    /**
     * Returns the output file extension including the leading dot,
     * e.g. {@code ".xml"}, {@code ".html"}, {@code ".json"}, {@code ".xlsx"}.
     */
    public abstract String extension();

    /**
     * Returns the human-readable display name for UI labels,
     * e.g. {@code "XML"}, {@code "HTML"}, {@code "JSON"}, {@code "Excel"}.
     */
    public abstract String displayName();

    /**
     * Creates a new {@link StructuredWriter} for the given output file.
     * Each implementation instantiates its own {@code StructuredWriterAPI}
     * directly (no {@code StructuredWriterProvider}).
     * <p>
     * Implementations should pass {@code filePath} as the third argument to
     * the {@code StructuredWriter} constructor so that {@code writer.outputPath}
     * is always set — even when the underlying {@code Writer} is in-memory.
     */
    protected abstract StructuredWriter createWriter(Path filePath) throws IOException;

    /**
     * Public facade: creates a writer for {@code filePath} and assigns it to
     * {@link #writer}. Called by the engine before each {@code open} call.
     */
    public final void openWriter(Path filePath) throws IOException {
        this.writer = createWriter(filePath);
    }

    // ── Eight hooks ───────────────────────────────────────────────────────────

    /**
     * Called once before any module is processed. Default: no-op.
     */
    public void init(ExportContext ctx) throws Exception {
    }

    /**
     * Called at the start of each class data file, after {@code writer} has
     * been set by the engine. Default opens the standard two-level structure:
     * <pre>
     *   writer.openStructure("export")
     *   writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()))
     *   writer.openStructure("objects")
     * </pre>
     */
    public void open(ExportContext ctx) throws Exception {
        writer.openStructure("export");
        if (ctx.schemaClass != null) {
            writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()));
        }
        writer.openStructure("objects");
    }

    /**
     * Called once per object <em>before</em> {@code onObject}. Not for content
     * writing — for schema observation only (e.g. XSD registration).
     * Default: no-op.
     */
    public void observeObject(ExportContext ctx) throws Exception {
    }

    /**
     * Called after {@code observeObject}, before field export. Returns
     * {@code true} if the handler has fully written this object (field loop is
     * skipped); {@code false} to proceed with the default field pipeline.
     * <p>
     * Default opens the object element:
     * <pre>
     *   Map&lt;String,String&gt; attrs = exportNativeIds ? Map.of("id", objectId) : null;
     *   writer.openStructure(ctx.schemaClass.destinationName, attrs);
     *   return false;
     * </pre>
     * When {@code false} is returned the engine calls
     * {@code writer.closeStructure(ctx.schemaClass.destinationName)} after the
     * field loop.
     */
    public boolean onObject(ExportContext ctx) throws Exception {
        if (ctx.schemaClass == null) return true; // no schema match — skip object safely
        Map<String, String> attrs = null;
        if (ctx.operation.exportNativeIds) {
            attrs = Map.of("id", String.valueOf(ctx.currentObject().objectId));
        }
        writer.openStructure(ctx.schemaClass.destinationName, attrs);
        return false;
    }

    /**
     * Called once per schema field <em>before</em> {@code onField}. Not for
     * content writing — for schema observation only (e.g. XSD registration).
     * Default: no-op.
     */
    public void observeField(ExportContext ctx) throws Exception {
    }

    /**
     * Called after {@code observeField}, before the default field pipeline.
     * Returns {@code true} if the handler has fully written this field (default
     * pipeline is skipped); {@code false} to let the pipeline handle it.
     * Default: {@code return false}.
     */
    public boolean onField(ExportContext ctx) throws Exception {
        return false;
    }

    /**
     * Called after the data file is fully written. Default closes the two
     * structures opened by {@code open} and flushes the writer:
     * <pre>
     *   writer.closeStructure("objects")
     *   writer.closeStructure("export")
     *   writer.writer.flush()
     * </pre>
     */
    public void close(ExportContext ctx) throws Exception {
        writer.closeStructure("objects");
        writer.closeStructure("export");
        writer.writer.flush();
    }

    /**
     * Called once per handler after all modules finish, before {@code done}.
     * <p>
     * Default implementation replaces {@code ReferencedClassesExporter}: it
     * exports every class in {@code ctx.referencedClassTracker} that has not
     * already been exported, writing files under {@code Referenced/}. Reference
     * tracking is disabled during this pass to prevent infinite recursion.
     * The recursive use of {@code open}/{@code close} ensures format-specific
     * post-processing (HTML viewer, XSD registration) applies automatically.
     */
    public void onReferencedClasses(ExportContext ctx) throws Exception {
        if (ctx.referencedClassTracker == null) return;
        Set<String> toExport = ctx.referencedClassTracker.getReferencedClasses();
        if (toExport.isEmpty()) return;

        Path referencedPath = ctx.basePath.resolve("Referenced");
        Files.createDirectories(referencedPath);

        ReferencedClassTracker saved = ctx.referencedClassTracker;
        ctx.referencedClassTracker = null;
        try {
            for (String className : toExport) {
                if (saved.isReferencedClassExported(className)) continue;

                DOSchemaClass schemaClass = ctx.operation.referenceSchema.findClassByName(className);
                DOSchemaClass dbSchemaClass = ctx.operation.databaseSchema.findClassByName(className);
                if (schemaClass == null || dbSchemaClass == null) continue;

                ctx.setClass(schemaClass, null);
                Path filePath = referencedPath.resolve(schemaClass.destinationName + extension());
                this.writer = createWriter(filePath);
                this.open(ctx);
                new ObjectExportLoop(ctx, this).run(dbSchemaClass);
                this.close(ctx);
                ctx.clearClass();
                saved.markReferencedClassAsExported(className);
            }
        } finally {
            ctx.referencedClassTracker = saved;
        }
    }

    /**
     * Called after {@code onReferencedClasses}, once per handler, for final
     * tasks. Default: no-op.
     */
    public void done(ExportContext ctx) throws Exception {
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Creates handler instances for the requested formats.
     *
     * @param formats     list of formats to export
     * @param generateXsd whether to generate XSD (passed to {@link XmlFormatHandler})
     * @return one handler per format, in request order
     */
    public static List<FormatHandler> create(List<ExportFormat> formats, boolean generateXsd) {
        List<FormatHandler> handlers = new ArrayList<>();
        for (ExportFormat format : formats) {
            switch (format) {
                case XML  -> handlers.add(new XmlFormatHandler(generateXsd));
                case HTML -> handlers.add(new HtmlFormatHandler());
                case JSON -> handlers.add(new JsonFormatHandler());
                case EXCEL -> handlers.add(new ExcelFormatHandler());
            }
        }
        return handlers;
    }
}
