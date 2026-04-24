package migration4o.ui.panels.database_panels.cost_panel;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseDelegate;
import migration4o.migration.OrganizationDetectionService;
import migration4o.migration.OrganizationInfo;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaConstants;
import migration4o.models.ui.ClassExportConfig;
import migration4o.schema.DOSchemaService;
import migration4o.schema.indicators.ProcessingIndicatorService;
import migration4o.schema.indicators.ProcessingIndicatorService.IndicatorClass;
import migration4o.schema.indicators.ProcessingIndicatorService.ProcessingIndicator;

import com.db4o.reflect.generic.GenericObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * Panel that shows one row per processing indicator class (configured in the "Processing indicators" panel), with the object count from the database. For multi-organisation databases the count is split by organisation.
 *
 * <p>
 * Copy options:
 * <ul>
 * <li>Right-click a column header → copy that column's values or header+values.</li>
 * <li>"Copy table" button → copies the entire table as tab-separated text.</li>
 * </ul>
 */
public class CostPanel extends JPanel {

    private static final Logger log = LogManager.getLogger(CostPanel.class);
    private static final NumberFormat INT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final Color GRAND_TOTAL_BG = new Color(240, 240, 240);

    private final DODatabase database;
    private JTable table;
    private IndicatorTableModel tableModel;

    /** Organizations detected in the database, cached on refresh. */
    private List<OrganizationInfo> organizations = List.of();

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

