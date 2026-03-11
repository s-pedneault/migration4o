package migration4o.migration.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import migration4o.migration.ExportFormat;
import migration4o.migration.SummaryGenerator;
import migration4o.migration.tasks.NavTreeBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;
import migration4o.util.JsViewerHtmlGenerator;
import migration4o.util.SchemaUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterJS;

/**
 * Format handler that produces self-contained {@code .html} files — one per
 * exported class — with the data embedded inline as a {@code <script>} block.
 * <p>
 * No intermediate {@code .js} file is written to disk. The JS serialisation
 * layer ({@link StructuredWriterJS}) writes into an in-memory
 * {@link StringWriter}; {@link #close(ExportContext)} wraps the accumulated
 * script in the full HTML template and writes the single {@code .html} file.
 * <p>
 * Own state: {@code cachedNavJson}, {@code idEntiteTargetCache},
 * {@code idEntiteSummaryCache}.
 * Overrides four hooks: {@code init}, {@code onObject}, {@code onField},
 * {@code close}.
 */
public class HtmlFormatHandler extends FormatHandler {

    private String cachedNavJson = "[]";
    private final Map<String, Long> idEntiteTargetCache = new HashMap<>();
    private final Map<Long, String> idEntiteSummaryCache = new HashMap<>();
    /** Captured in {@link #open} so {@link #close} has the class even after exportObject nulls ctx.schemaClass. */
    private migration4o.models.schema.DOSchemaClass currentSchemaClass;
    /** Captured in {@link #open} — classRef title override (may be null). */
    private String currentConfigTitle;
    /** Captured in {@link #open} — default columns JSON ("null" when not configured). */
    private String currentDefaultColumnsJson;
    /** Temp file path for streaming JS data to disk; cleaned up in {@link #close}. */
    private Path currentTempJsPath;

    public HtmlFormatHandler() {
        super(ExportFormat.HTML);
    }

    @Override
    public String extension() {
        return ".html";
    }

    @Override
    public String displayName() {
        return "HTML";
    }

