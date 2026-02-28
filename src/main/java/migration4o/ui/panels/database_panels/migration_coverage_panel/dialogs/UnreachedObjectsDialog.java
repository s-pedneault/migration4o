package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import migration4o.database.reach.ObjectExportTrackingIndex;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Dialog showing unreached objects grouped by leaf class.
 * Provides object-level class hierarchy drill-down and direct ID tracer access.
 */
public class UnreachedObjectsDialog extends JDialog {

    private final ObjectExportTrackingIndex trackingIndex;
    private final DOSchema databaseSchema;

    private JTable leafClassTable;
    private JTable objectIdsTable;
    private JTree hierarchyTree;
    private JLabel summaryLabel;

    private DefaultTableModel leafClassModel;
    private DefaultTableModel objectIdsModel;
    private DefaultTreeModel hierarchyModel;

    private String selectedLeafClass;
    private Long selectedObjectId;
    private migration4o.database.DODatabaseContext dbContext;

    public UnreachedObjectsDialog(Frame parent, ObjectExportTrackingIndex trackingIndex, DOSchema databaseSchema, migration4o.database.DODatabaseContext dbContext) {
        super(parent, "Unreached Objects Explorer", false);
        this.trackingIndex = trackingIndex;
        this.databaseSchema = databaseSchema;
        this.dbContext = dbContext;

        setLayout(new BorderLayout(8, 8));
        setSize(1200, 700);
        setLocationRelativeTo(parent);
        initializeUI();
        loadLeafClassData();
    }

