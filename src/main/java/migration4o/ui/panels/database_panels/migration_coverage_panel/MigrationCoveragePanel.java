package migration4o.ui.panels.database_panels.migration_coverage_panel;

import migration4o.database.DODatabaseOpener;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs.ClassObjectsDialog;
import migration4o.util.ObjectResolverUtil;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel showing migration coverage analysis with combined class list.
 */
public class MigrationCoveragePanel extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private DOSchema referenceSchema;
    private DOSchema databaseSchema;
    private String databasePath;

    public MigrationCoveragePanel(DOSchema referenceSchema, DOSchema databaseSchema, String databasePath) {
        setLayout(new BorderLayout());

        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;

        // Create table model
        String[] columnNames = { "Class Name", "Objects", "Unique", "Reached", "Migration" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1 || columnIndex == 2 || columnIndex == 3 || columnIndex == 4) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        // Create table
        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Set custom renderer for the first column (class name) with colors
        table.getColumnModel().getColumn(0).setCellRenderer(new ClassNameRenderer());

        // Set custom renderer for the Migration column (progress bar)
        table.getColumnModel().getColumn(4).setCellRenderer(new MigrationProgressRenderer());
        table.getColumnModel().getColumn(4).setPreferredWidth(150);

        // Add double-click listener to view class objects
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = table.convertRowIndexToModel(row);
                        String className = (String) tableModel.getValueAt(modelRow, 0);
                        viewClassObjects(className);
                    }
                }
            }
        });

        // Populate table with merged class list
        populateTable(referenceSchema, databaseSchema);

        // Add to scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Add summary panel at top
        add(createSummaryPanel(referenceSchema, databaseSchema), BorderLayout.NORTH);

        // Add button panel at bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton reachButton = new JButton("Reach");
        reachButton.addActionListener(e -> performReachAnalysis());
        buttonPanel.add(reachButton);

        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportObjectIds());
        buttonPanel.add(exportButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createSummaryPanel(DOSchema referenceSchema, DOSchema databaseSchema) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        int refCount = referenceSchema != null && referenceSchema.getClasses() != null
                ? referenceSchema.getClasses().length
                : 0;
        int dbCount = databaseSchema != null && databaseSchema.getClasses() != null ? databaseSchema.getClasses().length
                : 0;

        JLabel summaryLabel = new JLabel(String.format(
                "Reference Schema: %d classes  |  Database: %d classes  |  Total unique: %d classes",
                refCount, dbCount, tableModel.getRowCount()));
        summaryLabel.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(summaryLabel);

        return panel;
    }

    private void populateTable(DOSchema referenceSchema, DOSchema databaseSchema) {
        // Clear existing rows before repopulating
        tableModel.setRowCount(0);

        // Build maps for quick lookup
        Map<String, DOSchemaClass> refClassMap = new HashMap<>();
        Map<String, DOSchemaClass> dbClassMap = new HashMap<>();

        if (referenceSchema != null && referenceSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                refClassMap.put(schemaClass.source, schemaClass);
            }
        }

        if (databaseSchema != null && databaseSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                dbClassMap.put(schemaClass.source, schemaClass);
            }
        }

        // Collect all unique class names
        Set<String> allClassNames = new TreeSet<>(); // TreeSet for sorted order
        allClassNames.addAll(refClassMap.keySet());
        allClassNames.addAll(dbClassMap.keySet());

        // Add to table (already sorted by TreeSet)
        for (String className : allClassNames) {
            DOSchemaClass dbClass = dbClassMap.get(className);

            int objectCount = 0;
            int uniqueCount = 0;
            int reachedCount = 0;
            int migratedCount = 0;

            if (dbClass != null) {
                objectCount = dbClass.objectIds != null ? dbClass.objectIds.length : 0;
                uniqueCount = dbClass.uniqueObjectIds != null ? dbClass.uniqueObjectIds.length : 0;
                reachedCount = dbClass.reachedObjectIds != null ? dbClass.reachedObjectIds.length : 0;
                migratedCount = objectCount - uniqueCount; // Duplicates removed = migrated
            }

            tableModel.addRow(new Object[] { className, objectCount, uniqueCount, reachedCount, migratedCount });

            // Debug specific class
            if (className.equals("gest.dossPrev.PersonneRess")) {
                System.out.println("DEBUG TABLE: Adding PersonneRess row - Objects=" + objectCount + ", Unique="
                        + uniqueCount + ", Reached=" + reachedCount + ", Migrated=" + migratedCount);
            }
        }
    }

    /**
     * Custom cell renderer for class names with color coding:
     * - Grey: class not set to migrate
     * - Black: class has 0 unique objects
     * - Green: class found in schema
     * - Red: class only in database
     */
    private class ClassNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected && value instanceof String) {
                String className = (String) value;

                // Find the class in reference and database schemas
                DOSchemaClass refClass = findClassInSchema(referenceSchema, className);
                DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);

                // Determine color based on rules
                if (refClass != null && !refClass.migrate) {
                    // Grey: class not set to migrate
                    c.setForeground(Color.GRAY);
                } else if (dbClass != null
                        && (dbClass.uniqueObjectIds == null || dbClass.uniqueObjectIds.length == 0)) {
                    // Black: class has 0 unique objects
                    c.setForeground(Color.BLACK);
                } else if (refClass != null) {
                    // Green: class found in schema
                    c.setForeground(new Color(0, 128, 0));
                } else if (dbClass != null) {
                    // Red: class only in database
                    c.setForeground(Color.RED);
                } else {
                    c.setForeground(Color.BLACK);
                }
            }

            return c;
        }

        private DOSchemaClass findClassInSchema(DOSchema schema, String className) {
            if (schema == null || schema.getClasses() == null) {
                return null;
            }
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass.source.equals(className)) {
                    return schemaClass;
                }
            }
            return null;
        }
    }

    /**
     * Custom cell renderer for Migration column showing progress bar
     * Max = Objects count, Value = Objects - Unique (migrated/deduplicated count)
     * Background = red, Progress = green
     * Special cases:
     * - Light grey if not flagged to migrate
     * - Blue if descendant of EntiteContientID
     * - Yellow if descendant of IDEntite
     */
    private class MigrationProgressRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private int value;
        private int maximum;
        private Color backgroundColor;
        private Color progressColor;

        public MigrationProgressRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val,
                boolean isSelected, boolean hasFocus, int row, int column) {

            if (val instanceof Integer) {
                this.value = (Integer) val;

                // Get the Objects count from column 1
                this.maximum = 0;
                Object objectsValue = table.getValueAt(row, 1);
                if (objectsValue instanceof Integer) {
                    this.maximum = (Integer) objectsValue;
                }

                // Get class name from column 0
                String className = "";
                Object classNameValue = table.getValueAt(row, 0);
                if (classNameValue instanceof String) {
                    className = (String) classNameValue;
                }

                // Determine colors based on class properties
                determineColors(className);
            }

            return this;
        }

        private void determineColors(String className) {
            // Find the class in reference and database schemas
            DOSchemaClass refClass = findClassInSchema(referenceSchema, className);
            DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);
            DOSchemaClass schemaClass = refClass != null ? refClass : dbClass;

            // Default colors
            backgroundColor = new Color(239, 68, 68); // Red
            progressColor = new Color(34, 197, 94); // Green

            if (schemaClass != null) {
                // Check if class is not flagged to migrate
                if (!schemaClass.migrate) {
                    backgroundColor = new Color(211, 211, 211); // Light grey
                    progressColor = new Color(169, 169, 169); // Darker grey for progress
                }
                // Check if descendant of EntiteContientID
                else if (schemaClass.isEntite(referenceSchema)) {
                    backgroundColor = new Color(59, 130, 246); // Blue
                    progressColor = new Color(59, 130, 246); // Blue (fully)
                } // Check if descendant of EntiteParam
                else if (schemaClass.isParam(referenceSchema)) {
                    backgroundColor = new Color(99, 190, 246); // Blue
                    progressColor = new Color(99, 190, 246); // Blue (fully)
                }
                // Check if descendant of IDEntite
                else if (schemaClass.isIDEntite(referenceSchema)) {
                    backgroundColor = new Color(234, 179, 8); // Yellow
                    progressColor = new Color(234, 179, 8); // Yellow (fully)
                }
            }
        }

        private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
            String currentParent = schemaClass.parentClassName;

            // Walk up the inheritance chain
            while (currentParent != null && !currentParent.isEmpty()) {
                if (currentParent.equals(ancestorClassName)) {
                    return true;
                }

                // Find parent class and continue walking up
                DOSchemaClass parentClass = findClassInSchema(referenceSchema, currentParent);
                if (parentClass == null) {
                    parentClass = findClassInSchema(databaseSchema, currentParent);
                }

                if (parentClass == null) {
                    break;
                }

                currentParent = parentClass.parentClassName;
            }

            return false;
        }

        private DOSchemaClass findClassInSchema(DOSchema schema, String className) {
            if (schema == null || schema.getClasses() == null) {
                return null;
            }
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass.source.equals(className)) {
                    return schemaClass;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int width = getWidth();
            int height = getHeight();

            // Draw background
            g.setColor(backgroundColor);
            g.fillRect(0, 0, width, height);

            // Draw progress
            if (maximum > 0) {
                int progressWidth = (int) ((double) value / maximum * width);
                g.setColor(progressColor);
                g.fillRect(0, 0, progressWidth, height);
            }

            // Draw border
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, width - 1, height - 1);
        }
    }

    /**
     * Performs reach analysis to identify which objects can be reached through
     * parent references.
     * Goes through descendants of EntiteContientID and EntiteParam, activates each
     * object,
     * and removes child IDs from their respective class's uniqueObjectIds array.
     */
    private void performReachAnalysis() {
        if (databasePath == null || databasePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No database path available. Please reopen the database.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create monitoring dialog with tree view
        JDialog monitorDialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Reach Analysis Monitor", true);
        JPanel dialogPanel = new JPanel(new BorderLayout(10, 10));
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status label at top
        JLabel statusLabel = new JLabel("Initializing...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dialogPanel.add(statusLabel, BorderLayout.NORTH);

        // Tree to show exploration path
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Reach Exploration");
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        JTree explorationTree = new JTree(treeModel);
        explorationTree.setRootVisible(true);
        JScrollPane treeScrollPane = new JScrollPane(explorationTree);
        treeScrollPane.setPreferredSize(new Dimension(600, 400));
        dialogPanel.add(treeScrollPane, BorderLayout.CENTER);

        // Progress bar at bottom
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        dialogPanel.add(progressBar, BorderLayout.SOUTH);

        monitorDialog.setContentPane(dialogPanel);
        monitorDialog.setSize(650, 500);
        monitorDialog.setLocationRelativeTo(this);
        monitorDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Track current exploration path for tree updates
        Stack<DefaultMutableTreeNode> explorationStack = new Stack<>();
        explorationStack.push(rootNode);

        // Map to track nodes by their labels (for removal)
        Map<String, DefaultMutableTreeNode> nodeMap = new java.util.concurrent.ConcurrentHashMap<>();

        // Run in background to avoid blocking UI
        SwingWorker<Void, TreeUpdate> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(new TreeUpdate(TreeUpdateType.STATUS, "Opening database..."));

                ExtObjectContainer container = null;
                try {
                    // Open database
                    DODatabaseOpener opener = new DODatabaseOpener();
                    container = opener.openDatabase(databasePath);

                    publish(new TreeUpdate(TreeUpdateType.STATUS, "Database opened successfully"));

                    // Track all reached objects globally to avoid infinite loops and duplicates
                    Set<Long> reachedObjectIds = new HashSet<>();

                    // Maps to track object processing progress per class
                    Map<String, Integer> classProcessedCount = new java.util.concurrent.ConcurrentHashMap<>();
                    Map<String, Integer> classTotalCount = new java.util.concurrent.ConcurrentHashMap<>();

                    // Pre-calculate total counts for each class
                    for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                        classTotalCount.put(schemaClass.source,
                                schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0);
                        classProcessedCount.put(schemaClass.source, 0);
                    }

                    // Count classes to process
                    int classCount = 0;
                    for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                        if (schemaClass.isEntite(referenceSchema) ||
                                schemaClass.isParam(referenceSchema)) {
                            classCount++;
                        }
                    }

                    publish(new TreeUpdate(TreeUpdateType.STATUS, "Found " + classCount + " root classes to process"));

                    // Process all root classes (descendants of EntiteContientID or EntiteParam)
                    int processedCount = 0;
                    for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                        if (schemaClass.isEntite(referenceSchema) ||
                                schemaClass.isParam(referenceSchema)) {

                            processedCount++;
                            String simpleName = schemaClass.source;
                            if (simpleName.contains(".")) {
                                simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
                            }
                            publish(new TreeUpdate(TreeUpdateType.STATUS,
                                    String.format("Exploring %d/%d: %s (%d objects)",
                                            processedCount, classCount, simpleName,
                                            schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length
                                                    : 0)));

                            // Explore all objects in this root class recursively
                            long[] uniqueIds = schemaClass.uniqueObjectIds;
                            if (uniqueIds != null) {
                                for (long objectId : uniqueIds) {
                                    MigrationCoveragePanel.this.exploreObjectRecursively(container, objectId,
                                            reachedObjectIds,
                                            this::publish, rootNode, treeModel, classProcessedCount, classTotalCount);
                                }
                            }
                        }
                    }

                    publish(new TreeUpdate(TreeUpdateType.STATUS,
                            "Reached " + reachedObjectIds.size() + " total objects"));
                    publish(new TreeUpdate(TreeUpdateType.STATUS,
                            "Adding reached objects to class reachedObjectIds arrays..."));

                    // Now add all reached object IDs to their respective classes' reachedObjectIds
                    Map<String, Set<Long>> reachedByClass = new HashMap<>();
                    for (long objectId : reachedObjectIds) {
                        try {
                            Object obj = container.ext().getByID(objectId);
                            if (obj != null) {
                                String className = getClassName(obj);
                                if (className != null) {
                                    reachedByClass.computeIfAbsent(className, k -> new HashSet<>()).add(objectId);
                                }
                            }
                        } catch (Exception e) {
                            // Skip objects that can't be retrieved
                        }
                    }

                    for (Map.Entry<String, Set<Long>> entry : reachedByClass.entrySet()) {
                        String childClassName = entry.getKey();
                        Set<Long> idsToAdd = entry.getValue();

                        DOSchemaClass childClass = findClassInSchemaByName(databaseSchema, childClassName);
                        if (childClass != null) {
                            addIdsToReachedList(childClass, idsToAdd);
                        }
                    }

                    publish(new TreeUpdate(TreeUpdateType.STATUS, "Finalizing reach analysis..."));

                } finally {
                    if (container != null) {
                        container.close();
                    }
                }

                return null;
            }

            @Override
            protected void process(List<TreeUpdate> chunks) {
                // Update UI based on tree update type
                for (TreeUpdate update : chunks) {
                    switch (update.type) {
                        case STATUS:
                            statusLabel.setText(update.message);
                            System.out.println(update.message);
                            break;
                        case ADD_NODE:
                            // Add node to tree
                            if (update.parentLabel != null && update.childLabel != null) {
                                DefaultMutableTreeNode parentNode = nodeMap.get(update.parentLabel);
                                if (parentNode == null) {
                                    // If parent not found, try using root
                                    parentNode = rootNode;
                                }
                                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(update.childLabel);
                                parentNode.add(newNode);
                                nodeMap.put(update.childLabel, newNode);
                                treeModel.nodeStructureChanged(parentNode);
                                // Expand and scroll to show the new node
                                TreePath path = new TreePath(newNode.getPath());
                                explorationTree.scrollPathToVisible(path);
                                explorationTree.expandPath(path.getParentPath());
                            }
                            break;
                        case REMOVE_NODE:
                            // Remove node from tree
                            if (update.nodeLabel != null) {
                                DefaultMutableTreeNode nodeToRemove = nodeMap.get(update.nodeLabel);
                                if (nodeToRemove != null && nodeToRemove.getParent() != null) {
                                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) nodeToRemove.getParent();
                                    parent.remove(nodeToRemove);
                                    nodeMap.remove(update.nodeLabel);
                                    treeModel.nodeStructureChanged(parent);
                                }
                            }
                            break;
                    }
                }
            }

            @Override
            protected void done() {
                // Close monitor dialog
                monitorDialog.dispose();

                try {
                    get(); // Check for exceptions

                    // Debug: Check a specific class before refresh
                    DOSchemaClass testClass = findClassInSchemaByName(databaseSchema, "gest.dossPrev.PersonneRess");
                    if (testClass != null) {
                        System.out.println("DEBUG: Before refresh - PersonneRess unique count: "
                                + (testClass.uniqueObjectIds != null ? testClass.uniqueObjectIds.length : 0));
                    }

                    // Refresh table to show updated counts
                    statusLabel.setText("Refreshing table...");
                    populateTable(referenceSchema, databaseSchema);

                    // Debug: Check after refresh
                    if (testClass != null) {
                        System.out.println("DEBUG: After refresh - PersonneRess unique count: "
                                + (testClass.uniqueObjectIds != null ? testClass.uniqueObjectIds.length : 0));
                    }

                    JOptionPane.showMessageDialog(MigrationCoveragePanel.this,
                            "Reach analysis completed successfully!\nThe table has been updated with new object counts.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(MigrationCoveragePanel.this,
                            "Error during reach analysis: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        // Start the worker
        worker.execute();

        // Show monitor dialog (will block until worker calls dispose)
        monitorDialog.setVisible(true);
    }

    // Helper classes for tree updates
    private enum TreeUpdateType {
        STATUS, ADD_NODE, REMOVE_NODE
    }

    private static class TreeUpdate {
        TreeUpdateType type;
        String message;
        String parentLabel; // Label of parent node
        String nodeLabel; // Label of node to add or remove
        String childLabel; // Alias for nodeLabel for ADD operations

        TreeUpdate(TreeUpdateType type, String message) {
            this.type = type;
            this.message = message;
        }

        TreeUpdate(TreeUpdateType type, String parentLabel, String childLabel) {
            this.type = type;
            this.parentLabel = parentLabel;
            this.childLabel = childLabel;
            this.nodeLabel = childLabel;
        }

        static TreeUpdate removeNode(String nodeLabel) {
            TreeUpdate update = new TreeUpdate(TreeUpdateType.REMOVE_NODE, (String) null);
            update.nodeLabel = nodeLabel;
            return update;
        }
    }

    /**
     * Recursively explores an object and all objects reachable from it.
     * Marks all encountered objects as reached.
     */
    public void exploreObjectRecursively(ExtObjectContainer container, long objectId, Set<Long> reachedObjectIds,
            java.util.function.Consumer<TreeUpdate> publisher,
            DefaultMutableTreeNode parentNode, DefaultTreeModel treeModel,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount) {
        // Avoid processing the same object twice - check and add atomically
        if (!reachedObjectIds.add(objectId)) {
            // Object was already processed, skip it
            return;
        }

        // Track the object node label for removal later
        String objectNodeLabel = null;

        try {
            // Get and activate the object
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return;
            }

            String className = getClassName(obj);
            String simpleClassName = className != null ? className.substring(className.lastIndexOf('.') + 1) : "Object";

            // Update processed count for this class
            int processed = classProcessedCount.getOrDefault(className, 0) + 1;
            classProcessedCount.put(className, processed);
            int total = classTotalCount.getOrDefault(className, 0);

            objectNodeLabel = simpleClassName + " [" + processed + "/" + total + "]";

            // Add object node to tree
            String parentLabel = (String) parentNode.getUserObject();
            publisher.accept(new TreeUpdate(TreeUpdateType.ADD_NODE, parentLabel, objectNodeLabel));

            ObjectResolverUtil.activateObject(container, obj, objectId);

            // If it's a GenericObject, explore all its fields
            if (obj instanceof GenericObject) {
                GenericObject genericObj = (GenericObject) obj;

                StoredClass storedClass = container.ext().storedClass(genericObj);
                if (storedClass != null) {
                    // Don't pre-add field nodes - they will be added on-demand when important
                    // objects are found
                    exploreAllFields(container, genericObj, className, reachedObjectIds, publisher,
                            objectNodeLabel, treeModel, classProcessedCount, classTotalCount);
                }
            }

            // Remove object node from tree when done exploring this object
            if (objectNodeLabel != null) {
                publisher.accept(TreeUpdate.removeNode(objectNodeLabel));
            }
        } catch (Exception e) {
            System.err.println("Error exploring object " + objectId + ": " + e.getMessage());
            // Remove node on error
            if (objectNodeLabel != null) {
                publisher.accept(TreeUpdate.removeNode(objectNodeLabel));
            }
        }
    }

    /**
     * Explores all fields of a GenericObject, following references recursively.
     * Field nodes are added on-demand only when important child objects are found.
     */
    private void exploreAllFields(ExtObjectContainer container, GenericObject obj, String parentClassName,
            Set<Long> reachedObjectIds,
            java.util.function.Consumer<TreeUpdate> publisher,
            String objectNodeLabel, DefaultTreeModel treeModel,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount) {
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return;
            }

            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue == null) {
                        continue; // Skip null fields
                    }

                    // Handle collections
                    if (fieldValue instanceof Collection) {
                        Collection<?> collection = (Collection<?>) fieldValue;
                        // Check if any item in collection is important
                        boolean hasImportantItems = false;
                        for (Object item : collection) {
                            if (item != null && isImportantObject(container, item)) {
                                hasImportantItems = true;
                                break;
                            }
                        }

                        if (hasImportantItems) {
                            // Add field node only if it contains important objects
                            String fieldLabel = "Field: " + field.getName();
                            publisher.accept(new TreeUpdate(TreeUpdateType.ADD_NODE, objectNodeLabel, fieldLabel));

                            for (Object item : collection) {
                                if (item != null) {
                                    processFieldReference(container, item, field.getName(), parentClassName,
                                            reachedObjectIds, publisher, fieldLabel, treeModel,
                                            classProcessedCount, classTotalCount);
                                }
                            }
                        } else {
                            // Process without showing in tree
                            for (Object item : collection) {
                                if (item != null) {
                                    processFieldReference(container, item, field.getName(), parentClassName,
                                            reachedObjectIds, publisher, objectNodeLabel, treeModel,
                                            classProcessedCount, classTotalCount);
                                }
                            }
                        }
                    }
                    // Handle arrays
                    else if (fieldValue.getClass().isArray()) {
                        int length = java.lang.reflect.Array.getLength(fieldValue);
                        // Check if any item in array is important
                        boolean hasImportantItems = false;
                        for (int i = 0; i < length; i++) {
                            Object item = java.lang.reflect.Array.get(fieldValue, i);
                            if (item != null && isImportantObject(container, item)) {
                                hasImportantItems = true;
                                break;
                            }
                        }

                        if (hasImportantItems) {
                            // Add field node only if it contains important objects
                            String fieldLabel = "Field: " + field.getName();
                            publisher.accept(new TreeUpdate(TreeUpdateType.ADD_NODE, objectNodeLabel, fieldLabel));

                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(fieldValue, i);
                                if (item != null) {
                                    processFieldReference(container, item, field.getName(), parentClassName,
                                            reachedObjectIds, publisher, fieldLabel, treeModel,
                                            classProcessedCount, classTotalCount);
                                }
                            }
                        } else {
                            // Process without showing in tree
                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(fieldValue, i);
                                if (item != null) {
                                    processFieldReference(container, item, field.getName(), parentClassName,
                                            reachedObjectIds, publisher, objectNodeLabel, treeModel,
                                            classProcessedCount, classTotalCount);
                                }
                            }
                        }
                    }
                    // Handle single object references
                    else {
                        long refId = container.ext().getID(fieldValue);
                        if (refId > 0) {
                            // Check if this is an important object
                            if (isImportantObject(container, fieldValue)) {
                                // Add field node for important single reference
                                String fieldLabel = "Field: " + field.getName();
                                publisher.accept(new TreeUpdate(TreeUpdateType.ADD_NODE, objectNodeLabel, fieldLabel));

                                processFieldReference(container, fieldValue, field.getName(), parentClassName,
                                        reachedObjectIds, publisher, fieldLabel, treeModel,
                                        classProcessedCount, classTotalCount);
                            } else {
                                // Process without showing in tree
                                processFieldReference(container, fieldValue, field.getName(), parentClassName,
                                        reachedObjectIds, publisher, objectNodeLabel, treeModel,
                                        classProcessedCount, classTotalCount);
                            }
                        }
                        // Primitives and non-persistent values are ignored
                    }

                    // Note: Field nodes are NOT removed here because child objects may still be
                    // adding themselves under these field nodes asynchronously.
                    // Field nodes will be cleaned up when their parent object node is removed.
                } catch (Exception e) {
                    System.err.println("Error processing field " + field.getName() + ": " + e.getMessage());
                    // Field node will be cleaned up with parent object node
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing fields: " + e.getMessage());
        }
    }

    /**
     * Processes a field value that might be a reference to another object.
     * Handles special IDEntite relationships with type matching.
     */
    private void processFieldReference(ExtObjectContainer container, Object item, String fieldName,
            String parentClassName, Set<Long> reachedObjectIds,
            java.util.function.Consumer<TreeUpdate> publisher,
            String parentLabel, DefaultTreeModel treeModel,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount) {
        long childId = container.ext().getID(item);
        if (childId <= 0) {
            return; // Not a persistent object
        }

        String className = getClassName(item);
        if (className == null) {
            return;
        }

        // Check if this is an IDEntite descendant
        DOSchemaClass itemClass = findClassInSchemaByName(databaseSchema, className);
        if (itemClass != null && itemClass.isIDEntite(referenceSchema)) {
            // This is an IDEntite - get target type from pointsTo or extract from field
            // name
            String expectedType = itemClass.pointsTo;
            if (expectedType == null) {
                // Fallback to name extraction
                expectedType = extractExpectedTypeFromFieldName(fieldName, className);
            }

            // Handle the special mID relationship with type filtering
            handleIDEntiteRelationship(container, item, childId, expectedType, reachedObjectIds,
                    publisher, parentLabel, treeModel, classProcessedCount, classTotalCount);
        } else {
            // Regular object - explore it recursively
            DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(parentLabel);
            exploreObjectRecursively(container, childId, reachedObjectIds, publisher, parentNode,
                    treeModel, classProcessedCount, classTotalCount);
        }
    }

    /**
     * Extracts expected EntiteContientID type from field name.
     * Example: "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
     */
    private String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        // If field name starts with "mID", extract the part after it
        if (fieldName.startsWith("mID")) {
            return fieldName.substring(3); // Remove "mID" prefix
        }
        // Otherwise try to extract from the ID class name
        // "IDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2); // Remove "ID" prefix
        }
        return null;
    }

    /**
     * Handles IDEntite relationships: marks the IDEntite as reached, then finds
     * the corresponding EntiteContientID object with matching mID and type.
     */
    private void handleIDEntiteRelationship(ExtObjectContainer container, Object idEntiteObj,
            long idEntiteId, String expectedType, Set<Long> reachedObjectIds,
            java.util.function.Consumer<TreeUpdate> publisher,
            String parentLabel, DefaultTreeModel treeModel,
            Map<String, Integer> classProcessedCount,
            Map<String, Integer> classTotalCount) {
        // Mark the IDEntite object itself as reached
        if (!reachedObjectIds.contains(idEntiteId)) {
            reachedObjectIds.add(idEntiteId);
        }

        try {
            // Activate and extract the mID field
            ObjectResolverUtil.activateObject(container, idEntiteObj, idEntiteId);
            Long mID = extractMIDField(container, idEntiteObj);

            if (mID == null) {
                return; // No mID field found
            }

            // Find EntiteContientID objects with the same mID and matching type
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                if (schemaClass.isEntite(referenceSchema)) {
                    // Check if this class matches the expected type (if specified)
                    String simpleClassName = schemaClass.source;
                    if (simpleClassName.contains(".")) {
                        simpleClassName = simpleClassName.substring(simpleClassName.lastIndexOf('.') + 1);
                    }

                    // Only explore if type matches or no type specified
                    if (expectedType != null && !simpleClassName.equals(expectedType)) {
                        continue; // Skip classes that don't match the expected type
                    }

                    long[] objectIds = schemaClass.uniqueObjectIds;
                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            try {
                                Object obj = container.ext().getByID(objectId);
                                if (obj != null) {
                                    ObjectResolverUtil.activateObject(container, obj, objectId);
                                    Long objMID = extractMIDField(container, obj);

                                    // If mIDs match, explore this EntiteContientID object
                                    if (mID.equals(objMID)) {
                                        // Show this object in tree even if already reached elsewhere
                                        // This allows users to see the IDEntite field relationships
                                        DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(parentLabel);
                                        exploreObjectRecursively(container, objectId, reachedObjectIds,
                                                publisher, parentNode, treeModel, classProcessedCount, classTotalCount);
                                        // Only show the first matching object for this field
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // Skip objects that can't be processed
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling IDEntite relationship for object " + idEntiteId + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the mID field value from a GenericObject.
     */
    private Long extractMIDField(ExtObjectContainer container, Object obj) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        StoredField[] fields = storedClass.getStoredFields();
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                } catch (Exception e) {
                    // Field access failed
                }
            }
        }

        return null;
    }

    /**
     * DEPRECATED: Old non-recursive method - replaced by exploreObjectRecursively
     * Processes a single class: activates each object in uniqueObjectIds,
     * examines fields for collections, extracts child IDs, and removes them
     * from their respective class's uniqueObjectIds array.
     */
    private void processClass(ExtObjectContainer container, DOSchemaClass parentClass) {
        long[] uniqueIds = parentClass.uniqueObjectIds;
        if (uniqueIds == null || uniqueIds.length == 0) {
            return;
        }

        // Track all child IDs found, organized by class name
        Map<String, Set<Long>> childIdsByClass = new HashMap<>();

        // Process each object
        for (long objectId : uniqueIds) {
            try {
                // Get and activate the object
                Object obj = container.ext().getByID(objectId);
                if (obj != null) {
                    ObjectResolverUtil.activateObject(container, obj, objectId);

                    // If it's a GenericObject, we can inspect its fields
                    if (obj instanceof GenericObject) {
                        GenericObject genericObj = (GenericObject) obj;
                        extractChildIds(container, genericObj, childIdsByClass);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing object " + objectId + " of class " +
                        parentClass.source + ": " + e.getMessage());
            }
        }

        // Now add child IDs to their respective classes' reachedObjectIds
        for (Map.Entry<String, Set<Long>> entry : childIdsByClass.entrySet()) {
            String childClassName = entry.getKey();
            Set<Long> idsToAdd = entry.getValue();

            DOSchemaClass childClass = findClassInSchemaByName(databaseSchema, childClassName);
            if (childClass != null) {
                addIdsToReachedList(childClass, idsToAdd);
            }
        }
    }

    /**
     * Extracts child object IDs from all collection fields of a GenericObject.
     */
    private void extractChildIds(ExtObjectContainer container, GenericObject obj,
            Map<String, Set<Long>> childIdsByClass) {
        try {
            // Use db4o StoredClass API to access fields properly
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return;
            }

            // Get all stored fields
            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null) {
                        // Check if it's a collection
                        if (fieldValue instanceof Collection) {
                            Collection<?> collection = (Collection<?>) fieldValue;
                            for (Object item : collection) {
                                if (item != null) {
                                    long childId = container.ext().getID(item);
                                    if (childId > 0) {
                                        // Get the class name of the child object
                                        String childClassName = getClassName(item);
                                        if (childClassName != null) {
                                            childIdsByClass.computeIfAbsent(childClassName, k -> new HashSet<>())
                                                    .add(childId);
                                        }
                                    }
                                }
                            }
                        } else if (fieldValue.getClass().isArray()) {
                            // Handle arrays
                            int length = java.lang.reflect.Array.getLength(fieldValue);
                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(fieldValue, i);
                                if (item != null) {
                                    long childId = container.ext().getID(item);
                                    if (childId > 0) {
                                        String childClassName = getClassName(item);
                                        if (childClassName != null) {
                                            childIdsByClass.computeIfAbsent(childClassName, k -> new HashSet<>())
                                                    .add(childId);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error extracting child IDs from field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing stored class for object: " + e.getMessage());
        }
    }

    /**
     * Gets the class name of an object (handles both GenericObject and regular
     * objects).
     */
    private String getClassName(Object obj) {
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            return genericObj.getGenericClass().getName();
        } else {
            return obj.getClass().getName();
        }
    }

    /**
     * Removes a set of object IDs from a class's uniqueObjectIds array.
     */
    private void addIdsToReachedList(DOSchemaClass schemaClass, Set<Long> idsToAdd) {
        long[] currentReachedIds = schemaClass.reachedObjectIds;
        if (currentReachedIds == null) {
            currentReachedIds = new long[0];
        }

        // Create a set of existing reached IDs to avoid duplicates
        Set<Long> existingIds = new HashSet<>();
        for (long id : currentReachedIds) {
            existingIds.add(id);
        }

        // Add new IDs that aren't already in the list
        Set<Long> newIds = new HashSet<>();
        for (long id : idsToAdd) {
            if (!existingIds.contains(id)) {
                newIds.add(id);
            }
        }

        // Combine existing and new IDs
        if (!newIds.isEmpty()) {
            long[] combinedIds = new long[currentReachedIds.length + newIds.size()];
            System.arraycopy(currentReachedIds, 0, combinedIds, 0, currentReachedIds.length);
            int index = currentReachedIds.length;
            for (long id : newIds) {
                combinedIds[index++] = id;
            }
            schemaClass.reachedObjectIds = combinedIds;
            System.out.println("Added " + newIds.size() +
                    " reached objects to class " + schemaClass.source +
                    " (was " + currentReachedIds.length + ", now " + combinedIds.length + ")");
        }
    }

    /**
     * Helper method to find a class by name in a schema array.
     */
    private DOSchemaClass findClassInSchemaByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Helper method to check if a class is a descendant of another class.
     */
    private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
        String currentParent = schemaClass.parentClassName;

        // Walk up the inheritance chain
        while (currentParent != null && !currentParent.isEmpty()) {
            if (currentParent.equals(ancestorClassName)) {
                return true;
            }

            // Find parent class and continue walking up
            DOSchemaClass parentClass = findClassInSchemaByName(referenceSchema, currentParent);
            if (parentClass == null) {
                parentClass = findClassInSchemaByName(databaseSchema, currentParent);
            }

            if (parentClass == null) {
                break;
            }

            currentParent = parentClass.parentClassName;
        }

        return false;
    }

    /**
     * Checks if an object is important (descendant of EntiteContientID or
     * IDEntite).
     */
    private boolean isImportantObject(ExtObjectContainer container, Object obj) {
        if (obj == null) {
            return false;
        }

        String className = getClassName(obj);
        if (className == null) {
            return false;
        }

        DOSchemaClass objClass = findClassInSchemaByName(databaseSchema, className);
        if (objClass != null) {
            return objClass.isEntite(referenceSchema) ||
                    objClass.isIDEntite(referenceSchema);
        }

        return false;
    }

    /**
     * DEPRECATED: No longer used - we check objects directly instead of field
     * types.
     * Checks if a field type is important (descendant of EntiteContientID or
     * IDEntite).
     * Also returns true for collections/arrays that might contain important types.
     */
    private boolean isImportantFieldType(String fieldTypeName) {
        // Always include collections and arrays - they might contain important objects
        if (fieldTypeName.startsWith("java.util.") || fieldTypeName.startsWith("[")) {
            return true;
        }

        // Check if the type itself is important
        DOSchemaClass fieldClass = findClassInSchemaByName(databaseSchema, fieldTypeName);
        if (fieldClass != null) {
            return fieldClass.isEntite(referenceSchema) ||
                    fieldClass.isIDEntite(referenceSchema);
        }

        return false;
    }

    /**
     * Exports object IDs to files.
     */
    private void exportObjectIds() {
        try {
            // Create output directory
            java.nio.file.Path outputDir = java.nio.file.Paths.get("output/migration/ids");
            java.nio.file.Files.createDirectories(outputDir);

            // Prepare output files
            java.nio.file.Path entriesFile = outputDir.resolve("entries.txt");
            java.nio.file.Path leafsFile = outputDir.resolve("leafs.txt");
            java.nio.file.Path countersFile = outputDir.resolve("counters.txt");

            try (java.io.PrintWriter entriesWriter = new java.io.PrintWriter(
                    java.nio.file.Files.newBufferedWriter(entriesFile));
                    java.io.PrintWriter leafsWriter = new java.io.PrintWriter(
                            java.nio.file.Files.newBufferedWriter(leafsFile));
                    java.io.PrintWriter countersWriter = new java.io.PrintWriter(
                            java.nio.file.Files.newBufferedWriter(countersFile))) {

                // Iterate through all classes in the database schema
                if (databaseSchema != null && databaseSchema.getClasses() != null) {
                    // Sort classes by name for consistent output
                    List<DOSchemaClass> sortedClasses = new ArrayList<>(Arrays.asList(databaseSchema.getClasses()));
                    sortedClasses.sort(Comparator.comparing(sc -> sc.source));

                    for (DOSchemaClass schemaClass : sortedClasses) {
                        String className = schemaClass.source;

                        // Get all object IDs (entries) - export all classes even with no IDs
                        long[] allObjectIds = schemaClass.objectIds;
                        entriesWriter.print(className);
                        entriesWriter.print("\t");
                        if (allObjectIds != null && allObjectIds.length > 0) {
                            for (int i = 0; i < allObjectIds.length; i++) {
                                if (i > 0) {
                                    entriesWriter.print(",");
                                }
                                entriesWriter.print(allObjectIds[i]);
                            }
                        }
                        entriesWriter.println();

                        // Get unique object IDs (leafs) - export all classes even with no IDs
                        long[] uniqueObjectIds = schemaClass.uniqueObjectIds;
                        leafsWriter.print(className);
                        leafsWriter.print("\t");
                        if (uniqueObjectIds != null && uniqueObjectIds.length > 0) {
                            // Use a set to remove duplicates and sort
                            Set<Long> uniqueIdSet = new TreeSet<>();
                            for (long id : uniqueObjectIds) {
                                uniqueIdSet.add(id);
                            }

                            boolean first = true;
                            for (Long id : uniqueIdSet) {
                                if (!first) {
                                    leafsWriter.print(",");
                                }
                                leafsWriter.print(id);
                                first = false;
                            }
                        }
                        leafsWriter.println();

                        // Export counters: className, objectIds length, uniqueObjectIds length,
                        // reachedObjectIds length
                        long[] reachedObjectIds = schemaClass.reachedObjectIds;
                        countersWriter.print(className);
                        countersWriter.print("\t");
                        countersWriter.print(allObjectIds != null ? allObjectIds.length : 0);
                        countersWriter.print("\t");
                        countersWriter.print(uniqueObjectIds != null ? uniqueObjectIds.length : 0);
                        countersWriter.print("\t");
                        countersWriter.print(reachedObjectIds != null ? reachedObjectIds.length : 0);
                        countersWriter.println();
                    }
                }

                JOptionPane.showMessageDialog(this,
                        "Object IDs exported successfully to:\n" +
                                entriesFile.toAbsolutePath() + "\n" +
                                leafsFile.toAbsolutePath() + "\n" +
                                countersFile.toAbsolutePath(),
                        "Export Successful",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error exporting object IDs: " + ex.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * Opens a dialog to view actual objects of a class from the database.
     */
    private void viewClassObjects(String className) {
        if (databasePath == null || databasePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No database path available. Please reopen the database.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Find the schema class
        DOSchemaClass schemaClass = findClassInSchemaByName(databaseSchema, className);
        if (schemaClass == null) {
            JOptionPane.showMessageDialog(this,
                    "Class not found: " + className,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open dialog
        ClassObjectsDialog dialog = new ClassObjectsDialog(
                (java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                className,
                schemaClass,
                databaseSchema,
                databasePath);
        dialog.setVisible(true);
    }

    /**
     * Update the reached object counts for exported classes.
     * This should be called after an export completes successfully.
     * 
     * @param exportedClasses Map of class name to number of exported objects
     */
    public void updateExportedCounts(Map<String, Integer> exportedClasses) {
        if (exportedClasses == null || exportedClasses.isEmpty()) {
            System.out.println("DEBUG MigrationCoveragePanel: No exported classes to update");
            return;
        }

        System.out.println("DEBUG MigrationCoveragePanel: Updating " + exportedClasses.size() + " classes");

        // Update the database schema's reachedObjectIds for each exported class
        for (Map.Entry<String, Integer> entry : exportedClasses.entrySet()) {
            String className = entry.getKey();
            int exportedCount = entry.getValue();

            System.out.println("DEBUG: Updating class " + className + " with " + exportedCount + " objects");

            // Find the class in database schema
            DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);
            if (dbClass != null) {
                System.out.println("DEBUG: Found class in schema, setting reachedObjectIds");
                // Update the reached count to match exported count
                // Note: We set the reachedObjectIds length to match the exported count
                // This is a simplified approach - ideally we'd track actual object IDs
                if (exportedCount > 0) {
                    // Create a dummy array of the right size for display purposes
                    long[] reachedIds = new long[exportedCount];
                    dbClass.reachedObjectIds = reachedIds;
                }
            } else {
                System.out.println("DEBUG: Class not found in database schema: " + className);
            }
        }

        System.out.println("DEBUG: Refreshing table...");
        // Refresh the table to show updated counts
        refreshTable();
    }

    /**
     * Refresh the table with current data from the schemas.
     */
    private void refreshTable() {
        // Clear existing rows
        tableModel.setRowCount(0);

        // Re-populate table
        populateTable(referenceSchema, databaseSchema);

        // Force table repaint
        table.repaint();
    }

    /**
     * Helper method to find a class in a schema.
     * Searches by both source (fully qualified) and destinationName (simple name).
     */
    private DOSchemaClass findClassInSchema(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            // Try matching by source (fully qualified name)
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
            // Also try matching by destinationName (simple name)
            if (schemaClass.destinationName != null && schemaClass.destinationName.equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }
}
