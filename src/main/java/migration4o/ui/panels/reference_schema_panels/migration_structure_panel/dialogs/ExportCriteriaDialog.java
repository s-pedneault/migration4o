package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ExportCriteria;
import migration4o.ui.common.dialogs.BaseFormDialog;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for editing a single ExportCriteria (field, operator, value).
 */
public class ExportCriteriaDialog extends BaseFormDialog {

    private final DOSchemaClass schemaClass;
    private final ExportCriteria initialCriteria;
    private JComboBox<String> fieldNameComboBox;
    private JComboBox<ExportCriteria.Operator> operatorComboBox;
    private JTextField valueField;
    private JLabel valueLabel;

    private ExportCriteria result;

    public ExportCriteriaDialog(Frame owner, DOSchemaClass schemaClass, ExportCriteria existingCriteria) {
        this(owner, existingCriteria == null ? "Add Criteria" : "Edit Criteria", schemaClass, existingCriteria);
    }

    private ExportCriteriaDialog(Frame owner, String title, DOSchemaClass schemaClass,
            ExportCriteria existingCriteria) {
        super(owner, title);
        this.schemaClass = schemaClass;
        this.initialCriteria = existingCriteria;

        // Populate field combo box after dialog is fully constructed
        SwingUtilities.invokeLater(() -> {
            if (fieldNameComboBox != null && schemaClass != null && schemaClass.fields != null) {
                System.out.println("DEBUG ExportCriteriaDialog: Loading fields for " + schemaClass.attributes.source);
                System.out.println("  Field count: " + schemaClass.fields.length);
                for (DOSchemaField field : schemaClass.fields) {
                    System.out.println("  Adding field: " + field.attributes.source);
                    fieldNameComboBox.addItem(field.attributes.source);
                }
            }

            // Load existing criteria if provided (must be done after combo is populated)
            if (initialCriteria != null) {
                loadCriteria(initialCriteria);
            }
        });
    }

    @Override
    protected JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Field name row
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Field Name:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Populate combo box with field names from schema
        fieldNameComboBox = new JComboBox<>();
        fieldNameComboBox.setEditable(true); // Allow custom field names too
        // Fields will be populated after dialog construction via invokeLater
        fieldNameComboBox.setToolTipText("Select or enter the field name");
        panel.add(fieldNameComboBox, gbc);

        // Operator row
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Operator:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        operatorComboBox = new JComboBox<>(ExportCriteria.Operator.values());
        operatorComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ExportCriteria.Operator) {
                    ExportCriteria.Operator op = (ExportCriteria.Operator) value;
                    setText(op.getSymbol() + " - " + op.name());
                }
                return this;
            }
        });
        operatorComboBox.addActionListener(e -> updateValueFieldVisibility());
        panel.add(operatorComboBox, gbc);

        // Value row
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        valueLabel = new JLabel("Value:");
        panel.add(valueLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        valueField = new JTextField(20);
        valueField.setToolTipText("Enter the comparison value (leave empty for IS NULL / IS NOT NULL)");
        panel.add(valueField, gbc);

        // Help text
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel helpLabel = new JLabel("<html><i>Example: Field='mStatut', Operator='==', Value='1'</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN, 11f));
        helpLabel.setForeground(Color.GRAY);
        panel.add(helpLabel, gbc);

        return panel;
    }

    private void updateValueFieldVisibility() {
        ExportCriteria.Operator selectedOp = (ExportCriteria.Operator) operatorComboBox.getSelectedItem();
        boolean needsValue = selectedOp != ExportCriteria.Operator.IS_NULL
                && selectedOp != ExportCriteria.Operator.IS_NOT_NULL;

        valueLabel.setEnabled(needsValue);
        valueField.setEnabled(needsValue);

        if (!needsValue) {
            valueField.setText("");
        }
    }

    private void loadCriteria(ExportCriteria criteria) {
        fieldNameComboBox.setSelectedItem(criteria.getFieldName());
        operatorComboBox.setSelectedItem(criteria.getOperator());
        if (criteria.getValue() != null) {
            valueField.setText(criteria.getValue());
        }
        updateValueFieldVisibility();
    }

    @Override
    protected boolean validateInput() {
        Object selectedItem = fieldNameComboBox.getSelectedItem();
        String fieldName = selectedItem != null ? selectedItem.toString().trim() : "";
        if (fieldName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Field name is required.",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            fieldNameComboBox.requestFocus();
            return false;
        }

        ExportCriteria.Operator operator = (ExportCriteria.Operator) operatorComboBox.getSelectedItem();

        // Check if value is needed but missing
        if (operator != ExportCriteria.Operator.IS_NULL
                && operator != ExportCriteria.Operator.IS_NOT_NULL) {
            String value = valueField.getText().trim();
            if (value.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Value is required for operator: " + operator.getSymbol(),
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                valueField.requestFocus();
                return false;
            }
        }

        // Build the result
        String value = valueField.getText().trim();

        // For IS_NULL and IS_NOT_NULL, value should be null
        if (operator == ExportCriteria.Operator.IS_NULL
                || operator == ExportCriteria.Operator.IS_NOT_NULL) {
            value = null;
        } else if (value.isEmpty()) {
            value = null;
        }

        result = new ExportCriteria(fieldName, operator, value);
        return true;
    }

    public ExportCriteria getResult() {
        return isConfirmed() ? result : null;
    }
}
