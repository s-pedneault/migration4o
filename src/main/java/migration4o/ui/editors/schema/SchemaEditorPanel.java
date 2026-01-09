package migration4o.ui.editors.schema;

import migration4o.models.schema.*;
import migration4o.schema.DODatabaseSchemaReader;
import migration4o.schema.DODatabaseSchemaWriter;
import migration4o.ui.components.PropertyPanel;
import migration4o.ui.models.SchemaTreeNode;
import migration4o.ui.models.SchemaTreeNode.NodeType;
import migration4o.util.TypeUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private String currentFilter = "";

    // Column definitions for the fields table
    private static class ColumnDefinition {
        final String name;
        final int width;
        final Class<?> columnClass;

        ColumnDefinition(String name, int width, Class<?> columnClass) {
            this.name = name;
            this.width = width;
            this.columnClass = columnClass;
        }
    }

    private ColumnDefinition[] fieldColumns;

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
        searchField.putClientProperty("JTextField.placeholderText", "Filter classes...");

        // Add document listener to filter as user types
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterTree(searchField.getText());
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterTree(searchField.getText());
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterTree(searchField.getText());
            }
        });

        panel.add(searchField, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFieldsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fields"));

        // Define columns with their properties
        fieldColumns = new ColumnDefinition[] {
                new ColumnDefinition("Source", 200, String.class),
                new ColumnDefinition("Destination", 150, String.class),
                new ColumnDefinition("Type", 150, String.class),
                new ColumnDefinition("Exported", 60, Boolean.class),
                new ColumnDefinition("Skip If Empty", 90, Boolean.class),
                new ColumnDefinition("Collection", 70, Boolean.class),
                new ColumnDefinition("Embed Contents", 120, Boolean.class),
                new ColumnDefinition("Children Type", 200, String.class)
        };

        // Extract column names
        String[] columnNames = new String[fieldColumns.length];
        for (int i = 0; i < fieldColumns.length; i++) {
            columnNames[i] = fieldColumns[i].name;
        }

        // Create table model
        fieldsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return fieldColumns[columnIndex].columnClass;
            }
        };

        // Add table model listener to track changes
        fieldsTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                markModified();
            }
        });

        fieldsTable = new JTable(fieldsTableModel);
        fieldsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldsTable.getTableHeader().setReorderingAllowed(false);

        // Configure column widths
        for (int i = 0; i < fieldColumns.length; i++) {
            fieldsTable.getColumnModel().getColumn(i).setPreferredWidth(fieldColumns[i].width);
        }

        // Set custom renderer for Children Type column
        fieldsTable.getColumnModel().getColumn(7).setCellRenderer(new ChildrenTypeRenderer());

        // Add mouse listener for Children Type clicks
        fieldsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = fieldsTable.rowAtPoint(e.getPoint());
                int col = fieldsTable.columnAtPoint(e.getPoint());

                // Check if clicked on Children Type column
                if (col == 7 && row >= 0) {
                    String childrenType = (String) fieldsTableModel.getValueAt(row, col);
                    if (childrenType != null && !childrenType.isEmpty()) {
                        navigateToClass(childrenType);
                    }
                }
            }
        });

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

        if (schema == null || schema.getClasses() == null) {
            treeModel.reload();
            return;
        }

        // If filtering, show all matching classes as a flat list
        if (!currentFilter.isEmpty()) {
            List<DOSchemaClass> matchingClasses = new ArrayList<>();
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (matchesFilter(schemaClass)) {
                    matchingClasses.add(schemaClass);
                }
            }

            // Sort by display name
            matchingClasses.sort((a, b) -> getClassDisplayName(a).compareToIgnoreCase(getClassDisplayName(b)));

            // Add all matching classes as direct children of root
            for (DOSchemaClass schemaClass : matchingClasses) {
                SchemaTreeNode classNode = createClassNode(schemaClass);
                rootNode.add(classNode);
            }

            treeModel.reload();
            expandFirstLevel();
            return;
        }

        // No filter - build hierarchy normally
        // Build class name to class map for quick lookup
        java.util.Map<String, DOSchemaClass> classMap = new java.util.HashMap<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            classMap.put(schemaClass.getShortName(), schemaClass);
            // Also map by absolute name for parent lookups
            if (schemaClass.getAbsoluteName() != null) {
                classMap.put(schemaClass.getAbsoluteName(), schemaClass);
            }
        }

        // Build parent-to-children map
        java.util.Map<String, List<DOSchemaClass>> childrenMap = new java.util.HashMap<>();
        List<DOSchemaClass> rootClasses = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String parentName = schemaClass.getParentClass();
            if (parentName == null || parentName.isEmpty() || parentName.equals("Undetermined")) {
                rootClasses.add(schemaClass);
            } else {
                childrenMap.computeIfAbsent(parentName, k -> new ArrayList<>()).add(schemaClass);
            }
        }

        // Sort root classes by name
        rootClasses.sort((a, b) -> getClassDisplayName(a).compareToIgnoreCase(getClassDisplayName(b)));

        // Build tree from root classes
        for (DOSchemaClass rootClass : rootClasses) {
            SchemaTreeNode classNode = createClassNode(rootClass);
            rootNode.add(classNode);
            addChildClasses(classNode, rootClass, childrenMap, classMap);
        }

        treeModel.reload();
        expandFirstLevel();
    }

    private SchemaTreeNode createClassNode(DOSchemaClass schemaClass) {
        String className = getClassDisplayName(schemaClass);
        if (!schemaClass.isMigrate()) {
            className += " (not exported)";
        }
        return new SchemaTreeNode(className, NodeType.CLASS, schemaClass);
    }

    private void addChildClasses(SchemaTreeNode parentNode, DOSchemaClass parentClass,
            java.util.Map<String, List<DOSchemaClass>> childrenMap,
            java.util.Map<String, DOSchemaClass> classMap) {
        // Look for children by short name and absolute name
        List<DOSchemaClass> children = new ArrayList<>();

        if (childrenMap.containsKey(parentClass.getShortName())) {
            children.addAll(childrenMap.get(parentClass.getShortName()));
        }
        if (parentClass.getAbsoluteName() != null && childrenMap.containsKey(parentClass.getAbsoluteName())) {
            List<DOSchemaClass> absoluteChildren = childrenMap.get(parentClass.getAbsoluteName());
            for (DOSchemaClass child : absoluteChildren) {
                if (!children.contains(child)) {
                    children.add(child);
                }
            }
        }

        // Sort children by name
        children.sort((a, b) -> getClassDisplayName(a).compareToIgnoreCase(getClassDisplayName(b)));

        // Add child nodes recursively
        for (DOSchemaClass child : children) {
            SchemaTreeNode childNode = createClassNode(child);
            parentNode.add(childNode);
            addChildClasses(childNode, child, childrenMap, classMap);
        }
    }

    private String getClassDisplayName(DOSchemaClass schemaClass) {
        String sourceName = schemaClass.getAbsoluteName();
        String shortName = sourceName != null && sourceName.contains(".")
                ? sourceName.substring(sourceName.lastIndexOf('.') + 1)
                : (sourceName != null ? sourceName : schemaClass.getShortName());

        String destinationName = schemaClass.getShortName();
        if (destinationName != null && !destinationName.isEmpty()
                && !shortName.equals(destinationName)) {
            return shortName + " > " + destinationName;
        }
        return shortName;
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
            // Update tree label based on checkbox state
            String className = getClassDisplayName(schemaClass);
            if (!exportCheckBox.isSelected()) {
                className += " (not exported)";
            }
            if (currentSelectedNode != null) {
                currentSelectedNode.setUserObject(className);
                treeModel.nodeChanged(currentSelectedNode);
            }
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

    private JComboBox<String> createChildrenClassComboBox() {
        List<String> classNames = new ArrayList<>();
        classNames.add("");

        // Collect all class names from schema
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                classNames.add(cls.getAbsoluteName());
            }
        }

        return new JComboBox<>(classNames.toArray(new String[0]));
    }

    private JComboBox<String> createTypeComboBox() {
        List<String> types = new ArrayList<>();

        // Add Java primitives
        types.add("");
        types.add("boolean");
        types.add("byte");
        types.add("char");
        types.add("short");
        types.add("int");
        types.add("long");
        types.add("float");
        types.add("double");
        types.add("String");
        types.add("java.lang.Object");
        types.add("java.lang.Object[]");
        types.add("java.util.Date");
        types.add("java.util.Vector");

        // Add all schema classes
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                types.add(cls.getAbsoluteName());
            }
        }

        return new JComboBox<>(types.toArray(new String[0]));
    }

    private void populateFieldsTable(DOSchemaClass schemaClass) {
        if (schemaClass.getFields() == null) {
            return;
        }

        for (DOSchemaField field : schemaClass.getFields()) {
            Object[] rowData = {
                    field.getSource(),
                    field.getDestinationName(),
                    field.getType() != null ? field.getType() : "",
                    field.isExported(),
                    field.isSkipIfEmpty(),
                    field.isCollection(),
                    field.isEmbedContents(),
                    field.getChildrenType() != null ? field.getChildrenType() : ""
            };
            fieldsTableModel.addRow(rowData);
        }

        // Set up cell editors now that schema is loaded
        fieldsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(createTypeComboBox()));
        fieldsTable.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(createChildrenClassComboBox()));
    }

    /**
     * Custom renderer for the Children Type column.
     * - Classes: blue and underlined
     * - Primitives: green
     * - Others: red
     */
    private class ChildrenTypeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null && !value.toString().isEmpty()) {
                String typeName = value.toString();

                // Check if it's a class in our schema
                boolean isSchemaClass = false;
                if (schema != null && schema.getClasses() != null) {
                    for (DOSchemaClass cls : schema.getClasses()) {
                        if (cls.getAbsoluteName().equals(typeName)) {
                            isSchemaClass = true;
                            break;
                        }
                    }
                }

                if (isSchemaClass) {
                    // Class: blue and underlined
                    setText("<html><u><font color='blue'>" + typeName + "</font></u></html>");
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (TypeUtil.isPrimitiveType(typeName)) {
                    // Primitive: green
                    setText("<html><font color='green'>" + typeName + "</font></html>");
                    setCursor(Cursor.getDefaultCursor());
                } else {
                    // Other: red
                    setText("<html><font color='red'>" + typeName + "</font></html>");
                    setCursor(Cursor.getDefaultCursor());
                }
            } else {
                setCursor(Cursor.getDefaultCursor());
            }

            return c;
        }
    }

    /**
     * Navigate to a class in the tree by its absolute name.
     */
    private void navigateToClass(String className) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        // Find the class in the schema
        DOSchemaClass targetClass = null;
        for (DOSchemaClass cls : schema.getClasses()) {
            if (cls.getAbsoluteName().equals(className)) {
                targetClass = cls;
                break;
            }
        }

        if (targetClass == null) {
            return; // Class not found in schema
        }

        // Clear any filter to ensure the class is visible
        if (!currentFilter.isEmpty()) {
            currentFilter = "";
            buildTree();
        }

        // Find the node in the tree
        SchemaTreeNode nodeToSelect = findClassNode((SchemaTreeNode) treeModel.getRoot(), targetClass);
        if (nodeToSelect != null) {
            TreePath path = new TreePath(treeModel.getPathToRoot(nodeToSelect));
            schemaTree.setSelectionPath(path);
            schemaTree.scrollPathToVisible(path);
        }
    }

    /**
     * Recursively find a class node in the tree.
     */
    private SchemaTreeNode findClassNode(SchemaTreeNode node, DOSchemaClass targetClass) {
        if (node.getNodeType() == NodeType.CLASS) {
            DOSchemaClass nodeClass = (DOSchemaClass) node.getSchemaElement();
            if (nodeClass.getAbsoluteName().equals(targetClass.getAbsoluteName())) {
                return node;
            }
        }

        // Search children
        for (int i = 0; i < node.getChildCount(); i++) {
            SchemaTreeNode child = (SchemaTreeNode) node.getChildAt(i);
            SchemaTreeNode result = findClassNode(child, targetClass);
            if (result != null) {
                return result;
            }
        }

        return null;
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
        if (schema == null) {
            JOptionPane.showMessageDialog(this,
                    "No schema loaded to save.",
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            setStatus("Saving schema...");

            // Use the writer to save the schema
            DODatabaseSchemaWriter writer = new DODatabaseSchemaWriter();
            writer.writeSchema(schema, schemaFilePath);

            modified = false;
            setStatus("Schema saved successfully");

            JOptionPane.showMessageDialog(this,
                    "Schema saved successfully!\nBackup created.",
                    "Save Successful",
                    JOptionPane.INFORMATION_MESSAGE);

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

    private void filterTree(String filterText) {
        currentFilter = filterText != null ? filterText.toLowerCase().trim() : "";
        buildTree();
    }

    private boolean matchesFilter(DOSchemaClass schemaClass) {
        if (currentFilter.isEmpty()) {
            return true;
        }

        // Check display name (includes both source name and destination)
        String displayName = getClassDisplayName(schemaClass).toLowerCase();
        if (displayName.contains(currentFilter)) {
            return true;
        }

        // Check source class name only (last part of absolute name)
        if (schemaClass.getAbsoluteName() != null) {
            String sourceName = schemaClass.getAbsoluteName();
            String sourceClassName = sourceName.contains(".")
                    ? sourceName.substring(sourceName.lastIndexOf('.') + 1)
                    : sourceName;
            if (sourceClassName.toLowerCase().contains(currentFilter)) {
                return true;
            }
        }

        // Check destination name
        if (schemaClass.getShortName() != null &&
                schemaClass.getShortName().toLowerCase().contains(currentFilter)) {
            return true;
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
