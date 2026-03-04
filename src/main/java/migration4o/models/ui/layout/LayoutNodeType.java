package migration4o.models.ui.layout;

/**
 * Types of layout nodes in a detail view design.
 */
public enum LayoutNodeType {
    SECTION("section"), COLUMNS("columns"), COLUMN("column"), FIELD("field"), DIVIDER("divider"), TABLE("table"), TABBED_SECTION("tabs"), TAB("tab");

    public final String xmlTag;

    LayoutNodeType(String xmlTag) {
        this.xmlTag = xmlTag;
    }

    public static LayoutNodeType fromXmlTag(String tag) {
        for (LayoutNodeType t : values()) {
            if (t.xmlTag.equals(tag))
                return t;
        }
        return null;
    }
}
