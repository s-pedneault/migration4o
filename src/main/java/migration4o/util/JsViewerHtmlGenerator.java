package migration4o.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Generates lightweight HTML viewers for JS exports produced by StructuredWriterJS.
 */
public final class JsViewerHtmlGenerator {

    private static final String TEMPLATE_RESOURCE = "/templates/class-viewer-template.html";
    private static final String WELCOME_TEMPLATE_RESOURCE = "/templates/welcome-template.html";
    private static final String SIDEBAR_CSS_RESOURCE = "/templates/sidebar.css";
    private static final String SIDEBAR_NAV_JS_RESOURCE = "/templates/sidebar-nav.js";

    /**
     * Assets (classpath resources under {@code /assets/}) that are copied to the
     * {@code _App/} sub-folder of every HTML export root.  Add entries here to
     * include new static files automatically on every export.
     */
    private static final String[] HTML_EXPORT_ASSETS = { "/assets/splash.jpg" };
    private static volatile String cachedTemplate;
    private static volatile String cachedWelcomeTemplate;
    private static volatile String cachedSidebarCss;
    private static volatile String cachedSidebarNavJs;

    /**
     * Export language code ({@code "fr"} or {@code "en"}). Set before HTML generation; replaces the {@code __EXPORT_LANGUAGE__} placeholder in the sidebar JS.
     */
    private static volatile String exportLanguage = "fr";

    /**
     * Sets the export language used as the default viewer language.
     */
    public static void setExportLanguage(String language) {
        exportLanguage = (language != null && !language.isBlank()) ? language : "fr";
    }

    private JsViewerHtmlGenerator() {
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass) throws IOException {
        return writeViewerForJs(jsPath, schemaClass, "[]", "./", "null");
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref) throws IOException {
        return writeViewerForJs(jsPath, schemaClass, navItemsJson, baseHref, "null");
    }

    /**
     * Writes a self-contained HTML viewer for in-memory JS data (no intermediate file). The caller provides the complete data script string; this method embeds it directly in the template and writes to {@code outputPath}.
     *
     * @param outputPath destination {@code .html} file
     * @param schemaClass schema class for title / field metadata; may be null
     * @param navItemsJson serialised nav-tree JSON
     * @param baseHref relative path back to the export root (e.g. {@code "../../"})
     * @param layoutJson detail-layout JSON or {@code "null"}
     * @param dataScript pre-built JS data script (the full {@code window.__m4o={…};\n})
     */
    public static Path writeViewerForJs(Path outputPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref, String layoutJson, String dataScript) throws IOException {
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }

        String embeddedJs = (dataScript != null ? dataScript : "").replaceAll("(?i)</script", "<\\/script");

