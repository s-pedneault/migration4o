package migration4o.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;

/**
 * Post-processes all generated HTML viewer files in an export output directory
 * to inject a collapsible navigation sidebar listing all module pages.
 *
 * <p>The sidebar is populated by replacing the {@code // @nav-data-begin} /
 * {@code // @nav-data-end} markers embedded in the HTML templates with a JSON
 * array of navigation items whose hrefs are relative to each individual file.</p>
 *
 * <p>Must be called after all HTML viewer files have been generated.</p>
 */
public final class HtmlNavPostProcessor {

    /** Marker that starts the replaceable nav data block inside HTML scripts. */
    private static final String MARKER_START = "// @nav-data-begin\n";

    /** Marker that ends the replaceable nav data block. */
    private static final String MARKER_END = "\n// @nav-data-end";

    private HtmlNavPostProcessor() {
    }

    // ── Public entry point ─────────────────────────────────────────────────────

    /**
     * Scans all {@code .html} files under {@code dbBasePath}, builds a navigation
     * tree ordered by the given module list, and injects per-file relative
     * navigation links into each one.
     *
     * @param dbBasePath  Root of the database export output (e.g. {@code output/54060})
     * @param modules     Ordered list of export modules (from DOModuleService)
     * @param modulePaths Corresponding output paths, one per module
     * @param monitor     Optional progress monitor (may be null)
     */
    public static void postProcess(Path dbBasePath, List<MigrationModule> modules, List<String> modulePaths, DOExportMonitor monitor) {
        if (dbBasePath == null || !Files.isDirectory(dbBasePath)) {
            return;
        }
        try {
            List<Path> htmlFiles = collectHtmlFiles(dbBasePath);
            if (htmlFiles.isEmpty())
                return;

            NavNode root = buildNavTreeFromModules(dbBasePath, modules, modulePaths, htmlFiles);

            int updated = 0;
            for (Path htmlFile : htmlFiles) {
                try {
                    if (injectNavIntoFile(htmlFile, root))
                        updated++;
                } catch (IOException e) {
                    if (monitor != null) {
                        monitor.onStatusMessage("Warning: nav injection failed for " + htmlFile.getFileName() + ": " + e.getMessage());
                    }
                }
            }

            if (monitor != null && updated > 0) {
                monitor.onStatusMessage("Navigation sidebar injected into " + updated + " HTML file(s).");
            }
        } catch (IOException e) {
            if (monitor != null) {
                monitor.onStatusMessage("Warning: nav post-processing failed: " + e.getMessage());
            }
        }
    }

    // ── File collection ────────────────────────────────────────────────────────