    private void initializeUI() {
        summaryLabel = new JLabel("Loading unreached object IDs...");
        add(summaryLabel, BorderLayout.NORTH);

        leafClassModel = new DefaultTableModel(new Object[] { "Leaf Class", "Unreached IDs" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        leafClassTable = new JTable(leafClassModel);
        leafClassTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> leafSorter = new TableRowSorter<>(leafClassModel);
        leafClassTable.setRowSorter(leafSorter);
        leafSorter.setSortKeys(java.util.List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        leafClassTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onLeafClassSelected();
            }
        });

        objectIdsModel = new DefaultTableModel(new Object[] { "Object ID" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        objectIdsTable = new JTable(objectIdsModel);
        objectIdsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectIdsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onObjectIdSelected();
            }
        });
        objectIdsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openTracerForSelection();
                }
            }
        });

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Select an object ID to drill through class hierarchy");
        hierarchyModel = new DefaultTreeModel(root);
        hierarchyTree = new JTree(hierarchyModel);
        hierarchyTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedClass();
                }
            }
        });

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 6, 6));
        rightPanel.add(wrap("Unreached Object IDs", new JScrollPane(objectIdsTable)));
        rightPanel.add(wrap("Class Hierarchy Drill", new JScrollPane(hierarchyTree)));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wrap("Leaf Classes", new JScrollPane(leafClassTable)), rightPanel);
        splitPane.setResizeWeight(0.45);
        splitPane.setDividerLocation(500);

        add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton tracerButton = new JButton("Trace Selected ID");
        tracerButton.addActionListener(e -> openTracerForSelection());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(tracerButton);
        bottomPanel.add(closeButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel wrap(String title, JScrollPane pane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(pane, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(400, 300));
        return panel;
    }

    private void loadLeafClassData() {
        leafClassModel.setRowCount(0);
        Map<String, List<Long>> unreachedByLeaf = trackingIndex.getUnreachedByLeafClass();

        int totalUnreached = 0;
        for (Map.Entry<String, List<Long>> entry : unreachedByLeaf.entrySet()) {
            int count = entry.getValue().size();
            totalUnreached += count;
            leafClassModel.addRow(new Object[] { entry.getKey(), count });
        }

        summaryLabel.setText(String.format("Unreached objects: %,d across %,d leaf classes", totalUnreached, unreachedByLeaf.size()));

        if (leafClassModel.getRowCount() > 0) {
            if (leafClassTable.getRowSorter() instanceof TableRowSorter) {
                @SuppressWarnings("unchecked")
                TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) leafClassTable.getRowSorter();
                sorter.setSortKeys(java.util.List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
            }
            leafClassTable.setRowSelectionInterval(0, 0);
        }
    }

    private void onLeafClassSelected() {
        int viewRow = leafClassTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = leafClassTable.convertRowIndexToModel(viewRow);
        selectedLeafClass = String.valueOf(leafClassModel.getValueAt(modelRow, 0));

        List<Long> unreachedIds = trackingIndex.getUnreachedForLeafClass(selectedLeafClass);
        objectIdsModel.setRowCount(0);
        for (Long objectId : unreachedIds) {
            objectIdsModel.addRow(new Object[] { objectId });
        }

        if (objectIdsModel.getRowCount() > 0) {
            objectIdsTable.setRowSelectionInterval(0, 0);
        } else {
            selectedObjectId = null;
            resetHierarchy();
        }
    }

    private void onObjectIdSelected() {
        int viewRow = objectIdsTable.getSelectedRow();
        if (viewRow < 0) {
            selectedObjectId = null;
            resetHierarchy();
            return;
        }

        int modelRow = objectIdsTable.convertRowIndexToModel(viewRow);
        Object value = objectIdsModel.getValueAt(modelRow, 0);
        if (value == null) {
            selectedObjectId = null;
            resetHierarchy();
            return;
        }

        selectedObjectId = Long.parseLong(String.valueOf(value));
        renderHierarchy(selectedObjectId);
    }

    private void renderHierarchy(long objectId) {
        String leafClass = trackingIndex.getLeafClassForObjectId(objectId);
        Set<String> allClasses = new LinkedHashSet<>(trackingIndex.getClassHierarchyForObjectId(objectId));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Object " + objectId);
        if (leafClass != null) {
            DefaultMutableTreeNode leafNode = new DefaultMutableTreeNode("Leaf: " + leafClass);
            root.add(leafNode);

            List<String> chain = buildParentChain(leafClass);
            DefaultMutableTreeNode current = leafNode;
            for (String className : chain) {
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(className);
                current.add(classNode);
                current = classNode;
            }
        }

        if (!allClasses.isEmpty()) {
            DefaultMutableTreeNode allTypes = new DefaultMutableTreeNode("All DB layers");
            for (String className : allClasses) {
                allTypes.add(new DefaultMutableTreeNode(className));
            }
            root.add(allTypes);
        }

        hierarchyModel.setRoot(root);
        expandAll();
    }

    private List<String> buildParentChain(String leafClass) {
        List<String> chain = new ArrayList<>();
        String current = leafClass;

        while (current != null && !current.isEmpty()) {
            chain.add(current);
            DOSchemaClass schemaClass = findInDatabaseSchema(current);
            if (schemaClass == null) {
                break;
            }
            current = schemaClass.parentClassName;
        }

        return chain;
    }

    private DOSchemaClass findInDatabaseSchema(String className) {
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (className.equals(schemaClass.source)) {
                return schemaClass;
            }
        }
        return null;
    }

    private void resetHierarchy() {
        hierarchyModel.setRoot(new DefaultMutableTreeNode("Select an object ID to drill through class hierarchy"));
    }

    private void expandAll() {
        for (int i = 0; i < hierarchyTree.getRowCount(); i++) {
            hierarchyTree.expandRow(i);
        }
    }

    private void openTracerForSelection() {
        if (selectedObjectId == null) {
            JOptionPane.showMessageDialog(this, "Select an object ID first.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        IDTracerDialog tracerDialog = new IDTracerDialog(dbContext);
        tracerDialog.setSearchId(selectedObjectId);
        tracerDialog.setVisible(true);
    }

    private void navigateToSelectedClass() {
        Object selected = hierarchyTree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode)) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selected;
        Object userObject = node.getUserObject();
        if (userObject == null) {
            return;
        }
        String text = String.valueOf(userObject);

        // Ignore synthetic tree nodes
        if (text.startsWith("Object ") || text.startsWith("Leaf:") || text.equals("All DB layers")) {
            return;
        }

        migration4o.ui.main.MainWindow mainWindow = migration4o.ui.main.MainWindow.getInstance();
        if (mainWindow != null) {
            mainWindow.detachAndNavigateToReferenceSchemaClass(text);
        }
    }
}
