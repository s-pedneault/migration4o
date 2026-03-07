package migration4o.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the HTML-viewer sidebar navigation tree, built at export time from
 * the module structure.
 *
 * <p>
 * This is a rendering artifact: it carries fields that only exist during a
 * concrete export pass (resolved root-relative hrefs, expanded inline SVG
 * strings, nesting depth) and has no equivalent in the schema model layer.
 *
 * <p>
 * Leaf nodes ({@link #isLeaf()} == true) represent individual exported class
 * files. Group nodes represent modules or path-prefix groups and hold
 * {@link #children}.
 */
public final class NavNode {
    public final String label;
    /**
     * Root-relative href (e.g. "Activités/Intervention/Intervention.html");
     * null for group nodes.
     */
    public final String rootRelativeHref;
    /**
     * Inline SVG string for a Lucide icon; non-null only when a recognised icon
     * name is configured on the module.
     */
    public final String iconSvg;
    /**
     * Nesting depth: 0 = top-level module tile, 1+ = child group or leaf.
     */
    public final int depth;
    /** Hex tile background colour; null = auto-cycle. */
    public final String tileBg;
    /** Hex tile text/label colour; null = auto. */
    public final String tileTextColor;
    /** Hex tile icon colour; null = auto. */
    public final String tileIconColor;
    /** Tile label font-size string, e.g. {@code "14"}; null = default. */
    public final String tileFontSize;
    public final List<NavNode> children = new ArrayList<>();

    /** Convenience constructor for child leaves (depth defaults to 1). */
    public NavNode(String label, String rootRelativeHref) {
        this(label, rootRelativeHref, null, 1, null, null, null, null);
    }

    public NavNode(String label, String rootRelativeHref, String iconSvg, int depth, String tileBg, String tileTextColor, String tileIconColor, String tileFontSize) {
        this.label = label;
        this.rootRelativeHref = rootRelativeHref;
        this.iconSvg = iconSvg;
        this.depth = depth;
        this.tileBg = tileBg;
        this.tileTextColor = tileTextColor;
        this.tileIconColor = tileIconColor;
        this.tileFontSize = tileFontSize;
    }

    /** Returns {@code true} when this node represents a concrete file link. */
    public boolean isLeaf() {
        return rootRelativeHref != null;
    }
}
