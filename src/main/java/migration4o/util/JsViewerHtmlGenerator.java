package migration4o.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import migration4o.models.schema.DOSchemaClass;

/**
 * Generates a lightweight HTML viewer for JS exports produced by StructuredWriterJS.
 */
public final class JsViewerHtmlGenerator {

    private static final String TEMPLATE_RESOURCE = "/templates/js-viewer-template.html";
    private static volatile String cachedTemplate;

    private JsViewerHtmlGenerator() {
    }

    public static Path writeViewerForJs(Path jsPath, DOSchemaClass schemaClass) throws IOException {
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

        Path htmlPath = jsPath.resolveSibling(baseName + ".html");
        String html = loadTemplate().replace("__TITLE__", escapeHtml(title)).replace("__ENTITY_NAME__", escapeHtml(entityName)).replace("__EMBEDDED_JS_DATA__", embeddedJs);

        if (htmlPath.getParent() != null) {
            Files.createDirectories(htmlPath.getParent());
        }
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(jsPath);
        return htmlPath;
    }

    private static String loadTemplate() throws IOException {
        String existing = cachedTemplate;
        if (existing != null) {
            return existing;
        }

        synchronized (JsViewerHtmlGenerator.class) {
            if (cachedTemplate != null) {
                return cachedTemplate;
            }
            try (InputStream in = JsViewerHtmlGenerator.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (in == null) {
                    throw new IOException("Missing HTML template resource: " + TEMPLATE_RESOURCE);
                }
                cachedTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return cachedTemplate;
            }
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

}