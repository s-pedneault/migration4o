package migration4o.ui.panels.database_panels.cost_panel;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.schema.DOSchemaService;
import migration4o.schema.modules.DOModuleService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Panel for displaying processing cost analysis for the migration. Shows all
 * classes with a unit cost for the selected price list, with per-class
 * subtotals based on the actual object count in the database, and a grand
 * total.
 */
public class CostPanel extends JPanel {

    private final DODatabase database;

    private JComboBox<String> priceListCombo;
    private JTable costTable;
    private CostTableModel tableModel;
    private JLabel totalLabel;

    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final NumberFormat INT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public CostPanel(DODatabase database) {
        this.database = database;
        initializeUI();
        refresh();
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private void initializeUI() {
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // -- Top: price list selector --
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbarPanel.add(new JLabel("Price list:"));
        priceListCombo = new JComboBox<>();
        priceListCombo.setPreferredSize(new Dimension(200, 26));
        priceListCombo.addActionListener(e -> refreshTable());
        toolbarPanel.add(priceListCombo);
        add(toolbarPanel, BorderLayout.NORTH);

        // -- Centre: table --
        tableModel = new CostTableModel();
        costTable = new JTable(tableModel);
        costTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        costTable.getTableHeader().setReorderingAllowed(false);
        costTable.setShowHorizontalLines(true);
        costTable.setShowVerticalLines(false);
        costTable.setGridColor(new Color(230, 230, 230));
        costTable.setRowHeight(22);
        costTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Right-align numeric columns
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        costTable.getColumnModel().getColumn(1).setCellRenderer(rightRenderer); // Unit
                                                                                // Cost
        costTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer); // Units
        costTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer); // Subtotal

        // Column widths
        costTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        costTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        costTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        costTable.getColumnModel().getColumn(3).setPreferredWidth(110);

