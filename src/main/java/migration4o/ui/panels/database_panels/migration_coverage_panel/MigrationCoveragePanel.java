package migration4o.ui.panels.database_panels.migration_coverage_panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabaseService;
import migration4o.database.reach.ReachResultAggregator;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs.ClassObjectsDialog;
import migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs.IDTracerDialog;
import migration4o.util.ObjectResolverUtil;

/**
 * Panel showing migration coverage analysis with combined class list.
 */
public class MigrationCoveragePanel extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;
    private DefaultTableModel fullTableModel; // Unfiltered data
    private DOSchema referenceSchema;
    private DOSchema databaseSchema;
    private String databasePath;
    private javax.swing.JTextField searchField;
    private Set<String> classNameFilter = null; // Filter to specific class names (null = no filter)

    // Filter state
    private boolean filterIDEntites = false; // Unchecked by default
    private boolean filterEntites = true;
    private boolean filterParams = true;
    private boolean filterOthers = true;
    private boolean filter100Percent = true;
    private boolean filterPartial = true;
    private boolean filterNotMigrated = true;

    public MigrationCoveragePanel(DOSchema referenceSchema, DOSchema databaseSchema, String databasePath) {
        setLayout(new BorderLayout());

        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;

        // Create table model
        String[] columnNames = { "Class Name", "Unique", "Objects", "Reached", "Not reached", "Migration" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1 || columnIndex == 2 || columnIndex == 3 || columnIndex == 5) {
                    return Integer.class;
                }
                return String.class; // Column 0 (Class Name) and Column 4 (Not reached) are String
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
        table.getColumnModel().getColumn(5).setCellRenderer(new MigrationProgressRenderer());
        table.getColumnModel().getColumn(5).setPreferredWidth(150);

        // Set right-aligned renderer for Not reached column
        javax.swing.table.DefaultTableCellRenderer rightRenderer = new javax.swing.table.DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);

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

        // Add summary panel and filters at top
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createSummaryPanel(referenceSchema, databaseSchema), BorderLayout.NORTH);
        topPanel.add(createFilterPanel(), BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Add button panel at bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton reachButton = new JButton("Reach");
        reachButton.setEnabled(false); // Disabled - tool extracted to ReachDiscoverTool
        reachButton.addActionListener(e -> performReachAnalysis());
        buttonPanel.add(reachButton);

        JButton exportButton = new JButton("Export");
        exportButton.setEnabled(false); // Disabled - tool extracted to ExportAttemptTool
        exportButton.addActionListener(e -> exportObjectIds());
        buttonPanel.add(exportButton);

        JButton exportIdsButton = new JButton("Export IDs");
        exportIdsButton.addActionListener(e -> exportAllObjectIds());
        buttonPanel.add(exportIdsButton);

        JButton idTracerButton = new JButton("ID Tracer");
        idTracerButton.addActionListener(e -> openIdTracer());
        buttonPanel.add(idTracerButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Helper class to centralize migration status calculations.
     * Ensures consistent logic between filtering and rendering.
     */
    private static class MigrationStatus {
        final boolean isNotSetToMigrate;
        final boolean is100Percent;
        final boolean isPartial;
        final boolean isNotMigrated;

        MigrationStatus(DOSchemaClass refClass, int unique, int objects, int reached) {
            // Check if class is not set to migrate
            this.isNotSetToMigrate = refClass != null && !refClass.migrate;

            // Categories are mutually exclusive:
            // 1. Not migrated: class not set to migrate OR (objects > 0 AND reached == 0)
            // 2. 100% migrated: reached all unique AND all non-unique objects (reached >=
            // objects)
            // 3. Partially migrated: reached some objects but not all
            this.is100Percent = !isNotSetToMigrate && objects > 0 && reached >= objects;
            this.isPartial = !isNotSetToMigrate && reached > 0 && reached < objects;
            this.isNotMigrated = isNotSetToMigrate || (objects > 0 && reached == 0);
        }
    }

    /**
     * Reset all reached values to 0 for all classes in the database schema.
     * Should be called before starting a new export to avoid accumulating values.
     */
    public void resetReachedValues() {
        if (databaseSchema != null && databaseSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                schemaClass.reachedObjectIds = null;
            }
        }

        // Refresh the table to show updated values
        populateTable(referenceSchema, databaseSchema);
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

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Search field at the top
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Filter classes: "));
        searchField = new javax.swing.JTextField(30);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters();
            }
        });
        searchPanel.add(searchField);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            searchField.setText("");
            classNameFilter = null;
            applyFilters();
        });
        searchPanel.add(clearButton);
        panel.add(searchPanel, BorderLayout.NORTH);

        // Checkbox filters below
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel filterLabel = new JLabel("Show: ");
        filterLabel.setFont(new Font("Arial", Font.BOLD, 12));
        checkboxPanel.add(filterLabel);

        // Create checkboxes
        javax.swing.JCheckBox cbIDEntites = new javax.swing.JCheckBox("IDEntites", filterIDEntites);
        javax.swing.JCheckBox cbEntites = new javax.swing.JCheckBox("Entites", filterEntites);
        javax.swing.JCheckBox cbParams = new javax.swing.JCheckBox("Params", filterParams);
        javax.swing.JCheckBox cbOthers = new javax.swing.JCheckBox("Others", filterOthers);
        javax.swing.JCheckBox cb100Percent = new javax.swing.JCheckBox("100% migrated", filter100Percent);
        javax.swing.JCheckBox cbPartial = new javax.swing.JCheckBox("Partially migrated", filterPartial);
        javax.swing.JCheckBox cbNotMigrated = new javax.swing.JCheckBox("Not migrated", filterNotMigrated);

        // Add action listeners to update filter state and refilter table
        cbIDEntites.addActionListener(e -> {
            filterIDEntites = cbIDEntites.isSelected();
            applyFilters();
        });
        cbEntites.addActionListener(e -> {
            filterEntites = cbEntites.isSelected();
            applyFilters();
        });
        cbParams.addActionListener(e -> {
            filterParams = cbParams.isSelected();
            applyFilters();
        });
        cbOthers.addActionListener(e -> {
            filterOthers = cbOthers.isSelected();
            applyFilters();
        });
        cb100Percent.addActionListener(e -> {
            filter100Percent = cb100Percent.isSelected();
            applyFilters();
        });
        cbPartial.addActionListener(e -> {
            filterPartial = cbPartial.isSelected();
            applyFilters();
        });
        cbNotMigrated.addActionListener(e -> {
            filterNotMigrated = cbNotMigrated.isSelected();
            applyFilters();
        });

        checkboxPanel.add(cbIDEntites);
        checkboxPanel.add(cbEntites);
        checkboxPanel.add(cbParams);
        checkboxPanel.add(cbOthers);
        checkboxPanel.add(new JLabel("  |  "));
        checkboxPanel.add(cb100Percent);
        checkboxPanel.add(cbPartial);
        checkboxPanel.add(cbNotMigrated);

        panel.add(checkboxPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Filter the table to show only the specified class names.
     * Clears any text search filter.
     */
    public void filterByClassNames(Set<String> classNames) {
        this.classNameFilter = classNames;
        if (searchField != null) {
            searchField.setText("");
        }
        applyFilters();
    }

    /**
     * Clear all filters (text search and class name filter).
     */
    public void clearAllFilters() {
        this.classNameFilter = null;
        if (searchField != null) {
            searchField.setText("");
        }
        applyFilters();
    }

    private void applyFilters() {
        // Clear current table
        tableModel.setRowCount(0);

        // Get search text
        String searchText = searchField != null ? searchField.getText().toLowerCase().trim() : "";

        // Re-add filtered rows from fullTableModel
        for (int i = 0; i < fullTableModel.getRowCount(); i++) {
            String className = (String) fullTableModel.getValueAt(i, 0);

            // Apply text search filter
            if (!searchText.isEmpty() && !className.toLowerCase().contains(searchText)) {
                continue;
            }

            // Apply class name filter (if set)
            if (classNameFilter != null && !classNameFilter.contains(className)) {
                continue;
            }
            int unique = (Integer) fullTableModel.getValueAt(i, 1);
            int objects = (Integer) fullTableModel.getValueAt(i, 2);
            int reached = (Integer) fullTableModel.getValueAt(i, 3);
            String notReached = (String) fullTableModel.getValueAt(i, 4);
            int migrated = (Integer) fullTableModel.getValueAt(i, 5);

            // Determine class type
            DOSchemaClass refClass = findClassInSchema(referenceSchema, className);
            DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);
            DOSchemaClass schemaClass = refClass != null ? refClass : dbClass;

            boolean isIDEntite = schemaClass != null && schemaClass.isIDEntite(referenceSchema);
            boolean isEntite = schemaClass != null && schemaClass.isEntite(referenceSchema);
            boolean isParam = schemaClass != null && schemaClass.isParam(referenceSchema);
            boolean isOther = !isIDEntite && !isEntite && !isParam;

            // Check type filters
            if (isIDEntite && !filterIDEntites)
                continue;
            if (isEntite && !filterEntites)
                continue;
            if (isParam && !filterParams)
                continue;
            if (isOther && !filterOthers)
                continue;

            // Calculate migration status using centralized logic
            MigrationStatus status = new MigrationStatus(refClass, unique, objects, reached);

            if (status.is100Percent && !filter100Percent)
                continue;
            if (status.isPartial && !filterPartial)
                continue;
            if (status.isNotMigrated && !filterNotMigrated)
                continue;

            // Add row
            tableModel.addRow(new Object[] { className, unique, objects, reached, notReached, migrated });
        }
    }

    private void populateTable(DOSchema referenceSchema, DOSchema databaseSchema) {
        // Create fullTableModel if it doesn't exist
        if (fullTableModel == null) {
            String[] columnNames = { "Class Name", "Unique", "Objects", "Reached", "Not reached", "Migration" };
            fullTableModel = new DefaultTableModel(columnNames, 0) {
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
        }

        // Clear existing rows before repopulating
        fullTableModel.setRowCount(0);
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

            if (dbClass != null) {
                objectCount = dbClass.objectIds != null ? dbClass.objectIds.length : 0;
                uniqueCount = dbClass.uniqueObjectIds != null ? dbClass.uniqueObjectIds.length : 0;
                reachedCount = dbClass.reachedObjectIds != null ? dbClass.reachedObjectIds.length : 0;
            }

            // Calculate Not reached components for equation display
            // We need to determine how many unique vs non-unique objects are unreached
            int uniqueReachedCount = 0;
            int nonUniqueReachedCount = 0;

            if (dbClass != null && dbClass.reachedObjectIds != null && dbClass.uniqueObjectIds != null) {
                // Create set of unique object IDs for fast lookup
                Set<Long> uniqueSet = new HashSet<>();
                for (long id : dbClass.uniqueObjectIds) {
                    uniqueSet.add(id);
                }

                // Count reached unique vs non-unique
                for (long reachedId : dbClass.reachedObjectIds) {
                    if (uniqueSet.contains(reachedId)) {
                        uniqueReachedCount++;
                    } else {
                        nonUniqueReachedCount++;
                    }
                }
            }

            // Calculate unreached counts
            int uniqueUnreached = uniqueCount - uniqueReachedCount;
            int nonUniqueCount = objectCount - uniqueCount;
            int nonUniqueUnreached = nonUniqueCount - nonUniqueReachedCount;
            int totalUnreached = uniqueUnreached + nonUniqueUnreached;

            // Format as equation: "51 + 24 = 75"
            String notReachedEquation = uniqueUnreached + " + " + nonUniqueUnreached + " = " + totalUnreached;

            // Migration column now shows reached count (what we actually use for progress)
            fullTableModel.addRow(new Object[] { className, uniqueCount, objectCount, reachedCount, notReachedEquation,
                    reachedCount });

            // Debug specific class
            if (className.equals("gest.dossPrev.PersonneRess")) {
                System.out.println("DEBUG TABLE: Adding PersonneRess row - Objects=" + objectCount + ", Unique="
                        + uniqueCount + ", Reached=" + reachedCount);
            }
        }

        // Apply filters to populate tableModel from fullTableModel
        applyFilters();
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
     * Custom cell renderer for Migration column showing dual-segment progress bar
     * Max = Total objects count
     * Segment 1 (blue/green): Unique objects reached
     * Segment 2 (darker blue/green): Non-unique objects reached
     * Background = gray
     * Colors:
     * - Blue for partial unique coverage, green when all unique reached
     * - Darker blue for partial non-unique coverage, darker green when all
     * non-unique reached
     * - Light grey if not flagged to migrate
     */
    private class MigrationProgressRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private int totalObjects; // Total objects (max of progress bar)
        private int uniqueObjects; // Total unique objects
        private int uniqueReached; // Unique objects reached
        private int nonUniqueReached; // Non-unique objects reached
        private Color backgroundColor;
        private Color uniqueProgressColor;
        private Color nonUniqueProgressColor;

        public MigrationProgressRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val,
                boolean isSelected, boolean hasFocus, int row, int column) {

            if (val instanceof Integer) {
                // Get class name from column 0
                String className = "";
                Object classNameValue = table.getValueAt(row, 0);
                if (classNameValue instanceof String) {
                    className = (String) classNameValue;
                }

                // Get values from table columns
                // Column 1: Unique
                // Column 2: Objects (total)
                // Column 3: Reached (total reached)
                this.uniqueObjects = 0;
                Object uniqueValue = table.getValueAt(row, 1);
                if (uniqueValue instanceof Integer) {
                    this.uniqueObjects = (Integer) uniqueValue;
                }

                this.totalObjects = 0;
                Object totalValue = table.getValueAt(row, 2);
                if (totalValue instanceof Integer) {
                    this.totalObjects = (Integer) totalValue;
                }

                int totalReached = 0;
                Object reachedValue = table.getValueAt(row, 3);
                if (reachedValue instanceof Integer) {
                    totalReached = (Integer) reachedValue;
                }

                // Calculate unique vs non-unique reached
                // We need to get the actual class to determine this properly
                calculateReachedCounts(className, totalReached);

                // Determine colors based on class properties
                determineColors(className);
            }

            return this;
        }

        /**
         * Calculate how many unique vs non-unique objects were reached.
         * Logic: If we reached an object that's in uniqueObjectIds, count it as unique.
         * If we reached an object NOT in uniqueObjectIds but in objectIds, it's
         * non-unique (ancestor).
         */
        private void calculateReachedCounts(String className, int totalReached) {
            DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);

            if (dbClass == null || dbClass.reachedObjectIds == null) {
                this.uniqueReached = 0;
                this.nonUniqueReached = 0;
                return;
            }

            // Create sets for fast lookup
            Set<Long> uniqueSet = new HashSet<>();
            if (dbClass.uniqueObjectIds != null) {
                for (long id : dbClass.uniqueObjectIds) {
                    uniqueSet.add(id);
                }
            }

            Set<Long> objectSet = new HashSet<>();
            if (dbClass.objectIds != null) {
                for (long id : dbClass.objectIds) {
                    objectSet.add(id);
                }
            }

            // Count reached objects by category
            int uniqueCount = 0;
            int nonUniqueCount = 0;

            for (long reachedId : dbClass.reachedObjectIds) {
                if (uniqueSet.contains(reachedId)) {
                    uniqueCount++;
                } else if (objectSet.contains(reachedId)) {
                    nonUniqueCount++;
                }
            }

            this.uniqueReached = uniqueCount;
            this.nonUniqueReached = nonUniqueCount;
        }

        private void determineColors(String className) {
            // Find the class in reference and database schemas
            DOSchemaClass refClass = findClassInSchema(referenceSchema, className);

            // Calculate migration status using centralized logic
            int totalReached = uniqueReached + nonUniqueReached;
            MigrationStatus status = new MigrationStatus(refClass, uniqueObjects, totalObjects, totalReached);

            // Use migration status to determine colors
            if (status.isNotSetToMigrate) {
                // Light gray for classes not set to migrate
                backgroundColor = new Color(211, 211, 211); // Light grey
                uniqueProgressColor = new Color(211, 211, 211); // Same light grey
                nonUniqueProgressColor = new Color(211, 211, 211); // Same light grey
            } else {
                // Darker grey background
                backgroundColor = new Color(169, 169, 169);

                // Unique segment color (blue or green)
                boolean allUniqueReached = (uniqueObjects > 0 && uniqueReached >= uniqueObjects);
                if (allUniqueReached) {
                    uniqueProgressColor = new Color(34, 197, 94); // Green - all unique reached
                } else {
                    uniqueProgressColor = new Color(59, 130, 246); // Blue - partial unique
                }

                // Non-unique segment color (darker blue or darker green)
                int nonUniqueTotalCount = totalObjects - uniqueObjects;
                boolean allNonUniqueReached = (nonUniqueTotalCount > 0 && nonUniqueReached >= nonUniqueTotalCount);
                if (allNonUniqueReached) {
                    nonUniqueProgressColor = new Color(22, 163, 74); // Darker green - all non-unique reached
                } else {
                    nonUniqueProgressColor = new Color(29, 78, 216); // Darker blue - partial non-unique
                }
            }
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

            // Draw dual-segment progress bar
            if (totalObjects > 0) {
                // Segment 1: Unique objects reached
                int uniqueWidth = (int) ((double) uniqueReached / totalObjects * width);
                if (uniqueWidth > 0) {
                    g.setColor(uniqueProgressColor);
                    g.fillRect(0, 0, uniqueWidth, height);
                }

                // Segment 2: Non-unique objects reached (continues from unique segment)
                int nonUniqueWidth = (int) ((double) nonUniqueReached / totalObjects * width);
                if (nonUniqueWidth > 0) {
                    g.setColor(nonUniqueProgressColor);
                    g.fillRect(uniqueWidth, 0, nonUniqueWidth, height);
                }
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
                publish(new TreeUpdate(TreeUpdateType.STATUS, "Getting database connection..."));

                try {
                    // Get the shared in-memory database container
                    ExtObjectContainer container = DODatabaseService.getInstance().getContainer();

                    if (container == null || container.ext().isClosed()) {
                        throw new IllegalStateException("No database is currently open.");
                    }

                    publish(new TreeUpdate(TreeUpdateType.STATUS, "Using shared database connection"));

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

                    // Note: We do NOT close the container here as it's a shared resource managed by
                    // DODatabaseService

                } catch (Exception e) {
                    throw e; // Propagate the exception to doInBackground's caller
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
     * Adds a set of object IDs to a class's reachedObjectIds array.
     * Only IDs that exist in uniqueObjectIds are added (to avoid counting
     * duplicates).
     */
    private void addIdsToReachedList(DOSchemaClass schemaClass, Set<Long> idsToAdd) {
        long[] currentReachedIds = schemaClass.reachedObjectIds;
        if (currentReachedIds == null) {
            currentReachedIds = new long[0];
        }

        // Create a set of unique IDs for this class (only these should be counted as
        // reached)
        Set<Long> uniqueIds = new HashSet<>();
        if (schemaClass.uniqueObjectIds != null) {
            for (long id : schemaClass.uniqueObjectIds) {
                uniqueIds.add(id);
            }
        }

        // Create a set of existing reached IDs to avoid duplicates
        Set<Long> existingIds = new HashSet<>();
        for (long id : currentReachedIds) {
            existingIds.add(id);
        }

        // Add new IDs that are:
        // 1. In the uniqueObjectIds list (not duplicates)
        // 2. Not already in reachedObjectIds
        Set<Long> newIds = new HashSet<>();
        int skippedDuplicates = 0;
        for (long id : idsToAdd) {
            if (!uniqueIds.contains(id)) {
                // Skip IDs that are not in the unique list (they're duplicates)
                skippedDuplicates++;
                continue;
            }
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
                    " (was " + currentReachedIds.length + ", now " + combinedIds.length +
                    ", skipped " + skippedDuplicates + " duplicate object instances)");
        } else if (skippedDuplicates > 0) {
            System.out.println("Skipped " + skippedDuplicates +
                    " duplicate object instances for class " + schemaClass.source);
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
     * Export all object IDs from the database to a tab-delimited file.
     * One class per line: class name, then all object IDs.
     */
    private void exportAllObjectIds() {
        // Show options dialog
        JDialog optionsDialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Export Options", true);
        JPanel optionsPanel = new JPanel(new BorderLayout(10, 10));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new javax.swing.BoxLayout(checkboxPanel, javax.swing.BoxLayout.Y_AXIS));

        javax.swing.JCheckBox cbAllClasses = new javax.swing.JCheckBox("Export all object IDs of all classes", true);
        javax.swing.JCheckBox cbCollections = new javax.swing.JCheckBox(
                "Export children IDs of all collections (including Vectors)", true);
        javax.swing.JCheckBox cbFieldObjects = new javax.swing.JCheckBox("Export field objects", true);

        checkboxPanel.add(cbAllClasses);
        checkboxPanel.add(javax.swing.Box.createVerticalStrut(5));
        checkboxPanel.add(cbCollections);
        checkboxPanel.add(javax.swing.Box.createVerticalStrut(5));
        checkboxPanel.add(cbFieldObjects);
        checkboxPanel.add(javax.swing.Box.createVerticalStrut(10));

        // Class filters
        javax.swing.JCheckBox cbIncludeEntite = new javax.swing.JCheckBox("Include gest.gen.Entite", false);
        javax.swing.JCheckBox cbIncludeIDEntite = new javax.swing.JCheckBox("Include gest.gen.IDEntite", false);
        javax.swing.JCheckBox cbIncludeIDEntiteDescendants = new javax.swing.JCheckBox(
                "Include descendants of IDEntite", false);

        checkboxPanel.add(cbIncludeEntite);
        checkboxPanel.add(javax.swing.Box.createVerticalStrut(5));
        checkboxPanel.add(cbIncludeIDEntite);
        checkboxPanel.add(javax.swing.Box.createVerticalStrut(5));
        checkboxPanel.add(cbIncludeIDEntiteDescendants);

        optionsPanel.add(checkboxPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        final boolean[] confirmed = { false };

        okButton.addActionListener(e -> {
            confirmed[0] = true;
            optionsDialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            optionsDialog.dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        optionsPanel.add(buttonPanel, BorderLayout.SOUTH);

        optionsDialog.setContentPane(optionsPanel);
        optionsDialog.pack();
        optionsDialog.setLocationRelativeTo(this);
        optionsDialog.setVisible(true);

        // If user cancelled, return
        if (!confirmed[0]) {
            return;
        }

        // Get selected options
        final boolean exportAllClasses = cbAllClasses.isSelected();
        final boolean exportCollections = cbCollections.isSelected();
        final boolean exportFieldObjects = cbFieldObjects.isSelected();
        final boolean includeEntite = cbIncludeEntite.isSelected();
        final boolean includeIDEntite = cbIncludeIDEntite.isSelected();
        final boolean includeIDEntiteDescendants = cbIncludeIDEntiteDescendants.isSelected();

        // Create processing dialog
        JDialog processingDialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Processing", true);
        JPanel dialogPanel = new JPanel(new BorderLayout(10, 10));
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel statusLabel = new JLabel("Exporting object IDs...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dialogPanel.add(statusLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        dialogPanel.add(progressBar, BorderLayout.SOUTH);

        processingDialog.setContentPane(dialogPanel);
        processingDialog.setSize(400, 120);
        processingDialog.setLocationRelativeTo(this);
        processingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Run export in background
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                // Get database container
                ExtObjectContainer container = DODatabaseService.getInstance().getContainer();
                if (container == null || container.ext().isClosed()) {
                    throw new IllegalStateException("No database is currently open.");
                }

                // Determine database folder name from database path
                String dbFolder = "default";
                if (databasePath != null) {
                    java.nio.file.Path dbPath = java.nio.file.Paths.get(databasePath);
                    java.nio.file.Path parent = dbPath.getParent();
                    if (parent != null) {
                        dbFolder = parent.getFileName().toString();
                    }
                }

                // Create output directory at top level of database output folder
                java.nio.file.Path outputDir = java.nio.file.Paths.get("output").resolve(dbFolder);
                java.nio.file.Files.createDirectories(outputDir);

                // Prepare output file
                java.nio.file.Path outputFile = outputDir.resolve("all-object-ids.txt");

                try (java.io.PrintWriter writer = new java.io.PrintWriter(
                        java.nio.file.Files.newBufferedWriter(outputFile))) {

                    // Get all stored classes from DB4O
                    StoredClass[] storedClasses = container.storedClasses();

                    // Sort classes by name for consistent output
                    java.util.Arrays.sort(storedClasses, (a, b) -> a.getName().compareTo(b.getName()));

                    // Helper to check if a class should be skipped based on filters
                    java.util.function.Predicate<String> shouldSkipClass = className -> {
                        // Skip DB4O internal classes
                        if (className.startsWith("com.db4o.")) {
                            return true;
                        }
                        // Check gest.gen.Entite filter
                        if (!includeEntite && "gest.gen.Entite".equals(className)) {
                            return true;
                        }
                        // Check gest.gen.IDEntite filter
                        if (!includeIDEntite && "gest.gen.IDEntite".equals(className)) {
                            return true;
                        }
                        // Check IDEntite descendants filter
                        if (!includeIDEntiteDescendants) {
                            DOSchemaClass schemaClass = findClassInSchemaByName(databaseSchema, className);
                            if (schemaClass != null && schemaClass.isIDEntite(referenceSchema)) {
                                return true;
                            }
                        }
                        return false;
                    };

                    // Export all class IDs if selected
                    if (exportAllClasses) {
                        for (StoredClass storedClass : storedClasses) {
                            String className = storedClass.getName();

                            if (shouldSkipClass.test(className)) {
                                continue;
                            }

                            // Get all object IDs for this class
                            long[] objectIds = storedClass.getIDs();

                            // Write class name
                            writer.print(className);

                            // Write all object IDs separated by tabs
                            if (objectIds != null && objectIds.length > 0) {
                                for (long id : objectIds) {
                                    writer.print("\t");
                                    writer.print(id);
                                }
                            }

                            writer.println();
                        }
                    }

                    // Export all collection contents if selected (including Vectors)
                    if (exportCollections) {
                        for (StoredClass storedClass : storedClasses) {
                            String className = storedClass.getName();

                            // Skip filtered classes
                            if (shouldSkipClass.test(className)) {
                                continue;
                            }

                            // Check if class is a Collection
                            try {
                                Class<?> clazz = Class.forName(className);
                                if (java.util.Collection.class.isAssignableFrom(clazz)) {
                                    long[] collectionIds = storedClass.getIDs();
                                    if (collectionIds != null && collectionIds.length > 0) {
                                        for (long collectionId : collectionIds) {
                                            try {
                                                Object obj = container.ext().getByID(collectionId);
                                                if (obj != null) {
                                                    // Activate the object to turn it into a real Collection
                                                    container.activate(obj, 1);

                                                    if (obj instanceof java.util.Collection) {
                                                        writer.print(className + "[" + collectionId + "]");

                                                        java.util.Collection<?> collection = (java.util.Collection<?>) obj;
                                                        for (Object element : collection) {
                                                            if (element != null) {
                                                                try {
                                                                    long elementId = container.getID(element);
                                                                    if (elementId > 0) {
                                                                        writer.print("\t");
                                                                        writer.print(elementId);
                                                                    }
                                                                } catch (Exception e) {
                                                                    // Skip elements without IDs
                                                                }
                                                            }
                                                        }
                                                        writer.println();
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // Skip collections that can't be loaded
                                            }
                                        }
                                    }
                                }
                            } catch (ClassNotFoundException e) {
                                // Skip classes that can't be loaded
                            }
                        }
                    }

                    // Export field objects if selected
                    if (exportFieldObjects) {
                        for (StoredClass storedClass : storedClasses) {
                            String className = storedClass.getName();

                            if (shouldSkipClass.test(className)) {
                                continue;
                            }

                            long[] objectIds = storedClass.getIDs();
                            if (objectIds != null && objectIds.length > 0) {
                                for (long objectId : objectIds) {
                                    try {
                                        Object obj = container.ext().getByID(objectId);
                                        if (obj != null) {
                                            writer.print(className + "[" + objectId + "]");

                                            // Handle GenericObject (most DB4O objects)
                                            if (obj instanceof GenericObject) {
                                                GenericObject genericObj = (GenericObject) obj;
                                                StoredField[] fields = storedClass.getStoredFields();
                                                if (fields != null) {
                                                    for (StoredField field : fields) {
                                                        try {
                                                            Object fieldValue = field.get(genericObj);
                                                            if (fieldValue != null) {
                                                                // Handle arrays - iterate through elements
                                                                if (fieldValue.getClass().isArray()) {
                                                                    try {
                                                                        int length = java.lang.reflect.Array
                                                                                .getLength(fieldValue);
                                                                        for (int i = 0; i < length; i++) {
                                                                            Object element = java.lang.reflect.Array
                                                                                    .get(fieldValue, i);
                                                                            if (element != null) {
                                                                                try {
                                                                                    long elementId = container
                                                                                            .getID(element);
                                                                                    if (elementId > 0) {
                                                                                        writer.print("\t");
                                                                                        writer.print(elementId);
                                                                                    }
                                                                                } catch (Exception e) {
                                                                                    // Skip elements without IDs
                                                                                }
                                                                            }
                                                                        }
                                                                    } catch (Exception e) {
                                                                        // Skip arrays that can't be accessed
                                                                    }
                                                                }
                                                                // Handle single object references
                                                                else {
                                                                    try {
                                                                        long fieldObjectId = container
                                                                                .getID(fieldValue);
                                                                        if (fieldObjectId > 0) {
                                                                            writer.print("\t");
                                                                            writer.print(fieldObjectId);
                                                                        }
                                                                    } catch (Exception e) {
                                                                        // Skip fields without IDs (primitives, etc.)
                                                                    }
                                                                }
                                                            }
                                                        } catch (Exception e) {
                                                            // Skip fields that can't be accessed
                                                        }
                                                    }
                                                }
                                            }
                                            writer.println();
                                        }
                                    } catch (Exception e) {
                                        // Skip objects that can't be loaded
                                    }
                                }
                            }
                        }
                    }
                }

                return outputFile.toAbsolutePath().toString();
            }

            @Override
            protected void done() {
                // Close processing dialog
                processingDialog.dispose();

                try {
                    // Get the result and show success message
                    String filePath = get();
                    JOptionPane.showMessageDialog(MigrationCoveragePanel.this,
                            "All object IDs exported successfully to:\n" + filePath,
                            "Export Successful",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    // Show error message
                    JOptionPane.showMessageDialog(MigrationCoveragePanel.this,
                            "Error exporting object IDs: " + ex.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        };

        // Start the worker
        worker.execute();

        // Show the processing dialog (this blocks until worker calls dispose)
        processingDialog.setVisible(true);
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
     * Called after export to populate reachedObjectIds based on what was actually
     * exported.
     * This method adds exported objects to ALL classes in their inheritance
     * hierarchy.
     * For example, exporting VectRechID #1234 will mark HVector #1234 and Vector
     * #1234 as reached too.
     * 
     * @param exportedClasses   Map of class name to number of exported objects
     * @param exportedObjectIds Map of class name to list of actual exported object
     *                          IDs
     */
    public void updateExportedCounts(Map<String, Integer> exportedClasses, Map<String, List<Long>> exportedObjectIds) {
        if (exportedClasses == null || exportedClasses.isEmpty()) {
            return;
        }

        // Get database container for class lookups
        ExtObjectContainer container = DODatabaseService.getInstance().getContainer();
        if (container == null) {
            System.err.println("Cannot update exported counts: no database container available");
            return;
        }

        // Collect all exported object IDs across all classes
        Set<Long> allExportedIds = new HashSet<>();
        if (exportedObjectIds != null) {
            for (List<Long> idList : exportedObjectIds.values()) {
                if (idList != null) {
                    allExportedIds.addAll(idList);
                }
            }
        }

        // Use ReachResultAggregator to add object IDs to all classes in their
        // inheritance hierarchy
        ReachResultAggregator aggregator = new ReachResultAggregator(databaseSchema);
        Map<String, Set<Long>> reachedByClass = aggregator.aggregateReachedObjects(allExportedIds, container);

        // Update reachedObjectIds for each class in the database schema
        for (Map.Entry<String, Set<Long>> entry : reachedByClass.entrySet()) {
            String className = entry.getKey();
            Set<Long> objectIdSet = entry.getValue();

            // Find the class in database schema
            DOSchemaClass dbClass = findClassInSchema(databaseSchema, className);
            if (dbClass != null && objectIdSet != null && !objectIdSet.isEmpty()) {
                // Convert Set<Long> to long[] array
                long[] reachedIds = new long[objectIdSet.size()];
                int i = 0;
                for (Long id : objectIdSet) {
                    reachedIds[i++] = id;
                }
                dbClass.reachedObjectIds = reachedIds;
            }
        }

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

    /**
     * Opens the ID Tracer dialog for tracing object containment relationships.
     */
    private void openIdTracer() {
        IDTracerDialog dialog = new IDTracerDialog();
        dialog.setVisible(true);
    }
}
