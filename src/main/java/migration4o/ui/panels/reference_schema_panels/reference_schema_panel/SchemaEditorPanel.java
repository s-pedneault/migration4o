package migration4o.ui.panels.reference_schema_panels.reference_schema_panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import migration4o.database.DODatabaseService;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaValueMap;
import migration4o.models.schema.analysis.DOSchemaAnomaly;
import migration4o.models.ui.ColumnDefinition;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.SchemaTreeNode;
import migration4o.models.ui.SchemaTreeNode.NodeType;
import migration4o.schema.DOReferenceSchemaWriter;
import migration4o.schema.DOSchemaService;
import migration4o.schema.modules.DOModuleService;
import migration4o.ui.common.PropertyPanel;
import migration4o.ui.common.renderers.SchemaTypeRenderer;
import migration4o.ui.panels.database_panels.dialogs.ClassObjectsDialog;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs.ClassFinderDialog;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs.FieldEditorDialog;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs.SummaryEditorDialog;
import migration4o.util.SchemaUtil;
import migration4o.util.TypeUtil;

/**
 * Editor panel for reference-schema.xml file. Provides a tree view with property editing.
 */
public class SchemaEditorPanel extends JPanel {

    private static final String EXPORT_ROW_LABEL = "Export (migrate)";
    private static final String EXPORT_CHECKBOX_COMPONENT_NAME = "exportCheckBox";
    private static final String SCHEMA_NOTES_COMPONENT_NAME = "schemaNotesField";
    private static final String STATIC_CHECKBOX_COMPONENT_NAME = "staticCheckBox";

    private DOSchema schema;
    private boolean modified;
    private boolean isReadOnly; // true for database structure panels (cannot be
                                // saved)

    private JTree schemaTree;
    private DefaultTreeModel treeModel;
    private PropertyPanel propertyPanel;
    private JTable fieldsTable;
    private DefaultTableModel fieldsTableModel;
    private JLabel statusLabel;
    private SchemaAnomaliesPanel anomaliesPanel;
    private SchemaTreeNode currentSelectedNode;
    private JTextField searchField;
    private String currentFilter = "";
    private boolean showOnlyErrors = false;
    private boolean groupByPackage = false;
    private Runnable onCompareRequested; // Callback for Compare button
    private Runnable onSchemaReloaded; // Callback for schema reload

    // Column definitions for the fields table
    private ColumnDefinition[] fieldColumns;

    private migration4o.database.DODatabaseContext dbContext;

    public SchemaEditorPanel() {
        this.modified = false;
        this.isReadOnly = false; // Reference schema can be edited and saved
        this.dbContext = null;

        initializeUI();
        loadSchema();
    }

    /**
     * Constructor for viewing an inferred schema (not backed by a file). The schema cannot be saved in this mode.
     */
    public SchemaEditorPanel(DOSchema schema, String displayName, migration4o.database.DODatabaseContext dbContext) {
        this.schema = schema;
        this.modified = false;
        this.isReadOnly = true; // Database structure panels are read-only
        this.dbContext = dbContext;

        initializeUI();
        buildTree();
        setStatus("Inferred schema loaded: " + displayName + " (read-only)");
    }

    public void setOnCompareRequested(Runnable callback) {
        this.onCompareRequested = callback;
    }

    /**
     * Set a callback to be invoked when the schema is reloaded.
     */
    public void setOnSchemaReloaded(Runnable callback) {
        this.onSchemaReloaded = callback;
    }

    /**
     * Get the current schema. This returns the live schema object, which may have been reloaded.
     */
    public DOSchema getSchema() {
        return schema;
    }

