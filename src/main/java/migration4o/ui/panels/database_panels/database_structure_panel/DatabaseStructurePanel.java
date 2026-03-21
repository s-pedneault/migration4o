package migration4o.ui.panels.database_panels.database_structure_panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseField;

/**
 * Read-only tree view of the DODatabase structure discovered from the DB4O container.
 * Shows each database class and its fields, with schema link status indicators.
 */
public class DatabaseStructurePanel extends JPanel {

    private static final NumberFormat INT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final Color LINKED_COLOR = new Color(0, 120, 0);
    private static final Color UNLINKED_COLOR = new Color(180, 0, 0);

    private final DODatabase database;
    private JTree tree;
    private JTextArea detailArea;

    public DatabaseStructurePanel(DODatabase database) {
        this.database = database;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Summary bar
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        int classCount = database != null && database.getClasses() != null ? database.getClasses().length : 0;
        int linkedCount = 0;
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass c : database.getClasses()) {
                if (c.schemaClass != null)
                    linkedCount++;
            }
        }
        summaryPanel.add(new JLabel("Classes: " + classCount));
        summaryPanel.add(new JLabel("Linked to schema: " + linkedCount));
        summaryPanel.add(new JLabel("Unlinked: " + (classCount - linkedCount)));
        add(summaryPanel, BorderLayout.NORTH);

        // Tree + detail split
        DefaultMutableTreeNode root = buildTree();
        tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new SchemaLinkCellRenderer());
        tree.addTreeSelectionListener(e -> showDetail());

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, detailScroll);
        split.setDividerLocation(400);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    private DefaultMutableTreeNode buildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Database");
        if (database == null || database.getClasses() == null) {
            return root;
        }

        for (DODatabaseClass dbClass : database.getClasses()) {
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassNodeData(dbClass));
            if (dbClass.fields != null) {
                for (DODatabaseField field : dbClass.fields) {
                    classNode.add(new DefaultMutableTreeNode(new FieldNodeData(field)));
                }
            }
            root.add(classNode);
        }
        return root;
    }

    private void showDetail() {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selected == null) {
            detailArea.setText("");
            return;
        }

        Object userObj = selected.getUserObject();
        if (userObj instanceof ClassNodeData) {
            showClassDetail(((ClassNodeData) userObj).dbClass);
        } else if (userObj instanceof FieldNodeData) {
            showFieldDetail(((FieldNodeData) userObj).field);
        }
    }

    private void showClassDetail(DODatabaseClass dbClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("CLASS: ").append(dbClass.attributes.source).append("\n\n");
        sb.append("Parent class:     ").append(nvl(dbClass.attributes.parentClassName)).append("\n");
        sb.append("Instance count:   ").append(INT_FORMAT.format(dbClass.attributes.instanceCount)).append("\n");

        int objectIdCount = dbClass.objects.objectIds != null ? dbClass.objects.objectIds.length : 0;
        int uniqueCount = dbClass.objects.uniqueObjectIds != null ? dbClass.objects.uniqueObjectIds.length : 0;
        sb.append("Object IDs:       ").append(INT_FORMAT.format(objectIdCount)).append("\n");
        sb.append("Unique object IDs: ").append(INT_FORMAT.format(uniqueCount)).append("\n");

        int fieldCount = dbClass.fields != null ? dbClass.fields.length : 0;
        sb.append("Fields:           ").append(fieldCount).append("\n\n");

        sb.append("--- Schema Link ---\n");
        if (dbClass.schemaClass != null) {
            sb.append("Status:           LINKED\n");
            sb.append("Schema class:     ").append(dbClass.schemaClass.attributes.source).append("\n");
            sb.append("Destination:      ").append(nvl(dbClass.schemaClass.attributes.destinationName)).append("\n");
            sb.append("Migrate:          ").append(dbClass.schemaClass.attributes.migrate).append("\n");
        } else {
            sb.append("Status:           NOT LINKED\n");
            sb.append("This class exists in the database but has no matching reference schema class.\n");
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private void showFieldDetail(DODatabaseField field) {
        StringBuilder sb = new StringBuilder();
        sb.append("FIELD: ").append(field.attributes.source).append("\n\n");
        sb.append("Type:             ").append(nvl(field.attributes.type)).append("\n");
        sb.append("Is collection:    ").append(field.attributes.isCollection).append("\n");
        sb.append("Is array:         ").append(field.attributes.isArray).append("\n");
        sb.append("Children type:    ").append(nvl(field.attributes.childrenType)).append("\n\n");

        sb.append("Parent class:     ").append(field.parentClass != null ? field.parentClass.attributes.source : "(none)").append("\n\n");

        sb.append("--- Schema Link ---\n");
        if (field.schemaField != null) {
            sb.append("Status:           LINKED\n");
            sb.append("Schema field:     ").append(field.schemaField.attributes.source).append("\n");
            sb.append("Destination:      ").append(nvl(field.schemaField.attributes.destinationName)).append("\n");
            sb.append("Exported:         ").append(field.schemaField.attributes.isExported).append("\n");
        } else {
            sb.append("Status:           NOT LINKED\n");
            sb.append("This field exists in the database but has no matching reference schema field.\n");
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private static String nvl(String value) {
        return value != null ? value : "(none)";
    }

    // -- Tree node data holders --

    private static class ClassNodeData {
        final DODatabaseClass dbClass;

        ClassNodeData(DODatabaseClass dbClass) {
            this.dbClass = dbClass;
        }

        @Override
        public String toString() {
            String name = dbClass.getSourceName();
            int count = dbClass.objects.uniqueObjectIds != null ? dbClass.objects.uniqueObjectIds.length : 0;
            String linked = dbClass.schemaClass != null ? "\u2713" : "\u2717";
            return name + "  [" + count + "]  " + linked;
        }
    }

    private static class FieldNodeData {
        final DODatabaseField field;

        FieldNodeData(DODatabaseField field) {
            this.field = field;
        }

        @Override
        public String toString() {
            String name = field.attributes.source;
            String type = field.attributes.type != null ? field.attributes.type : "?";
            String linked = field.schemaField != null ? "\u2713" : "\u2717";
            return name + " : " + type + "  " + linked;
        }
    }

    // -- Custom cell renderer to show schema link status in color --

    private static class SchemaLinkCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                boolean linked = false;
                if (userObj instanceof ClassNodeData) {
                    linked = ((ClassNodeData) userObj).dbClass.schemaClass != null;
                } else if (userObj instanceof FieldNodeData) {
                    linked = ((FieldNodeData) userObj).field.schemaField != null;
                }
                if (!sel) {
                    setForeground(linked ? LINKED_COLOR : UNLINKED_COLOR);
                }
            }
            return c;
        }
    }
}
