package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.ui.common.dialogs.BaseFormDialog;
import migration4o.util.DatabaseUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Dialog for editing ClassExportConfig - destination file name and export
 * criteria.
 */
public class ClassExportConfigDialog extends BaseFormDialog {

    private final String className;
    private final DOSchemaClass schemaClass;
    private JTextField titleField;
    private JTextField destinationFileField;
    private JTextArea descriptionArea;
    private JTable criteriaTable;
    private DefaultTableModel criteriaTableModel;
    private JButton addCriteriaButton;
    private JButton removeCriteriaButton;
    private JButton editCriteriaButton;
    private JTable priceListTable;
    private DefaultTableModel priceListTableModel;

    // ── Default Columns selector (HTML viewer) ────────────────────────────────
    private JPanel defaultColumnsContainer;
    private JTextField filterField;
    private final List<String> defaultColumnFieldNames = new ArrayList<>();
    private final List<String> defaultColumnLabels = new ArrayList<>();
    private final List<JCheckBox> defaultColumnCheckboxes = new ArrayList<>();
    private final List<JPanel> defaultColumnRowPanels = new ArrayList<>();

    private ClassExportConfig result;
    private ClassExportConfig initialConfig;

    public ClassExportConfigDialog(Frame owner, DOSchemaClass schemaClass, ClassExportConfig existingConfig) {
        super(owner, "Configure Export: " + schemaClass.source);
        this.className = schemaClass.source;
        this.schemaClass = schemaClass;
        this.initialConfig = existingConfig;

        // Load existing configuration if provided after UI is built
        if (initialConfig != null) {
            loadConfiguration(initialConfig);
        }

        // Build column selector rows now that schemaClass is set (was null during buildFormPanel)
        List<String> initialColumns = (existingConfig != null) ? new ArrayList<>(existingConfig.getDefaultColumns()) : Collections.emptyList();
        rebuildDefaultColumnsUI(initialColumns);
        pack();
    }

