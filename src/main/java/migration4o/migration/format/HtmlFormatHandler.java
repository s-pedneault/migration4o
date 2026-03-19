package migration4o.migration.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import migration4o.migration.ExportFormat;
import migration4o.migration.SummaryGenerator;
import migration4o.migration.SummaryGenerator.IDEntiteResult;
import migration4o.migration.tasks.NavTreeBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;
import migration4o.util.JsViewerHtmlGenerator;
import migration4o.util.SchemaUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterJS;

/**
 * Format handler that produces self-contained {@code .html} files — one per exported class — with the data embedded inline as a {@code <script>} block.
 * <p>
 * No intermediate {@code .js} file is written to disk. The JS serialisation layer ({@link StructuredWriterJS}) writes into an in-memory {@link StringWriter}; {@link #close(ExportCurrentState)} wraps the accumulated script in the full HTML template and writes the single {@code .html} file.
 * <p>
 * Own state: {@code cachedNavJson}, {@code idEntiteTargetCache}, {@code idEntiteSummaryCache}. Overrides four hooks: {@code init}, {@code onObject}, {@code onField}, {@code close}.
 */
public class HtmlFormatHandler extends FormatHandler {

    private String cachedNavJson = "[]";
    private final Map<String, Long> idEntiteTargetCache = new HashMap<>();
    private final Map<Long, String> idEntiteSummaryCache = new HashMap<>();
    /**
     * Captured in {@link #open} so {@link #close} has the class even after exportObject nulls ctx.schemaClass.
     */
    private migration4o.models.schema.DOSchemaClass currentSchemaClass;
    /** Captured in {@link #open} — classRef title override (may be null). */
    private String currentConfigTitle;
    /**
     * Captured in {@link #open} — default columns JSON ("null" when not configured).
     */
    private String currentDefaultColumnsJson;
    /**
     * Temp file path for streaming JS data to disk; cleaned up in {@link #close}.
     */
    private Path currentTempJsPath;

    // ── Cross-reference collection
    // ────────────────────────────────────────────────

    /**
     * One back-reference entry: a record in a source entity that references a record in a target entity.
     */
    static final class BackRefEntry {
        String sourceEntityDestName;
        String sourceEntityLabel; // human-readable display name (title or dest
                                  // name)
        String sourceId; // String.valueOf(objectId) of the source record
        String sourceSummary; // _summary of the source record
        String sourceHref; // relative href from html root to the source page
        String fieldTitle; // human-readable field title (e.g. "Dossier
                           // adresse")
    }

    /**
     * Registry for all back-references collected during the full export. Structure: targetEntityDestName → targetId → list of sources.
     */
    private final Map<String, Map<String, List<BackRefEntry>>> crossRefMap = new LinkedHashMap<>();

