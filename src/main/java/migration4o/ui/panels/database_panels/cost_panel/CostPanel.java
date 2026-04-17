package migration4o.ui.panels.database_panels.cost_panel;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseDelegate;
import migration4o.migration.OrganizationDetectionService;
import migration4o.migration.OrganizationInfo;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaConstants;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.schema.DOSchemaService;
import migration4o.schema.modules.DOModuleService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Panel for displaying processing cost analysis for the migration. Shows all classes with a unit cost for the selected price list, broken down by organization. Columns: Class | Unit Cost | Général (units) | Subtotal | {Org1 units} | Subtotal | ... | Total | Total cost.
 */
public class CostPanel extends JPanel {

    private static final Logger log = LogManager.getLogger(CostPanel.class);

    private final DODatabase database;

    private JComboBox<String> priceListCombo;
    private JTable costTable;
    private CostTableModel tableModel;
    private JLabel totalLabel;

    /** Organizations detected in the database, cached on refresh. */
    private List<OrganizationInfo> organizations = List.of();
    /** Column layout descriptor, rebuilt when organizations change. */
    private CostColumnLayout columnLayout = new CostColumnLayout(List.of());

    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final NumberFormat INT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private static final Color GRAND_TOTAL_BG = new Color(240, 240, 240);

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