        tableModel = new IndicatorTableModel();
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setGridColor(new Color(220, 220, 220));
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Right-click on column header → copy menu
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger())
                    showColumnCopyMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger())
                    showColumnCopyMenu(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: hint label + Copy table button
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));

        JLabel hint = new JLabel("Right-click a column header to copy a single column.");
        hint.setForeground(Color.GRAY);
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC));
        bottomPanel.add(hint, BorderLayout.WEST);

        JButton copyTableBtn = new JButton("Copy table");
        copyTableBtn.setToolTipText("Copy the entire table as tab-separated text");
        copyTableBtn.addActionListener(e -> copyTable());
        JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyPanel.add(copyTableBtn);
        bottomPanel.add(copyPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void applyColumnRenderers() {
        var cm = table.getColumnModel();
        int colCount = cm.getColumnCount();
        for (int c = 0; c < colCount; c++) {
            var tc = cm.getColumn(c);
            if (c == 0) {
                tc.setPreferredWidth(300);
            } else {
                tc.setPreferredWidth(100);
                tc.setMinWidth(60);
                tc.setMaxWidth(160);
            }
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(column == 0 ? JLabel.LEFT : JLabel.RIGHT);
                if (!isSelected) {
                    if (tableModel.isGrandTotalRow(row)) {
                        c.setBackground(GRAND_TOTAL_BG);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setBackground(Color.WHITE);
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

    public void refresh() {
        organizations = OrganizationDetectionService.detectOrganizations(database);
        List<ProcessingIndicator> indicators = ProcessingIndicatorService.getInstance().getIndicators();
        DOSchema refSchema = DOSchemaService.getInstance().getReferenceSchema();

        List<IndicatorRow> rows = new ArrayList<>();
        for (ProcessingIndicator indicator : indicators) {
            String label = resolveIndicatorLabel(indicator, refSchema);
            int[] orgCounts = new int[organizations.size()];
            int generalCount = 0;
            for (IndicatorClass ic : indicator.classes()) {
                int[] classOrg = new int[organizations.size()];
                int classGeneral = countObjects(ic, refSchema, classOrg);
                generalCount += classGeneral;
                for (int i = 0; i < orgCounts.length; i++)
                    orgCounts[i] += classOrg[i];
            }
            int total = generalCount;
            for (int v : orgCounts)
                total += v;
            rows.add(new IndicatorRow(label, generalCount, orgCounts, total));
        }

        tableModel.setData(rows, organizations);
        applyColumnRenderers();
    }

    private String resolveIndicatorLabel(ProcessingIndicator indicator, DOSchema refSchema) {
        if (indicator.name() != null && !indicator.name().isBlank())
            return indicator.name();
        if (indicator.classes().isEmpty())
            return "(empty)";
        return resolveDisplayName(indicator.classes().get(0).className(), refSchema);
    }

    private String resolveDisplayName(String className, DOSchema refSchema) {
        if (refSchema != null) {
            DOSchemaClass cls = refSchema.findClassByName(className);
            if (cls != null && cls.attributes.title != null && !cls.attributes.title.isEmpty()) {
                return cls.attributes.title;
            }
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private int countObjects(IndicatorClass ic, DOSchema refSchema, int[] orgCounts) {
        String className = ic.className();
        if (database == null)
            return 0;
        DODatabaseClass dbClass = database.findClassByName(className);
        if (dbClass == null || dbClass.objects.objectIds == null || dbClass.objects.objectIds.length == 0)
            return 0;

        boolean hasCriteria = !ic.criteria().isEmpty();
        boolean multiOrg = organizations.size() > 1 && isMultiOrg(className, refSchema);

        // Fast path: no criteria, no org split
        if (!hasCriteria && !multiOrg)
            return dbClass.objects.objectIds.length;

        ClassExportConfig cfg = hasCriteria ? new ClassExportConfig(className, null, ic.criteria()) : null;
        DODatabaseDelegate delegate = dbClass.delegate;

        Map<Integer, Integer> ssiToIdx = null;
        if (multiOrg) {
            ssiToIdx = new HashMap<>();
            for (int i = 0; i < organizations.size(); i++)
                ssiToIdx.put(organizations.get(i).idSSI(), i);
        }

        int generalCount = 0;
        for (long objectId : dbClass.objects.objectIds) {
            try {
                Object obj = delegate != null ? delegate.getByID(objectId) : null;
                if (obj == null)
                    continue;
                if (hasCriteria) {
                    if (!(obj instanceof GenericObject))
                        continue;
                    if (!cfg.matchesAllCriteria(delegate, (GenericObject) obj))
                        continue;
                }
                if (!multiOrg) {
                    generalCount++;
                    continue;
                }
                Object idVal = delegate.getStoredFieldValue(obj, DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME);
                if (!(idVal instanceof Number)) {
                    generalCount++;
                    continue;
                }
                int idSSI = ((Number) idVal).intValue();
                if (idSSI < 0) {
                    generalCount++;
                    continue;
                }
                Integer idx = ssiToIdx.get(idSSI);
                if (idx != null)
                    orgCounts[idx]++;
                else
                    generalCount++;
            } catch (Exception ex) {
                log.warn("Failed to load object {} of {}: {}", objectId, className, ex.getMessage());
            }
        }
        return generalCount;
    }

    private boolean isMultiOrg(String className, DOSchema refSchema) {
        if (refSchema == null)
            return false;
        DOSchemaClass cls = refSchema.findClassByName(className);
        return cls != null && cls.isMultiOrganization();
    }

    // -------------------------------------------------------------------------
    // Copy support
    // -------------------------------------------------------------------------

    private void showColumnCopyMenu(java.awt.event.MouseEvent e) {
        int col = table.getTableHeader().columnAtPoint(e.getPoint());
        if (col < 0)
            return;
        String colName = tableModel.getColumnName(col);

        JPopupMenu popup = new JPopupMenu();

        JMenuItem valuesOnly = new JMenuItem("Copy \"" + colName + "\" values");
        valuesOnly.addActionListener(ev -> copyColumn(col, false));
        popup.add(valuesOnly);

        JMenuItem withHeader = new JMenuItem("Copy \"" + colName + "\" with header");
        withHeader.addActionListener(ev -> copyColumn(col, true));
        popup.add(withHeader);

        popup.show(table.getTableHeader(), e.getX(), e.getY());
    }

    private void copyColumn(int col, boolean includeHeader) {
        StringBuilder sb = new StringBuilder();
        if (includeHeader) {
            sb.append(tableModel.getColumnName(col)).append('\n');
        }
        int dataRows = tableModel.getRowCount() - (tableModel.isGrandTotalRow(tableModel.getRowCount() - 1) ? 1 : 0);
        for (int row = 0; row < dataRows; row++) {
            Object val = tableModel.getValueAt(row, col);
            sb.append(val != null ? val : "").append('\n');
        }
        toClipboard(sb.toString());
    }

    private void copyTable() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < tableModel.getColumnCount(); c++) {
            if (c > 0)
                sb.append('\t');
            sb.append(tableModel.getColumnName(c));
        }
        sb.append('\n');
        int dataRows = tableModel.getRowCount() - (tableModel.isGrandTotalRow(tableModel.getRowCount() - 1) ? 1 : 0);
        for (int row = 0; row < dataRows; row++) {
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                if (c > 0)
                    sb.append('\t');
                Object val = tableModel.getValueAt(row, c);
                sb.append(val != null ? val : "");
            }
            sb.append('\n');
        }
        toClipboard(sb.toString());
    }

    private static void toClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    record IndicatorRow(String label, int generalCount, int[] orgCounts, int total) {
    }

    private static final class IndicatorTableModel extends AbstractTableModel {

        private List<IndicatorRow> rows = new ArrayList<>();
        private List<OrganizationInfo> orgs = List.of();

        void setData(List<IndicatorRow> rows, List<OrganizationInfo> orgs) {
            this.rows = rows;
            this.orgs = orgs;
            fireTableStructureChanged();
        }

        boolean isGrandTotalRow(int row) {
            return !rows.isEmpty() && row == rows.size();
        }

        @Override
        public int getRowCount() {
            return rows.size() + (rows.isEmpty() ? 0 : 1);
        }

        @Override
        public int getColumnCount() {
            // single-org: Class + Count
            // multi-org: Class + Général + org1..N + Total
            return orgs.size() <= 1 ? 2 : 2 + orgs.size() + 1;
        }

        @Override
        public String getColumnName(int col) {
            if (col == 0)
                return "Class";
            if (orgs.size() <= 1)
                return "Count";
            if (col == 1)
                return "Général";
            if (col <= orgs.size() + 1)
                return orgs.get(col - 2).name();
            return "Total";
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (isGrandTotalRow(row)) {
                if (col == 0)
                    return "Total";
                if (orgs.size() <= 1)
                    return INT_FORMAT.format(rows.stream().mapToInt(IndicatorRow::total).sum());
                if (col == 1)
                    return INT_FORMAT.format(rows.stream().mapToInt(IndicatorRow::generalCount).sum());
                if (col <= orgs.size() + 1) {
                    int orgIdx = col - 2;
                    return INT_FORMAT.format(rows.stream().mapToInt(r -> r.orgCounts()[orgIdx]).sum());
                }
                return INT_FORMAT.format(rows.stream().mapToInt(IndicatorRow::total).sum());
            }

            IndicatorRow r = rows.get(row);
            if (col == 0)
                return r.label();
            if (orgs.size() <= 1)
                return INT_FORMAT.format(r.total());
            if (col == 1)
                return INT_FORMAT.format(r.generalCount());
            if (col <= orgs.size() + 1)
                return INT_FORMAT.format(r.orgCounts()[col - 2]);
            return INT_FORMAT.format(r.total());
        }
    }
}
