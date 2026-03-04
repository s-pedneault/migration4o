package migration4o.models.ui.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a detail layout tree. Each node has a type, properties map,
 * and optional children. Properties vary by type:
 *
 * SECTION:        title, collapsible, titleColor
 * COLUMNS:        count, sizes (e.g. "50,50")
 * COLUMN:         (no props — width from parent sizes by index)
 * FIELD:          ref (dot-path), label (override), format (type:spec)
 * DIVIDER:        (none)
 * TABLE:          ref (collection field), columns (comma-sep), widths (comma-sep)
 * TABBED_SECTION: title (optional header)
 * TAB:            title (tab label)
 *
 * Format syntax for FIELD nodes:
 *   date:pattern       — e.g. date:yyyy-MM-dd
 *   bool:trueLabel,falseLabel — e.g. bool:Oui,Non
 *   longdate:pattern   — interpret long as date, e.g. longdate:yyyy-MM-dd HH:mm
 *   num:pattern suffix — e.g. num:#,##0.0 Km
 */
public class LayoutNode {
    public LayoutNodeType type;
    public Map<String, String> properties = new LinkedHashMap<>();
    public List<LayoutNode> children = new ArrayList<>();

    public LayoutNode(LayoutNodeType type) {
        this.type = type;
    }

    public String prop(String key) {
        return properties.get(key);
    }

    public String prop(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public void setProp(String key, String value) {
        if (value != null && !value.isEmpty()) {
            properties.put(key, value);
        } else {
            properties.remove(key);
        }
    }

    public boolean boolProp(String key) {
        return "true".equalsIgnoreCase(prop(key));
    }

    /** JSON serialization for embedding in HTML exports. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        appendJson(sb);
        return sb.toString();
    }

    void appendJson(StringBuilder sb) {
        sb.append("{\"type\":\"").append(escJson(type.xmlTag)).append("\"");
        if (!properties.isEmpty()) {
            sb.append(",\"props\":{");
            int i = 0;
            for (Map.Entry<String, String> e : properties.entrySet()) {
                if (i++ > 0)
                    sb.append(',');
                sb.append("\"").append(escJson(e.getKey())).append("\":\"").append(escJson(e.getValue())).append("\"");
            }
            sb.append('}');
        }
        if (!children.isEmpty()) {
            sb.append(",\"children\":[");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0)
                    sb.append(',');
                children.get(i).appendJson(sb);
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static String escJson(String v) {
        if (v == null)
            return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public String toString() {
        switch (type) {
        case SECTION:
            return "Section: " + prop("title", "(untitled)");
        case COLUMNS:
            return "Columns (" + prop("count", "2") + ")";
        case COLUMN:
            return "Column";
        case FIELD:
            return "Field: " + prop("ref", "?");
        case DIVIDER:
            return "── Divider ──";
        case TABLE:
            return "Table: " + prop("ref", "?");
        case TABBED_SECTION:
            return "Tabs: " + prop("title", "(tabs)");
        case TAB:
            return "Tab: " + prop("title", "(untitled)");
        default:
            return type.name();
        }
    }
}
