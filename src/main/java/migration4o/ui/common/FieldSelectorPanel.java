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
 * <h3>Usage</h3>
 * 
 * <pre>{@code
 * FieldSelectorPanel selector = new FieldSelectorPanel(schemaClass, alreadySelectedPaths, (fieldPath, fieldLabel) -> System.out.println("Selected: " + fieldPath));
 * someContainer.add(selector);
 * }</pre>
 */
public class FieldSelectorPanel extends JPanel {

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
     * fields as expandable groups with their leaf children.
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
                    addSubFields(groupNode, childClass, dest, fieldLabel);
                    if (groupNode.getChildCount() > 0) {
                        root.add(groupNode);
                    }
                }
            } else if (field.embedContents && !TypeUtil.isPrimitiveType(field.type)) {
                // Embedded entity: group node with leaf children
                DOSchemaClass embClass = resolveClass(field.type);
                if (embClass != null) {
                    // Check if it's an IDEntite (reference) — treat as leaf
                    if (embClass.isIDEntite(refSchema)) {
                        root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                    } else {
                        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(fieldLabel, dest));
                        addSubFields(groupNode, embClass, dest, fieldLabel);
                        if (groupNode.getChildCount() > 0) {
                            root.add(groupNode);
                        } else {
                            // No exported children — show as leaf
                            root.add(new DefaultMutableTreeNode(new FieldItem(fieldLabel, dest, fieldLabel)));
                        }
                    }
                } else {
                    // Can't resolve the type — show as leaf
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
     */
    private void addSubFields(DefaultMutableTreeNode parentNode, DOSchemaClass cls, String parentPath, String parentLabel) {
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

            if (sf.embedContents && !TypeUtil.isPrimitiveType(sf.type)) {
                DOSchemaClass embClass = resolveClass(sf.type);
                if (embClass != null && !embClass.isIDEntite(refSchema)) {
                    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new GroupItem(childLabel, dotPath));
                    addSubFields(groupNode, embClass, dotPath, fullLabel);
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
