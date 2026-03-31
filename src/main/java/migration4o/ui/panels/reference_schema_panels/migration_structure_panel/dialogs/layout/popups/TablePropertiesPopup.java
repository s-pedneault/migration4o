package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.popups;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.TableBlock;
import migration4o.util.DatabaseUtil;
import migration4o.util.TypeUtil;

/**
 * Popup editor for TABLE properties: collection ref, columns, titles, widths.
 * Uses JTable with AbstractTableModel for proper MVC separation and
 * TransferHandler with DropMode.INSERT_ROWS for native Swing drag-and-drop.
 */
public class TablePropertiesPopup {

    // ── Data model ──────────────────────────────────────────────────────

    private static class ColumnEntry {
        final String fieldName;
        final String label;
        boolean enabled;
        String titleOverride;
        String width;

        ColumnEntry(String fieldName, String label, boolean enabled, String titleOverride, String width) {
            this.fieldName = fieldName;
            this.label = label;
            this.enabled = enabled;
            this.titleOverride = titleOverride;
            this.width = width;
        }
    }

    // ── Table model ─────────────────────────────────────────────────────

    private static final int COL_ENABLED = 0;
    private static final int COL_LABEL = 1;
    private static final int COL_TITLE = 2;
    private static final int COL_WIDTH = 3;
    private static final String[] COLUMN_NAMES = { "", "Field", "Title", "Width" };

    private static class ColumnTableModel extends AbstractTableModel {
        private final List<ColumnEntry> entries;

        ColumnTableModel(List<ColumnEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMN_NAMES[col];
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == COL_ENABLED ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col != COL_LABEL;
        }

        @Override
        public Object getValueAt(int row, int col) {
            ColumnEntry e = entries.get(row);
            switch (col) {
            case COL_ENABLED:
                return e.enabled;
            case COL_LABEL:
                return e.label;
            case COL_TITLE:
                return e.titleOverride;
            case COL_WIDTH:
                return e.width;
            default:
                return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            ColumnEntry e = entries.get(row);
            switch (col) {
            case COL_ENABLED:
                e.enabled = (Boolean) value;
                break;
            case COL_TITLE:
                e.titleOverride = ((String) value).trim();
                break;
            case COL_WIDTH:
                e.width = ((String) value).trim();
                break;
            }
            fireTableCellUpdated(row, col);
        }

        void moveRow(int from, int to) {
            if (from == to)
                return;
            ColumnEntry moved = entries.remove(from);
            entries.add(to, moved);
            fireTableDataChanged();
        }

        List<ColumnEntry> getEntries() {
            return entries;
        }
    }

    // ── TransferHandler for row reordering ──────────────────────────────

