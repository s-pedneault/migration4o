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
    private boolean showOnlyErrors = false;

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
        schemaTree.setCellRenderer(new SchemaTreeCellRenderer(this));

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

        // Add "Errors only" checkbox
        JCheckBox errorsOnlyCheckbox = new JCheckBox("Errors only");
        errorsOnlyCheckbox.setToolTipText("Show only classes with unresolved field types");
        errorsOnlyCheckbox.addActionListener(e -> {
            showOnlyErrors = errorsOnlyCheckbox.isSelected();
            filterTree(searchField.getText());
        });
        panel.add(errorsOnlyCheckbox, BorderLayout.EAST);

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
                return false; // Disable inline editing
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return fieldColumns[columnIndex].columnClass;
            }
        };

        fieldsTable = new JTable(fieldsTableModel);
        fieldsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldsTable.getTableHeader().setReorderingAllowed(false);

        // Configure column widths and renderers
        for (int i = 0; i < fieldColumns.length; i++) {
            fieldsTable.getColumnModel().getColumn(i).setPreferredWidth(fieldColumns[i].width);

            // Set custom renderers based on column name
            if (fieldColumns[i].name.equals("Type")) {
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new TypeRenderer());
            } else if (fieldColumns[i].name.equals("Children Type")) {
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new ChildrenTypeRenderer());
            }
        }

        // Add mouse listener for double-clicks to edit fields
        fieldsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = fieldsTable.rowAtPoint(e.getPoint());
                int col = fieldsTable.columnAtPoint(e.getPoint());

                if (row < 0) {
                    return;
                }

                // Double-click to edit field
                if (e.getClickCount() == 2) {
                    openFieldEditor(row);
                }
                // Single click on Children Type column to navigate
                else if (e.getClickCount() == 1 && col >= 0 && col < fieldColumns.length
                        && fieldColumns[col].name.equals("Children Type")) {
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

        // If filtering (by text or errors only), show all matching classes as a flat
        // list
        if (!currentFilter.isEmpty() || showOnlyErrors) {
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
    }

    /**
     * Custom renderer for the Type column.
     * - Classes: blue
     * - Primitives: green
     * - Others: red (unresolved)
     */
    private class TypeRenderer extends DefaultTableCellRenderer {
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
                        if (cls.getAbsoluteName().equals(typeName) || cls.getShortName().equals(typeName)) {
                            isSchemaClass = true;
                            break;
                        }
                    }
                }

                if (isSchemaClass) {
                    // Class: blue
                    setText("<html><font color='blue'>" + typeName + "</font></html>");
                } else if (TypeUtil.isPrimitiveType(typeName)) {
                    // Primitive: green
                    setText("<html><font color='green'>" + typeName + "</font></html>");
                } else {
                    // Other: red (unresolved)
                    setText("<html><font color='red'>" + typeName + "</font></html>");
                }
            }

            return c;
        }
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
                        if (cls.getAbsoluteName().equals(typeName) || cls.getShortName().equals(typeName)) {
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

    /**
     * Open a dialog to edit a field's properties.
     */
    private void openFieldEditor(int rowIndex) {
        if (currentSelectedNode == null || currentSelectedNode.getNodeType() != NodeType.CLASS) {
            return;
        }

        DOSchemaClass schemaClass = (DOSchemaClass) currentSelectedNode.getSchemaElement();
        if (schemaClass.getFields() == null || rowIndex >= schemaClass.getFields().length) {
            return;
        }

        DOSchemaField field = schemaClass.getFields()[rowIndex];

        // Create dialog
        JDialog dialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Edit Field", true);
        dialog.setLayout(new BorderLayout(10, 10));

        // Create form panel
        PropertyPanel formPanel = new PropertyPanel();
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add fields
        JTextField sourceField = formPanel.addTextField("Source", field.getSource() != null ? field.getSource() : "");
        JTextField destField = formPanel.addTextField("Destination",
                field.getDestinationName() != null ? field.getDestinationName() : "");
        JComboBox<String> typeCombo = createTypeComboBox();
        typeCombo.setSelectedItem(field.getType() != null ? field.getType() : "");
        typeCombo.setEditable(true);
        formPanel.addCustomField("Type", typeCombo);

        JCheckBox exportedCheckBox = formPanel.addCheckBox("Exported", field.isExported());
        JCheckBox skipIfEmptyCheckBox = formPanel.addCheckBox("Skip If Empty", field.isSkipIfEmpty());
        JCheckBox collectionCheckBox = formPanel.addCheckBox("Collection", field.isCollection());
        JCheckBox embedContentsCheckBox = formPanel.addCheckBox("Embed Contents", field.isEmbedContents());

        // Children Type - show as text with Edit button
        JPanel childrenTypePanel = new JPanel(new BorderLayout(5, 0));
        JLabel childrenTypeLabel = new JLabel(field.getChildrenType() != null ? field.getChildrenType() : "");
        childrenTypeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        childrenTypePanel.add(childrenTypeLabel, BorderLayout.CENTER);

        JButton editChildrenTypeButton = new JButton("Edit");
        editChildrenTypeButton.addActionListener(e -> {
            String selected = showClassFinder(childrenTypeLabel.getText());
            if (selected != null) {
                childrenTypeLabel.setText(selected);
            }
        });
        childrenTypePanel.add(editChildrenTypeButton, BorderLayout.EAST);
        formPanel.addCustomField("Children Type", childrenTypePanel);

        JTextField titleField = formPanel.addTextField("Title", field.getTitle() != null ? field.getTitle() : "");
        JTextField descField = formPanel.addTextField("Description",
                field.getDescription() != null ? field.getDescription() : "");

        dialog.add(formPanel, BorderLayout.CENTER);

        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            // Update the field in the table
            fieldsTableModel.setValueAt(sourceField.getText(), rowIndex, 0);
            fieldsTableModel.setValueAt(destField.getText(), rowIndex, 1);
            fieldsTableModel.setValueAt(typeCombo.getSelectedItem(), rowIndex, 2);
            fieldsTableModel.setValueAt(exportedCheckBox.isSelected(), rowIndex, 3);
            fieldsTableModel.setValueAt(skipIfEmptyCheckBox.isSelected(), rowIndex, 4);
            fieldsTableModel.setValueAt(collectionCheckBox.isSelected(), rowIndex, 5);
            fieldsTableModel.setValueAt(embedContentsCheckBox.isSelected(), rowIndex, 6);
            fieldsTableModel.setValueAt(childrenTypeLabel.getText(), rowIndex, 7);

            markModified();
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Show a class finder dialog to select a class name.
     * 
     * @param initialValue The initial value to show in the search field
     * @return The selected class name, or null if cancelled
     */
    private String showClassFinder(String initialValue) {
        JDialog finderDialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Class Finder", true);
        finderDialog.setLayout(new BorderLayout(10, 10));
        finderDialog.setSize(500, 400);

        // Search field at the top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JTextField searchField = new JTextField(initialValue != null ? initialValue : "");
        searchField.putClientProperty("JTextField.placeholderText", "Type to search classes...");
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        finderDialog.add(searchPanel, BorderLayout.NORTH);

        // List of matching classes
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> classList = new JList<>(listModel);
        classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Populate initial list
        updateClassList(listModel, initialValue != null ? initialValue : "");

        // Update list as user types
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(listModel, searchField.getText());
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(listModel, searchField.getText());
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(listModel, searchField.getText());
            }
        });

        JScrollPane listScroll = new JScrollPane(classList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        finderDialog.add(listScroll, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        final String[] result = { null };

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            String selected = classList.getSelectedValue();
            if (selected != null) {
                result[0] = selected;
                finderDialog.dispose();
            }
        });

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            result[0] = "";
            finderDialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> finderDialog.dispose());

        // Double-click to select
        classList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = classList.getSelectedValue();
                    if (selected != null) {
                        result[0] = selected;
                        finderDialog.dispose();
                    }
                }
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);
        finderDialog.add(buttonPanel, BorderLayout.SOUTH);

        finderDialog.setLocationRelativeTo(this);
        finderDialog.setVisible(true);

        return result[0];
    }

    /**
     * Update the class list based on the search pattern.
     */
    private void updateClassList(DefaultListModel<String> listModel, String pattern) {
        listModel.clear();

        if (schema == null || schema.getClasses() == null) {
            return;
        }

        String lowerPattern = pattern.toLowerCase();
        List<String> matches = new ArrayList<>();

        for (DOSchemaClass cls : schema.getClasses()) {
            String className = cls.getAbsoluteName();
            if (className.toLowerCase().contains(lowerPattern)) {
                matches.add(className);
            }
        }

        // Sort matches
        matches.sort(String.CASE_INSENSITIVE_ORDER);

        // Add to list
        for (String match : matches) {
            listModel.addElement(match);
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
        // Check errors filter first
        if (showOnlyErrors && !hasErrors(schemaClass)) {
            return false;
        }

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

    /**
     * Check if a class has errors (unresolved field types).
     * A field type is unresolved if it's not a primitive and not found in the
     * schema classes.
     */
    /**
     * Check if a class has any unresolved type errors.
     * Made package-private so the renderer can access it.
     */
    boolean hasErrors(DOSchemaClass schemaClass) {
        if (schemaClass.getFields() == null || schemaClass.getFields().length == 0) {
            return false;
        }

        for (DOSchemaField field : schemaClass.getFields()) {
            // Check the main type field
            if (isUnresolvedType(field.getType())) {
                return true;
            }

            // Check the childrenType field (for collections)
            if (isUnresolvedType(field.getChildrenType())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a type name is unresolved (not primitive and not in schema).
     */
    private boolean isUnresolvedType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }

        // Check if it's a primitive type
        if (TypeUtil.isPrimitiveType(typeName)) {
            return false;
        }

        // Check if it's a class in our schema
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                // Check both absolute name and short name
                if (cls.getAbsoluteName().equals(typeName) || cls.getShortName().equals(typeName)) {
                    return false;
                }
            }
        }

        // Not primitive and not in schema = unresolved
        return true;
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
