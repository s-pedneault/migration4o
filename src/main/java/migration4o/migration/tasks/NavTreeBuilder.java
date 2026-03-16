package migration4o.migration.tasks;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.NavNode;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.JsonUtil;
import migration4o.util.LucideIcons;

/**
 * Builds and serializes the hierarchical nav tree for the HTML viewer sidebar.
 * <p>
 * The tree mirrors the module structure: path-prefix groups → module groups →
 * class leaves (recursively).
 * <p>
 * Call {@link #build(List, List, String)} to populate {@code operation.navTree}
 * and {@code operation.cachedNavJson}, and optionally write the welcome/index
 * page.
 */
public class NavTreeBuilder {

    private final ExportCurrentState operation;

    public NavTreeBuilder(ExportCurrentState operation) {
        this.operation = operation;
    }

    /**
     * Pre-builds the hierarchical nav tree and serializes it to JSON. Must be
     * called before export starts. Also writes the welcome page when
     * {@code operation.generateHtmlViewer} is {@code true}.
     * <p>
     * Delegates to {@link #build(List, List, Path)} using
     * {@code operation.request.getBaseOutputPath(baseOutputDir)} as the base.
     */
    public void build(List<DOSchemaModule> modules, String baseOutputDir) {
        build(modules, operation.request.getBaseOutputPath(baseOutputDir));
    }

    /**
     * Pre-builds the hierarchical nav tree and serializes it to JSON using an
     * explicit {@code base} path. Use this overload when the nav root does not
     * match {@code operation.request.getBaseOutputPath()} (e.g. a
     * format-specific sub-folder such as {@code html/}).
     */
    public void build(List<DOSchemaModule> modules, Path base) {
        operation.navTree.clear();
        operation.cachedNavJson = "[]";
        if (modules == null || modules.isEmpty()) {
            return;
        }
        LinkedHashMap<String, NavNode> prefixGroups = new LinkedHashMap<>();

        for (int i = 0; i < modules.size(); i++) {
            DOSchemaModule m = modules.get(i);
            String mp = m.name;
            String[] parts = mp.split("/");

            // Compute the module's actual output folder (mirrors
            // exportModuleRecursive logic)
            Path moduleFolderPath = base;
            for (String part : parts) {
                moduleFolderPath = moduleFolderPath.resolve(part);
            }
            Path actualModuleFolder = moduleFolderPath.getParent() != null ? moduleFolderPath.getParent().resolve(ModulePathUtil.moduleId(m)) : base.resolve(ModulePathUtil.moduleId(m));

            String iconName = m.icon;
            String iconSvg = (iconName != null && LucideIcons.isKnown(iconName)) ? LucideIcons.getSvg(iconName) : null;
            NavNode moduleNode = new NavNode(m.name, null, iconSvg, 0, m.tileBg, m.tileTextColor, m.tileIconColor, m.tileFontSize);
            buildModuleNavChildren(moduleNode, m, actualModuleFolder, base, 0);

            if (parts.length > 1) {
                NavNode group = prefixGroups.computeIfAbsent(parts[0], k -> {
                    NavNode g = new NavNode(k, null, null, 0, null, null, null, null);
                    operation.navTree.add(g);
                    return g;
                });
                group.children.add(moduleNode);
            } else {
                operation.navTree.add(moduleNode);
            }
        }

        operation.cachedNavJson = serializeNavTree();

        // Store counts for the welcome page; the actual HTML write is deferred
        // to HtmlFormatHandler.done() so the exported-object count is known.
        operation.htmlWelcomeModuleCount = modules.stream().mapToInt(this::countTotalModules).sum();
        operation.htmlWelcomeClassCount = modules.stream().mapToInt(this::countTotalClasses).sum();
    }

    // ── Nav tree construction
    // ─────────────────────────────────────────────────

    private void buildModuleNavChildren(NavNode node, DOSchemaModule module, Path folderPath, Path base, int depth) {
        for (ClassExportConfig config : module.classConfigs) {
            String destName = config.getDestinationFileName();
            DOSchemaClass sc = (operation.request.referenceSchema != null) ? operation.request.referenceSchema.findClassByName(config.getClassName()) : null;
            String label = config.hasTitle() ? config.getTitle() : (sc != null && sc.title != null && !sc.title.isBlank()) ? sc.title : destName;
            String href = base.relativize(folderPath.resolve(destName + ".html")).toString().replace('\\', '/');
            node.children.add(new NavNode(label, href, null, depth + 1, null, null, null, null));
        }
        for (DOSchemaModule child : module.children) {
            NavNode childNode = new NavNode(child.name, null, null, depth + 1, null, null, null, null);
            buildModuleNavChildren(childNode, child, folderPath.resolve(ModulePathUtil.moduleId(child)), base, depth + 1);
            node.children.add(childNode);
        }
    }

    private int countTotalModules(DOSchemaModule module) {
        int count = 1; // this module itself
        for (DOSchemaModule child : module.children) {
            count += countTotalModules(child);
        }
        return count;
    }

    private int countTotalClasses(DOSchemaModule module) {
        int count = module.classConfigs.size();
        for (DOSchemaModule child : module.children) {
            count += countTotalClasses(child);
        }
        return count;
    }

    // ── JSON serialization
    // ────────────────────────────────────────────────────

    private String serializeNavTree() {
        if (operation.navTree.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        appendNavNodes(sb, operation.navTree);
        return sb.toString();
    }

    private void appendNavNodes(StringBuilder sb, List<NavNode> nodes) {
        sb.append('[');
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0)
                sb.append(',');
            appendNavNode(sb, nodes.get(i));
        }
        sb.append(']');
    }

    private void appendNavNode(StringBuilder sb, NavNode node) {
        sb.append("{\"label\":\"").append(JsonUtil.escape(node.label)).append('"');
        sb.append(",\"depth\":").append(node.depth);
        if (node.iconSvg != null && !node.iconSvg.isEmpty()) {
            sb.append(",\"icon\":\"").append(JsonUtil.escape(node.iconSvg)).append('"');
        }
        if (node.tileBg != null) {
            sb.append(",\"tileBg\":\"").append(JsonUtil.escape(node.tileBg)).append('"');
        }
        if (node.tileTextColor != null) {
            sb.append(",\"tileText\":\"").append(JsonUtil.escape(node.tileTextColor)).append('"');
        }
        if (node.tileIconColor != null) {
            sb.append(",\"tileIcon\":\"").append(JsonUtil.escape(node.tileIconColor)).append('"');
        }
        if (node.tileFontSize != null) {
            sb.append(",\"tileFontSize\":\"").append(JsonUtil.escape(node.tileFontSize)).append('"');
        }
        if (node.isLeaf()) {
            sb.append(",\"href\":\"").append(JsonUtil.escape(node.rootRelativeHref)).append('"');
        } else {
            sb.append(",\"children\":");
            appendNavNodes(sb, node.children);
        }
        sb.append('}');
    }

}
