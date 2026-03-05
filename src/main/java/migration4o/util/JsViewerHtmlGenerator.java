package migration4o.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Generates lightweight HTML viewers for JS exports produced by StructuredWriterJS.
 */
public final class JsViewerHtmlGenerator {

    private static final String TEMPLATE_RESOURCE = "/templates/js-viewer-template.html";
    private static final String WELCOME_TEMPLATE_RESOURCE = "/templates/welcome-template.html";
    private static final String SIDEBAR_CSS_RESOURCE = "/templates/sidebar.css";
    private static final String SIDEBAR_NAV_JS_RESOURCE = "/templates/sidebar-nav.js";
    private static volatile String cachedTemplate;
    private static volatile String cachedWelcomeTemplate;
    private static volatile String cachedSidebarCss;
    private static volatile String cachedSidebarNavJs;

    private JsViewerHtmlGenerator() {
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass) throws IOException {
        return writeViewerForJs(jsPath, schemaClass, "[]", "./", "null");
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref) throws IOException {
        return writeViewerForJs(jsPath, schemaClass, navItemsJson, baseHref, "null");
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
        String entityName = (schemaClass != null && schemaClass.title != null && !schemaClass.title.isBlank()) ? schemaClass.title : ((schemaClass != null && schemaClass.destinationName != null && !schemaClass.destinationName.isBlank()) ? schemaClass.destinationName : baseName);
        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String base = (baseHref != null && !baseHref.isBlank()) ? baseHref : "./";
        String layout = (layoutJson != null && !layoutJson.isBlank()) ? layoutJson : "null";
        String schemaFieldsJson = buildFieldMetadataJson(schemaClass);

        Path htmlPath = jsPath.resolveSibling(baseName + ".html");
        String html = loadTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__BASE_HREF__", base).replace("__NAV_ITEMS__", nav).replace("__DETAIL_LAYOUT__", layout).replace("__SCHEMA_FIELDS__", schemaFieldsJson).replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName)).replace("__EMBEDDED_JS_DATA__", embeddedJs);

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
     * @param dbRoot       The database root folder (e.g. output/54060/)
     * @param dbName       Human-readable database name shown in the page header
     * @param navItemsJson Serialised NAV_ITEMS JSON array
     * @param moduleCount  Number of exported top-level modules
     * @param classCount   Number of exported classes
     */
    public static Path writeWelcomePage(Path dbRoot, String dbName, String navItemsJson, int moduleCount, int classCount) throws IOException {
        if (dbRoot == null) {
            throw new IllegalArgumentException("dbRoot must not be null");
        }

        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String name = (dbName != null && !dbName.isBlank()) ? dbName : "Export";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        String html = loadWelcomeTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__NAV_ITEMS__", nav).replace("__DB_NAME__", escapeHtml(name)).replace("__EXPORT_DATE__", escapeHtml(date)).replace("__MODULE_COUNT__", String.valueOf(moduleCount)).replace("__CLASS_COUNT__", String.valueOf(classCount));

        Files.createDirectories(dbRoot);
        Path welcomePath = dbRoot.resolve("index.html");
        Files.write(welcomePath, html.getBytes(StandardCharsets.UTF_8));
        return welcomePath;
    }

    /** Clears the in-memory template caches (useful after resource reload in tests). */
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
            if (!field.isExported) {
                continue;
            }
            String name = field.destinationName != null ? field.destinationName : field.source;
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
        String name = field.destinationName != null ? field.destinationName : field.source;
        sb.append('{');
        sb.append("\"name\":\"").append(escapeJson(name != null ? name : path)).append("\",");
        sb.append("\"path\":\"").append(escapeJson(path)).append("\",");
        sb.append("\"type\":\"").append(categorizeFieldType(field)).append("\",");
        sb.append("\"collection\":").append(field.isCollection);

        if (field.title != null && !field.title.isBlank()) {
            sb.append(",\"title\":\"").append(escapeJson(field.title)).append('"');
        }

        // Mark IDEntite fields that do not embed their contents — the HTML viewer
        // will render these inline (multicolumn) inside the parent section.
        if (!field.isCollection && !field.embedContents && field.type != null && refSchema != null) {
            DOSchemaClass typeClass = refSchema.findClassByName(field.type);
            if (typeClass != null && typeClass.isIDEntite(refSchema)) {
                sb.append(",\"idEntite\":true");
            }
        }

        if (depth < 2 && field.childrenSchemaClass != null && field.childrenSchemaClass.fields != null) {
            sb.append(",\"children\":[");
            boolean childFirst = true;
            Set<String> childSeen = new HashSet<>();
            for (DOSchemaField cf : field.childrenSchemaClass.fields) {
                if (!cf.isExported) {
                    continue;
                }
                String cn = cf.destinationName != null ? cf.destinationName : cf.source;
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
        if (field.isCollection) {
            return "collection";
        }
        String type = field.type;
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