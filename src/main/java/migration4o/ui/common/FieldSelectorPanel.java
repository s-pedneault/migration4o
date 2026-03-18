package migration4o.ui.common;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DOSchemaService;
import migration4o.util.DatabaseUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.TypeUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Reusable panel that displays a filterable tree of fields accessible from a
 * given schema class. The tree mirrors the structure used in the HTML viewer's
 * search-field picker:
 * <ul>
 * <li>Primitive (leaf) fields appear directly under the root.</li>
 * <li>Embedded object fields are shown as expandable groups whose children are
 * the leaf fields of the embedded class.</li>
 * <li>Collection (listed) fields that embed contents are shown as expandable
 * groups similarly.</li>
 * <li>IDEntite reference fields are shown as expandable groups whose children
 * are the fields of the <em>target</em> entity (resolved via the IDEntite
 * class's {@code pointsTo}).</li>
 * </ul>
 *
 * <p>
 * The panel provides:
 * <ul>
 * <li>A text filter for live-filtering the tree.</li>
 * <li>Visual indication (bold + checkmark) of already-selected fields.</li>
 * <li>A callback invoked on double-click of any leaf field node.</li>
 * </ul>
 *
 * <h3>Path format</h3>
 * <p>
 * Selected fields are returned as dot-separated <em>destination name</em> paths
 * (e.g. {@code "adresse.rue"}, {@code "idDossierAdresse.adresse.rue"}). Each
 * segment corresponds to a field's {@code destinationName} in the reference
 * schema. For IDEntite references the first segment is the IDEntite field
 * itself and subsequent segments belong to the resolved target entity.
 *
 * <h3>Consumers &amp; runtime resolution</h3>
 * <p>
 * Three dialog consumers use this panel. Each has its own runtime algorithm for
 * traversing the composite paths this panel produces:
 *
 * <table border="1" cellpadding="4">
 * <tr>
 * <th>Consumer</th>
 * <th>Runtime resolver</th>
 * <th>Notes</th>
 * </tr>
 * <tr>
 * <td>{@code SummaryEditorDialog}</td>
 * <td>{@link migration4o.migration.SummaryGenerator#generate
 * SummaryGenerator.generate()}<br>
 * (private helper {@code resolveToken()})</td>
 * <td>Walks the DB4O object graph segment by segment: looks up the field by
 * {@code destinationName} (including ancestors), reads the value via
 * {@code field.source}, then advances the schema class using
 * {@code field.type}. For IDEntite segments the IDEntite reference is resolved
 * to its target entity before continuing.</td>
 * </tr>
 * <tr>
 * <td>{@code SeedQueryDialog}</td>
 * <td>{@link migration4o.migration.tasks.ExportSelectionAdvisor
 * ExportSelectionAdvisor}<br>
 * (private helper {@code resolveDestinationPathToSourcePath()})<br>
 * + {@link migration4o.util.DatabaseUtil#getFieldValueByPath
 * DatabaseUtil.getFieldValueByPath()}</td>
 * <td>Two-phase: first translates the destination-name path to a source-name
 * path (segment by segment, advancing via {@code field.type} or
 * {@code field.childrenType}), then reads the live object value using the
 * source path via {@code DatabaseUtil.getFieldValueByPath()}.</td>
 * </tr>
 * <tr>
 * <td>{@code ClassExportConfigDialog}</td>
 * <td>JavaScript viewer (client-side)</td>
 * <td>Paths are stored as default-column configuration in
 * {@code ClassExportConfig.defaultColumns} and written into the HTML viewer
 * output. The JS viewer resolves them against the flattened XML record
 * structure at display time.</td>
 * </tr>
 * </table>
 *
 * <h3>Usage</h3>
 * 
 * <pre>{@code
 * FieldSelectorPanel selector = new FieldSelectorPanel(schemaClass, alreadySelectedPaths, (fieldPath, fieldLabel) -> System.out.println("Selected: " + fieldPath));
 * someContainer.add(selector);
 * }</pre>
 */
public class FieldSelectorPanel extends JPanel {

    /**
     * Maximum nesting depth for the field tree to prevent cycles and runaway
     * expansion.
     */
    private static final int MAX_DEPTH = 5;

    /**
     * Virtual field name injected into groups whose resolved class has a
     * {@code summary} template. Selecting this produces a path like
     * {@code idDossierAdresse.sommaire} which the {@link SummaryGenerator}
     * resolves by recursively generating the target entity's summary.
     */
    public static final String SUMMARY_FIELD_NAME = "sommaire";

    /** Callback interface for field selection events (double-click). */
    @FunctionalInterface
    public interface FieldSelectionCallback {
        /**
         * Called when a leaf field node is double-clicked.
         *
         * @param fieldPath dot-separated path, e.g. {@code "adresse.rue"}
         * @param fieldLabel human-readable label, e.g. {@code "Adresse › Rue"}
         */
        void onFieldSelected(String fieldPath, String fieldLabel);
    }

    private final DOSchemaClass schemaClass;
    private final DOSchema refSchema;
    private final Set<String> selectedPaths;
    private final FieldSelectionCallback callback;

    private JTextField filterField;
    private JTree fieldTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode fullRoot;

    /**
     * Creates a new field selector panel.
     *
     * @param schemaClass the root class whose fields to display
     * @param selectedPaths paths of already-selected fields (may be
     * {@code null})
     * @param callback invoked on double-click of a leaf field (may be
     * {@code null})
     */
    public FieldSelectorPanel(DOSchemaClass schemaClass, Collection<String> selectedPaths, FieldSelectionCallback callback) {
        super(new BorderLayout(5, 5));
        this.schemaClass = schemaClass;
        this.callback = callback;
        this.selectedPaths = selectedPaths != null ? new LinkedHashSet<>(selectedPaths) : new LinkedHashSet<>();

        DOSchema schema = null;
        try {
            schema = DOSchemaService.getInstance().getReferenceSchema();
        } catch (Exception ignored) {
        }
        this.refSchema = schema;

        buildUI();
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Updates the set of selected field paths and refreshes the visual
     * indicators in the tree.
     */
    public void setSelectedPaths(Collection<String> paths) {
        selectedPaths.clear();
        if (paths != null) {
            selectedPaths.addAll(paths);
        }
        fieldTree.repaint();
    }

    /**
     * Returns the currently selected field path in the tree, or {@code null} if
     * no leaf node is selected.
     */
    public String getSelectedFieldPath() {
        TreePath treePath = fieldTree.getSelectionPath();
        if (treePath == null)
            return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof FieldItem) {
            return ((FieldItem) userObj).path;
        }
        return null;
    }

    /**
     * Returns the label of the currently selected field, or {@code null}.
     */
    public String getSelectedFieldLabel() {
        TreePath treePath = fieldTree.getSelectionPath();
        if (treePath == null)
            return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof FieldItem) {
            return ((FieldItem) userObj).fullLabel;
        }
        return null;
    }

    // ── UI Construction ─────────────────────────────────────────────

    private void buildUI() {
        // Filter bar
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        filterRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        filterRow.add(new JLabel("Filter:"), BorderLayout.WEST);

        filterField = new JTextField();
        filterField.setToolTipText("Type to filter the field tree");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        filterRow.add(filterField, BorderLayout.CENTER);
        add(filterRow, BorderLayout.NORTH);

        // Build the full tree model
        fullRoot = new DefaultMutableTreeNode("Fields");
        populateTree(fullRoot);

        treeModel = new DefaultTreeModel(fullRoot);
        fieldTree = new JTree(treeModel);
        fieldTree.setRootVisible(false);
        fieldTree.setShowsRootHandles(true);
        fieldTree.setCellRenderer(new FieldTreeCellRenderer());

        // Double-click fires the callback
        fieldTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && callback != null) {
                    TreePath path = fieldTree.getPathForLocation(e.getX(), e.getY());
                    if (path == null)
                        return;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object userObj = node.getUserObject();
                    if (userObj instanceof FieldItem) {
                        FieldItem item = (FieldItem) userObj;
                        callback.onFieldSelected(item.path, item.fullLabel);
                    }
                }
            }
        });

        expandAllNodes(fieldTree);

        JScrollPane scrollPane = new JScrollPane(fieldTree);
        scrollPane.setPreferredSize(new Dimension(300, 300));
        add(scrollPane, BorderLayout.CENTER);
    }

    // ── Tree Population ─────────────────────────────────────────────

    /**
     * Populates the tree with fields from the schema class. Structure mirrors
     * the HTML viewer: primitive fields at root level, embedded/collection
     * fields as expandable groups with their leaf children. IDEntite reference
     * fields are expanded to show the target entity's fields regardless of
     * {@code embedContents}.
     */
    private void populateTree(DefaultMutableTreeNode root) {
        if (schemaClass == null)
            return;

        List<DOSchemaField> allFields = (refSchema != null) ? DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema) : (schemaClass.fields != null ? Arrays.asList(schemaClass.fields) : new ArrayList<>());

        for (DOSchemaField field : allFields) {
            if (!field.isExported)
                continue;
            String dest = field.destinationName;
            if (dest == null || dest.isEmpty())
                continue;

            String fieldLabel = getFieldLabel(field);

            if (field.isCollection && field.embedContents) {
                // Collection with embedded contents: group node with children
                DOSchemaClass childClass = resolveClass(field.childrenType);
                if (childClass != null) {
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(fieldLabel, dest));
                    addSummaryNodeIfAvailable(groupNode, childClass, dest, fieldLabel);
                    Set<String> visited = new HashSet<>();
                    if (schemaClass.source != null)
                        visited.add(schemaClass.source);
                    addSubFields(groupNode, childClass, dest, fieldLabel, 1, visited);
                    if (groupNode.getChildCount() > 0) {
                        root.add(groupNode);
                    }
                }
            } else if (!field.isCollection && !TypeUtil.isPrimitiveType(field.type)) {
                DOSchemaClass embClass = resolveClass(field.type);
                if (embClass != null && embClass.isIDEntite(refSchema)) {
                    // IDEntite reference — resolve target entity and show its
                    // fields as children (regardless of embedContents) so users
                    // can build composite paths like
                    // idDossierAdresse.adresse.rue
                    DOSchemaClass targetClass = resolveIDEntiteTargetClass(embClass, field);
                    if (targetClass != null) {
                        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(fieldLabel, dest));
                        addSummaryNodeIfAvailable(groupNode, targetClass, dest, fieldLabel);
                        Set<String> visited = new HashSet<>();
                        if (schemaClass.source != null)
                            visited.add(schemaClass.source);
                        addSubFields(groupNode, targetClass, dest, fieldLabel, 1, visited);
                        if (groupNode.getChildCount() > 0) {
                            root.add(groupNode);
                        } else {
                            root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                        }
                    } else {
                        root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                    }
                } else if (embClass != null && field.embedContents) {
                    // Regular embedded entity: group node with leaf children
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(fieldLabel, dest));
                    addSummaryNodeIfAvailable(groupNode, embClass, dest, fieldLabel);
                    Set<String> visited = new HashSet<>();
                    if (schemaClass.source != null)
                        visited.add(schemaClass.source);
                    addSubFields(groupNode, embClass, dest, fieldLabel, 1, visited);
                    if (groupNode.getChildCount() > 0) {
                        root.add(groupNode);
                    } else {
                        root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                    }
                } else {
                    // Non-embedded complex type — leaf
                    root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                }
            } else if (!field.isCollection) {
                // Primitive / scalar field — leaf node
                root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
            }
        }
    }

    /**
     * Recursively adds sub-fields from an embedded or collection class.
     *
     * @param depth current nesting depth (1 = direct child of root); recursion
     * stops when {@code depth >= MAX_DEPTH}
     * @param visited set of class source names already on the current tree
     * path; used to break cycles (e.g. Prevention → IDPrevention → Prevention)
     */
    private void addSubFields(DefaultMutableTreeNode parentNode, DOSchemaClass cls, String parentPath, String parentLabel, int depth, Set<String> visited) {
        if (depth >= MAX_DEPTH)
            return;
        // Cycle detection: if this class is already an ancestor on this path,
        // stop
        if (cls.source != null && visited.contains(cls.source))
            return;

        // Mark this class as visited for descendants on this branch
        Set<String> branchVisited = new HashSet<>(visited);
        if (cls.source != null)
            branchVisited.add(cls.source);

        List<DOSchemaField> subFields = (refSchema != null) ? DatabaseUtil.getAllSchemaFieldsIncludingAncestors(cls, refSchema) : (cls.fields != null ? Arrays.asList(cls.fields) : new ArrayList<>());

        for (DOSchemaField sf : subFields) {
            if (!sf.isExported || sf.destinationName == null || sf.destinationName.isEmpty())
                continue;

            String dotPath = parentPath + "." + sf.destinationName;
            String childLabel = getFieldLabel(sf);
            String fullLabel = parentLabel + " \u203a " + childLabel;

            if (sf.isCollection) {
                // Nested collection — skip (matches HTML viewer behavior)
                continue;
            }

            if (!TypeUtil.isPrimitiveType(sf.type)) {
                DOSchemaClass embClass = resolveClass(sf.type);
                if (embClass != null && embClass.isIDEntite(refSchema)) {
                    // IDEntite reference — resolve target entity
                    DOSchemaClass targetClass = resolveIDEntiteTargetClass(embClass, sf);
                    if (targetClass != null) {
                        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(childLabel, dotPath));
                        addSummaryNodeIfAvailable(groupNode, targetClass, dotPath, fullLabel);
                        addSubFields(groupNode, targetClass, dotPath, fullLabel, depth + 1, branchVisited);
                        if (groupNode.getChildCount() > 0) {
                            parentNode.add(groupNode);
                        } else {
                            parentNode.add(new DefaultMutableTreeNode(new FieldItem(childLabel, dotPath, fullLabel)));
                        }
                    } else {
                        parentNode.add(new DefaultMutableTreeNode(new FieldItem(childLabel, dotPath, fullLabel)));
                    }
                } else if (embClass != null && sf.embedContents) {
                    // Regular embedded entity
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(childLabel, dotPath));
                    addSummaryNodeIfAvailable(groupNode, embClass, dotPath, fullLabel);
                    addSubFields(groupNode, embClass, dotPath, fullLabel, depth + 1, branchVisited);
                    if (groupNode.getChildCount() > 0) {
                        parentNode.add(groupNode);
                    } else {
                        parentNode.add(new DefaultMutableTreeNode(new FieldItem(childLabel, dotPath, fullLabel)));
                    }
                } else {
                    parentNode.add(new DefaultMutableTreeNode(new FieldItem(childLabel, dotPath, fullLabel)));
                }
            } else {
                parentNode.add(new DefaultMutableTreeNode(new FieldItem(childLabel, dotPath, fullLabel)));
            }
        }
    }

    // ── Filtering ───────────────────────────────────────────────────

    private void applyFilter() {
        String query = filterField.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode("Fields");

        if (query.isEmpty()) {
            // No filter — show the full tree
            copyTree(fullRoot, filteredRoot);
        } else {
            filterNode(fullRoot, filteredRoot, query);
        }

        treeModel.setRoot(filteredRoot);
        treeModel.reload();
        expandAllNodes(fieldTree);
    }

    /**
     * Recursively filters the tree. A leaf node matches if its label or path
     * contains the query. A group node is included if any of its descendants
     * match.
     */
    private boolean filterNode(DefaultMutableTreeNode source, DefaultMutableTreeNode target, String query) {
        boolean anyChildMatch = false;

        for (int i = 0; i < source.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) source.getChildAt(i);
            Object userObj = child.getUserObject();

            if (userObj instanceof FieldItem) {
                FieldItem item = (FieldItem) userObj;
                if (item.label.toLowerCase(Locale.ROOT).contains(query) || item.path.toLowerCase(Locale.ROOT).contains(query) || item.fullLabel.toLowerCase(Locale.ROOT).contains(query)) {
                    target.add(new DefaultMutableTreeNode(item));
                    anyChildMatch = true;
                }
            } else if (userObj instanceof GroupItem) {
                GroupItem group = (GroupItem) userObj;
                DefaultMutableTreeNode filteredGroup = new DefaultMutableTreeNode(group);
                boolean groupLabelMatch = group.label.toLowerCase(Locale.ROOT).contains(query);
                boolean childrenMatch = filterNode(child, filteredGroup, query);

                if (groupLabelMatch && !childrenMatch) {
                    // Group label matches but no children do — include all
                    // children
                    copyTree(child, filteredGroup);
                    target.add(filteredGroup);
                    anyChildMatch = true;
                } else if (childrenMatch) {
                    target.add(filteredGroup);
                    anyChildMatch = true;
                }
            }
        }
        return anyChildMatch;
    }

    /** Deep-copies children from source to target. */
    private void copyTree(DefaultMutableTreeNode source, DefaultMutableTreeNode target) {
        for (int i = 0; i < source.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) source.getChildAt(i);
            DefaultMutableTreeNode copy = new DefaultMutableTreeNode(child.getUserObject());
            if (child.getChildCount() > 0) {
                copyTree(child, copy);
            }
            target.add(copy);
        }
    }

    // ── Cell Renderer ───────────────────────────────────────────────

    /**
     * Custom renderer that:
     * <ul>
     * <li>Shows group nodes in plain text with a folder-like appearance.</li>
     * <li>Shows leaf nodes; already-selected fields are rendered bold with a
     * checkmark prefix.</li>
     * <li>Leaf node tooltips show the full dot-path.</li>
     * </ul>
     */
    private class FieldTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof FieldItem) {
                FieldItem item = (FieldItem) userObj;
                boolean isSelected = selectedPaths.contains(item.path);

                String displayText = isSelected ? "\u2713 " + item.label : item.label;
                setText(displayText);
                setToolTipText(item.path);

                if (isSelected) {
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setFont(getFont().deriveFont(Font.PLAIN));
                }

                setIcon(null);
            } else if (userObj instanceof GroupItem) {
                GroupItem group = (GroupItem) userObj;
                setText(group.label);
                setToolTipText(group.path);
                setFont(getFont().deriveFont(Font.PLAIN));
                // Keep the default folder icons from the tree
            }

            return c;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * If {@code cls} has a {@code summary} template configured, adds a virtual
     * "Sommaire" leaf node as the first child of the group. The path is
     * {@code parentPath + ".sommaire"} so the SummaryGenerator can detect it
     * and recursively generate the target entity's summary.
     */
    private void addSummaryNodeIfAvailable(DefaultMutableTreeNode groupNode, DOSchemaClass cls, String parentPath, String parentLabel) {
        if (cls != null && cls.summary != null && !cls.summary.isEmpty()) {
            String dotPath = parentPath + "." + SUMMARY_FIELD_NAME;
            String label = "Sommaire";
            String fullLabel = parentLabel + " \u203a Sommaire";
            groupNode.add(new DefaultMutableTreeNode(new FieldItem(label, dotPath, fullLabel)));
        }
    }

    /**
     * Resolves the target entity class that an IDEntite reference points to.
     * Uses the IDEntite class's {@code pointsTo} first, then falls back to the
     * field-level {@code pointsTo}.
     *
     * @param idEntiteClass the IDEntite schema class
     * @param field the field that holds the IDEntite (may carry its own
     * {@code pointsTo})
     * @return the resolved target entity class, or {@code null}
     */
    private DOSchemaClass resolveIDEntiteTargetClass(DOSchemaClass idEntiteClass, DOSchemaField field) {
        // 1. Try IDEntite class-level pointsTo
        String target = idEntiteClass.pointsTo;
        // 2. Fall back to field-level pointsTo
        if ((target == null || target.isEmpty()) && field != null) {
            target = field.pointsTo;
        }
        if (target == null || target.isEmpty()) {
            return null;
        }
        return resolveClass(target);
    }

    private DOSchemaClass resolveClass(String typeName) {
        if (typeName == null || refSchema == null)
            return null;
        DOSchemaClass cls = SchemaUtil.findClassByName(typeName, refSchema);
        if (cls != null)
            return cls;
        // Try short name match
        String shortName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        for (DOSchemaClass c : refSchema.getClasses()) {
            if (c.source != null && c.source.endsWith("." + shortName))
                return c;
        }
        return null;
    }

    private String getFieldLabel(DOSchemaField field) {
        if (field == null)
            return "";
        if (field.title != null && !field.title.trim().isEmpty())
            return field.title.trim();
        if (field.destinationName != null && !field.destinationName.trim().isEmpty())
            return humanize(field.destinationName.trim());
        if (field.source != null && !field.source.trim().isEmpty())
            return humanize(field.source.trim());
        return "";
    }

    private static String humanize(String name) {
        if (name == null || name.isEmpty())
            return "";
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(dot + 1) : name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (i > 0 && Character.isUpperCase(c))
                sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private void expandAllNodes(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    // ── Data Items ──────────────────────────────────────────────────

    /** Leaf field node in the tree. */
    public static class FieldItem {
        public final String label; // Display label (leaf-only, e.g. "Rue")
        public final String path; // Dot-separated path (e.g. "adresse.rue")
        public final String fullLabel; // Full label with parent chain (e.g.
                                       // "Adresse › Rue")

        FieldItem(String label, String path, String fullLabel) {
            this.label = label;
            this.path = path;
            this.fullLabel = fullLabel;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Group node in the tree (embedded or collection parent). */
    public static class GroupItem {
        public final String label; // Display label (e.g. "Adresse")
        public final String path; // Dot-path prefix (e.g. "adresse")

        GroupItem(String label, String path) {
            this.label = label;
            this.path = path;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
