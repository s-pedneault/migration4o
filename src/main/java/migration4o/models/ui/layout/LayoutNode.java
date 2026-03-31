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
        String base;
        switch (type) {
        case SECTION:
            String layoutRef = prop("layoutRef");
            if (layoutRef != null) {
                String simple = layoutRef.contains(".") ? layoutRef.substring(layoutRef.lastIndexOf('.') + 1) : layoutRef;
                base = "\uD83D\uDD17 " + prop("title", simple) + " \u2192 " + simple;
            } else {
                base = "Section: " + prop("title", "(untitled)");
            }
            break;
        case COLUMNS:
            base = "Columns (" + prop("count", "2") + ")";
            break;
        case COLUMN:
            base = "Column";
            break;
        case FIELD:
            base = "Field: " + prop("ref", "?");
            break;
        case DIVIDER:
            base = "── Divider ──";
            break;
        case TABLE:
            base = "Table: " + prop("ref", "?");
            break;
        case TABBED_SECTION:
            base = "Tabs: " + prop("title", "(tabs)");
            break;
        case TAB:
            base = "Tab: " + prop("title", "(untitled)");
            break;
        default:
            base = type.name();
        }
        // Append style info if present
        String style = prop("style");
        String color = prop("color");
        if (style != null || color != null) {
            StringBuilder sb = new StringBuilder(base).append(" [");
            boolean first = true;
            if (style != null) {
                sb.append(style.toUpperCase());
                first = false;
            }
            if (color != null) {
                if (!first)
                    sb.append(", ");
                sb.append(color);
            }
            sb.append(']');
            return sb.toString();
        }
        return base;
    }
}