    /** DB4O native ID of the root object currently being exported. */
    private long currentRootObjectId = -1;
    /** Summary of the root object currently being exported. */
    private String currentRootObjectSummary = null;
    /**
     * Entries added for the current entity class (href set later in {@link #close}).
     */
    private final List<BackRefEntry> pendingHrefEntries = new ArrayList<>();
    /** HTML output root — set in {@link #init}. */
    private Path htmlBasePath = null;
    /**
     * Maps each exported root record's DB4O native object ID to the HTML file it was written into. Used by {@link #patchCrossRefsIntoHtmlFiles()} to find the exact file that contains a given target record, regardless of how many HTML files the same class is split across.
     */
    private final Map<Long, Path> objectIdToHtmlPath = new LinkedHashMap<>();

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
     * Creates a disk-backed writer that streams JS data to a temp file ({@code baseName.js.tmp}) so large exports never accumulate in memory. {@link #close(ExportCurrentState)} assembles the final HTML by streaming the temp file into the template, then deletes it.
     */
    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : "export.html";
        String baseName = fileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0)
            baseName = baseName.substring(0, dot);
        this.currentTempJsPath = filePath.resolveSibling(baseName + ".js.tmp");
        Files.createDirectories(filePath.getParent());
        java.io.Writer fileWriter = Files.newBufferedWriter(currentTempJsPath, StandardCharsets.UTF_8);
        return new StructuredWriter(new StructuredWriterJS(), fileWriter, filePath);
    }

    /**
     * Builds the nav tree from the operation's exported modules and serialises it to JSON. Also writes the {@code index.html} welcome page at the db output root.
     */
    @Override
    public void init(ExportCurrentState ctx) throws Exception {
        // Configure locale-aware formatting for summaries and viewer language
        SummaryGenerator.setExportLanguage(ctx.request.exportLanguage);
        JsViewerHtmlGenerator.setExportLanguage(ctx.request.exportLanguage);

        if (ctx.exportModules != null && !ctx.exportModules.isEmpty()) {
            // Enable welcome-page generation in NavTreeBuilder
            ctx.generateHtmlViewer = true;
            // Build nav tree rooted at the html/ sub-folder so that hrefs are
            // correct relative to the index.html and viewer files placed there.
            Path htmlBase = ctx.request.getBaseOutputPath(ctx.request.baseOutputPath).resolve(folderName());
            htmlBasePath = htmlBase.toAbsolutePath().normalize();
            new NavTreeBuilder(ctx).build(ctx.exportModules, htmlBase);
            cachedNavJson = ctx.cachedNavJson;
        }
    }

    /**
     * Captures the class and config title so {@link #close} can use them after exportObject nulls ctx.
     */
    @Override
    public void open(ExportCurrentState ctx) throws Exception {
        this.currentSchemaClass = ctx.schemaClass;
        this.currentConfigTitle = (ctx.exportConfig != null) ? ctx.exportConfig.getTitle() : null;
        this.currentDefaultColumnsJson = (ctx.exportConfig != null && ctx.exportConfig.hasDefaultColumns()) ? ctx.exportConfig.getDefaultColumnsJson() : "null";
        pendingHrefEntries.clear();
        super.open(ctx);
    }

    /**
     * For IDEntite classes: resolves a human-readable label and writes a flat element, skipping the nested field loop. Falls through to the default open-structure behaviour for all other classes.
     */
    @Override
    public boolean onObject(ExportCurrentState ctx) throws Exception {
        if (ctx.schemaClass == null)
            return false;

        // IDEntite: attempt to resolve to a human-readable label
        if (ctx.schemaClass.isIDEntite()) {
            Object obj = ctx.currentObject().obj;
            IDEntiteResult result = SummaryGenerator.resolveIDEntiteResult(ctx.request.container, obj, ctx.schemaClass, ctx.request.referenceSchema, ctx.request.databaseSchema, idEntiteTargetCache, idEntiteSummaryCache);
            // Collect back-ref for embedded IDEntite objects
            // (embedContents=true path)
            if (result.targetObjectId != null && currentSchemaClass != null && !currentSchemaClass.isIDEntite()) {
                collectBackRef(ctx, ctx.schemaClass, result.targetObjectId);
            }
            if (result.label != null && !result.label.isBlank()) {
                Map<String, String> idEntiteAttrs = result.targetObjectId != null ? java.util.Collections.singletonMap("_id", String.valueOf(result.targetObjectId)) : null;
                writer.elementWithContent(SchemaUtil.stripIdPrefix(ctx.schemaClass.attributes.destinationName), idEntiteAttrs, result.label, false);
                return true; // fully written — skip field loop
            }
            // No label resolved — fall through to default structure export
        }

        // Default: open a structure with id and optional summary attributes.
        // The HTML viewer always needs the native object id on root objects:
        // it drives CROSS_REFS lookups and the ?open= URL parameter regardless
        // of whether the user enabled exportNativeIds for XML output.
        Map<String, String> attrs = null;
        if (ctx.isRootObject() || ctx.request.exportNativeIds || hasSummary(ctx.schemaClass)) {
            attrs = new java.util.LinkedHashMap<>();
            // Always emit id on root objects for the viewer; also emit on all
            // objects when exportNativeIds is explicitly requested.
            if (ctx.isRootObject() || ctx.request.exportNativeIds) {
                attrs.put("id", String.valueOf(ctx.currentObject().objectId));
            }
            if (hasSummary(ctx.schemaClass)) {
                String summary = SummaryGenerator.generate(ctx.request.container, ctx.currentObject().obj, ctx.schemaClass, ctx.request.referenceSchema, ctx.request.databaseSchema);
                if (summary != null && !summary.isBlank()) {
                    attrs.put("_summary", summary);
                    if (ctx.isRootObject()) {
                        currentRootObjectId = ctx.currentObject().objectId;
                        currentRootObjectSummary = summary;
                    }
                }
            }
            if (attrs.isEmpty())
                attrs = null;
        }
        if (ctx.isRootObject()) {
            currentRootObjectId = ctx.currentObject().objectId;
            // Track this record's exact HTML file so
            // patchCrossRefsIntoHtmlFiles
            // can find it regardless of how many files the class is split
            // across.
            if (currentRootObjectId > 0 && writer != null && writer.outputPath != null) {
                objectIdToHtmlPath.put(currentRootObjectId, writer.outputPath);
            }
        }
        writer.openStructure(ctx.schemaClass.attributes.destinationName, attrs);
        return false;
    }

    /**
     * For non-embedded IDEntite field references: resolves a human-readable label and writes it as flat content, skipping the default pipeline.
     */
    @Override
    public boolean onField(ExportCurrentState ctx) throws Exception {
        if (ctx.field == null || ctx.field.attributes.embedContents || ctx.fieldValue == null)
            return false;

        try {
            String className = ClassUtil.getClassName(ctx.fieldValue);
            DOSchemaClass fieldClass = ctx.request.referenceSchema.findClassByName(className);
            if (fieldClass == null || !fieldClass.isIDEntite())
                return false;

            IDEntiteResult result = SummaryGenerator.resolveIDEntiteResult(ctx.request.container, ctx.fieldValue, fieldClass, ctx.request.referenceSchema, ctx.request.databaseSchema, idEntiteTargetCache, idEntiteSummaryCache);
            // Collect back-reference whenever target is resolved (even if label
            // is absent)
            if (result.targetObjectId != null && currentSchemaClass != null && !currentSchemaClass.isIDEntite()) {
                collectBackRef(ctx, fieldClass, result.targetObjectId);
            }
            if (result.targetObjectId != null) {
                // Always intercept when we know the DB4O target ID, even if
                // no human-readable label is available. Using the mId as
                // fallback display text means the viewer writes
                // {"@attributes":{"_id":"<db4oId>"},"#text":"<mId>"} and the
                // cross-page link uses the correct DB4O native ID — never the
                // raw application-level mId that would silently fail
                // navigation.
                String displayText = (result.label != null && !result.label.isBlank()) ? result.label : (result.mId != null ? String.valueOf(result.mId) : null);
                if (displayText != null) {
                    Map<String, String> idEntiteAttrs = java.util.Collections.singletonMap("_id", String.valueOf(result.targetObjectId));
                    // Use destinationName as-is (no prefix stripping) so the
                    // data key matches
                    // the SCHEMA_FIELDS path used by pointsToByPath in the JS
                    // viewer.
                    writer.elementWithContent(ctx.field.attributes.destinationName, idEntiteAttrs, displayText, false);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Non-persistent object or lookup failure — fall through to default
        }
        return false;
    }

    /** Maximum back-references stored per target record (display cap). */
    private static final int BACK_REF_CAP_PER_RECORD = 25;

    /**
     * Records a back-reference from the root entity currently being exported to the target entity identified by {@code targetObjectId}.
     */
    private void collectBackRef(ExportCurrentState ctx, DOSchemaClass idEntiteClass, long targetObjectId) {
        try {
            // Resolve target entity class via idEntiteClass.attributes.pointsTo
            String expectedType = idEntiteClass.attributes.pointsTo;
            if (expectedType == null || expectedType.isEmpty())
                return;
            DOSchemaClass targetClass = ctx.request.referenceSchema.findClassByName(expectedType);
            if (targetClass == null || targetClass.attributes.destinationName == null)
                return;

            String targetDestName = targetClass.attributes.destinationName;
            String targetId = String.valueOf(targetObjectId);

            // Cap refs per target record to avoid bloating the index
            List<BackRefEntry> existing = crossRefMap.computeIfAbsent(targetDestName, k -> new LinkedHashMap<>()).computeIfAbsent(targetId, k -> new ArrayList<>());
            if (existing.size() >= BACK_REF_CAP_PER_RECORD)
                return;

            // Deduplicate: same source record can reference the same target
            // through multiple fields — only keep the first occurrence per
            // source id
            long sourceId = currentRootObjectId >= 0 ? currentRootObjectId : ctx.objectChain.get(0).objectId;
            String sourceIdStr = String.valueOf(sourceId);
            for (BackRefEntry prev : existing) {
                if (sourceIdStr.equals(prev.sourceId))
                    return;
            }

            // Source is always the root entity currently being exported
            String sourceDestName = currentSchemaClass.attributes.destinationName;

            // Skip self-references: a record referencing itself is meaningless
            if (sourceDestName != null && sourceDestName.equals(targetDestName) && sourceIdStr.equals(targetId))
                return;

            String sourceEntityLabel = (currentSchemaClass.attributes.title != null && !currentSchemaClass.attributes.title.isBlank()) ? currentSchemaClass.attributes.title : sourceDestName;

            BackRefEntry entry = new BackRefEntry();
            entry.sourceEntityDestName = sourceDestName;
            entry.sourceEntityLabel = sourceEntityLabel;
            entry.sourceId = sourceIdStr;
            entry.sourceSummary = currentRootObjectSummary;
            entry.sourceHref = null; // backfilled in close()
            entry.fieldTitle = (ctx.field != null && ctx.field.attributes.title != null) ? ctx.field.attributes.title : null;

            existing.add(entry);
            pendingHrefEntries.add(entry);
        } catch (Exception ignored) {
            // Cross-ref collection is best-effort
        }
    }

    /**
     * Flushes and closes the temp JS file, then streams it into the HTML template (never loading the full JS data into memory). Deletes the temp file when done.
     */
    @Override
    public void close(ExportCurrentState ctx) throws Exception {
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
            // Backfill href for all back-ref entries collected during this
            // entity
            if (!pendingHrefEntries.isEmpty() && htmlBasePath != null && outputPath != null) {
                try {
                    String entityHref = htmlBasePath.relativize(outputPath.toAbsolutePath().normalize()).toString().replace('\\', '/');
                    for (BackRefEntry e : pendingHrefEntries) {
                        e.sourceHref = entityHref;
                    }
                } catch (Exception ignored) {
                }
            }
            pendingHrefEntries.clear();
            // objectIdToHtmlPath is built record-by-record in onObject().
            currentRootObjectId = -1;
            currentRootObjectSummary = null;
            this.currentSchemaClass = null;
            this.currentConfigTitle = null;
            this.currentDefaultColumnsJson = null;
        }
    }

    /**
     * After all classes (including referenced ones) are exported, regenerates the welcome page so it can report the definitive exported-object count.
     */
    @Override
    public void done(ExportCurrentState ctx) throws Exception {
        if (!ctx.generateHtmlViewer)
            return;
        try {
            java.nio.file.Path base = ctx.request.getBaseOutputPath(ctx.request.baseOutputPath).resolve(folderName());
            String dbName = ctx.request.getDatabaseFolderName();
            int objectCount = ctx.statistics != null ? ctx.statistics.getUniqueExportedCount() : this.exportedIds.size();
            JsViewerHtmlGenerator.writeWelcomePage(base, dbName, cachedNavJson, ctx.htmlWelcomeModuleCount, ctx.htmlWelcomeClassCount, objectCount);
        } catch (Exception e) {
            System.err.println("Warning: failed to regenerate welcome page in done(): " + e.getMessage());
        }
        // Patch cross-reference data inline into each entity's HTML file
        if (!crossRefMap.isEmpty()) {
            try {
                patchCrossRefsIntoHtmlFiles();
            } catch (Exception e) {
                System.err.println("Warning: failed to patch cross-refs into HTML files: " + e.getMessage());
            }
        }
    }

    /**
     * For each entity that has back-references pointing to it, reads its already-written HTML file and replaces the cross-ref placeholder ({@code null} followed by the XREF comment token) with the per-entity cross-ref JSON inline — keeping every HTML file completely self-contained (no external {@code crossrefs.js} dependency).
     */
    private void patchCrossRefsIntoHtmlFiles() {
        final String PLACEHOLDER = "null/*XREF*/";
        // Group the cross-ref data by the exact HTML file that contains each
        // target record.
        Map<Path, Map<String, List<BackRefEntry>>> byFile = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<BackRefEntry>>> targetEntry : crossRefMap.entrySet()) {
            for (Map.Entry<String, List<BackRefEntry>> idEntry : targetEntry.getValue().entrySet()) {
                long targetId;
                try {
                    targetId = Long.parseLong(idEntry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                Path htmlPath = objectIdToHtmlPath.get(targetId);
                if (htmlPath == null)
                    continue;
                byFile.computeIfAbsent(htmlPath, k -> new LinkedHashMap<>()).computeIfAbsent(idEntry.getKey(), k -> new ArrayList<>()).addAll(idEntry.getValue());
            }
        }
        for (Map.Entry<Path, Map<String, List<BackRefEntry>>> fileEntry : byFile.entrySet()) {
            Path htmlPath = fileEntry.getKey();
            if (!Files.exists(htmlPath))
                continue;
            try {
                String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
                if (!html.contains(PLACEHOLDER))
                    continue;
                StringBuilder sb = new StringBuilder();
                appendEntityRecordsJson(sb, fileEntry.getValue());
                String replaced = html.replace(PLACEHOLDER, sb.toString() + "/*XREF*/");
                Files.writeString(htmlPath, replaced, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Warning: failed to patch cross-refs into " + htmlPath + ": " + e.getMessage());
            }
        }
    }

    /**
     * Serialises the id→refs map for a single target entity. Produces: {@code {"id1":[{...}],"id2":[{...}]}}.
     */
    private void appendEntityRecordsJson(StringBuilder sb, Map<String, List<BackRefEntry>> idMap) {
        sb.append('{');
        boolean firstId = true;
        for (Map.Entry<String, List<BackRefEntry>> idEntry : idMap.entrySet()) {
            if (!firstId)
                sb.append(',');
            firstId = false;
            sb.append('"').append(escapeJsonStr(idEntry.getKey())).append('"').append(':').append('[');
            boolean firstRef = true;
            for (BackRefEntry e : idEntry.getValue()) {
                if (!firstRef)
                    sb.append(',');
                firstRef = false;
                sb.append('{');
                sb.append('"').append("entity").append('"').append(':').append('"').append(escapeJsonStr(e.sourceEntityDestName)).append('"');
                sb.append(',').append('"').append("label").append('"').append(':').append('"').append(escapeJsonStr(e.sourceEntityLabel)).append('"');
                sb.append(',').append('"').append("id").append('"').append(':').append('"').append(escapeJsonStr(e.sourceId)).append('"');
                if (e.sourceSummary != null && !e.sourceSummary.isBlank()) {
                    sb.append(',').append('"').append("summary").append('"').append(':').append('"').append(escapeJsonStr(e.sourceSummary)).append('"');
                }
                if (e.sourceHref != null && !e.sourceHref.isBlank()) {
                    sb.append(',').append('"').append("href").append('"').append(':').append('"').append(escapeJsonStr(e.sourceHref)).append('"');
                }
                if (e.fieldTitle != null && !e.fieldTitle.isBlank()) {
                    sb.append(',').append('"').append("fieldTitle").append('"').append(':').append('"').append(escapeJsonStr(e.fieldTitle)).append('"');
                }
                sb.append('}');
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static String escapeJsonStr(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ── Private helpers
    // ───────────────────────────────────────────────────────

    private static boolean hasSummary(DOSchemaClass sc) {
        return sc != null && sc.attributes.summary != null && !sc.attributes.summary.isEmpty();
    }
}