    private static class RowReorderHandler extends TransferHandler {
        private int draggedRow = -1;

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTable table = (JTable) c;
            if (table.isEditing())
                table.getCellEditor().stopCellEditing();
            draggedRow = table.getSelectedRow();
            return new StringSelection(String.valueOf(draggedRow));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && draggedRow >= 0 && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support))
                return false;
            JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
            int dest = dl.getRow();
            int src = draggedRow;
            if (src < dest)
                dest--;
            if (src == dest)
                return false;
            JTable table = (JTable) support.getComponent();
            ((ColumnTableModel) table.getModel()).moveRow(src, dest);
            table.setRowSelectionInterval(dest, dest);
            return true;
        }

        @Override
        protected void exportDone(JComponent c, Transferable data, int action) {
            draggedRow = -1;
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    public static boolean show(Component parent, TableBlock block, DOSchemaClass schemaClass, DOSchema refSchema) {
        LayoutNode node = block.getLayoutNode();
        String ref = node.prop("ref", "");
        List<ColumnEntry> entries = buildEntries(ref, node, schemaClass, refSchema);

        ColumnTableModel model = new ColumnTableModel(entries);
        JTable table = createTable(model, entries);

        // Dialog
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Table Properties", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(0, 8));
        dialog.setMinimumSize(new Dimension(600, 300));
        dialog.setSize(750, 500);
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: collection ref (read-only)
        JPanel topPanel = new JPanel(new BorderLayout(6, 0));
        topPanel.add(new JLabel("Collection:"), BorderLayout.WEST);
        JTextField refField = new JTextField(ref);
        refField.setEditable(false);
        refField.setBackground(new Color(240, 240, 240));
        topPanel.add(refField, BorderLayout.CENTER);
        dialog.add(topPanel, BorderLayout.NORTH);

        // Center: table in scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Columns  (drag rows to reorder)"));
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Bottom: OK / Cancel
        boolean[] accepted = { false };
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> {
            if (table.isEditing())
                table.getCellEditor().stopCellEditing();
            accepted[0] = true;
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(okBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(okBtn);

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        if (accepted[0]) {
            serializeToNode(model.getEntries(), node);
            block.refreshFromNode();
            return true;
        }
        return false;
    }

    // ── Table setup ─────────────────────────────────────────────────────

    private static JTable createTable(ColumnTableModel model, List<ColumnEntry> entries) {
        JTable table = new JTable(model);
        table.setDragEnabled(true);
        table.setDropMode(DropMode.INSERT_ROWS);
        table.setTransferHandler(new RowReorderHandler());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);

        // Column sizing
        table.getColumnModel().getColumn(COL_ENABLED).setMaxWidth(30);
        table.getColumnModel().getColumn(COL_ENABLED).setMinWidth(30);
        table.getColumnModel().getColumn(COL_WIDTH).setMaxWidth(60);
        table.getColumnModel().getColumn(COL_WIDTH).setMinWidth(40);
        table.getColumnModel().getColumn(COL_TITLE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COL_LABEL).setPreferredWidth(250);

        // Label column: show fieldName as tooltip
        table.getColumnModel().getColumn(COL_LABEL).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (c instanceof JLabel && row < entries.size()) {
                    ((JLabel) c).setToolTipText(entries.get(row).fieldName);
                }
                return c;
            }
        });

        return table;
    }

    // ── Entry building ──────────────────────────────────────────────────

    private static List<ColumnEntry> buildEntries(String ref, LayoutNode node, DOSchemaClass schemaClass, DOSchema refSchema) {
        List<String> availableFieldNames = new ArrayList<>();
        Map<String, String> labelsByField = new HashMap<>();
        resolveChildFields(ref, schemaClass, refSchema, availableFieldNames, labelsByField);

        String[] currentCols = splitSafely(node.prop("columns", ""));
        String[] currentTitles = splitSafely(node.prop("columnTitles", ""));
        String[] currentWidths = splitSafely(node.prop("widths", ""));
        Set<String> enabledCols = new LinkedHashSet<>(Arrays.asList(currentCols));

        List<ColumnEntry> entries = new ArrayList<>();
        for (int i = 0; i < currentCols.length; i++) {
            String name = currentCols[i].trim();
            if (name.isEmpty())
                continue;
            String title = i < currentTitles.length ? currentTitles[i].trim() : "";
            String w = i < currentWidths.length ? currentWidths[i].trim() : "";
            entries.add(new ColumnEntry(name, labelsByField.getOrDefault(name, name), true, title, w));
        }
        for (String f : availableFieldNames) {
            if (!enabledCols.contains(f)) {
                entries.add(new ColumnEntry(f, labelsByField.getOrDefault(f, f), false, "", ""));
            }
        }
        return entries;
    }

    private static String[] splitSafely(String csv) {
        return csv.isEmpty() ? new String[0] : csv.split(",", -1);
    }

    // ── Serialization ───────────────────────────────────────────────────

    private static void serializeToNode(List<ColumnEntry> entries, LayoutNode node) {
        StringBuilder cols = new StringBuilder(), titles = new StringBuilder(), widths = new StringBuilder();
        boolean first = true;
        for (ColumnEntry ce : entries) {
            if (!ce.enabled)
                continue;
            if (!first) {
                cols.append(',');
                titles.append(',');
                widths.append(',');
            }
            first = false;
            cols.append(ce.fieldName);
            titles.append(ce.titleOverride);
            widths.append(ce.width);
        }
        node.setProp("columns", cols.toString());
        node.setProp("columnTitles", titles.toString());
        node.setProp("widths", widths.toString());
    }

    private static void resolveChildFields(String ref, DOSchemaClass schemaClass, DOSchema refSchema, List<String> fieldNames, Map<String, String> labels) {
        if (ref.isEmpty())
            return;

        // Walk the ref path to find the collection field (refs use source names)
        String[] parts = ref.split("\\.");
        DOSchemaClass current = schemaClass;
        DOSchemaField collField = null;
        for (int i = 0; i < parts.length; i++) {
            collField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(current, parts[i], refSchema);
            if (collField == null)
                return;
            if (i < parts.length - 1) {
                String nextType = collField.attributes.isCollection && collField.attributes.childrenType != null ? collField.attributes.childrenType : collField.attributes.type;
                current = refSchema.findClassByName(nextType);
                if (current == null)
                    return;
            }
        }

        if (collField == null || collField.attributes.childrenType == null)
            return;
        DOSchemaClass childClass = refSchema.findClassByName(collField.attributes.childrenType);
        if (childClass == null)
            return;

        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                continue;
            if (sf.attributes.isCollection)
                continue;
            // Expand embedded non-primitive fields into dotted-path sub-columns
            if (sf.attributes.embedContents && !TypeUtil.isPrimitiveType(sf.attributes.type)) {
                DOSchemaClass embeddedClass = refSchema.findClassByName(sf.attributes.type);
                if (embeddedClass != null) {
                    String prefix = sf.attributes.destinationName;
                    String parentLabel = sf.attributes.title != null ? sf.attributes.title : prefix;
                    for (DOSchemaField esf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, refSchema)) {
                        if (!esf.attributes.isExported || esf.attributes.destinationName == null)
                            continue;
                        if (esf.attributes.isCollection)
                            continue;
                        String dottedName = prefix + "." + esf.attributes.destinationName;
                        fieldNames.add(dottedName);
                        String subLabel = esf.attributes.title != null ? esf.attributes.title : esf.attributes.destinationName;
                        labels.put(dottedName, parentLabel + " › " + subLabel);
                    }
                }
                continue;
            }
            fieldNames.add(sf.attributes.destinationName);
            String label = sf.attributes.title != null ? sf.attributes.title : sf.attributes.destinationName;
            labels.put(sf.attributes.destinationName, label);
        }
    }

}
