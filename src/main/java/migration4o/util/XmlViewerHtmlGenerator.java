package migration4o.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Generates a standalone HTML data viewer alongside each exported XML file.
 * The HTML embeds the XML as Base64 and optional schema metadata as JSON,
 * producing a self-contained data exploration UI.
 */
public final class XmlViewerHtmlGenerator {

    private static final String TEMPLATE_RESOURCE = "/templates/xml-viewer-template.html";
    private static volatile String cachedTemplate;

    private XmlViewerHtmlGenerator() {
    }

    /**
     * Write an HTML viewer for the given XML file (no schema metadata).
     */
    public static Path writeViewerForXml(Path xmlPath) throws IOException {
        return writeViewerForXml(xmlPath, null, null);
    }

    /**
     * Write an HTML viewer with schema-driven field metadata.
     */
    public static Path writeViewerForXml(Path xmlPath, DOSchemaClass schemaClass, DOSchema schema) throws IOException {
        if (xmlPath == null) {
            throw new IllegalArgumentException("xmlPath must not be null");
        }

        String fileName = xmlPath.getFileName() != null ? xmlPath.getFileName().toString() : "export.xml";
        Path htmlPath = resolveHtmlPath(xmlPath);
        String embeddedXmlBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(xmlPath));
        String schemaJson = buildFieldMetadataJson(schemaClass, schema);
        String entityName = deriveEntityName(schemaClass, fileName);
        String html = buildHtml(fileName, embeddedXmlBase64, schemaJson, entityName);

        if (htmlPath.getParent() != null) {
            Files.createDirectories(htmlPath.getParent());
        }
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        return htmlPath;
    }

    private static Path resolveHtmlPath(Path xmlPath) {
        String fileName = xmlPath.getFileName() != null ? xmlPath.getFileName().toString() : "export.xml";
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String htmlName = baseName + ".html";
        return xmlPath.resolveSibling(htmlName);
    }

    private static String deriveEntityName(DOSchemaClass schemaClass, String fileName) {
        if (schemaClass != null && schemaClass.destinationName != null) {
            return schemaClass.destinationName;
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Builds a hierarchical JSON array of top-level fields. Each field that
     * has embedded children includes a "children" array containing one level
     * of sub-fields for the child class.
     */
    private static String buildFieldMetadataJson(DOSchemaClass schemaClass, DOSchema schema) {
        if (schemaClass == null || schemaClass.fields == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (DOSchemaField field : schemaClass.fields) {
            if (!field.isExported) {
                continue;
            }
            String name = field.destinationName != null ? field.destinationName : field.source;
            if (name == null) {
                continue;
            }
            String dataType = categorizeFieldType(field);

            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{');
            sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"path\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"type\":\"").append(dataType).append("\",");
            sb.append("\"collection\":").append(field.isCollection);
            if (field.title != null && !field.title.isEmpty()) {
                sb.append(",\"title\":\"").append(escapeJson(field.title)).append('"');
            }

            DOSchemaClass childClass = null;
            if (field.embedContents && !field.isCollection) {
                childClass = resolveChildClass(field, schema);
            } else if (field.isCollection && field.embedContents) {
                childClass = resolveCollectionChildClass(field, schema);
            }

            if (childClass != null && childClass.fields != null) {
                sb.append(",\"children\":[");
                boolean childFirst = true;
                Set<String> seen = new HashSet<>();
                for (DOSchemaField cf : childClass.fields) {
                    if (!cf.isExported) {
                        continue;
                    }
                    String cn = cf.destinationName != null ? cf.destinationName : cf.source;
                    if (cn == null || seen.contains(cn)) {
                        continue;
                    }
                    seen.add(cn);
                    if (!childFirst) {
                        sb.append(',');
                    }
                    childFirst = false;
                    sb.append('{');
                    sb.append("\"name\":\"").append(escapeJson(cn)).append("\",");
                    sb.append("\"path\":\"").append(escapeJson(name + "." + cn)).append("\",");
                    sb.append("\"type\":\"").append(categorizeFieldType(cf)).append("\"");
                    if (cf.title != null && !cf.title.isEmpty()) {
                        sb.append(",\"title\":\"").append(escapeJson(cf.title)).append('"');
                    }
                    sb.append(",\"collection\":").append(cf.isCollection);
                    sb.append('}');
                }
                sb.append(']');
            }

            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static DOSchemaClass resolveChildClass(DOSchemaField field, DOSchema schema) {
        if (field.childrenSchemaClass != null) {
            return field.childrenSchemaClass;
        }
        if (schema != null && field.type != null) {
            return schema.findClassByName(field.type);
        }
        return null;
    }

    private static DOSchemaClass resolveCollectionChildClass(DOSchemaField field, DOSchema schema) {
        if (field.childrenSchemaClass != null) {
            return field.childrenSchemaClass;
        }
        if (schema != null && field.childrenType != null) {
            return schema.findClassByName(field.childrenType);
        }
        return null;
    }

    /**
     * Categorize a field type into a simple category for the viewer UI.
     */
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

    private static String buildHtml(String xmlFileName, String embeddedXmlBase64, String schemaFieldsJson, String entityName) throws IOException {
        String safeXmlBase64 = escapeJsString(embeddedXmlBase64);
        String safeTitle = escapeHtml(xmlFileName);
        String safeEntityName = escapeJsString(entityName);

        String template = loadTemplate();
        return template.replace("__TITLE__", safeTitle).replace("__ENTITY_NAME__", safeEntityName).replace("__XML_BASE64__", safeXmlBase64).replace("__SCHEMA_FIELDS__", schemaFieldsJson);
    }

    private static String loadTemplate() throws IOException {
        String existing = cachedTemplate;
        if (existing != null) {
            return existing;
        }

        synchronized (XmlViewerHtmlGenerator.class) {
            if (cachedTemplate != null) {
                return cachedTemplate;
            }
            try (InputStream in = XmlViewerHtmlGenerator.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
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

    private static String escapeJsString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
