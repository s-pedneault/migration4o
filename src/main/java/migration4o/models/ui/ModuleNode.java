package migration4o.models.ui;

/**
 * Model class representing a module node in the migration structure.
 * Contains module metadata (name and ID).
 */
public class ModuleNode {
    private String name;
    private String id;
    /** Optional Lucide icon name for HTML viewer nav tiles. May be null. */
    private String icon;
    /** Optional hex background color (e.g. "#e8f0fe"). Null = auto. */
    private String tileBg;
    /** Optional hex text/label color (e.g. "#1a1a2e"). Null = auto. */
    private String tileTextColor;
    /** Optional hex icon color (e.g. "#1a73e8"). Null = auto. */
    private String tileIconColor;
    /** Optional tile label font-size "12"–"16" (px). Null = default. */
    private String tileFontSize;

    public ModuleNode(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public ModuleNode(String name, String id, String icon) {
        this.name = name;
        this.id = id;
        this.icon = (icon != null && !icon.isBlank()) ? icon.trim() : null;
    }

    public ModuleNode(String name, String id, String icon, String tileBg, String tileTextColor, String tileIconColor, String tileFontSize) {
        this.name = name;
        this.id = id;
        this.icon = (icon != null && !icon.isBlank()) ? icon.trim() : null;
        this.tileBg = (tileBg != null && !tileBg.isBlank()) ? tileBg.trim() : null;
        this.tileTextColor = (tileTextColor != null && !tileTextColor.isBlank()) ? tileTextColor.trim() : null;
        this.tileIconColor = (tileIconColor != null && !tileIconColor.isBlank()) ? tileIconColor.trim() : null;
        this.tileFontSize = (tileFontSize != null && !tileFontSize.isBlank()) ? tileFontSize.trim() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = (icon != null && !icon.isBlank()) ? icon.trim() : null;
    }

    public String getTileBg() {
        return tileBg;
    }

    public void setTileBg(String tileBg) {
        this.tileBg = (tileBg != null && !tileBg.isBlank()) ? tileBg.trim() : null;
    }

    public String getTileTextColor() {
        return tileTextColor;
    }

    public void setTileTextColor(String tileTextColor) {
        this.tileTextColor = (tileTextColor != null && !tileTextColor.isBlank()) ? tileTextColor.trim() : null;
    }

    public String getTileIconColor() {
        return tileIconColor;
    }

    public void setTileIconColor(String tileIconColor) {
        this.tileIconColor = (tileIconColor != null && !tileIconColor.isBlank()) ? tileIconColor.trim() : null;
    }

    public String getTileFontSize() {
        return tileFontSize;
    }

    public void setTileFontSize(String tileFontSize) {
        this.tileFontSize = (tileFontSize != null && !tileFontSize.isBlank()) ? tileFontSize.trim() : null;
    }

    @Override
    public String toString() {
        return name;// + " [" + id + "]";
    }
}
