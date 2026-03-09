package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.ui.common.dialogs.BaseFormDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return true;
    }

    public ClassExportConfig getResult() {
        return isConfirmed() ? result : null;
    }
}