        JScrollPane scrollPane = new JScrollPane(costTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        add(scrollPane, BorderLayout.CENTER);

        // -- Bottom: grand total + Copy button --
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));

        totalLabel = new JLabel("Grand total: \u2014");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 13));
        bottomPanel.add(totalLabel, BorderLayout.WEST);

        JButton copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy the table as tab-delimited text");
        copyButton.addActionListener(e -> copyToClipboard());
        JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyPanel.add(copyButton);
        bottomPanel.add(copyPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the price-list combo and refreshes the table. Call this whenever
     * the underlying module data may have changed.
     */
    public void refresh() {
        // Collect all unique non-default price-list keys from all module class
        // configs
        Set<String> nonDefaultKeys = new LinkedHashSet<>();
        collectNonDefaultPriceListKeys(DOModuleService.getInstance().getModules(), nonDefaultKeys);

        // Remember selected item so we can restore it if possible
        String previousSelection = (String) priceListCombo.getSelectedItem();

        priceListCombo.removeAllItems();
        // "Default" represents the empty-string key; always shown first
        priceListCombo.addItem("Default");
        for (String key : nonDefaultKeys) {
            priceListCombo.addItem(key);
        }

        // Restore previous selection when it still exists
        if (previousSelection != null) {
            for (int i = 0; i < priceListCombo.getItemCount(); i++) {
                if (previousSelection.equals(priceListCombo.getItemAt(i))) {
                    priceListCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        refreshTable();
    }

    /**
     * Recursively gathers all non-empty price-list keys found in class configs.
     */
    private void collectNonDefaultPriceListKeys(List<DOSchemaModule> modules, Set<String> keys) {
        for (DOSchemaModule module : modules) {
            for (ClassExportConfig config : module.classConfigs) {
                for (String key : config.getUnitCosts().keySet()) {
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
            }
            collectNonDefaultPriceListKeys(module.children, keys);
        }
    }

    /** Rebuilds the table rows for the currently selected price list. */
    private void refreshTable() {
        String selectedDisplay = (String) priceListCombo.getSelectedItem();
        // Map "Default" display name back to the empty raw key
        String priceListKey = "Default".equals(selectedDisplay) ? "" : (selectedDisplay != null ? selectedDisplay : "");

        List<CostEntry> entries = buildEntries(priceListKey);
        tableModel.setEntries(entries);

        // Grand total
        float total = 0f;
        for (CostEntry entry : entries) {
            total += entry.subtotal;
        }
        totalLabel.setText("Grand total: " + MONEY_FORMAT.format(total));
    }

    /** Builds the list of cost entries for the given price-list key. */
    private List<CostEntry> buildEntries(String priceListKey) {
        List<CostEntry> entries = new ArrayList<>();
        collectEntries(DOModuleService.getInstance().getModules(), priceListKey, entries);
        return entries;
    }

    /**
     * Recursively walks modules and adds an entry for every class config whose
     * unit cost for the selected price list is greater than zero.
     */
    private void collectEntries(List<DOSchemaModule> modules, String priceListKey, List<CostEntry> entries) {
        for (DOSchemaModule module : modules) {
            for (ClassExportConfig config : module.classConfigs) {
                float unitCost = config.getUnitCost(priceListKey);
                if (unitCost > 0f) {
                    int units = getObjectCount(config);
                    float subtotal = unitCost * units;

                    // Use config title first (user-specified per-config title),
                    // then reference schema class title, then description, then
                    // destination file name
                    String displayName = config.hasTitle() ? config.getTitle() : null;
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = getReferenceClassTitle(config.getClassName());
                    }
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = config.getDescription();
                    }
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = config.getDestinationFileName();
                    }

                    entries.add(new CostEntry(displayName, config.getClassName(), unitCost, units, subtotal));
                }
            }
            collectEntries(module.children, priceListKey, entries);
        }
    }

    /**
     * Returns the title of a class from the reference schema, or null if not
     * found.
     */
    private String getReferenceClassTitle(String className) {
        DOSchema refSchema = DOSchemaService.getInstance().getReferenceSchema();
        if (refSchema == null) {
            return null;
        }
        DOSchemaClass cls = refSchema.findClassByName(className);
        return (cls != null && cls.attributes.title != null && !cls.attributes.title.isEmpty()) ? cls.attributes.title : null;
    }

    /**
     * Returns the object count for a config, applying criteria filtering when
     * the config has criteria defined. This prevents over-billing when the same
     * class appears multiple times with different criteria.
     */
    private int getObjectCount(ClassExportConfig config) {
        if (database == null) {
            return 0;
        }
        DODatabaseClass dbClass = database.findClassByName(config.getClassName());
        if (dbClass == null || dbClass.objects.objectIds == null || dbClass.objects.objectIds.length == 0) {
            return 0;
        }

        // When the config has criteria and we have a database delegate,
        // count only the objects that match all criteria
        if (config.hasCriteria() && dbClass.delegate != null && !dbClass.delegate.isClosed()) {
            return config.countMatchingObjects(dbClass.delegate, dbClass.objects.objectIds);
        }

        return dbClass.objects.objectIds.length;
    }

    // -------------------------------------------------------------------------
    // Copy to clipboard
    // -------------------------------------------------------------------------

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Class\tUnit Cost\tUnits\tSubtotal\n");

        // Data rows
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            sb.append(tableModel.getValueAt(row, 0)).append('\t').append(tableModel.getValueAt(row, 1)).append('\t').append(tableModel.getValueAt(row, 2)).append('\t').append(tableModel.getValueAt(row, 3)).append('\n');
        }

        // Grand total line
        sb.append("\t\t\t").append(totalLabel.getText());

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(sb.toString()), null);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Immutable value object representing a single table row. */
    private static final class CostEntry {
        final String displayName;
        final String className;
        final float unitCost;
        final int units;
        final float subtotal;

        CostEntry(String displayName, String className, float unitCost, int units, float subtotal) {
            this.displayName = displayName;
            this.className = className;
            this.unitCost = unitCost;
            this.units = units;
            this.subtotal = subtotal;
        }
    }

    /** Table model for the cost rows. */
    private final class CostTableModel extends AbstractTableModel {

        private final String[] COLUMNS = { "Class", "Unit Cost", "Units", "Subtotal" };
        private List<CostEntry> entries = new ArrayList<>();

        void setEntries(List<CostEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            CostEntry e = entries.get(row);
            switch (col) {
            case 0:
                return e.displayName;
            case 1:
                return MONEY_FORMAT.format(e.unitCost);
            case 2:
                return INT_FORMAT.format(e.units);
            case 3:
                return MONEY_FORMAT.format(e.subtotal);
            default:
                return "";
            }
        }
    }
}
