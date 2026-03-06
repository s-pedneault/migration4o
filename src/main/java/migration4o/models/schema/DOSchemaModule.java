package migration4o.models.schema;

import java.util.ArrayList;
import java.util.List;

import migration4o.models.ui.ClassExportConfig;

/**
 * A module grouping related classes for export. Loaded from
 * migration-format.xml via DOModuleStructureReader.
 */
public class DOSchemaModule {

    public String name;
    public String id;
    public String icon;
    public String tileBg;
    public String tileTextColor;
    public String tileIconColor;
    public String tileFontSize;
    public List<ClassExportConfig> classConfigs = new ArrayList<>();
    public List<DOSchemaModule> children = new ArrayList<>();

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