        String fileName = outputPath.getFileName() != null ? outputPath.getFileName().toString() : "export.html";
        String baseName = fileName;
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = fileName.substring(0, extensionIndex);
        }

        String title = baseName;
        String entityName = (schemaClass != null && schemaClass.attributes.title != null && !schemaClass.attributes.title.isBlank()) ? schemaClass.attributes.title : ((schemaClass != null && schemaClass.attributes.destinationName != null && !schemaClass.attributes.destinationName.isBlank()) ? schemaClass.attributes.destinationName : baseName);
        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String base = (baseHref != null && !baseHref.isBlank()) ? baseHref : "./";
        String layout = (layoutJson != null && !layoutJson.isBlank()) ? layoutJson : "null";
        String schemaFieldsJson = buildFieldMetadataJson(schemaClass);

        String html = loadTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__EXPORT_LANGUAGE__", exportLanguage).replace("__BASE_HREF__", base).replace("__NAV_ITEMS__", nav).replace("__DETAIL_LAYOUT__", layout).replace("__SCHEMA_FIELDS__", schemaFieldsJson).replace("__DEFAULT_COLUMNS__", "null").replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName)).replace("__EMBEDDED_JS_DATA__", embeddedJs);

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.write(outputPath, html.getBytes(StandardCharsets.UTF_8));
        return outputPath;
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref, String layoutJson) throws IOException {
        if (jsPath == null) {
            throw new IllegalArgumentException("jsPath must not be null");
        }

        String embeddedJs = Files.readString(jsPath, StandardCharsets.UTF_8);
        embeddedJs = embeddedJs.replaceAll("(?i)</script", "<\\\\/script");

        String jsFileName = jsPath.getFileName() != null ? jsPath.getFileName().toString() : "export.js";
        String baseName = jsFileName;
        int extensionIndex = jsFileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = jsFileName.substring(0, extensionIndex);
        }

        String title = baseName;
        String entityName = (schemaClass != null && schemaClass.attributes.title != null && !schemaClass.attributes.title.isBlank()) ? schemaClass.attributes.title : ((schemaClass != null && schemaClass.attributes.destinationName != null && !schemaClass.attributes.destinationName.isBlank()) ? schemaClass.attributes.destinationName : baseName);
        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String base = (baseHref != null && !baseHref.isBlank()) ? baseHref : "./";
        String layout = (layoutJson != null && !layoutJson.isBlank()) ? layoutJson : "null";
        String schemaFieldsJson = buildFieldMetadataJson(schemaClass);

        Path htmlPath = jsPath.resolveSibling(baseName + ".html");
        String html = loadTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__EXPORT_LANGUAGE__", exportLanguage).replace("__BASE_HREF__", base).replace("__NAV_ITEMS__", nav).replace("__DETAIL_LAYOUT__", layout).replace("__SCHEMA_FIELDS__", schemaFieldsJson).replace("__DEFAULT_COLUMNS__", "null").replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName)).replace("__EMBEDDED_JS_DATA__", embeddedJs);

        if (htmlPath.getParent() != null) {
            Files.createDirectories(htmlPath.getParent());
        }
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(jsPath);
        return htmlPath;
    }

    /**
     * Writes an index.html welcome page at the given database output root.
     *
     * @param dbRoot         The database root folder (e.g. output/54060/)
     * @param dbName         Human-readable database name shown in the page header
     * @param navItemsJson   Serialised NAV_ITEMS JSON array
     * @param moduleCount    Total number of exported modules (including sub-modules)
     * @param classCount     Number of exported class data files
     * @param objectCount    Total number of exported objects; 0 hides the bubble
     * @param municipality   Optional client municipality info; may be {@code null}
     */
    public static Path writeWelcomePage(Path dbRoot, String dbName, String navItemsJson, int moduleCount, int classCount, int objectCount, MunicipalityInfo municipality) throws IOException {
        if (dbRoot == null) {
            throw new IllegalArgumentException("dbRoot must not be null");
        }

        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String name = (dbName != null && !dbName.isBlank()) ? dbName : "Export";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String objects = objectCount > 0 ? String.format("%,d", objectCount).replace(',', '\u00a0') : "\u2014";

        // Client municipality placeholders
        String clientName = municipality != null && municipality.name != null ? escapeHtml(municipality.name) : "";
        String clientMrc = municipality != null && municipality.mrc != null ? escapeHtml(municipality.mrc) : "";
        String clientRegion = municipality != null && municipality.region != null ? escapeHtml(municipality.region) : "";
        String clientAddr = municipality != null && municipality.address != null ? escapeHtml(municipality.address) : "";
        String clientEmail = municipality != null && municipality.email != null ? escapeHtml(municipality.email) : "";
        String clientWeb = municipality != null && municipality.website != null ? escapeHtml(municipality.website) : "";
        String clientPop = municipality != null && municipality.population != null ? escapeHtml(municipality.population) : "";

        String html = loadWelcomeTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__EXPORT_LANGUAGE__", exportLanguage).replace("__NAV_ITEMS__", nav).replace("__DB_NAME__", escapeHtml(name)).replace("__EXPORT_DATE__", escapeHtml(date)).replace("__MODULE_COUNT__", String.valueOf(moduleCount)).replace("__CLASS_COUNT__", String.valueOf(classCount)).replace("__OBJECT_COUNT__", objects).replace("__CLIENT_NAME__", clientName).replace("__CLIENT_MRC__", clientMrc).replace("__CLIENT_REGION__", clientRegion).replace("__CLIENT_ADDR__", clientAddr).replace("__CLIENT_EMAIL__", clientEmail).replace("__CLIENT_WEB__", clientWeb).replace("__CLIENT_POP__", clientPop);

        Files.createDirectories(dbRoot);
        Path welcomePath = dbRoot.resolve("index.html");
        Files.write(welcomePath, html.getBytes(StandardCharsets.UTF_8));
        copyHtmlAssets(dbRoot);
        return welcomePath;
    }

    /**
     * Backwards-compatible overload — no municipality info.
     */
    public static Path writeWelcomePage(Path dbRoot, String dbName, String navItemsJson, int moduleCount, int classCount, int objectCount) throws IOException {
        return writeWelcomePage(dbRoot, dbName, navItemsJson, moduleCount, classCount, objectCount, null);
    }

    /**
     * Streaming HTML assembler: never loads the full JS data into memory.
     * <p>
     * Splits the processed template at the {@code __EMBEDDED_JS_DATA__} placeholder, writes the header half, then streams {@code jsDataFile} line-by-line (escaping {@code </script} as it goes), then writes the footer half.
     *
     * @param outputPath destination {@code .html} file
     * @param schemaClass schema class for title / field metadata; may be null
     * @param navItemsJson serialised nav-tree JSON
     * @param baseHref relative path back to the export root
     * @param layoutJson detail-layout JSON or {@code "null"}
     * @param jsDataFile temp file containing the pre-built JS data (will NOT be deleted here)
     */
    public static Path writeViewerFromTempFile(Path outputPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref, String layoutJson, Path jsDataFile) throws IOException {
        return writeViewerFromTempFile(outputPath, schemaClass, null, navItemsJson, baseHref, layoutJson, jsDataFile);
    }

    /**
     * Streaming HTML assembler with an optional config title override.
     *
     * @param configTitle display title from {@code classRef title="…"} (highest priority); may be null
     */
    public static Path writeViewerFromTempFile(Path outputPath, DOSchemaClass schemaClass, String configTitle, String navItemsJson, String baseHref, String layoutJson, Path jsDataFile) throws IOException {
        return writeViewerFromTempFile(outputPath, schemaClass, configTitle, "null", navItemsJson, baseHref, layoutJson, jsDataFile);
    }

    /**
     * Streaming HTML assembler with title override and default columns.
     *
     * @param configTitle display title from {@code classRef title="…"} (highest priority); may be null
     * @param defaultColumnsJson JSON array of default column field paths, e.g. {@code ["name","adresse.rue"]}, or {@code "null"}
     */
    public static Path writeViewerFromTempFile(Path outputPath, DOSchemaClass schemaClass, String configTitle, String defaultColumnsJson, String navItemsJson, String baseHref, String layoutJson, Path jsDataFile) throws IOException {
        if (outputPath == null)
            throw new IllegalArgumentException("outputPath must not be null");
        if (jsDataFile == null)
            throw new IllegalArgumentException("jsDataFile must not be null");

        String fileName = outputPath.getFileName() != null ? outputPath.getFileName().toString() : "export.html";
        String baseName = fileName;
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0)
            baseName = fileName.substring(0, extensionIndex);

        String title = baseName;
        String entityName = (configTitle != null && !configTitle.isBlank()) ? configTitle : (schemaClass != null && schemaClass.attributes.title != null && !schemaClass.attributes.title.isBlank()) ? schemaClass.attributes.title : ((schemaClass != null && schemaClass.attributes.destinationName != null && !schemaClass.attributes.destinationName.isBlank()) ? schemaClass.attributes.destinationName : baseName);
        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String base = (baseHref != null && !baseHref.isBlank()) ? baseHref : "./";
        String layout = (layoutJson != null && !layoutJson.isBlank()) ? layoutJson : "null";
        String defaultCols = (defaultColumnsJson != null && !defaultColumnsJson.isBlank()) ? defaultColumnsJson : "null";
        String schemaFieldsJson = buildFieldMetadataJson(schemaClass);

        // Build the full template with all substitutions EXCEPT
        // __EMBEDDED_JS_DATA__
        String template = loadTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__EXPORT_LANGUAGE__", exportLanguage).replace("__BASE_HREF__", base).replace("__NAV_ITEMS__", nav).replace("__DETAIL_LAYOUT__", layout).replace("__SCHEMA_FIELDS__", schemaFieldsJson).replace("__DEFAULT_COLUMNS__", defaultCols).replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName));

        // Split at the placeholder — stream header, then JS data, then footer
        final String PLACEHOLDER = "__EMBEDDED_JS_DATA__";
        int placeholderIndex = template.indexOf(PLACEHOLDER);
        String header = placeholderIndex >= 0 ? template.substring(0, placeholderIndex) : template;
        String footer = placeholderIndex >= 0 ? template.substring(placeholderIndex + PLACEHOLDER.length()) : "";

        if (outputPath.getParent() != null)
            Files.createDirectories(outputPath.getParent());

        try (BufferedWriter out = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8); BufferedReader jsIn = Files.newBufferedReader(jsDataFile, StandardCharsets.UTF_8)) {
            out.write(header);
            String line;
            while ((line = jsIn.readLine()) != null) {
                // Escape </script inside JS data to prevent the browser from
                // ending the script block early
                out.write(line.replaceAll("(?i)</script", "<\\/script"));
                out.write('\n');
            }
            out.write(footer);
        }
        return outputPath;
    }

    /**
     * Copies static HTML export assets (e.g. {@code splash.jpg}) from classpath
     * resources into the {@code _App/} sub-folder of the given HTML export root.
     * Safe to call multiple times — existing files are overwritten.
     */
    public static void copyHtmlAssets(Path htmlBase) throws IOException {
        Path appDir = htmlBase.resolve("_App");
        Files.createDirectories(appDir);
        for (String resource : HTML_EXPORT_ASSETS) {
            String filename = resource.substring(resource.lastIndexOf('/') + 1);
            try (InputStream in = JsViewerHtmlGenerator.class.getResourceAsStream(resource)) {
                if (in != null) {
                    Files.copy(in, appDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Clears the in-memory template caches (useful after resource reload in tests).
     */
    public static void clearCache() {
        cachedTemplate = null;
        cachedWelcomeTemplate = null;
        cachedSidebarCss = null;
        cachedSidebarNavJs = null;
    }

    private static String loadTemplate() throws IOException {
        String existing = cachedTemplate;
        if (existing != null)
            return existing;
        synchronized (JsViewerHtmlGenerator.class) {
            if (cachedTemplate != null)
                return cachedTemplate;
            cachedTemplate = loadResource(TEMPLATE_RESOURCE);
            return cachedTemplate;
        }
    }

    private static String loadWelcomeTemplate() throws IOException {
        String existing = cachedWelcomeTemplate;
        if (existing != null)
            return existing;
        synchronized (JsViewerHtmlGenerator.class) {
            if (cachedWelcomeTemplate != null)
                return cachedWelcomeTemplate;
            cachedWelcomeTemplate = loadResource(WELCOME_TEMPLATE_RESOURCE);
            return cachedWelcomeTemplate;
        }
    }

    private static String loadSidebarCss() throws IOException {
        String existing = cachedSidebarCss;
        if (existing != null)
            return existing;
        synchronized (JsViewerHtmlGenerator.class) {
            if (cachedSidebarCss != null)
                return cachedSidebarCss;
            cachedSidebarCss = loadResource(SIDEBAR_CSS_RESOURCE);
            return cachedSidebarCss;
        }
    }

    private static String loadSidebarNavJs() throws IOException {
        String existing = cachedSidebarNavJs;
        if (existing != null)
            return existing;
        synchronized (JsViewerHtmlGenerator.class) {
            if (cachedSidebarNavJs != null)
                return cachedSidebarNavJs;
            cachedSidebarNavJs = loadResource(SIDEBAR_NAV_JS_RESOURCE);
            return cachedSidebarNavJs;
        }
    }

    private static String loadResource(String resource) throws IOException {
        try (InputStream in = JsViewerHtmlGenerator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing HTML template resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String buildFieldMetadataJson(DOSchemaClass schemaClass) {
        if (schemaClass == null || schemaClass.fields == null) {
            return "[]";
        }

        DOSchema refSchema = null;
        try {
            refSchema = migration4o.schema.DOSchemaService.getInstance().getReferenceSchema();
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        Set<String> seen = new HashSet<>();

        for (DOSchemaField field : schemaClass.fields) {
            if (!field.attributes.isExported) {
                continue;
            }
            String name = field.attributes.destinationName != null ? field.attributes.destinationName : field.attributes.source;
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!seen.add(name)) {
                continue;
            }

            if (!first) {
                sb.append(',');
            }
            first = false;
            appendFieldJson(sb, field, name, 0, refSchema);
        }

        sb.append(']');
        return sb.toString();
    }

    private static void appendFieldJson(StringBuilder sb, DOSchemaField field, String path, int depth, DOSchema refSchema) {
        String name = field.attributes.destinationName != null ? field.attributes.destinationName : field.attributes.source;
        sb.append('{');
        sb.append("\"name\":\"").append(escapeJson(name != null ? name : path)).append("\",");
        sb.append("\"path\":\"").append(escapeJson(path)).append("\",");
        sb.append("\"type\":\"").append(categorizeFieldType(field)).append("\",");
        sb.append("\"collection\":").append(field.attributes.isCollection);

        if (field.attributes.title != null && !field.attributes.title.isBlank()) {
            sb.append(",\"title\":\"").append(escapeJson(field.attributes.title)).append('"');
        }

        // Mark IDEntite fields so the HTML viewer renders them inline
        // (with cross-page link) instead of as expandable nested sections.
        // This applies to both embedded and non-embedded IDEntite fields
        // because the HtmlFormatHandler intercepts all IDEntite references
        // and writes them as flat values with _id attributes.
        if (!field.attributes.isCollection && field.attributes.type != null && refSchema != null) {
            DOSchemaClass typeClass = refSchema.findClassByName(field.attributes.type);
            if (typeClass != null && typeClass.isIDEntite()) {
                sb.append(",\"idEntite\":true");
                // Resolve the target entity destination name for cross-page
                // deep-linking
                String targetFqn = typeClass.attributes.pointsTo;
                if (targetFqn != null && !targetFqn.isBlank()) {
                    DOSchemaClass targetClass = refSchema.findClassByName(targetFqn);
                    if (targetClass != null && targetClass.attributes.destinationName != null && !targetClass.attributes.destinationName.isBlank()) {
                        sb.append(",\"pointsTo\":\"").append(escapeJson(targetClass.attributes.destinationName)).append('"');
                    }
                }
            }
        }

        // Resolve children class: use pre-linked field, or look up in refSchema
        DOSchemaClass childrenClass = field.childrenSchemaClass;
        if (childrenClass == null && refSchema != null) {
            String typeName = field.attributes.isCollection && field.attributes.childrenType != null ? field.attributes.childrenType : (field.attributes.embedContents ? field.attributes.type : null);
            if (typeName != null) {
                childrenClass = refSchema.findClassByName(typeName);
            }
        }
        if (depth < 6 && childrenClass != null) {
            List<DOSchemaField> childFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childrenClass, refSchema);
            // Include subclass-specific fields so polymorphic types
            // (e.g. DetailEnvoi → DetailIntervEnvoi) have all fields indexed for title lookup
            if (refSchema != null && refSchema.getClasses() != null) {
                Set<String> existingNames = new HashSet<>();
                for (DOSchemaField f : childFields) {
                    String fn = f.attributes.destinationName != null ? f.attributes.destinationName : f.attributes.source;
                    if (fn != null)
                        existingNames.add(fn);
                }
                List<DOSchemaField> extra = new ArrayList<>();
                for (DOSchemaClass cls : refSchema.getClasses()) {
                    if (!cls.attributes.source.equals(childrenClass.attributes.source) && cls.isDescendantOf(childrenClass.attributes.source)) {
                        for (DOSchemaField sf : cls.fields) {
                            if (!sf.attributes.isExported)
                                continue;
                            String sfn = sf.attributes.destinationName != null ? sf.attributes.destinationName : sf.attributes.source;
                            if (sfn != null && existingNames.add(sfn)) {
                                extra.add(sf);
                            }
                        }
                    }
                }
                if (!extra.isEmpty()) {
                    childFields = new ArrayList<>(childFields);
                    childFields.addAll(extra);
                }
            }
            sb.append(",\"children\":[");
            boolean childFirst = true;
            Set<String> childSeen = new HashSet<>();
            for (DOSchemaField cf : childFields) {
                if (!cf.attributes.isExported) {
                    continue;
                }
                String cn = cf.attributes.destinationName != null ? cf.attributes.destinationName : cf.attributes.source;
                if (cn == null || cn.isBlank() || !childSeen.add(cn)) {
                    continue;
                }
                if (!childFirst) {
                    sb.append(',');
                }
                childFirst = false;
                appendFieldJson(sb, cf, path + "." + cn, depth + 1, refSchema);
            }
            sb.append(']');
        }

        sb.append('}');
    }

    private static String categorizeFieldType(DOSchemaField field) {
        if (field.attributes.isCollection) {
            return "collection";
        }
        String type = field.attributes.type;
        if (type == null || type.isEmpty()) {
            return "string";
        }
        String lower = type.toLowerCase().replaceAll("\\[\\]", "").trim();

        if (lower.equals("int") || lower.equals("long") || lower.equals("short") || lower.equals("float") || lower.equals("double") || lower.equals("byte") || lower.equals("java.lang.integer") || lower.equals("java.lang.long") || lower.equals("java.lang.double") || lower.equals("java.lang.float") || lower.equals("java.lang.short") || lower.equals("java.lang.byte") || lower.equals("java.math.bigdecimal") || lower.equals("java.math.biginteger")) {
            return "number";
        }

        if (lower.equals("date") || lower.equals("java.util.date") || lower.equals("java.sql.date") || lower.equals("java.sql.timestamp") || lower.equals("java.time.localdate") || lower.equals("java.time.localdatetime") || lower.equals("java.time.zoneddatetime")) {
            return "date";
        }

        if (lower.equals("boolean") || lower.equals("java.lang.boolean")) {
            return "boolean";
        }

        if (lower.equals("string") || lower.equals("java.lang.string") || lower.equals("java.lang.character") || lower.equals("char") || lower.equals("object") || lower.equals("java.lang.object")) {
            return "string";
        }

        return "reference";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}