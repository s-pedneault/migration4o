package migration4o.models.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a migration module that groups related classes for export.
 * Modules can be hierarchical with child modules.
 */
public class MigrationModule {

    private final String name;
    private final String id;
    /** Optional Lucide icon name (e.g. "fire-truck", "building-2"). May be null. */
    private final String icon;
    /** Optional hex background color for the nav tile (e.g. "#e8f0fe"). Null = auto. */
    private final String tileBg;
    /** Optional hex text/label color for the nav tile (e.g. "#1a1a2e"). Null = auto. */
    private final String tileTextColor;
    /** Optional hex icon color for the nav tile (e.g. "#1a73e8"). Null = auto. */
    private final String tileIconColor;
    /** Optional tile label font-size "12"–"16" (px). Null = default (14). */
    private final String tileFontSize;
    private final List<ClassExportConfig> classConfigs;
    private final List<MigrationModule> childModules;

    public MigrationModule(String name, String id, String icon, String tileBg, String tileTextColor, String tileIconColor, String tileFontSize, List<ClassExportConfig> classConfigs, List<MigrationModule> childModules) {
        this.name = name;
        this.id = id;
        this.icon = (icon != null && !icon.isBlank()) ? icon.trim() : null;
        this.tileBg = (tileBg != null && !tileBg.isBlank()) ? tileBg.trim() : null;
        this.tileTextColor = (tileTextColor != null && !tileTextColor.isBlank()) ? tileTextColor.trim() : null;
        this.tileIconColor = (tileIconColor != null && !tileIconColor.isBlank()) ? tileIconColor.trim() : null;
        this.tileFontSize = (tileFontSize != null && !tileFontSize.isBlank()) ? tileFontSize.trim() : null;
        this.classConfigs = new ArrayList<>(classConfigs);
        this.childModules = new ArrayList<>(childModules);
    }

    /** Backward-compatible constructor — tile colors / font-size default to null. */
    public MigrationModule(String name, String id, String icon, List<ClassExportConfig> classConfigs, List<MigrationModule> childModules) {
        this(name, id, icon, null, null, null, null, classConfigs, childModules);
    }

    /** Backward-compatible constructor — icon and tile colors default to null. */
    public MigrationModule(String name, String id, List<ClassExportConfig> classConfigs, List<MigrationModule> childModules) {
        this(name, id, null, null, null, null, null, classConfigs, childModules);
    }

    /**
     * Backward compatibility constructor that takes class names as strings.
     */
    public MigrationModule(String name, String id, List<String> classNames, List<MigrationModule> childModules, boolean isStringList) {
        this.name = name;
        this.id = id;
        this.icon = null;
        this.tileBg = null;
        this.tileTextColor = null;
        this.tileIconColor = null;
        this.tileFontSize = null;
        this.classConfigs = classNames.stream().map(ClassExportConfig::new).collect(Collectors.toList());
        this.childModules = new ArrayList<>(childModules);
    }

    public MigrationModule(String name, String id, List<String> classNames) {
        this(name, id, classNames, new ArrayList<>(), true);
    }

    public MigrationModule(String name, List<ClassExportConfig> classConfigs) {
        this(name, name, null, null, null, null, null, classConfigs, Collections.emptyList());
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the Lucide icon name for this module (may be null).
     */
    public String getIcon() {
        return icon;
    }

    /** Returns the hex tile background color (e.g. "#e8f0fe") or null for auto. */
    public String getTileBg() {
        return tileBg;
    }

    /** Returns the hex tile text/label color (e.g. "#1a1a2e") or null for auto. */
    public String getTileTextColor() {
        return tileTextColor;
    }

    /** Returns the hex tile icon color (e.g. "#1a73e8") or null for auto. */
    public String getTileIconColor() {
        return tileIconColor;
    }

    /**
     * Returns the tile label font-size string (e.g. "14") or null for default.
     */
    public String getTileFontSize() {
        return tileFontSize;
    }

    /**
     * Returns the list of class export configurations.
     */
    public List<ClassExportConfig> getClassConfigs() {
        return Collections.unmodifiableList(classConfigs);
    }

    /**
     * Returns class names only (backward compatibility).
     * Note: If a class appears multiple times with different configs, it will
     * appear multiple times in the list.
     */
    public List<String> getClassNames() {
        return classConfigs.stream().map(ClassExportConfig::getClassName).collect(Collectors.toList());
    }

    public List<MigrationModule> getChildModules() {
        return Collections.unmodifiableList(childModules);
    }

    /**
     * Recursively collects all class names from this module and its children.
     */
    public List<String> getAllClassNames() {
        List<String> allClasses = new ArrayList<>(getClassNames());
        for (MigrationModule child : childModules) {
            allClasses.addAll(child.getAllClassNames());
        }
        return allClasses;
    }

    /**
     * Recursively collects all class configurations from this module and its
     * children.
     */
    public List<ClassExportConfig> getAllClassConfigs() {
        List<ClassExportConfig> allConfigs = new ArrayList<>(classConfigs);
        for (MigrationModule child : childModules) {
            allConfigs.addAll(child.getAllClassConfigs());
        }
        return allConfigs;
    }

    @Override
    public String toString() {
        return name + " (" + classConfigs.size() + " classes, " + childModules.size() + " submodules)";
    }
}
