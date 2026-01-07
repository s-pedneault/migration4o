package migration4o.ui.editors.schema;

import migration4o.models.schema.*;
import migration4o.schema.DODatabaseSchemaReader;
import migration4o.ui.components.PropertyPanel;
import migration4o.ui.models.SchemaTreeNode;
import migration4o.ui.models.SchemaTreeNode.NodeType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor panel for database-schema.xml file.
 * Provides a tree view with property editing.
 */
public class SchemaEditorPanel extends JPanel {

    private final String schemaFilePath;
    private DOSchema schema;
    private boolean modified;

    private JTree schemaTree;
    private DefaultTreeModel treeModel;
    private PropertyPanel propertyPanel;
    private JTable fieldsTable;
    private DefaultTableModel fieldsTableModel;
    private JLabel statusLabel;
    private SchemaTreeNode currentSelectedNode;

    public SchemaEditorPanel(String schemaFilePath) {
        this.schemaFilePath = schemaFilePath;
        this.modified = false;

        initializeUI();
        loadSchema();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));

        // Create toolbar
        add(createToolbar(), BorderLayout.NORTH);

        // Create split pane with tree on left and properties on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.4);

        // Create tree panel
        splitPane.setLeftComponent(createTreePanel());

        // Create right panel with properties and fields table
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        propertyPanel = new PropertyPanel();
        rightPanel.add(propertyPanel, BorderLayout.NORTH);
        rightPanel.add(createFieldsTablePanel(), BorderLayout.CENTER);

        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);

        // Create status bar
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Reload button
        JButton reloadButton = new JButton("Reload");
        reloadButton.setIcon(UIManager.getIcon("FileView.hardDriveIcon"));
        reloadButton.addActionListener(e -> reloadSchema());
        toolbar.add(reloadButton);

        toolbar.addSeparator();

        // Save button
        JButton saveButton = new JButton("Save");
        saveButton.setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
        saveButton.addActionListener(e -> saveSchema());
        toolbar.add(saveButton);

        toolbar.addSeparator();

        // Expand/Collapse buttons
        JButton expandAllButton = new JButton("Expand All");
        expandAllButton.addActionListener(e -> expandAll());
        toolbar.add(expandAllButton);

        JButton collapseAllButton = new JButton("Collapse All");
        collapseAllButton.addActionListener(e -> collapseAll());
        toolbar.add(collapseAllButton);

        return toolbar;
    }

    private JPanel createTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Schema Structure"));

        // Create tree
        SchemaTreeNode rootNode = new SchemaTreeNode("Database Schema", NodeType.ROOT);
        treeModel = new DefaultTreeModel(rootNode);
        schemaTree = new JTree(treeModel);
        schemaTree.setShowsRootHandles(true);
        schemaTree.setRootVisible(true);

        // Set custom renderer for better visualization
        schemaTree.setCellRenderer(new SchemaTreeCellRenderer());

        // Add selection listener
        schemaTree.addTreeSelectionListener(e -> onTreeSelectionChanged());

        JScrollPane scrollPane = new JScrollPane(schemaTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add search field
        panel.add(createSearchPanel(), BorderLayout.NORTH);

        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search classes...");

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchTree(searchField.getText()));

        panel.add(searchField, BorderLayout.CENTER);
        panel.add(searchButton, BorderLayout.EAST);

        return panel;
    }

    private JPanel createFieldsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fields"));

        // Create table model
        String[] columnNames = { "Field Name", "Source", "Destination", "Exported", "Skip If Empty", "Collection",
                "Embed Contents", "Children Class" };
        fieldsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only for now
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 3 || columnIndex == 4 || columnIndex == 5 || columnIndex == 6) {
                    return Boolean.class;
                }
                return String.class;
            }
        };

        fieldsTable = new JTable(fieldsTableModel);
        fieldsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldsTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        fieldsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        fieldsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        fieldsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        fieldsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        fieldsTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        fieldsTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        fieldsTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        fieldsTable.getColumnModel().getColumn(7).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(fieldsTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        statusLabel = new JLabel("Ready");
        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    private void loadSchema() {
        try {
            setStatus("Loading schema...");

            // Load schema using DODatabaseSchemaReader
            DODatabaseSchemaReader reader = new DODatabaseSchemaReader();
            schema = reader.readSchema(schemaFilePath);

            // Build tree
            buildTree();

            setStatus("Schema loaded successfully. " + getSchemaStats());
            modified = false;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading schema: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            setStatus("Error loading schema");
        }
    }

    private void buildTree() {
        SchemaTreeNode rootNode = (SchemaTreeNode) treeModel.getRoot();
        rootNode.removeAllChildren();

        if (schema == null || schema.getModules() == null) {
            treeModel.reload();
            return;
        }

        // Add modules
        for (DOSchemaModule module : schema.getModules()) {
            SchemaTreeNode moduleNode = new SchemaTreeNode(
                    module.getName(),
                    NodeType.MODULE,
                    module);
            rootNode.add(moduleNode);

            // Add classes
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    String className = schemaClass.getShortName();
                    if (!schemaClass.isMigrate()) {
                        className += " (not exported)";
                    }

                    SchemaTreeNode classNode = new SchemaTreeNode(
                            className,
                            NodeType.CLASS,
                            schemaClass);
                    moduleNode.add(classNode);
                }
            }
        }

        treeModel.reload();
        expandFirstLevel();
    }

    private void onTreeSelectionChanged() {
        TreePath path = schemaTree.getSelectionPath();
        if (path == null) {
            propertyPanel.clear();
            clearFieldsTable();
            currentSelectedNode = null;
            return;
        }

        SchemaTreeNode node = (SchemaTreeNode) path.getLastPathComponent();
        currentSelectedNode = node;
        displayProperties(node);
    }

    private void clearFieldsTable() {
        fieldsTableModel.setRowCount(0);
    }

    private void displayProperties(SchemaTreeNode node) {
        propertyPanel.clear();
        clearFieldsTable();

        switch (node.getNodeType()) {
            case MODULE:
                displayModuleProperties((DOSchemaModule) node.getSchemaElement());
                break;
            case CLASS:
                displayClassProperties((DOSchemaClass) node.getSchemaElement());
                break;
            default:
                break;
        }
    }

    private void displayModuleProperties(DOSchemaModule module) {
        propertyPanel.addReadOnlyTextField("Type", "Module");
        propertyPanel.addTextField("Name", module.getName())
                .addActionListener(e -> markModified());
    }

    private void displayClassProperties(DOSchemaClass schemaClass) {
        propertyPanel.addReadOnlyTextField("Type", "Class");
        propertyPanel.addReadOnlyTextField("Source", schemaClass.getAbsoluteName());
        propertyPanel.addTextField("Destination Name", schemaClass.getShortName())
                .addActionListener(e -> markModified());

        JCheckBox exportCheckBox = propertyPanel.addCheckBox("Export (migrate)", schemaClass.isMigrate());
        exportCheckBox.addActionListener(e -> {
            markModified();
            updateTreeNodeLabel(currentSelectedNode, schemaClass);
        });

        // Parent Class selector
        JComboBox<String> parentClassCombo = createParentClassComboBox(schemaClass.getParentClass());
        propertyPanel.addCustomField("Parent Class", parentClassCombo);
        parentClassCombo.addActionListener(e -> markModified());

        if (schemaClass.getTitle() != null && !schemaClass.getTitle().isEmpty()) {
            propertyPanel.addTextField("Title", schemaClass.getTitle())
                    .addActionListener(e -> markModified());
        }

        int fieldCount = schemaClass.getFields() != null ? schemaClass.getFields().length : 0;
        propertyPanel.addReadOnlyTextField("Field Count", String.valueOf(fieldCount));

        // Populate fields table
        populateFieldsTable(schemaClass);
    }

    private JComboBox<String> createParentClassComboBox(String currentParent) {
        List<String> classNames = new ArrayList<>();
        classNames.add("Undetermined");

        // Collect all class names from schema
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                classNames.add(cls.getAbsoluteName());
            }
        }

        JComboBox<String> comboBox = new JComboBox<>(classNames.toArray(new String[0]));

        // Set current selection
        if (currentParent != null && !currentParent.isEmpty()) {
            comboBox.setSelectedItem(currentParent);
        } else {
            comboBox.setSelectedItem("Undetermined");
        }

        return comboBox;
    }

    private void updateTreeNodeLabel(SchemaTreeNode node, DOSchemaClass schemaClass) {
        if (node == null || node.getNodeType() != NodeType.CLASS) {
            return;
        }

        String className = schemaClass.getShortName();
        if (!schemaClass.isMigrate()) {
            className += " (not exported)";
        }

        node.setUserObject(className);
        treeModel.nodeChanged(node);
    }

    private void populateFieldsTable(DOSchemaClass schemaClass) {
        if (schemaClass.getFields() == null) {
            return;
        }

        for (DOSchemaField field : schemaClass.getFields()) {
            Object[] rowData = {
                    field.getDestinationName(),
                    field.getSource(),
                    field.getDestinationName(),
                    field.isExported(),
                    field.isSkipIfEmpty(),
                    field.isCollection(),
                    field.isEmbedContents(),
                    field.getChildrenClassName() != null ? field.getChildrenClassName() : ""
            };
            fieldsTableModel.addRow(rowData);
        }
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            setStatus("Modified - unsaved changes");
        }
    }

    private void reloadSchema() {
        if (modified) {
            int result = JOptionPane.showConfirmDialog(this,
                    "You have unsaved changes. Reload anyway?",
                    "Confirm Reload",
                    JOptionPane.YES_NO_OPTION);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        loadSchema();
    }

    private void saveSchema() {
        try {
            setStatus("Saving schema...");

            // TODO: Implement schema saving (write back to XML)
            // This will require a schema writer component

            JOptionPane.showMessageDialog(this,
                    "Schema saving is not yet implemented.\n" +
                            "This feature will write changes back to the XML file.",
                    "Not Implemented",
                    JOptionPane.INFORMATION_MESSAGE);

            setStatus("Ready");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving schema: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            setStatus("Error saving schema");
        }
    }

    private void expandAll() {
        for (int i = 0; i < schemaTree.getRowCount(); i++) {
            schemaTree.expandRow(i);
        }
    }

    private void collapseAll() {
        for (int i = schemaTree.getRowCount() - 1; i >= 0; i--) {
            schemaTree.collapseRow(i);
        }
    }

    private void expandFirstLevel() {
        TreeNode root = (TreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            schemaTree.expandPath(new TreePath(new Object[] { root, root.getChildAt(i) }));
        }
    }

    private void searchTree(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }

        String lowerSearch = searchText.toLowerCase();
        TreeNode root = (TreeNode) treeModel.getRoot();

        // Simple search through tree nodes
        searchNode(root, lowerSearch);
    }

    private boolean searchNode(TreeNode node, String searchText) {
        if (node instanceof SchemaTreeNode) {
            SchemaTreeNode schemaNode = (SchemaTreeNode) node;
            String nodeText = schemaNode.toString().toLowerCase();

            if (nodeText.contains(searchText)) {
                TreePath path = new TreePath(treeModel.getPathToRoot(schemaNode));
                schemaTree.setSelectionPath(path);
                schemaTree.scrollPathToVisible(path);
                return true;
            }
        }

        // Search children
        for (int i = 0; i < node.getChildCount(); i++) {
            if (searchNode(node.getChildAt(i), searchText)) {
                return true;
            }
        }

        return false;
    }

    private String getSchemaStats() {
        if (schema == null || schema.getModules() == null) {
            return "";
        }

        int moduleCount = schema.getModules().length;
        int classCount = 0;
        int fieldCount = 0;

        for (DOSchemaModule module : schema.getModules()) {
            if (module.getClasses() != null) {
                classCount += module.getClasses().length;
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    if (schemaClass.getFields() != null) {
                        fieldCount += schemaClass.getFields().length;
                    }
                }
            }
        }

        return String.format("%d modules, %d classes, %d fields", moduleCount, classCount, fieldCount);
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }
}