    private static List<Path> collectHtmlFiles(Path base) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.endsWith(".html") || name.endsWith(".HTML")) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.naturalOrder());
        return result;
    }

    // ── Nav tree ───────────────────────────────────────────────────────────────

    /**
     * A node in the navigation tree: either a folder group (has children) or an
     * HTML file leaf (has an absolute path, no children).
     */
    private static final class NavNode {
        final String label;
        /** Non-null for leaf nodes; null for group/folder nodes. */
        final Path absolutePath;
        final List<NavNode> children = new ArrayList<>();

        NavNode(String label, Path absolutePath) {
            this.label = label;
            this.absolutePath = absolutePath;
        }

        boolean isLeaf() {
            return absolutePath != null;
        }
    }

    /**
     * Builds a nav tree whose top-level groups are the export modules (in order,
     * with their proper display names from the module definition). HTML files found
     * under each module's output path are attached as leaf children. Any HTML files
     * not covered by a module (e.g. {@code _Migration/Extra.html}) are appended
     * under a catch-all group at the end.
     */
    private static NavNode buildNavTreeFromModules(Path dbBasePath, List<MigrationModule> modules, List<String> modulePaths, List<Path> allHtmlFiles) {

        NavNode root = new NavNode("root", null);

        // Track which HTML files have been placed under a module
        List<Path> unassigned = new ArrayList<>(allHtmlFiles);

        // One group per module, in declaration order
        for (int i = 0; i < modules.size(); i++) {
            MigrationModule module = modules.get(i);
            String modulePath = (modulePaths != null && i < modulePaths.size()) ? modulePaths.get(i) : module.getName();
            Path moduleDir = dbBasePath.resolve(modulePath);

            // Collect HTML files that live inside this module's directory
            List<Path> moduleFiles = new ArrayList<>();
            for (Path f : allHtmlFiles) {
                if (f.startsWith(moduleDir)) {
                    moduleFiles.add(f);
                }
            }
            unassigned.removeAll(moduleFiles);

            if (moduleFiles.isEmpty()) {
                continue; // module produced no HTML — skip it in the nav
            }

            NavNode moduleGroup = new NavNode(module.getName(), null);
            // Within the module, mirror sub-folder structure
            for (Path file : moduleFiles) {
                Path relative = moduleDir.relativize(file);
                int nameCount = relative.getNameCount();
                NavNode current = moduleGroup;
                for (int s = 0; s < nameCount - 1; s++) {
                    current = findOrCreateGroup(current, relative.getName(s).toString());
                }
                current.children.add(new NavNode(stripHtmlExtension(relative.getName(nameCount - 1).toString()), file));
            }
            root.children.add(moduleGroup);
        }

        // Append any unassigned files (e.g. _Migration/) at the end
        for (Path file : unassigned) {
            Path relative = dbBasePath.relativize(file);
            int nameCount = relative.getNameCount();
            NavNode current = root;
            for (int s = 0; s < nameCount - 1; s++) {
                current = findOrCreateGroup(current, relative.getName(s).toString());
            }
            current.children.add(new NavNode(stripHtmlExtension(relative.getName(nameCount - 1).toString()), file));
        }

        return root;
    }

    private static NavNode findOrCreateGroup(NavNode parent, String name) {
        for (NavNode child : parent.children) {
            if (!child.isLeaf() && child.label.equals(name))
                return child;
        }
        NavNode group = new NavNode(name, null);
        parent.children.add(group);
        return group;
    }

    private static String stripHtmlExtension(String fileName) {
        if (fileName.endsWith(".html"))
            return fileName.substring(0, fileName.length() - 5);
        if (fileName.endsWith(".HTML"))
            return fileName.substring(0, fileName.length() - 5);
        return fileName;
    }

    // ── Injection ──────────────────────────────────────────────────────────────

    /**
     * Replaces the nav data block in the given HTML file with JSON navigation
     * data computed relative to that file. Returns {@code true} if modified.
     */
    private static boolean injectNavIntoFile(Path htmlFile, NavNode root) throws IOException {
        String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
        int startIdx = content.indexOf(MARKER_START);
        if (startIdx < 0)
            return false; // template not nav-enabled
        int endIdx = content.indexOf(MARKER_END, startIdx);
        if (endIdx < 0)
            return false;

        String navJson = buildNavJson(root.children, htmlFile);
        String newBlock = "const NAV_ITEMS = " + navJson + ";";

        String newContent = content.substring(0, startIdx + MARKER_START.length()) + newBlock + content.substring(endIdx);

        Files.writeString(htmlFile, newContent, StandardCharsets.UTF_8);
        return true;
    }

    // ── JSON serialisation ─────────────────────────────────────────────────────

    private static String buildNavJson(List<NavNode> children, Path currentFile) {
        StringBuilder sb = new StringBuilder();
        appendChildren(sb, children, currentFile);
        return sb.toString();
    }

    private static void appendChildren(StringBuilder sb, List<NavNode> children, Path currentFile) {
        sb.append('[');
        for (int i = 0; i < children.size(); i++) {
            if (i > 0)
                sb.append(',');
            appendNode(sb, children.get(i), currentFile);
        }
        sb.append(']');
    }

    private static void appendNode(StringBuilder sb, NavNode node, Path currentFile) {
        sb.append('{');
        sb.append("\"label\":\"").append(escapeJson(node.label)).append('"');
        if (node.isLeaf()) {
            Path relPath = currentFile.getParent().relativize(node.absolutePath);
            String href = relPath.toString().replace('\\', '/');
            sb.append(",\"href\":\"").append(escapeJson(href)).append('"');
            if (node.absolutePath.equals(currentFile)) {
                sb.append(",\"current\":true");
            }
        } else {
            sb.append(",\"children\":");
            appendChildren(sb, node.children, currentFile);
        }
        sb.append('}');
    }

    private static String escapeJson(String value) {
        if (value == null)
            return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
