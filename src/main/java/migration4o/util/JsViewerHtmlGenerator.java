package migration4o.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import migration4o.models.schema.DOSchemaClass;

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
        return writeViewerForJs(jsPath, schemaClass, "[]", "./");
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref) throws IOException {
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
        String entityName = (schemaClass != null && schemaClass.destinationName != null && !schemaClass.destinationName.isBlank()) ? schemaClass.destinationName : baseName;
        String nav = (navItemsJson != null && !navItemsJson.isBlank()) ? navItemsJson : "[]";
        String base = (baseHref != null && !baseHref.isBlank()) ? baseHref : "./";

        Path htmlPath = jsPath.resolveSibling(baseName + ".html");
        String html = loadTemplate().replace("__SIDEBAR_CSS__", loadSidebarCss()).replace("__SIDEBAR_NAV_JS__", loadSidebarNavJs()).replace("__BASE_HREF__", base).replace("__NAV_ITEMS__", nav).replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName)).replace("__EMBEDDED_JS_DATA__", embeddedJs);

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
}