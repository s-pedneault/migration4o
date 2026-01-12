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
import javax.swing.table.TableCellRenderer;
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

    /**
     * Constructor for viewing an inferred schema (not backed by a file).
     * The schema cannot be saved in this mode.
     */
    public SchemaEditorPanel(DOSchema schema, String displayName) {
        this.schemaFilePath = null; // No file backing
        this.schema = schema;
        this.modified = false;

        initializeUI();
        buildTree();
        setStatus("Inferred schema loaded: " + displayName + " (read-only)");
    }

    /**
     * Add a class from another schema to this schema.
     * This is used when adding a database-only class to the reference schema.
     * 
     * @param className   The name of the class to add
     * @param sourceClass The source class to copy from
     */
    public void addClassFromComparison(String className, DOSchemaClass sourceClass) {
        if (className == null || className.isEmpty() || schema == null || sourceClass == null) {
            return;
        }

        // Check if class already exists
        if (schema.getClasses() != null) {
            for (DOSchemaClass existing : schema.getClasses()) {
                if (existing.getAbsoluteName().equals(className) || existing.getSourceName().equals(className)) {
                    JOptionPane.showMessageDialog(this,
                            "Class '" + className + "' already exists in the schema.",
                            "Class Exists", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        // Create a new class copying all properties from the source
        DOSchemaClass newClass = new DOSchemaClass(
                sourceClass.getSourceName(),
                sourceClass.getDestinationName(),
                sourceClass.getDescription(),
                sourceClass.getTitle(),
                sourceClass.getParentClass(),
                sourceClass.getFields() != null ? sourceClass.getFields().clone() : new DOSchemaField[0],
                sourceClass.getSchemaReferences(),
                sourceClass.isMigrate());

        // Create new schema with the added class
        DOSchemaClass[] existingClasses = schema.getClasses();
        DOSchemaClass[] newClasses = new DOSchemaClass[existingClasses.length + 1];
        System.arraycopy(existingClasses, 0, newClasses, 0, existingClasses.length);
        newClasses[existingClasses.length] = newClass;

        // Recreate schema with new classes array
        schema = new DOSchema(newClasses, schema.getModules(), schema.getFoundationClasses());

        // Rebuild the tree to show the new class
        buildTree();

        // Find and select the new class in the tree
        selectClassByName(className);

        markModified();

        setStatus("Added class: " + className + " with " +
                (sourceClass.getFields() != null ? sourceClass.getFields().length : 0) + " field(s)");
    }

    /**
     * Select a class in the tree by its name.
     */
    private void selectClassByName(String className) {
        if (className == null || treeModel == null) {
            return;
        }

        SchemaTreeNode root = (SchemaTreeNode) treeModel.getRoot();
        selectClassInNode(root, className);
    }

    private boolean selectClassInNode(SchemaTreeNode node, String className) {
        // Check if this node is the class we're looking for
        if (node.getNodeType() == NodeType.CLASS) {
            DOSchemaClass schemaClass = (DOSchemaClass) node.getSchemaElement();
            if (schemaClass != null &&
                    (schemaClass.getAbsoluteName().equals(className)
                            || schemaClass.getSourceName().equals(className))) {
                TreePath path = new TreePath(node.getPath());
                schemaTree.setSelectionPath(path);
                schemaTree.scrollPathToVisible(path);
                return true;
            }
        }

        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            SchemaTreeNode child = (SchemaTreeNode) node.getChildAt(i);
            if (selectClassInNode(child, className)) {
                return true;
            }
        }

        return false;
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

        // Reload button (only enabled if we have a file)
        JButton reloadButton = new JButton("Reload");
        reloadButton.setIcon(UIManager.getIcon("FileView.hardDriveIcon"));
        reloadButton.addActionListener(e -> reloadSchema());
        reloadButton.setEnabled(schemaFilePath != null);
        toolbar.add(reloadButton);

        toolbar.addSeparator();

        // Save button (only enabled if we have a file)
        JButton saveButton = new JButton("Save");
        saveButton.setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
        saveButton.addActionListener(e -> saveSchema());
        saveButton.setEnabled(schemaFilePath != null);
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
            } else if (fieldColumns[i].name.equals("Collection")) {
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new CollectionRenderer());
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
                // Single click on Type or Children Type column to navigate
                else if (e.getClickCount() == 1 && col >= 0 && col < fieldColumns.length
                        && (fieldColumns[col].name.equals("Type") || fieldColumns[col].name.equals("Children Type"))) {
                    String typeName = (String) fieldsTableModel.getValueAt(row, col);
                    if (typeName != null && !typeName.isEmpty()) {
                        navigateToClass(typeName);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(fieldsTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add button panel at the bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addFieldButton = new JButton("Add Field");
        addFieldButton.addActionListener(e -> addNewField());
        buttonPanel.add(addFieldButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

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
                    // Class: blue and underlined
                    setText("<html><u><font color='blue'>" + typeName + "</font></u></html>");
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (TypeUtil.isPrimitiveType(typeName)) {
                    // Primitive: green
                    setText("<html><font color='green'>" + typeName + "</font></html>");
                    setCursor(Cursor.getDefaultCursor());
                } else {
                    // Other: red (unresolved)
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
     * Custom renderer for the Collection column.
     * Displays a checkbox and highlights the cell with red background if there's a
     * collection/childrenType mismatch.
     */
    private class CollectionRenderer extends JCheckBox implements TableCellRenderer {

        public CollectionRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            // Set checkbox state
            if (value instanceof Boolean) {
                setSelected((Boolean) value);
            } else {
                setSelected(false);
            }

            // Check for collection/childrenType consistency error
            boolean hasError = false;
            if (row >= 0 && row < fieldsTableModel.getRowCount()) {
                // Get collection value (current cell)
                Boolean isCollection = (Boolean) value;

                // Get childrenType value from the table
                Object childrenTypeObj = fieldsTableModel.getValueAt(row, getColumnIndex("Children Type"));
                String childrenType = childrenTypeObj != null ? childrenTypeObj.toString() : "";
                boolean hasChildrenType = !childrenType.isEmpty();

                if (isCollection != null) {
                    // Error: collection is true but childrenType is empty
                    if (isCollection && !hasChildrenType) {
                        hasError = true;
                    }
                    // Error: collection is false but childrenType is not empty
                    if (!isCollection && hasChildrenType) {
                        hasError = true;
                    }
                }
            }

            // Set background color based on error state
            if (hasError) {
                if (!isSelected) {
                    setBackground(new Color(255, 200, 200)); // Light red background
                } else {
                    setBackground(table.getSelectionBackground().darker());
                }
            } else {
                if (!isSelected) {
                    setBackground(table.getBackground());
                } else {
                    setBackground(table.getSelectionBackground());
                }
            }

            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

            return this;
        }

        private int getColumnIndex(String columnName) {
            for (int i = 0; i < fieldColumns.length; i++) {
                if (fieldColumns[i].name.equals(columnName)) {
                    return i;
                }
            }
            return -1;
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
     * Add a new field to the current class.
     */
    private void addNewField() {
        if (currentSelectedNode == null || currentSelectedNode.getNodeType() != NodeType.CLASS) {
            return;
        }

        // Create a new empty field
        DOSchemaField newField = new DOSchemaField("", "", "java.lang.String", true, true, false, false, "", null,
                null);

        // Show field editor dialog
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        FieldEditorDialog dialog = FieldEditorDialog.showDialog(owner, schema, newField, true);

        if (dialog != null) {
            if (dialog.isDeleted()) {
                // User cancelled by deleting
                return;
            }

            // Check if a new class should be created
            if (dialog.getClassToCreate() != null) {
                createNewClass(dialog.getClassToCreate());
            }

            // Create the new field object with all properties
            DOSchemaField newFieldWithData = new DOSchemaField(
                    dialog.getFieldSource(),
                    dialog.getFieldDestination(),
                    dialog.getFieldType(),
                    dialog.isFieldExported(),
                    dialog.isFieldSkipIfEmpty(),
                    dialog.isFieldCollection(),
                    dialog.isFieldEmbedContents(),
                    dialog.getFieldChildrenType(),
                    dialog.getFieldTitle(),
                    dialog.getFieldDescription(),
                    null,
                    null);

            // Add the new field to the table
            Object[] rowData = {
                    dialog.getFieldSource(),
                    dialog.getFieldDestination(),
                    dialog.getFieldType(),
                    dialog.isFieldExported(),
                    dialog.isFieldSkipIfEmpty(),
                    dialog.isFieldCollection(),
                    dialog.isFieldEmbedContents(),
                    dialog.getFieldChildrenType()
            };
            fieldsTableModel.addRow(rowData);

            // Rebuild the class with the updated fields
            rebuildCurrentClassFields();

            markModified();
        }
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

        // Show field editor dialog
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        FieldEditorDialog dialog = FieldEditorDialog.showDialog(owner, schema, field, false);

        if (dialog != null) {
            if (dialog.isDeleted()) {
                // Remove the field from the table
                fieldsTableModel.removeRow(rowIndex);

                // Rebuild the class with the updated fields
                rebuildCurrentClassFields();

                markModified();
                return;
            }

            // Check if a new class should be created
            if (dialog.getClassToCreate() != null) {
                createNewClass(dialog.getClassToCreate());
            }

            // Update the field in the table
            fieldsTableModel.setValueAt(dialog.getFieldSource(), rowIndex, 0);
            fieldsTableModel.setValueAt(dialog.getFieldDestination(), rowIndex, 1);
            fieldsTableModel.setValueAt(dialog.getFieldType(), rowIndex, 2);
            fieldsTableModel.setValueAt(dialog.isFieldExported(), rowIndex, 3);
            fieldsTableModel.setValueAt(dialog.isFieldSkipIfEmpty(), rowIndex, 4);
            fieldsTableModel.setValueAt(dialog.isFieldCollection(), rowIndex, 5);
            fieldsTableModel.setValueAt(dialog.isFieldEmbedContents(), rowIndex, 6);
            fieldsTableModel.setValueAt(dialog.getFieldChildrenType(), rowIndex, 7);

            // Rebuild the class with the updated fields
            rebuildCurrentClassFields();

            markModified();
        }
    }

    /**
     * Create a new class in the schema.
     * 
     * @param className The name of the class to create
     */
    private void createNewClass(String className) {
        if (className == null || className.isEmpty() || schema == null) {
            return;
        }

        // Check if class already exists
        if (schema.getClasses() != null) {
            for (DOSchemaClass existing : schema.getClasses()) {
                if (existing.getAbsoluteName().equals(className) || existing.getShortName().equals(className)) {
                    // Class already exists, no need to create
                    return;
                }
            }
        }

        // Create a new class with minimal fields
        DOSchemaClass newClass = new DOSchemaClass(
                className, // absoluteName
                className, // simpleName (use same name)
                null, // description
                null, // title
                null, // parentClassName
                new DOSchemaField[0], // empty fields array
                null, // schemaReferences
                true // migrate
        );

        // Create new schema with the added class
        DOSchemaClass[] existingClasses = schema.getClasses();
        DOSchemaClass[] newClasses = new DOSchemaClass[existingClasses.length + 1];
        System.arraycopy(existingClasses, 0, newClasses, 0, existingClasses.length);
        newClasses[existingClasses.length] = newClass;

        // Recreate schema with new classes array
        schema = new DOSchema(newClasses, schema.getModules(), schema.getFoundationClasses());

        // Rebuild the tree to show the new class
        buildTree();

        markModified();

        setStatus("Created new class: " + className);
    }

    /**
     * Rebuild the currently selected class with updated fields from the table
     * model.
     * This is necessary because DOSchemaClass and DOSchemaField are immutable.
     */
    private void rebuildCurrentClassFields() {
        if (currentSelectedNode == null || currentSelectedNode.getNodeType() != NodeType.CLASS) {
            return;
        }

        DOSchemaClass oldClass = (DOSchemaClass) currentSelectedNode.getSchemaElement();

        // Create a map of original fields by source name to preserve title and
        // description
        java.util.Map<String, DOSchemaField> originalFieldsMap = new java.util.HashMap<>();
        if (oldClass.getFields() != null) {
            for (DOSchemaField field : oldClass.getFields()) {
                originalFieldsMap.put(field.getSource(), field);
            }
        }

        // Build new fields array from table model
        int rowCount = fieldsTableModel.getRowCount();
        DOSchemaField[] newFields = new DOSchemaField[rowCount];

        for (int i = 0; i < rowCount; i++) {
            String source = (String) fieldsTableModel.getValueAt(i, 0);
            String destination = (String) fieldsTableModel.getValueAt(i, 1);
            String type = (String) fieldsTableModel.getValueAt(i, 2);
            Boolean exported = (Boolean) fieldsTableModel.getValueAt(i, 3);
            Boolean skipIfEmpty = (Boolean) fieldsTableModel.getValueAt(i, 4);
            Boolean collection = (Boolean) fieldsTableModel.getValueAt(i, 5);
            Boolean embedContents = (Boolean) fieldsTableModel.getValueAt(i, 6);
            String childrenType = (String) fieldsTableModel.getValueAt(i, 7);

            // Try to find the original field by source name to preserve title and
            // description
            String title = null;
            String description = null;
            DOSchemaField originalField = originalFieldsMap.get(source);
            if (originalField != null) {
                title = originalField.getTitle();
                description = originalField.getDescription();
            }

            newFields[i] = new DOSchemaField(
                    source != null ? source : "",
                    destination != null ? destination : "",
                    type != null ? type : "",
                    exported != null ? exported : false,
                    skipIfEmpty != null ? skipIfEmpty : true,
                    collection != null ? collection : false,
                    embedContents != null ? embedContents : false,
                    childrenType != null ? childrenType : "",
                    title,
                    description,
                    null, // databaseClass
                    null // childrenSchemaClass
            );
        }

        // Create new class with updated fields
        DOSchemaClass newClass = new DOSchemaClass(
                oldClass.getSourceName(),
                oldClass.getDestinationName(),
                oldClass.getDescription(),
                oldClass.getTitle(),
                oldClass.getParentClass(),
                newFields,
                oldClass.getSchemaReferences(),
                oldClass.isMigrate());

        // Replace the class in the schema
        if (schema != null && schema.getClasses() != null) {
            DOSchemaClass[] classes = schema.getClasses();
            for (int i = 0; i < classes.length; i++) {
                if (classes[i] == oldClass) {
                    classes[i] = newClass;
                    // Update the node reference
                    currentSelectedNode.setSchemaElement(newClass);
                    break;
                }
            }
        }
    }

    /**
     * Show a class finder dialog to select a class name.
     * 
     * @param initialValue The initial value to show in the search field
     * @return The selected class name, or null if cancelled
     */
    private String showClassFinder(String initialValue) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        return ClassFinderDialog.showDialog(owner, schema, initialValue);
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

            // Check collection/childrenType consistency
            boolean isCollection = field.isCollection();
            String childrenType = field.getChildrenType();
            boolean hasChildrenType = childrenType != null && !childrenType.isEmpty();

            // Error: collection is true but childrenType is empty
            if (isCollection && !hasChildrenType) {
                return true;
            }

            // Error: collection is false but childrenType is not empty
            if (!isCollection && hasChildrenType) {
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
