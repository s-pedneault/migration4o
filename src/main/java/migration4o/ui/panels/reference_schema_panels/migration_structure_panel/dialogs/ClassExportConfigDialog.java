package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.ui.common.dialogs.BaseFormDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for editing ClassExportConfig - destination file name and export
 * criteria.
 */
public class ClassExportConfigDialog extends BaseFormDialog {

    private final String className;
    private final DOSchemaClass schemaClass;
    private JTextField destinationFileField;
    private JTable criteriaTable;
    private DefaultTableModel criteriaTableModel;
    private JButton addCriteriaButton;
    private JButton removeCriteriaButton;
    private JButton editCriteriaButton;

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

        // Top panel: Destination file name
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Output Settings"));

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

        topPanel.add(fileNamePanel, BorderLayout.NORTH);

        panel.add(topPanel, BorderLayout.NORTH);

        // Center panel: Criteria table
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Export Criteria (AND logic)"));

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
        scrollPane.setPreferredSize(new Dimension(500, 150));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

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

        centerPanel.add(criteriaButtonPanel, BorderLayout.SOUTH);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Enable/disable buttons based on selection
        criteriaTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = criteriaTable.getSelectedRow() >= 0;
            editCriteriaButton.setEnabled(hasSelection);
            removeCriteriaButton.setEnabled(hasSelection);
        });

        return panel;
    }

    private String getDefaultFileName() {
        return schemaClass.destinationName;
    }

    private void loadConfiguration(ClassExportConfig config) {
        // Load destination file name
        if (config.hasCustomDestination()) {
            destinationFileField.setText(config.getRawDestinationFileName());
        }

        // Load criteria
        for (ExportCriteria criterion : config.getCriteria()) {
            addCriteriaToTable(criterion);
        }
    }

    private void addCriteriaToTable(ExportCriteria criterion) {
        Object[] row = {
                criterion.getFieldName(),
                criterion.getOperator().getSymbol(),
                criterion.getValue() != null ? criterion.getValue() : ""
        };
        criteriaTableModel.addRow(row);
    }

    private void addCriteria() {
        System.out.println("DEBUG ClassExportConfigDialog.addCriteria: schemaClass=" + schemaClass);
        if (schemaClass != null) {
            System.out.println("  schemaClass.source=" + schemaClass.source);
            System.out.println(
                    "  schemaClass.fields=" + (schemaClass.fields != null ? schemaClass.fields.length : "null"));
        }
        ExportCriteriaDialog dialog = new ExportCriteriaDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                schemaClass,
                null);
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
        ExportCriteriaDialog dialog = new ExportCriteriaDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                schemaClass,
                currentCriteria);
        dialog.setVisible(true);

        ExportCriteria updatedCriteria = dialog.getResult();
        if (updatedCriteria != null) {
            // Update table row
            criteriaTableModel.setValueAt(updatedCriteria.getFieldName(), selectedRow, 0);
            criteriaTableModel.setValueAt(updatedCriteria.getOperator().getSymbol(), selectedRow, 1);
            criteriaTableModel.setValueAt(updatedCriteria.getValue() != null ? updatedCriteria.getValue() : "",
                    selectedRow, 2);
        }
    }

    private void removeSelectedCriteria() {
        int selectedRow = criteriaTable.getSelectedRow();
        if (selectedRow >= 0) {
            criteriaTableModel.removeRow(selectedRow);
        }
    }

    @Override
    protected boolean validateInput() {
        // Build the ClassExportConfig from form values when OK is clicked
        String destinationFile = destinationFileField.getText().trim();
        if (destinationFile.isEmpty()) {
            destinationFile = null; // Use default
        }

        // Collect criteria
        List<ExportCriteria> criteria = new ArrayList<>();
        for (int i = 0; i < criteriaTableModel.getRowCount(); i++) {
            String fieldName = (String) criteriaTableModel.getValueAt(i, 0);
            String operatorStr = (String) criteriaTableModel.getValueAt(i, 1);
            String value = (String) criteriaTableModel.getValueAt(i, 2);

            ExportCriteria.Operator operator = ExportCriteria.Operator.fromSymbol(operatorStr);
            ExportCriteria criterion = new ExportCriteria(
                    fieldName,
                    operator,
                    value.isEmpty() ? null : value);
            criteria.add(criterion);
        }

        result = new ClassExportConfig(className, destinationFile, criteria);
        return true;
    }

    public ClassExportConfig getResult() {
        return isConfirmed() ? result : null;
    }
}
