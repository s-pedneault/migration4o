package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.ui.common.FieldSelectorPanel;
import migration4o.ui.common.dialogs.BaseFormDialog;

import javax.swing.*;
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

    // ── Default Columns selector (HTML viewer)
    // ────────────────────────────────
    private FieldSelectorPanel fieldSelectorPanel;
    private JPanel fieldSelectorContainer;
    private JPanel selectedColumnsContainer;
    private final List<String> selectedColumnPaths = new ArrayList<>();
    private final List<String> selectedColumnLabels = new ArrayList<>();
    private final List<JPanel> selectedColumnRowPanels = new ArrayList<>();

    private ClassExportConfig result;
    private ClassExportConfig initialConfig;

    public ClassExportConfigDialog(Frame owner, DOSchemaClass schemaClass, ClassExportConfig existingConfig) {
        super(owner, "Configure Export: " + schemaClass.attributes.source);
        this.className = schemaClass.attributes.source;
        this.schemaClass = schemaClass;
        this.initialConfig = existingConfig;

        // Load existing configuration if provided after UI is built
        if (initialConfig != null) {
            loadConfiguration(initialConfig);
        }

        // Now that schemaClass is assigned, create the real FieldSelectorPanel
        // and inject it into the placeholder container built by buildFormPanel().
        List<String> initialColumns = (existingConfig != null) ? new ArrayList<>(existingConfig.getDefaultColumns()) : Collections.emptyList();
        fieldSelectorPanel = new FieldSelectorPanel(schemaClass, initialColumns, (fieldPath, fieldLabel) -> {
            addSelectedColumn(fieldPath, fieldLabel);
        });
        if (fieldSelectorContainer != null) {
            fieldSelectorContainer.add(fieldSelectorPanel, BorderLayout.CENTER);
            fieldSelectorContainer.revalidate();
        }

        // Populate selected columns list
        rebuildSelectedColumnsUI(initialColumns);
        // Sync visual checkmarks in the field selector
        fieldSelectorPanel.setSelectedPaths(selectedColumnPaths);
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

        JSplitPane columnsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        columnsSplit.setResizeWeight(0.5);
        columnsSplit.setBorder(null);

        // Left: field selector container — populated in the constructor once
        // schemaClass is assigned (it is null at this point because buildFormPanel
        // is called from super() before the subclass constructor body runs).
        fieldSelectorContainer = new JPanel(new BorderLayout());
        fieldSelectorContainer.setBorder(BorderFactory.createTitledBorder("Available Fields"));
        columnsSplit.setLeftComponent(fieldSelectorContainer);

        // Right: selected columns list with drag-reorder & remove
        JPanel selectedPanel = new JPanel(new BorderLayout(5, 5));
        selectedPanel.setBorder(BorderFactory.createTitledBorder("Selected Columns"));

        selectedColumnsContainer = new JPanel();
        selectedColumnsContainer.setLayout(new BoxLayout(selectedColumnsContainer, BoxLayout.Y_AXIS));

        JScrollPane selectedScroll = new JScrollPane(selectedColumnsContainer);
        selectedScroll.setPreferredSize(new Dimension(250, 160));
        selectedScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        selectedPanel.add(selectedScroll, BorderLayout.CENTER);

        JLabel columnsHint = new JLabel("Double-click a field to add it. Drag \u2807 to reorder.");
        columnsHint.setFont(columnsHint.getFont().deriveFont(Font.ITALIC, 11f));
        columnsHint.setForeground(Color.GRAY);
        selectedPanel.add(columnsHint, BorderLayout.SOUTH);

        columnsSplit.setRightComponent(selectedPanel);
        columnsSection.add(columnsSplit, BorderLayout.CENTER);

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

    // ── Default Columns: selected columns helpers ────────────────────────────

    /**
     * Rebuilds the selected-columns list from the given initial column paths.
     * For each path we resolve a human-readable label via the
     * FieldSelectorPanel tree (falling back to a simple humanization).
     */
    private void rebuildSelectedColumnsUI(List<String> initialCols) {
        if (selectedColumnsContainer == null)
            return;

        selectedColumnPaths.clear();
        selectedColumnLabels.clear();
        selectedColumnRowPanels.clear();
        selectedColumnsContainer.removeAll();

        for (String col : initialCols) {
            if (col == null || col.isBlank())
                continue;
            String label = humanizeColumnPath(col);
            addSelectedColumnRow(col, label);
        }

        selectedColumnsContainer.revalidate();
        selectedColumnsContainer.repaint();
    }

    /**
     * Adds a field to the selected columns list (called from FieldSelectorPanel
     * double-click callback). Duplicates are silently ignored.
     */
    private void addSelectedColumn(String path, String label) {
        if (path == null || path.isBlank())
            return;
        if (selectedColumnPaths.contains(path))
            return; // already selected

        addSelectedColumnRow(path, label);
        selectedColumnsContainer.revalidate();
        selectedColumnsContainer.repaint();

        // Update the field selector's visual indicators
        if (fieldSelectorPanel != null) {
            fieldSelectorPanel.setSelectedPaths(selectedColumnPaths);
        }
    }

    /**
     * Removes a field from the selected columns list and refreshes the UI.
     */
    private void removeSelectedColumn(int index) {
        if (index < 0 || index >= selectedColumnPaths.size())
            return;

        selectedColumnPaths.remove(index);
        selectedColumnLabels.remove(index);
        JPanel removed = selectedColumnRowPanels.remove(index);
        selectedColumnsContainer.remove(removed);
        selectedColumnsContainer.revalidate();
        selectedColumnsContainer.repaint();

        // Update the field selector's visual indicators
        if (fieldSelectorPanel != null) {
            fieldSelectorPanel.setSelectedPaths(selectedColumnPaths);
        }
    }

    /**
     * Creates a single row in the selected-columns list with a grip handle for
     * drag-reorder, a label, and a remove button.
     */
    private void addSelectedColumnRow(String path, String label) {
        JPanel rowPanel = new JPanel(new BorderLayout(3, 0));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowPanel.setOpaque(true);

        // Left: grip + label
        JPanel leftPart = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
        leftPart.setOpaque(false);

        JLabel grip = new JLabel("\u2807");
        grip.setForeground(Color.GRAY);
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        grip.setToolTipText("Drag to reorder");

        JLabel nameLbl = new JLabel(label);
        nameLbl.setToolTipText(path);

        leftPart.add(grip);
        leftPart.add(nameLbl);
        rowPanel.add(leftPart, BorderLayout.CENTER);

        // Right: remove button
        JButton removeBtn = new JButton("\u2715");
        removeBtn.setMargin(new Insets(0, 4, 0, 4));
        removeBtn.setFont(removeBtn.getFont().deriveFont(10f));
        removeBtn.setToolTipText("Remove");
        removeBtn.setFocusPainted(false);
        removeBtn.addActionListener(e -> {
            int idx = selectedColumnRowPanels.indexOf(rowPanel);
            if (idx >= 0)
                removeSelectedColumn(idx);
        });
        rowPanel.add(removeBtn, BorderLayout.EAST);

        selectedColumnPaths.add(path);
        selectedColumnLabels.add(label);
        selectedColumnRowPanels.add(rowPanel);
        selectedColumnsContainer.add(rowPanel);

        // Drag reorder via mouse listeners on the grip
        Runnable clearHighlights = () -> {
            for (JPanel rp : selectedColumnRowPanels) {
                rp.setBackground(UIManager.getColor("Panel.background"));
                rp.setBorder(null);
            }
        };

        grip.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                grip.putClientProperty("dragStart", selectedColumnRowPanels.indexOf(rowPanel));
                rowPanel.setBackground(new Color(200, 210, 230));
                rowPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 140, 200), 1));
                selectedColumnsContainer.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                clearHighlights.run();
                Object startObj = grip.getClientProperty("dragStart");
                if (startObj == null)
                    return;
                int startIdx = (int) startObj;
                Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), selectedColumnsContainer);
                int targetIdx = -1;
                for (int r = 0; r < selectedColumnRowPanels.size(); r++) {
                    Rectangle bounds = selectedColumnRowPanels.get(r).getBounds();
                    if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                        targetIdx = r;
                        break;
                    }
                }
                grip.putClientProperty("dragStart", null);
                if (targetIdx < 0 || targetIdx == startIdx)
                    return;

                // Reorder all parallel lists
                String movedPath = selectedColumnPaths.remove(startIdx);
                String movedLabel = selectedColumnLabels.remove(startIdx);
                JPanel movedPanel = selectedColumnRowPanels.remove(startIdx);
                int insertAt = targetIdx > startIdx ? targetIdx - 1 : targetIdx;
                selectedColumnPaths.add(insertAt, movedPath);
                selectedColumnLabels.add(insertAt, movedLabel);
                selectedColumnRowPanels.add(insertAt, movedPanel);

                selectedColumnsContainer.removeAll();
                for (JPanel rp : selectedColumnRowPanels)
                    selectedColumnsContainer.add(rp);
                selectedColumnsContainer.revalidate();
                selectedColumnsContainer.repaint();
            }
        });

        grip.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Object startObj = grip.getClientProperty("dragStart");
                if (startObj == null)
                    return;
                Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), selectedColumnsContainer);
                clearHighlights.run();
                rowPanel.setBackground(new Color(200, 210, 230));
                rowPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 140, 200), 1));
                for (int r = 0; r < selectedColumnRowPanels.size(); r++) {
                    JPanel rp = selectedColumnRowPanels.get(r);
                    if (rp == rowPanel)
                        continue;
                    Rectangle bounds = rp.getBounds();
                    if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                        boolean above = (pt.y - bounds.y) < bounds.height / 2;
                        if (above) {
                            rp.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(60, 120, 220)));
                        } else {
                            rp.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(60, 120, 220)));
                        }
                        break;
                    }
                }
                selectedColumnsContainer.repaint();
            }
        });
    }

    private String humanizeColumnPath(String path) {
        if (path == null || path.isBlank())
            return "";
        String[] segments = path.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0)
                sb.append(" \u203a ");
            sb.append(humanizeColumnName(segments[i]));
        }
        return sb.toString();
    }

    private String humanizeColumnName(String name) {
        if (name == null || name.isBlank())
            return "";
        String spaced = name.replaceAll("([A-Z])", " $1").trim();
        if (spaced.isEmpty())
            return name;
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String getDefaultFileName() {
        return schemaClass.attributes.destinationName;
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
            System.out.println("  schemaClass.attributes.source=" + schemaClass.attributes.source);
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

        // Collect selected default columns (in the order they appear in the UI)
        result.setDefaultColumns(selectedColumnPaths.isEmpty() ? null : new ArrayList<>(selectedColumnPaths));

        return true;
    }

    public ClassExportConfig getResult() {
        return isConfirmed() ? result : null;
    }
}