        // -- Centre: table (model rebuilt on each refresh) --
        tableModel = new CostTableModel();
        costTable = new JTable(tableModel);
        costTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        costTable.getTableHeader().setReorderingAllowed(false);
        costTable.setShowHorizontalLines(true);
        costTable.setShowVerticalLines(true);
        costTable.setGridColor(new Color(220, 220, 220));
        costTable.setRowHeight(22);
        costTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(costTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
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

    /** Applies column widths and cell renderers after a structural change. */
    private void applyColumnRenderers() {
        TableColumnModel cm = costTable.getColumnModel();
        int colCount = cm.getColumnCount();

        for (int c = 0; c < colCount; c++) {
            TableColumn tc = cm.getColumn(c);
            if (c == 0) {
                tc.setPreferredWidth(300);
            } else {
                tc.setPreferredWidth(100);
                tc.setMinWidth(60);
                tc.setMaxWidth(160);
            }
        }

        costTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column >= 1) {
                    setHorizontalAlignment(JLabel.RIGHT);
                } else {
                    setHorizontalAlignment(JLabel.LEFT);
                }
                if (!isSelected) {
                    boolean isGrandTotalRow = row == table.getRowCount() - 1 && tableModel.isGrandTotalRow(row);
                    if (isGrandTotalRow) {
                        c.setBackground(GRAND_TOTAL_BG);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setBackground(columnLayout.columnBackground(column));
                        c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    }
                }
                return c;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the price-list combo and refreshes the table. Call this whenever the underlying module data may have changed.
     */
    public void refresh() {
        organizations = OrganizationDetectionService.detectOrganizations(database);
        columnLayout = new CostColumnLayout(organizations);

        Set<String> nonDefaultKeys = new LinkedHashSet<>();
        collectNonDefaultPriceListKeys(DOModuleService.getInstance().getModules(), nonDefaultKeys);

        String previousSelection = (String) priceListCombo.getSelectedItem();

        priceListCombo.removeAllItems();
        priceListCombo.addItem("Default");
        for (String key : nonDefaultKeys) {
            priceListCombo.addItem(key);
        }

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

    private void refreshTable() {
        String selectedDisplay = (String) priceListCombo.getSelectedItem();
        String priceListKey = "Default".equals(selectedDisplay) ? "" : (selectedDisplay != null ? selectedDisplay : "");

        List<CostEntry> entries = buildEntries(priceListKey);
        tableModel.setData(entries, columnLayout);
        applyColumnRenderers();

        float grandTotal = 0f;
        for (CostEntry e : entries) {
            grandTotal += e.totalCost();
        }
        totalLabel.setText("Grand total: " + MONEY_FORMAT.format(grandTotal));
    }

    private List<CostEntry> buildEntries(String priceListKey) {
        List<CostEntry> entries = new ArrayList<>();
        DOSchema refSchema = DOSchemaService.getInstance().getReferenceSchema();
        collectEntries(DOModuleService.getInstance().getModules(), priceListKey, refSchema, entries);
        return entries;
    }

    private void collectEntries(List<DOSchemaModule> modules, String priceListKey, DOSchema refSchema, List<CostEntry> entries) {
        for (DOSchemaModule module : modules) {
            for (ClassExportConfig config : module.classConfigs) {
                float unitCost = config.getUnitCost(priceListKey);
                if (unitCost > 0f) {
                    String displayName = resolveDisplayName(config, refSchema);

                    int[] orgUnits = new int[organizations.size()];
                    int generalUnits = countObjectsByOrganization(config, refSchema, orgUnits);

                    int totalUnits = generalUnits;
                    for (int u : orgUnits)
                        totalUnits += u;

                    entries.add(new CostEntry(displayName, unitCost, generalUnits, orgUnits, totalUnits, unitCost * totalUnits));
                }
            }
            collectEntries(module.children, priceListKey, refSchema, entries);
        }
    }

    /**
     * Resolves the best display name for a class config: config title → reference schema class title → description → destination file name.
     */
    private String resolveDisplayName(ClassExportConfig config, DOSchema refSchema) {
        if (config.hasTitle() && !config.getTitle().isEmpty()) {
            return config.getTitle();
        }
        String schemaTitle = getReferenceClassTitle(config.getClassName(), refSchema);
        if (schemaTitle != null) {
            return schemaTitle;
        }
        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return config.getDestinationFileName();
    }

    private String getReferenceClassTitle(String className, DOSchema refSchema) {
        if (refSchema == null)
            return null;
        DOSchemaClass cls = refSchema.findClassByName(className);
        return (cls != null && cls.attributes.title != null && !cls.attributes.title.isEmpty()) ? cls.attributes.title : null;
    }

    /**
     * Counts objects split by organization. Fills {@code orgUnits} (one slot per organization) and returns the general-data count (IDSSI&lt;0 or non-multi-org classes).
     */
    private int countObjectsByOrganization(ClassExportConfig config, DOSchema refSchema, int[] orgUnits) {
        if (database == null)
            return 0;

        DODatabaseClass dbClass = database.findClassByName(config.getClassName());
        if (dbClass == null || dbClass.objects.objectIds == null || dbClass.objects.objectIds.length == 0) {
            return 0;
        }

        boolean multiOrg = isMultiOrganization(config.getClassName(), refSchema);

        Map<Integer, Integer> idSSIToIndex = buildIdSSILookup();

        DODatabaseDelegate delegate = dbClass.delegate;
        boolean useCriteria = config.hasCriteria() && delegate != null && !delegate.isClosed();

        int generalCount = 0;
        for (long objectId : dbClass.objects.objectIds) {
            Object obj;
            try {
                obj = delegate.getByID(objectId);
            } catch (Exception e) {
                log.warn("Failed to load object {} of class {}: {}", objectId, config.getClassName(), e.getMessage());
                continue;
            }
            if (obj == null)
                continue;

            if (useCriteria && !matchesCriteria(config, delegate, obj)) {
                continue;
            }

            if (!multiOrg) {
                generalCount++;
                continue;
            }

            generalCount += classifyByOrganization(delegate, obj, idSSIToIndex, orgUnits);
        }
        return generalCount;
    }

    private boolean isMultiOrganization(String className, DOSchema refSchema) {
        if (refSchema == null)
            return false;
        DOSchemaClass schemaClass = refSchema.findClassByName(className);
        return schemaClass != null && schemaClass.isMultiOrganization();
    }

    private Map<Integer, Integer> buildIdSSILookup() {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < organizations.size(); i++) {
            map.put(organizations.get(i).idSSI(), i);
        }
        return map;
    }

    private boolean matchesCriteria(ClassExportConfig config, DODatabaseDelegate delegate, Object obj) {
        if (obj instanceof com.db4o.reflect.generic.GenericObject) {
            return config.matchesAllCriteria(delegate, (com.db4o.reflect.generic.GenericObject) obj);
        }
        return false;
    }

    /**
     * Classifies a single object into its organization bucket. Returns 1 if the object is general data, 0 otherwise (org bucket incremented directly).
     */
    private int classifyByOrganization(DODatabaseDelegate delegate, Object obj, Map<Integer, Integer> idSSIToIndex, int[] orgUnits) {
        Object idValue = delegate.getStoredFieldValue(obj, DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME);
        if (!(idValue instanceof Number)) {
            return 1; // No IDSSI → general
        }
        int idSSI = ((Number) idValue).intValue();
        if (idSSI < 0) {
            return 1; // Negative IDSSI → general
        }
        Integer idx = idSSIToIndex.get(idSSI);
        if (idx != null) {
            orgUnits[idx]++;
            return 0;
        }
        return 1; // Unknown org → general
    }

    // -------------------------------------------------------------------------
    // Copy to clipboard
    // -------------------------------------------------------------------------

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder();

        for (int c = 0; c < tableModel.getColumnCount(); c++) {
            if (c > 0)
                sb.append('\t');
            sb.append(tableModel.getColumnName(c));
        }
        sb.append('\n');

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                if (c > 0)
                    sb.append('\t');
                Object val = tableModel.getValueAt(row, c);
                sb.append(val != null ? val : "");
            }
            sb.append('\n');
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(sb.toString()), null);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Value object representing a single data row. */
    record CostEntry(String displayName, float unitCost, int generalUnits, int[] orgUnits, int totalUnits, float totalCost) {
    }

    /** Table model with dynamic columns based on detected organizations. */
    private static final class CostTableModel extends AbstractTableModel {

        private List<CostEntry> entries = new ArrayList<>();
        private CostColumnLayout layout = new CostColumnLayout(List.of());
        private boolean hasGrandTotal = false;

        // Grand total accumulators
        private float grandTotalGeneralSubtotal;
        private float[] grandTotalOrgSubtotals = new float[0];
        private float grandTotalCost;
        private int grandTotalUnits;

        void setData(List<CostEntry> entries, CostColumnLayout layout) {
            this.entries = entries;
            this.layout = layout;

            grandTotalGeneralSubtotal = 0f;
            grandTotalOrgSubtotals = new float[layout.pairCount() - 2]; // org pairs only
            grandTotalCost = 0f;
            grandTotalUnits = 0;
            for (CostEntry e : entries) {
                grandTotalGeneralSubtotal += e.unitCost * e.generalUnits;
                for (int i = 0; i < grandTotalOrgSubtotals.length; i++) {
                    grandTotalOrgSubtotals[i] += e.unitCost * e.orgUnits[i];
                }
                grandTotalCost += e.totalCost;
                grandTotalUnits += e.totalUnits;
            }
            hasGrandTotal = !entries.isEmpty();

            fireTableStructureChanged();
        }

        boolean isGrandTotalRow(int row) {
            return hasGrandTotal && row == entries.size();
        }

        @Override
        public int getRowCount() {
            return entries.size() + (hasGrandTotal ? 1 : 0);
        }

        @Override
        public int getColumnCount() {
            return layout.columnCount();
        }

        @Override
        public String getColumnName(int col) {
            return layout.columnName(col);
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (isGrandTotalRow(row)) {
                return getGrandTotalValue(col);
            }
            return getEntryValue(entries.get(row), col);
        }

        private Object getEntryValue(CostEntry e, int col) {
            if (col == 0)
                return e.displayName;
            if (col == 1)
                return MONEY_FORMAT.format(e.unitCost);

            CostColumnLayout.PairPosition pos = layout.resolvePair(col);
            if (pos.isGeneralPair()) {
                return pos.isSubtotal() ? MONEY_FORMAT.format(e.unitCost * e.generalUnits) : INT_FORMAT.format(e.generalUnits);
            } else if (pos.isOrganizationPair()) {
                int units = e.orgUnits[pos.organizationIndex()];
                return pos.isSubtotal() ? MONEY_FORMAT.format(e.unitCost * units) : INT_FORMAT.format(units);
            } else {
                return pos.isSubtotal() ? MONEY_FORMAT.format(e.totalCost) : INT_FORMAT.format(e.totalUnits);
            }
        }

        private Object getGrandTotalValue(int col) {
            if (col == 0)
                return "Grand total";
            if (col == 1)
                return "";

            CostColumnLayout.PairPosition pos = layout.resolvePair(col);
            if (pos.isGeneralPair()) {
                return pos.isSubtotal() ? MONEY_FORMAT.format(grandTotalGeneralSubtotal) : "";
            } else if (pos.isOrganizationPair()) {
                return pos.isSubtotal() ? MONEY_FORMAT.format(grandTotalOrgSubtotals[pos.organizationIndex()]) : "";
            } else {
                return pos.isSubtotal() ? MONEY_FORMAT.format(grandTotalCost) : INT_FORMAT.format(grandTotalUnits);
            }
        }
    }
}