    /**
     * Creates a disk-backed writer that streams JS data to a temp file
     * ({@code baseName.js.tmp}) so large exports never accumulate in memory.
     * {@link #close(ExportContext)} assembles the final HTML by streaming
     * the temp file into the template, then deletes it.
     */
    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : "export.html";
        String baseName = fileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0)
            baseName = baseName.substring(0, dot);
        this.currentTempJsPath = filePath.resolveSibling(baseName + ".js.tmp");
        java.io.Writer fileWriter = Files.newBufferedWriter(currentTempJsPath, StandardCharsets.UTF_8);
        return new StructuredWriter(new StructuredWriterJS(), fileWriter, filePath);
    }

    /**
     * Builds the nav tree from the operation's exported modules and serialises
     * it to JSON. Also writes the {@code index.html} welcome page at the db
     * output root.
     */
    @Override
    public void init(ExportContext ctx) throws Exception {
        if (ctx.operation.exportModules != null && !ctx.operation.exportModules.isEmpty()) {
            // Enable welcome-page generation in NavTreeBuilder
            ctx.operation.generateHtmlViewer = true;
            // NavTreeBuilder stores results in operation.navTree / cachedNavJson;
            // we copy cachedNavJson to our own field.
            new NavTreeBuilder(ctx.operation).build(ctx.operation.exportModules, ctx.operation.exportModulePaths, ctx.operation.baseOutputPath);
            cachedNavJson = ctx.operation.cachedNavJson;
        }
    }

    /** Captures the class and config title so {@link #close} can use them after exportObject nulls ctx. */
    @Override
    public void open(ExportContext ctx) throws Exception {
        this.currentSchemaClass = ctx.schemaClass;
        this.currentConfigTitle = (ctx.exportConfig != null) ? ctx.exportConfig.getTitle() : null;
        this.currentDefaultColumnsJson = (ctx.exportConfig != null && ctx.exportConfig.hasDefaultColumns()) ? ctx.exportConfig.getDefaultColumnsJson() : "null";
        super.open(ctx);
    }

    /**
     * For IDEntite classes: resolves a human-readable label and writes a flat
     * element, skipping the nested field loop. Falls through to the default
     * open-structure behaviour for all other classes.
     */
    @Override
    public boolean onObject(ExportContext ctx) throws Exception {
        if (ctx.schemaClass == null)
            return false;

        // IDEntite: attempt to resolve to a human-readable label
        if (ctx.schemaClass.isIDEntite(ctx.operation.databaseSchema)) {
            Object obj = ctx.currentObject().obj;
            String refLabel = SummaryGenerator.resolveIDEntiteLabel(ctx.operation.container, obj, ctx.schemaClass, ctx.operation.referenceSchema, ctx.operation.databaseSchema, idEntiteTargetCache, idEntiteSummaryCache);
            if (refLabel != null && !refLabel.isBlank()) {
                writer.elementWithContent(stripIdPrefix(ctx.schemaClass.destinationName), null, refLabel, false);
                return true; // fully written — skip field loop
            }
            // No label resolved — fall through to default structure export
        }

        // Default: open a structure with optional native id and summary attributes
        Map<String, String> attrs = null;
        if (ctx.operation.exportNativeIds || hasSummary(ctx.schemaClass)) {
            attrs = new java.util.LinkedHashMap<>();
            if (ctx.operation.exportNativeIds) {
                attrs.put("id", String.valueOf(ctx.currentObject().objectId));
            }
            if (hasSummary(ctx.schemaClass)) {
                String summary = SummaryGenerator.generate(ctx.operation.container, ctx.currentObject().obj, ctx.schemaClass, ctx.operation.referenceSchema);
                if (summary != null && !summary.isBlank()) {
                    attrs.put("_summary", summary);
                }
            }
            if (attrs.isEmpty())
                attrs = null;
        }
        writer.openStructure(ctx.schemaClass.destinationName, attrs);
        return false;
    }

    /**
     * For non-embedded IDEntite field references: resolves a human-readable
     * label and writes it as flat content, skipping the default pipeline.
     */
    @Override
    public boolean onField(ExportContext ctx) throws Exception {
        if (ctx.field == null || ctx.field.embedContents || ctx.fieldValue == null)
            return false;

        try {
            String className = ClassUtil.getClassName(ctx.fieldValue);
            DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, ctx.operation.referenceSchema);
            if (fieldClass == null || !fieldClass.isIDEntite(ctx.operation.databaseSchema))
                return false;

            String refLabel = SummaryGenerator.resolveIDEntiteLabel(ctx.operation.container, ctx.fieldValue, fieldClass, ctx.operation.referenceSchema, ctx.operation.databaseSchema, idEntiteTargetCache, idEntiteSummaryCache);
            if (refLabel != null && !refLabel.isBlank()) {
                writer.elementWithContent(stripIdPrefix(ctx.field.destinationName), null, refLabel, false);
                return true;
            }
        } catch (Exception ignored) {
            // Non-persistent object or lookup failure — fall through to default
        }
        return false;
    }

    /**
     * Flushes and closes the temp JS file, then streams it into the HTML template
     * (never loading the full JS data into memory). Deletes the temp file when done.
     */
    @Override
    public void close(ExportContext ctx) throws Exception {
        // Close the JS structures so onDocumentComplete writes the final ";\n"
        writer.closeStructure("objects");
        writer.closeStructure("export");
        writer.writer.flush();
        writer.writer.close(); // flush + close the temp JS file on disk

        // Compute baseHref: one "../" per module nesting level
        int levels = ctx.moduleChain.size();
        String baseHref = levels == 0 ? "./" : "../".repeat(levels);

        String layoutJson = (ctx.exportConfig != null && ctx.exportConfig.hasLayout()) ? ctx.exportConfig.getLayout().toJson() : "null";

        Path outputPath = writer.outputPath;
        try {
            if (outputPath != null && currentTempJsPath != null) {
                Files.createDirectories(outputPath.getParent());
                JsViewerHtmlGenerator.writeViewerFromTempFile(outputPath, currentSchemaClass, currentConfigTitle, currentDefaultColumnsJson, cachedNavJson, baseHref, layoutJson, currentTempJsPath);
            }
        } finally {
            if (currentTempJsPath != null) {
                try {
                    Files.deleteIfExists(currentTempJsPath);
                } catch (Exception ignored) {
                }
                currentTempJsPath = null;
            }
            this.currentSchemaClass = null;
            this.currentConfigTitle = null;
            this.currentDefaultColumnsJson = null;
        }
    }

    /**
     * After all classes (including referenced ones) are exported, regenerates
     * the welcome page so it can report the definitive exported-object count.
     */
    @Override
    public void done(ExportContext ctx) throws Exception {
        if (!ctx.operation.generateHtmlViewer)
            return;
        try {
            java.nio.file.Path base = ctx.operation.getBaseOutputPath(ctx.operation.baseOutputPath);
            String dbName = ctx.operation.getDatabaseFolderName();
            int objectCount = this.exportedIds.size();
            JsViewerHtmlGenerator.writeWelcomePage(base, dbName, cachedNavJson, ctx.operation.htmlWelcomeModuleCount, ctx.operation.htmlWelcomeClassCount, objectCount);
        } catch (Exception e) {
            System.err.println("Warning: failed to regenerate welcome page in done(): " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean hasSummary(DOSchemaClass sc) {
        return sc != null && sc.summary != null && !sc.summary.isEmpty();
    }

    private static String stripIdPrefix(String name) {
        if (name == null || name.isEmpty())
            return name;
        String stripped = name.replaceFirst("(?i)^id\\s*", "");
        if (stripped.isEmpty() || stripped.equals(name))
            return name;
        return Character.isUpperCase(stripped.charAt(0)) ? Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1) : stripped;
    }
}