    /**
     * Add a class from another schema to this schema. This is used when adding a database-only class to the reference schema.
     * 
     * @param className The name of the class to add
     * @param sourceClass The source class to copy from
     */
    public void addClassFromComparison(String className, DOSchemaClass sourceClass) {
        if (className == null || className.isEmpty() || schema == null || sourceClass == null) {
            return;
        }

        // Check if class already exists
        if (schema.getClasses() != null) {
            for (DOSchemaClass existing : schema.getClasses()) {
                if (existing.attributes.source.equals(className) || existing.attributes.source.equals(className)) {
                    JOptionPane.showMessageDialog(this, "Class '" + className + "' already exists in the schema.", "Class Exists", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        // Create a new class copying all properties from the source
        DOSchemaClass newClass = new DOSchemaClass(schema);
        newClass.attributes.source = sourceClass.attributes.source;
        newClass.attributes.destinationName = sourceClass.attributes.destinationName;
        newClass.attributes.description = sourceClass.attributes.description;
        newClass.attributes.schemaNotes = sourceClass.attributes.schemaNotes;
        newClass.attributes.title = sourceClass.attributes.title;
        newClass.attributes.parentClassName = sourceClass.attributes.parentClassName;

        // Convert fields to use common field references where applicable
        DOSchemaField[] fields = sourceClass.fields != null ? sourceClass.fields.clone() : new DOSchemaField[0];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = SchemaUtil.convertToCommonFieldIfExists(fields[i], schema);
        }
        newClass.setFields(fields);

        newClass.schemaReferences = sourceClass.schemaReferences;
        newClass.attributes.migrate = sourceClass.attributes.migrate;

        // Add the class to the schema (alphabetically)
        SchemaUtil.addClass(schema, newClass);

        // Rebuild the tree to show the new class
        buildTree();

        // Find and select the new class in the tree
        clearSearchAndSelectClass(className);

        markModified();

        setStatus("Added class: " + className + " with " + (sourceClass.fields != null ? sourceClass.fields.length : 0) + " field(s)");
    }

    /**
     * Clear the search filter and select a class by name. Used after adding classes or fields to ensure they're visible.
     */
    private void clearSearchAndSelectClass(String className) {
        if (searchField != null && !searchField.getText().isEmpty()) {
            searchField.setText("");
            // The document listener will trigger filterTree("") automatically
        }
        selectClassByName(className);
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
            if (schemaClass != null && (schemaClass.attributes.source.equals(className) || schemaClass.attributes.source.equals(className))) {
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

    /**
     * Add a field from another schema/database to a class in this schema. This is used when adding a database-only field to a reference schema class.
     * 
     * @param parentClass The class to add the field to
     * @param field The field to add
     */
    public void addFieldFromComparison(DOSchemaClass parentClass, DOSchemaField field) {
        if (parentClass == null || field == null || schema == null) {
            return;
        }

        // Find the class in the current schema
        DOSchemaClass targetClass = null;
        int classIndex = -1;
        DOSchemaClass[] classes = schema.getClasses();

        for (int i = 0; i < classes.length; i++) {
            if (classes[i].attributes.source.equals(parentClass.attributes.source)) {
                targetClass = classes[i];
                classIndex = i;
                break;
            }
        }

        if (targetClass == null) {
            JOptionPane.showMessageDialog(this, "Class '" + parentClass.attributes.source + "' not found in schema.", "Class Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if field already exists
        if (targetClass.fields != null) {
            for (DOSchemaField existing : targetClass.fields) {
                if (existing.attributes.source.equals(field.attributes.source)) {
                    JOptionPane.showMessageDialog(this, "Field '" + field.attributes.source + "' already exists in class '" + targetClass.attributes.source + "'.", "Field Exists", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        // Create new field array with the added field
        DOSchemaField[] oldFields = targetClass.fields != null ? targetClass.fields : new DOSchemaField[0];
        DOSchemaField[] newFields = new DOSchemaField[oldFields.length + 1];
        System.arraycopy(oldFields, 0, newFields, 0, oldFields.length);
        // Convert to common field reference if one exists
        newFields[oldFields.length] = SchemaUtil.convertToCommonFieldIfExists(field, schema);

        // Create new class with updated fields
        DOSchemaClass newClass = new DOSchemaClass(schema);
        newClass.attributes.source = targetClass.attributes.source;
        newClass.attributes.destinationName = targetClass.attributes.destinationName;
        newClass.attributes.description = targetClass.attributes.description;
        newClass.attributes.schemaNotes = targetClass.attributes.schemaNotes;
        newClass.attributes.title = targetClass.attributes.title;
        newClass.attributes.parentClassName = targetClass.attributes.parentClassName;
        newClass.setFields(newFields);
        newClass.schemaReferences = targetClass.schemaReferences;
        newClass.attributes.migrate = targetClass.attributes.migrate;

        // Replace the class in the schema
        classes[classIndex] = newClass;

        // Rebuild the tree to reflect the changes
        buildTree();

        // Find and select the class to show the new field
        clearSearchAndSelectClass(targetClass.attributes.source);

        markModified();

        setStatus("Added field: " + field.attributes.source + " to class: " + targetClass.attributes.source);
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

        // Create bottom panel with status bar and anomalies panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createStatusBar(), BorderLayout.NORTH);

        anomaliesPanel = new SchemaAnomaliesPanel();
        anomaliesPanel.setVisible(false); // Hidden until anomalies are detected
        anomaliesPanel.setNavigationCallback(this::navigateToAnomaly);
        bottomPanel.add(anomaliesPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Reload button (only enabled for reference schema, not for database
        // structure)
        if (!isReadOnly) {
            JButton reloadButton = new JButton("Reload");
            reloadButton.setIcon(UIManager.getIcon("FileView.hardDriveIcon"));
            reloadButton.addActionListener(e -> reloadSchema());
            toolbar.add(reloadButton);
        }

        // Save button (only enabled for reference schema, not for database
        // structure)
        if (!isReadOnly) {
            toolbar.addSeparator();
            JButton saveButton = new JButton("Save");
            saveButton.setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
            saveButton.addActionListener(e -> saveSchema());
            toolbar.add(saveButton);
            toolbar.addSeparator();
        }

        // Expand/Collapse buttons
        JButton expandAllButton = new JButton("Expand All");
        expandAllButton.addActionListener(e -> expandAll());
        toolbar.add(expandAllButton);

        JButton collapseAllButton = new JButton("Collapse All");
        collapseAllButton.addActionListener(e -> collapseAll());
        toolbar.add(collapseAllButton);

        toolbar.addSeparator();

        // Compare button
        JButton compareButton = new JButton("Compare...");
        compareButton.setToolTipText("Open database to compare with this schema");
        compareButton.addActionListener(e -> {
            if (onCompareRequested != null) {
                onCompareRequested.run();
            }
        });
        toolbar.add(compareButton);

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

        // Add right-click context menu for database schema mode
        schemaTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger())
                    showTreeContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    showTreeContextMenu(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(schemaTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add search field
        panel.add(createSearchPanel(), BorderLayout.NORTH);

        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        searchField = new JTextField();
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

        // Add options panel on the right (with checkboxes)
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        // Add "Errors only" checkbox
        JCheckBox errorsOnlyCheckbox = new JCheckBox("Errors only");
        errorsOnlyCheckbox.setToolTipText("Show only classes with unresolved field types");
        errorsOnlyCheckbox.addActionListener(e -> {
            showOnlyErrors = errorsOnlyCheckbox.isSelected();
            filterTree(searchField.getText());
        });
        optionsPanel.add(errorsOnlyCheckbox);

        // Add "Group by package" checkbox
        JCheckBox groupByPackageCheckbox = new JCheckBox("By Package");
        groupByPackageCheckbox.setToolTipText("Group classes by package instead of inheritance");
        groupByPackageCheckbox.addActionListener(e -> {
            groupByPackage = groupByPackageCheckbox.isSelected();
            buildTree();
        });
        optionsPanel.add(groupByPackageCheckbox);

        panel.add(optionsPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createFieldsTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fields"));

        // Define columns with their properties
        fieldColumns = new ColumnDefinition[] { new ColumnDefinition("Source", 200, String.class), new ColumnDefinition("Destination", 150, String.class), new ColumnDefinition("Type", 150, String.class), new ColumnDefinition("Exported", 60, Boolean.class), new ColumnDefinition("Skip When", 150, String.class), new ColumnDefinition("Collection", 70, Boolean.class), new ColumnDefinition("Embed Contents", 120, Boolean.class), new ColumnDefinition("Children Type", 200, String.class) };

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
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new SchemaTypeRenderer(schema));
            } else if (fieldColumns[i].name.equals("Children Type")) {
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new SchemaTypeRenderer(schema));
            } else if (fieldColumns[i].name.equals("Collection")) {
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new CollectionRenderer());
            } else if (fieldColumns[i].name.equals("Source") || fieldColumns[i].name.equals("Destination")) {
                // Highlight shared fields
                fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new SharedFieldRenderer());
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
                else if (e.getClickCount() == 1 && col >= 0 && col < fieldColumns.length && (fieldColumns[col].name.equals("Type") || fieldColumns[col].name.equals("Children Type"))) {
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

            // Load schema using the central service (business logic is
            // centralized)
            schema = DOSchemaService.getInstance().loadReferenceSchema();

            // Build tree
            buildTree();

            // Update table renderers now that schema is loaded
            updateTableRenderers();

            setStatus("Schema loaded successfully. " + getSchemaStats());
            modified = false;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading schema: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            setStatus("Error loading schema");
        }
    }

    /**
     * Updates the table cell renderers with the loaded schema. Must be called after schema is loaded to ensure renderers have access to schema classes.
     */
    private void updateTableRenderers() {
        if (fieldsTable != null && schema != null) {
            for (int i = 0; i < fieldColumns.length; i++) {
                if (fieldColumns[i].name.equals("Type") || fieldColumns[i].name.equals("Children Type")) {
                    fieldsTable.getColumnModel().getColumn(i).setCellRenderer(new SchemaTypeRenderer(schema));
                }
            }
        }
    }

    private void buildTree() {
        if (groupByPackage) {
            buildTreeByPackage();
        } else {
            buildTreeByInheritance();
        }

        // Update anomalies panel
        if (anomaliesPanel != null && schema != null) {
            anomaliesPanel.setAnomalies(schema.anomalies);
        }
    }

    private void buildTreeByInheritance() {
        SchemaTreeNode rootNode = (SchemaTreeNode) treeModel.getRoot();
        rootNode.removeAllChildren();

        if (schema == null || schema.getClasses() == null) {
            treeModel.reload();
            return;
        }

        // If filtering (by text or errors only), show all matching classes as a
        // flat
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
            String shortName = schemaClass.attributes.source.contains(".") ? schemaClass.attributes.source.substring(schemaClass.attributes.source.lastIndexOf('.') + 1) : schemaClass.attributes.source;
            classMap.put(shortName, schemaClass);
            // Also map by absolute name for parent lookups
            if (schemaClass.attributes.source != null) {
                classMap.put(schemaClass.attributes.source, schemaClass);
            }
        }

        // Build parent-to-children map
        java.util.Map<String, List<DOSchemaClass>> childrenMap = new java.util.HashMap<>();
        List<DOSchemaClass> rootClasses = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String parentName = schemaClass.attributes.parentClassName;
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

    private void buildTreeByPackage() {
        SchemaTreeNode rootNode = (SchemaTreeNode) treeModel.getRoot();
        rootNode.removeAllChildren();

        if (schema == null || schema.getClasses() == null) {
            treeModel.reload();
            return;
        }

        // If filtering, show all matching classes as a flat list
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

        // No filter - group by package
        java.util.Map<String, List<DOSchemaClass>> packageMap = new java.util.TreeMap<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String className = schemaClass.attributes.source;
            String packageName = "(default)";

            if (className != null && className.contains(".")) {
                packageName = className.substring(0, className.lastIndexOf('.'));
            }

            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(schemaClass);
        }

        // Create package nodes and add classes
        for (java.util.Map.Entry<String, List<DOSchemaClass>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            List<DOSchemaClass> classes = entry.getValue();

            // Sort classes within package
            classes.sort((a, b) -> getClassDisplayName(a).compareToIgnoreCase(getClassDisplayName(b)));

            // Create package node
            SchemaTreeNode packageNode = new SchemaTreeNode(packageName + " (" + classes.size() + ")", NodeType.MODULE);
            rootNode.add(packageNode);

            // Add classes to package node
            for (DOSchemaClass schemaClass : classes) {
                SchemaTreeNode classNode = createClassNode(schemaClass);
                packageNode.add(classNode);
            }
        }

        treeModel.reload();
        expandFirstLevel();
    }

    private SchemaTreeNode createClassNode(DOSchemaClass schemaClass) {
        String className = getClassDisplayName(schemaClass);
        if (!schemaClass.attributes.migrate) {
            className += " (not exported)";
        }
        SchemaTreeNode classNode = new SchemaTreeNode(className, NodeType.CLASS, schemaClass);

        if (schemaClass.isIDEntite() && (schemaClass.attributes.pointsTo == null || schemaClass.attributes.pointsTo.isBlank())) {
            SchemaTreeNode warningNode = new SchemaTreeNode("⚠ IDEntite missing pointsTo attribute", NodeType.FIELD, null);
            classNode.add(warningNode);
        }

        // Add field references that point to this class
        addFieldReferencesToClass(classNode, schemaClass);

        return classNode;
    }

    /**
     * Finds and adds all fields throughout the schema that reference the given class. Includes both direct field type references and IDEntite-style references.
     */
    private void addFieldReferencesToClass(SchemaTreeNode classNode, DOSchemaClass targetClass) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        List<String> references = new ArrayList<>();
        String targetShortName = targetClass.attributes.source.contains(".") ? targetClass.attributes.source.substring(targetClass.attributes.source.lastIndexOf('.') + 1) : targetClass.attributes.source;
        String targetAbsoluteName = targetClass.attributes.source;

        // Search all classes and their fields for references to this class
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.fields == null) {
                continue;
            }

            for (DOSchemaField field : schemaClass.fields) {
                boolean isReference = false;

                // Check direct field type reference
                String fieldType = field.attributes.type;
                if (fieldType != null) {
                    if (fieldType.equals(targetShortName) || fieldType.equals(targetAbsoluteName)) {
                        isReference = true;
                    }
                }

                // Check collection children type
                String childrenType = field.attributes.childrenType;
                if (childrenType != null) {
                    if (childrenType.equals(targetShortName) || childrenType.equals(targetAbsoluteName)) {
                        isReference = true;
                    } else {
                        // Check if childrenType is an IDEntite class that
                        // points to target
                        DOSchemaClass childrenClass = findClassByName(childrenType);
                        if (childrenClass != null && isIDEntiteType(childrenType)) {
                            String pointsTo = childrenClass.attributes.pointsTo;
                            if (pointsTo != null && (pointsTo.equals(targetShortName) || pointsTo.equals(targetAbsoluteName))) {
                                isReference = true;
                            }
                        }
                    }
                }

                // Check IDEntite-style reference (mID* fields)
                if (!isReference && fieldType != null && isIDEntiteType(fieldType)) {
                    // Find the IDEntite class to get its pointsTo attribute
                    DOSchemaClass idEntiteClass = findClassByName(fieldType);
                    if (idEntiteClass != null) {
                        String pointsTo = idEntiteClass.attributes.pointsTo;
                        if (pointsTo != null && (pointsTo.equals(targetShortName) || pointsTo.equals(targetAbsoluteName))) {
                            isReference = true;
                        }
                    }
                }

                if (isReference) {
                    String refName = schemaClass.attributes.source + "." + field.attributes.source;
                    references.add(refName);
                }
            }
        }

        // Sort and add references as child nodes
        if (!references.isEmpty()) {
            references.sort(String::compareToIgnoreCase);
            SchemaTreeNode referencesFolder = new SchemaTreeNode("Referenced by (" + references.size() + " fields)", NodeType.FOLDER, null);
            classNode.add(referencesFolder);

            for (String ref : references) {
                SchemaTreeNode refNode = new SchemaTreeNode(ref, NodeType.FIELD, null);
                referencesFolder.add(refNode);
            }
        }
    }

    /**
     * Check if a type is an IDEntite descendant.
     */
    private boolean isIDEntiteType(String typeName) {
        if (typeName == null || schema == null || schema.getClasses() == null) {
            return false;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (typeName.equals(schemaClass.attributes.destinationName) || typeName.equals(schemaClass.attributes.source)) {
                return schemaClass.isIDEntite();
            }
        }
        return false;
    }

    /**
     * Check if a class is a descendant of a given parent class.
     */
    private boolean isDescendantOf(DOSchemaClass schemaClass, String parentClassName) {
        if (schemaClass == null || parentClassName == null) {
            return false;
        }

        String currentParent = schemaClass.attributes.parentClassName;
        while (currentParent != null && !currentParent.isEmpty() && !currentParent.equals("Undetermined")) {
            if (currentParent.equals(parentClassName)) {
                return true;
            }

            // Find parent class and continue up the hierarchy
            DOSchemaClass parentClass = findClassByName(currentParent);
            if (parentClass == null) {
                break;
            }
            currentParent = parentClass.attributes.parentClassName;
        }

        return false;
    }

    /**
     * Find a class by its short name or absolute name.
     */
    private DOSchemaClass findClassByName(String className) {
        if (schema == null || schema.getClasses() == null || className == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (className.equals(schemaClass.attributes.destinationName) || className.equals(schemaClass.attributes.source)) {
                return schemaClass;
            }
        }
        return null;
    }

    private void addChildClasses(SchemaTreeNode parentNode, DOSchemaClass parentClass, java.util.Map<String, List<DOSchemaClass>> childrenMap, java.util.Map<String, DOSchemaClass> classMap) {
        // Look for children by short name and absolute name
        List<DOSchemaClass> children = new ArrayList<>();

        String parentShortName = parentClass.attributes.source.contains(".") ? parentClass.attributes.source.substring(parentClass.attributes.source.lastIndexOf('.') + 1) : parentClass.attributes.source;
        if (childrenMap.containsKey(parentShortName)) {
            children.addAll(childrenMap.get(parentShortName));
        }
        if (parentClass.attributes.source != null && childrenMap.containsKey(parentClass.attributes.source)) {
            List<DOSchemaClass> absoluteChildren = childrenMap.get(parentClass.attributes.source);
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
        String sourceName = schemaClass.attributes.source;
        String shortName = sourceName != null && sourceName.contains(".") ? sourceName.substring(sourceName.lastIndexOf('.') + 1) : sourceName;

        String destinationName = schemaClass.attributes.source.contains(".") ? schemaClass.attributes.source.substring(schemaClass.attributes.source.lastIndexOf('.') + 1) : schemaClass.attributes.source;
        if (destinationName != null && !destinationName.isEmpty() && !shortName.equals(destinationName)) {
            return shortName + " > " + destinationName;
        }
        return shortName;
    }

    private void onTreeSelectionChanged() {
        // Apply any pending property changes before switching
        applyCurrentProperties();

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

    private void showTreeContextMenu(MouseEvent e) {
        if (dbContext == null || dbContext.database == null)
            return;

        TreePath path = schemaTree.getPathForLocation(e.getX(), e.getY());
        if (path == null)
            return;

        SchemaTreeNode node = (SchemaTreeNode) path.getLastPathComponent();
        if (node.getNodeType() != NodeType.CLASS)
            return;

        schemaTree.setSelectionPath(path);

        DOSchemaClass schemaClass = (DOSchemaClass) node.getSchemaElement();
        migration4o.database.DODatabaseClass dbClass = dbContext.database.findClassByName(schemaClass.attributes.source);
        if (dbClass == null)
            return;

        JPopupMenu popup = new JPopupMenu();
        JMenuItem viewObjects = new JMenuItem("View Objects...");
        viewObjects.addActionListener(ev -> {
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            ClassObjectsDialog dialog = new ClassObjectsDialog(parentFrame, dbClass, schema);
            dialog.setVisible(true);
        });
        popup.add(viewObjects);
        popup.show(schemaTree, e.getX(), e.getY());
    }

    private void clearFieldsTable() {
        fieldsTableModel.setRowCount(0);
    }

    private void displayProperties(SchemaTreeNode node) {
        propertyPanel.clear();
        clearFieldsTable();

        switch (node.getNodeType()) {
        case MODULE:
            // Modules are now in migration-format.xml, not in reference schema
            // displayModuleProperties((DOSchemaModule)
            // node.getSchemaElement());
            propertyPanel.addReadOnlyTextField("Type", "Module");
            propertyPanel.addReadOnlyTextField("Info", "Modules are now managed in migration-format.xml");
            break;
        case CLASS:
            displayClassProperties((DOSchemaClass) node.getSchemaElement());
            break;
        default:
            break;
        }
    }

    /**
     * Apply current property panel values to the schema object. This is called before saving or switching selections to preserve edits.
     */
    private void applyCurrentProperties() {
        if (currentSelectedNode == null) {
            return;
        }

        switch (currentSelectedNode.getNodeType()) {
        case MODULE:
            // Modules are now in migration-format.xml, not in reference schema
            // applyModuleProperties((DOSchemaModule)
            // currentSelectedNode.getSchemaElement());
            break;
        case CLASS:
            applyClassProperties((DOSchemaClass) currentSelectedNode.getSchemaElement());
            break;
        default:
            break;
        }
    }

    /**
     * Apply module property changes from the property panel. DISABLED: Modules are now in migration-format.xml, not in reference schema
     */
    /*
     * /* private void applyModuleProperties(DOSchemaModule module) { JComponent nameField = propertyPanel.getField("Name"); if (nameField instanceof JTextField) { String newName = ((JTextField) nameField).getText(); if (newName != null && !newName.trim().isEmpty()) { module.setName(newName.trim()); } } }
     */

    /**
     * Apply class property changes from the property panel.
     */
    private void applyClassProperties(DOSchemaClass schemaClass) {
        // Apply Destination Name
        JComponent destNameField = propertyPanel.getField("Destination Name");
        if (destNameField instanceof JTextField) {
            String newDestName = ((JTextField) destNameField).getText();
            if (newDestName != null && !newDestName.trim().isEmpty()) {
                schemaClass.attributes.destinationName = newDestName.trim();
            }
        }

        // Apply Export checkbox and Schema Notes
        JCheckBox exportCheckBox = getExportCheckBoxFromPanel();
        if (exportCheckBox != null) {
            schemaClass.attributes.migrate = exportCheckBox.isSelected();
        }

        JTextField schemaNotesField = getSchemaNotesFieldFromPanel();
        if (schemaNotesField != null) {
            String notes = schemaNotesField.getText();
            schemaClass.attributes.schemaNotes = notes != null && !notes.trim().isEmpty() ? notes.trim() : null;
        }

        // Apply Static DB checkbox
        JCheckBox staticCheckBox = getStaticCheckBoxFromPanel();
        if (staticCheckBox != null) {
            schemaClass.attributes.isStatic = staticCheckBox.isSelected();
        }

        // Apply Parent Class
        JComponent parentField = propertyPanel.getField("Parent Class");
        if (parentField instanceof JComboBox) {
            @SuppressWarnings("unchecked")
            JComboBox<String> comboBox = (JComboBox<String>) parentField;
            String selectedParent = (String) comboBox.getSelectedItem();
            if (selectedParent != null) {
                if ("Undetermined".equals(selectedParent)) {
                    schemaClass.attributes.parentClassName = "";
                } else {
                    schemaClass.attributes.parentClassName = selectedParent;
                }
            }
        }

        // Apply Title
        JComponent titleField = propertyPanel.getField("Title");
        if (titleField instanceof JTextField) {
            String newTitle = ((JTextField) titleField).getText();
            schemaClass.attributes.title = newTitle != null ? newTitle.trim() : "";
        }

        // Apply Description
        JComponent descriptionField = propertyPanel.getField("Description");
        if (descriptionField instanceof JTextField) {
            String newDescription = ((JTextField) descriptionField).getText();
            schemaClass.attributes.description = newDescription != null ? newDescription.trim() : "";
        }

        // Update the tree node display in case the name changed
        if (currentSelectedNode != null) {
            String displayName = getClassDisplayName(schemaClass);
            if (!schemaClass.attributes.migrate) {
                displayName += " (not exported)";
            }
            currentSelectedNode.setUserObject(displayName);
            treeModel.nodeChanged(currentSelectedNode);
        }
    }

    /*
     * private void displayModuleProperties(DOSchemaModule module) { propertyPanel.addReadOnlyTextField("Type", "Module"); propertyPanel.addTextField("Name", module.getName()) .addActionListener(e -> markModified()); }
     */

    private void displayClassProperties(DOSchemaClass schemaClass) {
        propertyPanel.addReadOnlyTextField("Type", "Class");
        propertyPanel.addReadOnlyTextField("Source", schemaClass.attributes.source);
        propertyPanel.addTextField("Destination Name", schemaClass.attributes.destinationName).addActionListener(e -> markModified());

        JPanel exportAndNotesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        JCheckBox exportCheckBox = new JCheckBox();
        exportCheckBox.setName(EXPORT_CHECKBOX_COMPONENT_NAME);
        exportCheckBox.setSelected(schemaClass.attributes.migrate);
        exportAndNotesPanel.add(exportCheckBox);

        JLabel staticLabel = new JLabel("Static DB:");
        exportAndNotesPanel.add(staticLabel);

        JCheckBox staticCheckBox = new JCheckBox();
        staticCheckBox.setName(STATIC_CHECKBOX_COMPONENT_NAME);
        staticCheckBox.setSelected(schemaClass.attributes.isStatic);
        staticCheckBox.addActionListener(e -> {
            schemaClass.attributes.isStatic = staticCheckBox.isSelected();
            markModified();
        });
        exportAndNotesPanel.add(staticCheckBox);

        JLabel schemaNotesLabel = new JLabel("Schema notes:");
        exportAndNotesPanel.add(schemaNotesLabel);

        JTextField schemaNotesField = new JTextField(schemaClass.attributes.schemaNotes != null ? schemaClass.attributes.schemaNotes : "", 24);
        schemaNotesField.setName(SCHEMA_NOTES_COMPONENT_NAME);
        exportAndNotesPanel.add(schemaNotesField);

        propertyPanel.addCustomField(EXPORT_ROW_LABEL, exportAndNotesPanel);

        exportCheckBox.addActionListener(e -> {
            schemaClass.attributes.migrate = exportCheckBox.isSelected();
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

        schemaNotesField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateSchemaNotes() {
                String notes = schemaNotesField.getText();
                schemaClass.attributes.schemaNotes = notes != null && !notes.trim().isEmpty() ? notes.trim() : null;
                markModified();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSchemaNotes();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSchemaNotes();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSchemaNotes();
            }
        });

        // Parent Class selector
        JComboBox<String> parentClassCombo = createParentClassComboBox(schemaClass.attributes.parentClassName);
        propertyPanel.addCustomField("Parent Class", parentClassCombo);
        parentClassCombo.addActionListener(e -> markModified());

        // Always show Title field (editable)
        String title = schemaClass.attributes.title != null ? schemaClass.attributes.title : "";
        propertyPanel.addTextField("Title", title).addActionListener(e -> markModified());

        // Always show Description field (editable)
        String description = schemaClass.attributes.description != null ? schemaClass.attributes.description : "";
        propertyPanel.addTextField("Description", description).addActionListener(e -> markModified());

        // Summary row: read-only display + Edit button
        JPanel summaryPanel = new JPanel(new BorderLayout(5, 0));
        JTextField summaryDisplayField = new JTextField(schemaClass.attributes.summary != null ? schemaClass.attributes.summary : "");
        summaryDisplayField.setEditable(false);
        summaryDisplayField.setBackground(UIManager.getColor("TextField.inactiveBackground"));
        summaryDisplayField.setForeground(new Color(60, 80, 140));
        summaryDisplayField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        JButton editSummaryButton = new JButton("Edit...");
        editSummaryButton.setToolTipText("Edit the summary template for this class");
        editSummaryButton.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            String edited = SummaryEditorDialog.showDialog(owner, schemaClass, schema);
            // null → user cancelled; any String (including "") → user confirmed
            // OK
            if (edited != null) {
                schemaClass.attributes.summary = edited.isEmpty() ? null : edited;
                summaryDisplayField.setText(schemaClass.attributes.summary != null ? schemaClass.attributes.summary : "");
                markModified();
            }
        });
        summaryPanel.add(summaryDisplayField, BorderLayout.CENTER);
        summaryPanel.add(editSummaryButton, BorderLayout.EAST);
        propertyPanel.addCustomField("Summary", summaryPanel);

        int fieldCount = schemaClass.fields != null ? schemaClass.fields.length : 0;
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
                classNames.add(cls.attributes.source);
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
                classNames.add(cls.attributes.source);
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
                types.add(cls.attributes.source);
            }
        }

        return new JComboBox<>(types.toArray(new String[0]));
    }

    private void populateFieldsTable(DOSchemaClass schemaClass) {
        if (schemaClass.fields == null) {
            return;
        }

        for (DOSchemaField field : schemaClass.fields) {
            Object[] rowData = { field.attributes.source, field.attributes.destinationName, field.attributes.type != null ? field.attributes.type : "", field.attributes.isExported, field.attributes.skipWhen != null ? field.attributes.skipWhen : "", field.attributes.isCollection, field.attributes.embedContents, field.attributes.childrenType != null ? field.attributes.childrenType : "" };
            fieldsTableModel.addRow(rowData);
        }
    }

    /**
     * Custom renderer for the Type column. - Classes: blue - Primitives: green - Others: red (unresolved)
     */
    /**
     * Custom renderer for the Collection column. Displays a checkbox and highlights the cell with red background if there's a collection/childrenType mismatch.
     */
    private class CollectionRenderer extends JCheckBox implements TableCellRenderer {

        public CollectionRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            // Set checkbox state
            if (value instanceof Boolean) {
                setSelected((Boolean) value);
            } else {
                setSelected(false);
            }

            // Check for collection/childrenType consistency error
            boolean hasError = false;
            String errorMessage = null;
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
                        errorMessage = "Collection is true but Children Type is empty";
                    }
                    // Error: collection is false but childrenType is not empty
                    if (!isCollection && hasChildrenType) {
                        hasError = true;
                        errorMessage = "Collection is false but Children Type is set to: " + childrenType;
                    }
                }
            }

            // Set background color and tooltip based on error state
            if (hasError) {
                if (!isSelected) {
                    setBackground(new Color(255, 200, 200)); // Light red
                                                             // background
                } else {
                    setBackground(table.getSelectionBackground().darker());
                }
                setToolTipText(errorMessage);
            } else {
                if (!isSelected) {
                    setBackground(table.getBackground());
                } else {
                    setBackground(table.getSelectionBackground());
                }
                setToolTipText(null);
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
     * Renderer that highlights shared fields with a light blue background.
     */
    private class SharedFieldRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Check if this field is a shared field reference
            if (row >= 0 && row < fieldsTableModel.getRowCount() && currentSelectedNode != null && currentSelectedNode.getNodeType() == NodeType.CLASS) {
                DOSchemaClass schemaClass = (DOSchemaClass) currentSelectedNode.getSchemaElement();
                if (schemaClass.fields != null && row < schemaClass.fields.length) {
                    DOSchemaField field = schemaClass.fields[row];
                    if (field.isSharedField()) {
                        if (!isSelected) {
                            c.setBackground(new Color(200, 220, 255)); // Light
                                                                       // blue
                                                                       // for
                                                                       // shared
                                                                       // fields
                        } else {
                            c.setBackground(table.getSelectionBackground());
                        }
                        setToolTipText("Shared field definition: " + field.attributes.definitionId);
                    } else {
                        if (!isSelected) {
                            c.setBackground(table.getBackground());
                        } else {
                            c.setBackground(table.getSelectionBackground());
                        }
                        setToolTipText(null);
                    }
                }
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
            if (cls.attributes.source.equals(className)) {
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
            if (nodeClass.attributes.source.equals(targetClass.attributes.source)) {
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
        DOSchemaField newField = new DOSchemaField(schema, (DOSchemaClass) currentSelectedNode.getSchemaElement());
        newField.attributes.source = "";
        newField.attributes.destinationName = "";
        newField.attributes.type = "java.lang.String";
        newField.attributes.isExported = true;
        newField.attributes.isCollection = false;
        newField.attributes.embedContents = false;
        newField.attributes.childrenType = "";
        newField.childrenSchemaClass = null;

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
            DOSchemaField newFieldWithData = new DOSchemaField(schema, (DOSchemaClass) currentSelectedNode.getSchemaElement());
            newFieldWithData.attributes.source = dialog.getFieldSource();
            newFieldWithData.attributes.destinationName = dialog.getFieldDestination();
            newFieldWithData.attributes.type = dialog.getFieldType();
            newFieldWithData.attributes.format = dialog.getFieldFormat();
            newFieldWithData.attributes.isExported = dialog.isFieldExported();
            newFieldWithData.attributes.skipWhen = dialog.getFieldSkipWhen();
            newFieldWithData.attributes.skipUserOption = dialog.getFieldSkipUserOption();
            newFieldWithData.attributes.isCollection = dialog.isFieldCollection();
            newFieldWithData.attributes.embedContents = dialog.isFieldEmbedContents();
            newFieldWithData.attributes.childrenType = dialog.getFieldChildrenType();
            newFieldWithData.attributes.title = dialog.getFieldTitle();
            newFieldWithData.attributes.description = dialog.getFieldDescription();
            newFieldWithData.attributes.pointsTo = dialog.getFieldPointsTo();
            newFieldWithData.attributes.valueMap = DOSchemaValueMap.copyOf(dialog.getValueMappings());
            newFieldWithData.childrenSchemaClass = null;

            // Add the new field to the table
            Object[] rowData = { dialog.getFieldSource(), dialog.getFieldDestination(), dialog.getFieldType(), dialog.isFieldExported(), dialog.getFieldSkipWhen(), dialog.isFieldCollection(), dialog.isFieldEmbedContents(), dialog.getFieldChildrenType() };
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
        if (schemaClass.fields == null || rowIndex >= schemaClass.fields.length) {
            return;
        }

        DOSchemaField field = schemaClass.fields[rowIndex];

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
            fieldsTableModel.setValueAt(dialog.getFieldSkipWhen(), rowIndex, 4);
            fieldsTableModel.setValueAt(dialog.isFieldCollection(), rowIndex, 5);
            fieldsTableModel.setValueAt(dialog.isFieldEmbedContents(), rowIndex, 6);
            fieldsTableModel.setValueAt(dialog.getFieldChildrenType(), rowIndex, 7);

            // Update fields not shown in the table (title, description,
            // pointsTo, valueMap,
            // definitionId)
            field.attributes.title = dialog.getFieldTitle();
            field.attributes.description = dialog.getFieldDescription();
            field.attributes.pointsTo = dialog.getFieldPointsTo();
            field.attributes.format = dialog.getFieldFormat();
            field.attributes.valueMap = DOSchemaValueMap.copyOf(dialog.getValueMappings());
            field.attributes.skipUserOption = dialog.getFieldSkipUserOption();
            field.attributes.definitionId = dialog.getFieldDefinitionId();

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
                String shortName = existing.attributes.source.contains(".") ? existing.attributes.source.substring(existing.attributes.source.lastIndexOf('.') + 1) : existing.attributes.source;
                if (existing.attributes.source.equals(className) || shortName.equals(className)) {
                    // Class already exists, no need to create
                    return;
                }
            }
        }

        // Create a new class with minimal fields
        DOSchemaClass newClass = new DOSchemaClass(schema);
        newClass.attributes.source = className;
        newClass.attributes.destinationName = className;
        newClass.attributes.description = null;
        newClass.attributes.schemaNotes = null;
        newClass.attributes.title = null;
        newClass.attributes.parentClassName = null;
        newClass.setFields(new DOSchemaField[0]);
        newClass.schemaReferences = null;
        newClass.attributes.migrate = true;

        // Add the class to the schema (alphabetically)
        SchemaUtil.addClass(schema, newClass);

        // Rebuild the tree to show the new class
        buildTree();

        markModified();

        setStatus("Created new class: " + className);
    }

    /**
     * Rebuild the currently selected class with updated fields from the table model. This is necessary because DOSchemaClass and DOSchemaField are immutable.
     */
    private void rebuildCurrentClassFields() {
        if (currentSelectedNode == null || currentSelectedNode.getNodeType() != NodeType.CLASS) {
            return;
        }

        DOSchemaClass oldClass = (DOSchemaClass) currentSelectedNode.getSchemaElement();

        // Create a map of original fields by source name to preserve title and
        // description
        java.util.Map<String, DOSchemaField> originalFieldsMap = new java.util.HashMap<>();
        if (oldClass.fields != null) {
            for (DOSchemaField field : oldClass.fields) {
                originalFieldsMap.put(field.attributes.source, field);
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
            String skipWhen = (String) fieldsTableModel.getValueAt(i, 4);
            Boolean collection = (Boolean) fieldsTableModel.getValueAt(i, 5);
            Boolean embedContents = (Boolean) fieldsTableModel.getValueAt(i, 6);
            String childrenType = (String) fieldsTableModel.getValueAt(i, 7);

            // Try to find the original field by source name to preserve title
            // and
            // description
            String title = null;
            String description = null;
            String pointsTo = null;
            String format = null;
            String skipUserOption = null;
            String definitionId = null;
            DOSchemaValueMap valueMap = null;
            DOSchemaField originalField = originalFieldsMap.get(source);
            if (originalField != null) {
                title = originalField.attributes.title;
                description = originalField.attributes.description;
                pointsTo = originalField.attributes.pointsTo;
                format = originalField.attributes.format;
                skipUserOption = originalField.attributes.skipUserOption;
                definitionId = originalField.attributes.definitionId;
                valueMap = originalField.attributes.valueMap;
            }

            DOSchemaField field = new DOSchemaField(schema, oldClass);
            field.attributes.source = source != null ? source : "";
            field.attributes.destinationName = destination != null ? destination : "";
            field.attributes.type = type != null ? type : "";
            field.attributes.format = format;
            field.attributes.isExported = exported != null ? exported : false;
            field.attributes.skipWhen = skipWhen != null ? skipWhen : "";
            field.attributes.isCollection = collection != null ? collection : false;
            field.attributes.embedContents = embedContents != null ? embedContents : false;
            field.attributes.childrenType = childrenType != null ? childrenType : "";
            field.attributes.title = title;
            field.attributes.description = description;
            field.attributes.pointsTo = pointsTo;
            field.attributes.skipUserOption = skipUserOption;
            field.attributes.definitionId = definitionId;
            field.attributes.valueMap = valueMap;
            field.childrenSchemaClass = null;
            newFields[i] = field;
        }

        // Create new class with updated fields
        DOSchemaClass newClass = new DOSchemaClass(schema);
        newClass.attributes.source = oldClass.attributes.source;
        newClass.attributes.destinationName = oldClass.attributes.destinationName;
        newClass.attributes.description = oldClass.attributes.description;
        newClass.attributes.schemaNotes = oldClass.attributes.schemaNotes;
        newClass.attributes.title = oldClass.attributes.title;
        newClass.attributes.parentClassName = oldClass.attributes.parentClassName;
        newClass.setFields(newFields);
        newClass.schemaReferences = oldClass.schemaReferences;
        newClass.attributes.migrate = oldClass.attributes.migrate;

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

    /**
     * Mark the schema as modified (unsaved changes).
     */
    public void markModified() {
        if (!modified) {
            modified = true;
            setStatus("Modified - unsaved changes");
        }
    }

    private JCheckBox getExportCheckBoxFromPanel() {
        JComponent exportField = propertyPanel.getField(EXPORT_ROW_LABEL);
        if (!(exportField instanceof JPanel)) {
            return null;
        }

        for (Component component : ((JPanel) exportField).getComponents()) {
            if (component instanceof JCheckBox && EXPORT_CHECKBOX_COMPONENT_NAME.equals(component.getName())) {
                return (JCheckBox) component;
            }
        }

        return null;
    }

    private JTextField getSchemaNotesFieldFromPanel() {
        JComponent exportField = propertyPanel.getField(EXPORT_ROW_LABEL);
        if (!(exportField instanceof JPanel)) {
            return null;
        }

        for (Component component : ((JPanel) exportField).getComponents()) {
            if (component instanceof JTextField && SCHEMA_NOTES_COMPONENT_NAME.equals(component.getName())) {
                return (JTextField) component;
            }
        }

        return null;
    }

    private JCheckBox getStaticCheckBoxFromPanel() {
        JComponent exportField = propertyPanel.getField(EXPORT_ROW_LABEL);
        if (!(exportField instanceof JPanel)) {
            return null;
        }

        for (Component component : ((JPanel) exportField).getComponents()) {
            if (component instanceof JCheckBox && STATIC_CHECKBOX_COMPONENT_NAME.equals(component.getName())) {
                return (JCheckBox) component;
            }
        }

        return null;
    }

    private void reloadSchema() {
        if (modified) {
            int result = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Reload anyway?", "Confirm Reload", JOptionPane.YES_NO_OPTION);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        loadSchema();

        // Notify listeners that schema was reloaded
        if (onSchemaReloaded != null) {
            onSchemaReloaded.run();
        }
    }

    private void saveSchema() {
        if (schema == null) {
            JOptionPane.showMessageDialog(this, "No schema loaded to save.", "Save Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Apply any pending property changes before saving
        applyCurrentProperties();

        try {
            setStatus("Saving schema...");

            // Use the writer to save the schema
            DOReferenceSchemaWriter writer = new DOReferenceSchemaWriter();
            writer.writeSchema(schema);

            modified = false;
            setStatus("Schema saved successfully");

            JOptionPane.showMessageDialog(this, "Schema saved successfully!\nBackup created.", "Save Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving schema: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
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
        if (schemaClass.attributes.source != null) {
            String sourceName = schemaClass.attributes.source;
            String sourceClassName = sourceName.contains(".") ? sourceName.substring(sourceName.lastIndexOf('.') + 1) : sourceName;
            if (sourceClassName.toLowerCase().contains(currentFilter)) {
                return true;
            }
        }

        // Check destination name
        if (schemaClass.attributes.destinationName != null && schemaClass.attributes.destinationName.toLowerCase().contains(currentFilter)) {
            return true;
        }

        return false;
    }

    /**
     * Check if a class has errors (unresolved field types). A field type is unresolved if it's not a primitive and not found in the schema classes.
     */
    /**
     * Check if a class has any unresolved type errors. Made package-private so the renderer can access it.
     */
    public boolean hasErrors(DOSchemaClass schemaClass) {
        if (schemaClass.fields == null || schemaClass.fields.length == 0) {
            return false;
        }

        for (DOSchemaField field : schemaClass.fields) {
            // Check the main type field
            if (isUnresolvedType(field.attributes.type)) {
                return true;
            }

            // Check the childrenType field (for collections)
            if (isUnresolvedType(field.attributes.childrenType)) {
                return true;
            }

            // Check collection/childrenType consistency
            boolean isCollection = field.attributes.isCollection;
            String childrenType = field.attributes.childrenType;
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
                String shortName = cls.attributes.source.contains(".") ? cls.attributes.source.substring(cls.attributes.source.lastIndexOf('.') + 1) : cls.attributes.source;
                if (cls.attributes.source.equals(typeName) || shortName.equals(typeName)) {
                    return false;
                }
            }
        }

        // Not primitive and not in schema = unresolved
        return true;
    }

    private String getSchemaStats() {
        if (schema == null) {
            return "";
        }

        List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();
        int moduleCount = modules.size();
        int classCount = 0;
        int fieldCount = 0;

        for (DOSchemaModule module : modules) {
            List<String> classNames = new ArrayList<>();
            collectAllClassNames(module, classNames);
            classCount += classNames.size();
            for (String className : classNames) {
                DOSchemaClass schemaClass = schema.findClassByName(className);
                if (schemaClass != null && schemaClass.fields != null) {
                    fieldCount += schemaClass.fields.length;
                }
            }
        }

        return String.format("%d modules, %d classes, %d fields", moduleCount, classCount, fieldCount);
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    /** Recursively collects all class names from a module and its children. */
    private void collectAllClassNames(DOSchemaModule module, List<String> classNames) {
        for (ClassExportConfig config : module.classConfigs) {
            classNames.add(config.getClassName());
        }
        for (DOSchemaModule child : module.children) {
            collectAllClassNames(child, classNames);
        }
    }

    /**
     * Navigate to an anomaly by selecting its class in the tree and optionally opening the field editor.
     * 
     * @param anomaly The anomaly to navigate to
     * @param openEditor Whether to open the field editor
     */
    private void navigateToAnomaly(DOSchemaAnomaly anomaly, boolean openEditor) {
        if (anomaly == null || anomaly.schemaClass == null) {
            return;
        }

        // Select the class in the tree
        String className = anomaly.schemaClass.attributes.source;
        selectClassByName(className);

        // If there's a field, select it in the fields table and optionally open
        // the
        // editor
        if (anomaly.schemaField != null && currentSelectedNode != null && currentSelectedNode.getNodeType() == NodeType.CLASS) {

            DOSchemaClass schemaClass = (DOSchemaClass) currentSelectedNode.getSchemaElement();
            if (schemaClass.fields != null) {
                // Find the field index
                for (int i = 0; i < schemaClass.fields.length; i++) {
                    if (schemaClass.fields[i].attributes.source.equals(anomaly.schemaField.attributes.source)) {
                        // Select the field in the table
                        fieldsTable.setRowSelectionInterval(i, i);
                        fieldsTable.scrollRectToVisible(fieldsTable.getCellRect(i, 0, true));

                        // If requested, open the field editor
                        if (openEditor) {
                            openFieldEditor(i);
                        }
                        break;
                    }
                }
            }
        }
    }
}