    @Override
    protected JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel: Destination file name and description
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createTitledBorder("Output Settings"));

        JPanel titlePanel = new JPanel(new BorderLayout(5, 0));
        titlePanel.add(new JLabel("Display Title:"), BorderLayout.WEST);
        titleField = new JTextField(20);
        titleField.setToolTipText("Optional: override the class title shown in the nav sidebar and search screen (leave empty to use schema title)");
        titlePanel.add(titleField, BorderLayout.CENTER);
        JLabel titleHint = new JLabel("(Leave empty to use schema title)");
        titleHint.setFont(titleHint.getFont().deriveFont(Font.ITALIC, 11f));
        titleHint.setForeground(Color.GRAY);
        titlePanel.add(titleHint, BorderLayout.SOUTH);
        topPanel.add(titlePanel);
        topPanel.add(Box.createVerticalStrut(10));

        JPanel fileNamePanel = new JPanel(new BorderLayout(5, 0));
        fileNamePanel.add(new JLabel("Destination File Name:"), BorderLayout.WEST);

        destinationFileField = new JTextField(20);
        destinationFileField.setToolTipText("Leave empty to use class name as default");
        fileNamePanel.add(destinationFileField, BorderLayout.CENTER);

        String defaultName = className != null ? getDefaultFileName() : "";
        JLabel hintLabel = new JLabel("(Leave empty to use default: " + defaultName + ")");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hintLabel.setForeground(Color.GRAY);
        fileNamePanel.add(hintLabel, BorderLayout.SOUTH);

        topPanel.add(fileNamePanel);
        topPanel.add(Box.createVerticalStrut(10));

        // Description field
        JPanel descPanel = new JPanel(new BorderLayout(5, 0));
        descPanel.add(new JLabel("Description:"), BorderLayout.NORTH);
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descPanel.add(descScroll, BorderLayout.CENTER);
        topPanel.add(descPanel);

        panel.add(topPanel, BorderLayout.NORTH);

        // Center panel: Criteria table and Price list table side-by-side
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // Left: Criteria table
        JPanel criteriaPanel = buildCriteriaPanel();
        centerPanel.add(criteriaPanel);

        // Right: Price list table
        JPanel pricePanel = buildPriceListPanel();
        centerPanel.add(pricePanel);

        panel.add(centerPanel, BorderLayout.CENTER);

        // South panel: Default Columns selector for HTML viewer
        JPanel columnsSection = new JPanel(new BorderLayout(5, 5));
        columnsSection.setBorder(BorderFactory.createTitledBorder("Default Columns (HTML Viewer)"));

        defaultColumnsContainer = new JPanel();
        defaultColumnsContainer.setLayout(new BoxLayout(defaultColumnsContainer, BoxLayout.Y_AXIS));

        // Filter row above the scroll pane
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        filterRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        filterRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        filterField = new JTextField();
        filterField.setToolTipText("Type to filter the list of available columns");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyColumnFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyColumnFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyColumnFilter();
            }
        });
        filterRow.add(filterField, BorderLayout.CENTER);
        columnsSection.add(filterRow, BorderLayout.NORTH);

        JScrollPane columnsScroll = new JScrollPane(defaultColumnsContainer);
        columnsScroll.setPreferredSize(new Dimension(550, 160));
        columnsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        columnsSection.add(columnsScroll, BorderLayout.CENTER);

        JLabel columnsHint = new JLabel("Select and order the columns shown by default in the HTML viewer search results. Drag \u2807 to reorder.");
        columnsHint.setFont(columnsHint.getFont().deriveFont(Font.ITALIC, 11f));
        columnsHint.setForeground(Color.GRAY);
        columnsSection.add(columnsHint, BorderLayout.SOUTH);

        panel.add(columnsSection, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildCriteriaPanel() {
        JPanel criteriaPanel = new JPanel(new BorderLayout(5, 5));
        criteriaPanel.setBorder(BorderFactory.createTitledBorder("Export Criteria (AND logic)"));

        String[] columnNames = { "Field", "Operator", "Value" };
        criteriaTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only, use Edit button
            }
        };

        criteriaTable = new JTable(criteriaTableModel);
        criteriaTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        criteriaTable.getTableHeader().setReorderingAllowed(false);
        criteriaTable.setShowHorizontalLines(true);
        criteriaTable.setShowVerticalLines(false);
        criteriaTable.setGridColor(new Color(230, 230, 230));

        // Double-click to edit
        criteriaTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    editSelectedCriteria();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(criteriaTable);
        scrollPane.setPreferredSize(new Dimension(250, 150));
        criteriaPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel for criteria
        JPanel criteriaButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        addCriteriaButton = new JButton("Add...");
        addCriteriaButton.addActionListener(e -> addCriteria());
        criteriaButtonPanel.add(addCriteriaButton);

        editCriteriaButton = new JButton("Edit...");
        editCriteriaButton.addActionListener(e -> editSelectedCriteria());
        editCriteriaButton.setEnabled(false);
        criteriaButtonPanel.add(editCriteriaButton);

        removeCriteriaButton = new JButton("Remove");
        removeCriteriaButton.addActionListener(e -> removeSelectedCriteria());
        removeCriteriaButton.setEnabled(false);
        criteriaButtonPanel.add(removeCriteriaButton);

        criteriaPanel.add(criteriaButtonPanel, BorderLayout.SOUTH);

        // Enable/disable buttons based on selection
        criteriaTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = criteriaTable.getSelectedRow() >= 0;
            editCriteriaButton.setEnabled(hasSelection);
            removeCriteriaButton.setEnabled(hasSelection);
        });

        return criteriaPanel;
    }

    private JPanel buildPriceListPanel() {
        JPanel pricePanel = new JPanel(new BorderLayout(5, 5));
        pricePanel.setBorder(BorderFactory.createTitledBorder("Unit Costs by Price List"));

        String[] columnNames = { "Price List", "Unit Cost" };
        priceListTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true; // Allow inline editing
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 1)
                    return Float.class; // Unit Cost column
                return String.class;
            }
        };

        priceListTable = new JTable(priceListTableModel);
        priceListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        priceListTable.getTableHeader().setReorderingAllowed(false);
        priceListTable.setShowHorizontalLines(true);
        priceListTable.setShowVerticalLines(false);
        priceListTable.setGridColor(new Color(230, 230, 230));

        // Format Unit Cost column as money
        priceListTable.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Float) {
                    setText(String.format("%.2f $", value));
                    setHorizontalAlignment(JLabel.RIGHT);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(priceListTable);
        scrollPane.setPreferredSize(new Dimension(250, 150));
        pricePanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel for prices
        JPanel priceButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton addPriceButton = new JButton("Add...");
        addPriceButton.addActionListener(e -> addPrice());
        priceButtonPanel.add(addPriceButton);

        JButton editPriceButton = new JButton("Edit...");
        editPriceButton.addActionListener(e -> editSelectedPrice());
        editPriceButton.setEnabled(false);
        priceButtonPanel.add(editPriceButton);

        JButton removePriceButton = new JButton("Remove");
        removePriceButton.addActionListener(e -> removeSelectedPrice());
        removePriceButton.setEnabled(false);
        priceButtonPanel.add(removePriceButton);

        pricePanel.add(priceButtonPanel, BorderLayout.SOUTH);

        // Enable/disable buttons based on selection
        priceListTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = priceListTable.getSelectedRow() >= 0;
            editPriceButton.setEnabled(hasSelection);
            removePriceButton.setEnabled(hasSelection);
        });

        return pricePanel;
    }

    // ── Default Columns selector helpers ─────────────────────────────────────

    /**
     * Rebuilds the column rows inside {@code defaultColumnsContainer}.
     * Enabled columns (from {@code initialCols}) appear first in their configured
     * order and are checked; remaining available fields follow, unchecked.
     */
    private void rebuildDefaultColumnsUI(List<String> initialCols) {
        if (defaultColumnsContainer == null)
            return;

        defaultColumnFieldNames.clear();
        defaultColumnLabels.clear();
        defaultColumnCheckboxes.clear();
        defaultColumnRowPanels.clear();
        defaultColumnsContainer.removeAll();

        List<FieldInfo> available = collectAvailableColumns();

        // Build ordered list: configured columns first, then remaining available
        List<String> orderedNames = new ArrayList<>();
        for (String col : initialCols) {
            if (!col.isBlank())
                orderedNames.add(col);
        }
        for (FieldInfo fi : available) {
            if (!orderedNames.contains(fi.path))
                orderedNames.add(fi.path);
        }

        // Build label map
        Map<String, String> labelByPath = new LinkedHashMap<>();
        for (FieldInfo fi : available)
            labelByPath.put(fi.path, fi.label);

        Set<String> enabledSet = new LinkedHashSet<>(initialCols);

        for (int i = 0; i < orderedNames.size(); i++) {
            String fname = orderedNames.get(i);
            boolean enabled = enabledSet.contains(fname);
            String label = labelByPath.getOrDefault(fname, humanizeColumnName(fname));

            JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
            rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Grip handle for drag-reorder
            JLabel grip = new JLabel("\u2807");
            grip.setForeground(Color.GRAY);
            grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            grip.setToolTipText("Drag to reorder");

            JCheckBox cb = new JCheckBox("", enabled);

            JLabel nameLbl = new JLabel(label);
            nameLbl.setPreferredSize(new Dimension(150, 20));
            nameLbl.setToolTipText(fname);

            if (!labelByPath.containsKey(fname)) {
                nameLbl.setForeground(Color.GRAY); // unknown / no longer in schema
                nameLbl.setToolTipText(fname + " (not found in schema)");
            }

            rowPanel.add(grip);
            rowPanel.add(cb);
            rowPanel.add(nameLbl);

            defaultColumnFieldNames.add(fname);
            defaultColumnLabels.add(label);
            defaultColumnCheckboxes.add(cb);
            defaultColumnRowPanels.add(rowPanel);
            defaultColumnsContainer.add(rowPanel);

            // Drag reorder via mouse listeners on the grip
            Runnable clearHighlights = () -> {
                for (JPanel rp : defaultColumnRowPanels)
                    rp.setBackground(UIManager.getColor("Panel.background"));
            };

            grip.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    grip.putClientProperty("dragStart", defaultColumnRowPanels.indexOf(rowPanel));
                    rowPanel.setBackground(new Color(200, 210, 230));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    clearHighlights.run();
                    Object startObj = grip.getClientProperty("dragStart");
                    if (startObj == null)
                        return;
                    int startIdx = (int) startObj;
                    Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), defaultColumnsContainer);
                    int targetIdx = -1;
                    for (int r = 0; r < defaultColumnRowPanels.size(); r++) {
                        Rectangle bounds = defaultColumnRowPanels.get(r).getBounds();
                        if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                            targetIdx = r;
                            break;
                        }
                    }
                    grip.putClientProperty("dragStart", null);
                    if (targetIdx < 0 || targetIdx == startIdx)
                        return;

                    String movedName = defaultColumnFieldNames.remove(startIdx);
                    JPanel movedPanel = defaultColumnRowPanels.remove(startIdx);
                    JCheckBox movedCb = defaultColumnCheckboxes.remove(startIdx);
                    int insertAt = targetIdx > startIdx ? targetIdx - 1 : targetIdx;
                    defaultColumnFieldNames.add(insertAt, movedName);
                    defaultColumnRowPanels.add(insertAt, movedPanel);
                    defaultColumnCheckboxes.add(insertAt, movedCb);

                    defaultColumnsContainer.removeAll();
                    for (JPanel rp : defaultColumnRowPanels)
                        defaultColumnsContainer.add(rp);
                    defaultColumnsContainer.revalidate();
                    defaultColumnsContainer.repaint();
                }
            });

            grip.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    Object startObj = grip.getClientProperty("dragStart");
                    if (startObj == null)
                        return;
                    Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), defaultColumnsContainer);
                    clearHighlights.run();
                    rowPanel.setBackground(new Color(200, 210, 230));
                    for (JPanel rp : defaultColumnRowPanels) {
                        if (rp == rowPanel)
                            continue;
                        Rectangle bounds = rp.getBounds();
                        if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                            rp.setBackground(new Color(180, 220, 180));
                            break;
                        }
                    }
                }
            });
        }

        defaultColumnsContainer.revalidate();
        defaultColumnsContainer.repaint();

        // Re-apply any active filter after rebuilding
        if (filterField != null && !filterField.getText().isBlank())
            applyColumnFilter();
    }

    /**
     * Shows/hides column rows based on the text in {@code filterField}.
     * Filtering is case-insensitive and matches against both the path and the display label.
     */
    private void applyColumnFilter() {
        if (filterField == null || defaultColumnsContainer == null)
            return;
        String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < defaultColumnRowPanels.size(); i++) {
            boolean show = filter.isEmpty() || defaultColumnFieldNames.get(i).toLowerCase(Locale.ROOT).contains(filter) || (i < defaultColumnLabels.size() && defaultColumnLabels.get(i).toLowerCase(Locale.ROOT).contains(filter));
            defaultColumnRowPanels.get(i).setVisible(show);
        }
        defaultColumnsContainer.revalidate();
        defaultColumnsContainer.repaint();
    }

    /** Collects all flat leaf field paths exported by this class, including embedded entities one level deep. */
    private List<FieldInfo> collectAvailableColumns() {
        if (schemaClass == null)
            return new ArrayList<>();

        DOSchema refSchema = null;
        try {
            refSchema = migration4o.schema.DOSchemaService.getInstance().getReferenceSchema();
        } catch (Exception ignored) {
        }

        List<DOSchemaField> allFields = (refSchema != null) ? DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema) : (schemaClass.fields != null ? Arrays.asList(schemaClass.fields) : new ArrayList<>());

        List<FieldInfo> result = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        final DOSchema fRefSchema = refSchema;

        for (DOSchemaField field : allFields) {
            if (!field.isExported)
                continue;
            String name = field.destinationName != null ? field.destinationName : field.source;
            if (name == null || name.isBlank())
                continue;
            if (field.isCollection)
                continue;

            if (field.embedContents && !isPrimitiveType(field.type)) {
                // Embedded entity: expose its leaf children as "parentName.childName"
                DOSchemaClass embClass = findClassByType(field.type, fRefSchema);
                if (embClass != null) {
                    List<DOSchemaField> embFields = (fRefSchema != null) ? DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embClass, fRefSchema) : (embClass.fields != null ? Arrays.asList(embClass.fields) : new ArrayList<>());
                    for (DOSchemaField ef : embFields) {
                        if (!ef.isExported || ef.isCollection)
                            continue;
                        String eName = ef.destinationName != null ? ef.destinationName : ef.source;
                        if (eName == null || eName.isBlank())
                            continue;
                        String path = name + "." + eName;
                        if (seenPaths.add(path)) {
                            String parentLabel = getFieldLabel(field);
                            String childLabel = getFieldLabel(ef);
                            String fullLabel = parentLabel.isBlank() ? childLabel : childLabel.isBlank() ? parentLabel : parentLabel + " \u203a " + childLabel;
                            result.add(new FieldInfo(path, fullLabel));
                        }
                    }
                } else {
                    if (seenPaths.add(name))
                        result.add(new FieldInfo(name, getFieldLabel(field)));
                }
            } else {
                if (seenPaths.add(name))
                    result.add(new FieldInfo(name, getFieldLabel(field)));
            }
        }
        // Sort: direct fields first (alphabetically by label), then embedded (alphabetically by label,
        // which groups them by parent since the label is "Parent › Child").
        result.sort((a, b) -> {
            boolean aDot = a.path.contains(".");
            boolean bDot = b.path.contains(".");
            if (aDot != bDot)
                return aDot ? 1 : -1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }

    private DOSchemaClass findClassByType(String typeName, DOSchema refSchema) {
        if (typeName == null || refSchema == null)
            return null;
        DOSchemaClass cls = refSchema.findClassByName(typeName);
        if (cls != null)
            return cls;
        String shortName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        for (DOSchemaClass c : refSchema.getClasses()) {
            if (c.source != null && c.source.endsWith("." + shortName))
                return c;
        }
        return null;
    }

    private boolean isPrimitiveType(String type) {
        if (type == null)
            return true;
        return type.equals("string") || type.equals("int") || type.equals("long") || type.equals("float") || type.equals("double") || type.equals("boolean") || type.equals("date") || type.equals("byte") || type.equals("short") || type.equals("char") || type.startsWith("java.lang.") || type.startsWith("java.util.Date") || type.equals("java.util.Date") || type.equals("java.sql.Date") || type.equals("java.sql.Timestamp");
    }

    private String getFieldLabel(DOSchemaField field) {
        if (field == null)
            return "";
        if (field.title != null && !field.title.isBlank())
            return field.title.trim();
        if (field.destinationName != null && !field.destinationName.isBlank())
            return humanizeColumnName(field.destinationName.trim());
        if (field.source != null && !field.source.isBlank())
            return humanizeColumnName(field.source.trim());
        return "";
    }

    private String humanizeColumnName(String name) {
        if (name == null || name.isBlank())
            return "";
        // Handle dot-path: use last segment
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(dot + 1) : name;
        // Insert space before uppercase letters
        String spaced = base.replaceAll("([A-Z])", " $1").trim();
        if (spaced.isEmpty())
            return base;
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Simple (path, label) pair for available column fields. */
    private static class FieldInfo {
        final String path;
        final String label;

        FieldInfo(String path, String label) {
            this.path = path;
            this.label = label;
        }
    }

    private String getDefaultFileName() {
        return schemaClass.destinationName;
    }

    private void loadConfiguration(ClassExportConfig config) {
        // Load title override
        if (config.hasTitle()) {
            titleField.setText(config.getTitle());
        }

        // Load destination file name
        if (config.hasCustomDestination()) {
            destinationFileField.setText(config.getRawDestinationFileName());
        }

        // Load description
        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            descriptionArea.setText(config.getDescription());
        }

        // Load criteria
        for (ExportCriteria criterion : config.getCriteria()) {
            addCriteriaToTable(criterion);
        }

        // Load unit costs
        Map<String, Float> unitCosts = config.getUnitCosts();
        if (unitCosts != null) {
            for (Map.Entry<String, Float> entry : unitCosts.entrySet()) {
                addPriceToTable(entry.getKey(), entry.getValue());
            }
        }
    }

    private void addCriteriaToTable(ExportCriteria criterion) {
        Object[] row = { criterion.getFieldName(), criterion.getOperator().getSymbol(), criterion.getValue() != null ? criterion.getValue() : "" };
        criteriaTableModel.addRow(row);
    }

    private void addPriceToTable(String priceList, Float unitCost) {
        Object[] row = { priceList, unitCost };
        priceListTableModel.addRow(row);
    }

    private void addCriteria() {
        System.out.println("DEBUG ClassExportConfigDialog.addCriteria: schemaClass=" + schemaClass);
        if (schemaClass != null) {
            System.out.println("  schemaClass.source=" + schemaClass.source);
            System.out.println("  schemaClass.fields=" + (schemaClass.fields != null ? schemaClass.fields.length : "null"));
        }
        ExportCriteriaDialog dialog = new ExportCriteriaDialog((Frame) SwingUtilities.getWindowAncestor(this), schemaClass, null);
        dialog.setVisible(true);

        ExportCriteria newCriteria = dialog.getResult();
        if (newCriteria != null) {
            addCriteriaToTable(newCriteria);
        }
    }

    private void editSelectedCriteria() {
        int selectedRow = criteriaTable.getSelectedRow();
        if (selectedRow < 0)
            return;

        // Get current criteria values
        String fieldName = (String) criteriaTableModel.getValueAt(selectedRow, 0);
        String operatorStr = (String) criteriaTableModel.getValueAt(selectedRow, 1);
        String value = (String) criteriaTableModel.getValueAt(selectedRow, 2);

        ExportCriteria.Operator operator = ExportCriteria.Operator.fromSymbol(operatorStr);
        ExportCriteria currentCriteria = new ExportCriteria(fieldName, operator, value.isEmpty() ? null : value);

        // Show edit dialog
        ExportCriteriaDialog dialog = new ExportCriteriaDialog((Frame) SwingUtilities.getWindowAncestor(this), schemaClass, currentCriteria);
        dialog.setVisible(true);

        ExportCriteria updatedCriteria = dialog.getResult();
        if (updatedCriteria != null) {
            // Update table row
            criteriaTableModel.setValueAt(updatedCriteria.getFieldName(), selectedRow, 0);
            criteriaTableModel.setValueAt(updatedCriteria.getOperator().getSymbol(), selectedRow, 1);
            criteriaTableModel.setValueAt(updatedCriteria.getValue() != null ? updatedCriteria.getValue() : "", selectedRow, 2);
        }
    }

    private void removeSelectedCriteria() {
        int selectedRow = criteriaTable.getSelectedRow();
        if (selectedRow >= 0) {
            criteriaTableModel.removeRow(selectedRow);
        }
    }

    private void addPrice() {
        // Just add an empty row - user will edit it inline
        addPriceToTable("", 0.0f);
    }

    private void editSelectedPrice() {
        int selectedRow = priceListTable.getSelectedRow();
        if (selectedRow < 0)
            return;

        String currentPriceList = (String) priceListTableModel.getValueAt(selectedRow, 0);
        Float currentCost = (Float) priceListTableModel.getValueAt(selectedRow, 1);

        String priceListName = (String) JOptionPane.showInputDialog(this, "Enter price list name:", "Edit Unit Cost", JOptionPane.PLAIN_MESSAGE, null, null, currentPriceList);

        if (priceListName == null || priceListName.trim().isEmpty()) {
            return;
        }

        String costStr = (String) JOptionPane.showInputDialog(this, "Enter unit cost for '" + priceListName.trim() + "':", "Edit Unit Cost", JOptionPane.PLAIN_MESSAGE, null, null, currentCost.toString());

        if (costStr == null || costStr.trim().isEmpty()) {
            return;
        }

        try {
            float cost = Float.parseFloat(costStr.trim());
            priceListTableModel.setValueAt(priceListName.trim(), selectedRow, 0);
            priceListTableModel.setValueAt(cost, selectedRow, 1);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format: " + costStr, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedPrice() {
        int selectedRow = priceListTable.getSelectedRow();
        if (selectedRow >= 0) {
            priceListTableModel.removeRow(selectedRow);
        }
    }

    @Override
    protected boolean validateInput() {
        // Build the ClassExportConfig from form values when OK is clicked
        String destinationFile = destinationFileField.getText().trim();
        if (destinationFile.isEmpty()) {
            destinationFile = null; // Use default
        }

        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) {
            description = null;
        }

        // Collect criteria
        List<ExportCriteria> criteria = new ArrayList<>();
        for (int i = 0; i < criteriaTableModel.getRowCount(); i++) {
            String fieldName = (String) criteriaTableModel.getValueAt(i, 0);
            String operatorStr = (String) criteriaTableModel.getValueAt(i, 1);
            String value = (String) criteriaTableModel.getValueAt(i, 2);

            ExportCriteria.Operator operator = ExportCriteria.Operator.fromSymbol(operatorStr);
            ExportCriteria criterion = new ExportCriteria(fieldName, operator, value.isEmpty() ? null : value);
            criteria.add(criterion);
        }

        // Collect unit costs
        Map<String, Float> unitCosts = new HashMap<>();
        for (int i = 0; i < priceListTableModel.getRowCount(); i++) {
            String priceList = (String) priceListTableModel.getValueAt(i, 0);
            Float cost = (Float) priceListTableModel.getValueAt(i, 1);
            unitCosts.put(priceList, cost);
        }

        result = new ClassExportConfig(className, destinationFile, criteria, description, unitCosts);
        String titleOverride = titleField.getText().trim();
        if (!titleOverride.isEmpty()) {
            result.setTitle(titleOverride);
        }
        if (initialConfig != null && initialConfig.hasLayout()) {
            result.setLayout(initialConfig.getLayout());
        }

        // Collect selected default columns (in the order they appear in the UI)
        List<String> selectedColumns = new ArrayList<>();
        for (int i = 0; i < defaultColumnFieldNames.size(); i++) {
            if (defaultColumnCheckboxes.get(i).isSelected()) {
                selectedColumns.add(defaultColumnFieldNames.get(i));
            }
        }
        result.setDefaultColumns(selectedColumns.isEmpty() ? null : selectedColumns);

        return true;
    }

    public ClassExportConfig getResult() {
        return isConfirmed() ? result : null;
    }
}
