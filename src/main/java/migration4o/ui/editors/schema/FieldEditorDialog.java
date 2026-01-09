package migration4o.ui.editors.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.ui.components.PropertyPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for editing a schema field.
 */
public class FieldEditorDialog extends JDialog {

    private final DOSchema schema;
    private final JTextField sourceField;
    private final JTextField destField;
    private final JLabel typeLabel;
    private final JCheckBox exportedCheckBox;
    private final JCheckBox skipIfEmptyCheckBox;
    private final JCheckBox collectionCheckBox;
    private final JCheckBox embedContentsCheckBox;
    private final JLabel childrenTypeLabel;
    private final JTextField titleField;
    private final JTextField descField;

    private boolean okClicked = false;

    public FieldEditorDialog(Frame owner, DOSchema schema, DOSchemaField field) {
        super(owner, "Edit Field", true);
        this.schema = schema;

        setLayout(new BorderLayout(10, 10));

        // Create form panel
        PropertyPanel formPanel = new PropertyPanel();
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add fields
        sourceField = formPanel.addTextField("Source", field.getSource() != null ? field.getSource() : "");
        destField = formPanel.addTextField("Destination",
                field.getDestinationName() != null ? field.getDestinationName() : "");

        // Type - show as text with Edit button
        JPanel typePanel = new JPanel(new BorderLayout(5, 0));
        typeLabel = new JLabel(field.getType() != null ? field.getType() : "");
        typeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        typePanel.add(typeLabel, BorderLayout.CENTER);

        JButton editTypeButton = new JButton("Edit");
        editTypeButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, typeLabel.getText());
            if (selected != null) {
                typeLabel.setText(selected);
            }
        });
        typePanel.add(editTypeButton, BorderLayout.EAST);
        formPanel.addCustomField("Type", typePanel);

        exportedCheckBox = formPanel.addCheckBox("Exported", field.isExported());
        skipIfEmptyCheckBox = formPanel.addCheckBox("Skip If Empty", field.isSkipIfEmpty());
        collectionCheckBox = formPanel.addCheckBox("Collection", field.isCollection());
        embedContentsCheckBox = formPanel.addCheckBox("Embed Contents", field.isEmbedContents());

        // Children Type - show as text with Edit button
        JPanel childrenTypePanel = new JPanel(new BorderLayout(5, 0));
        childrenTypeLabel = new JLabel(field.getChildrenType() != null ? field.getChildrenType() : "");
        childrenTypeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        childrenTypePanel.add(childrenTypeLabel, BorderLayout.CENTER);

        JButton editChildrenTypeButton = new JButton("Edit");
        editChildrenTypeButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, childrenTypeLabel.getText());
            if (selected != null) {
                childrenTypeLabel.setText(selected);
            }
        });
        childrenTypePanel.add(editChildrenTypeButton, BorderLayout.EAST);
        formPanel.addCustomField("Children Type", childrenTypePanel);

        titleField = formPanel.addTextField("Title", field.getTitle() != null ? field.getTitle() : "");
        descField = formPanel.addTextField("Description",
                field.getDescription() != null ? field.getDescription() : "");

        add(formPanel, BorderLayout.CENTER);

        // Create button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        pack();
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            okClicked = true;
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public String getFieldSource() {
        return sourceField.getText();
    }

    public String getFieldDestination() {
        return destField.getText();
    }

    public String getFieldType() {
        return typeLabel.getText();
    }

    public boolean isFieldExported() {
        return exportedCheckBox.isSelected();
    }

    public boolean isFieldSkipIfEmpty() {
        return skipIfEmptyCheckBox.isSelected();
    }

    public boolean isFieldCollection() {
        return collectionCheckBox.isSelected();
    }

    public boolean isFieldEmbedContents() {
        return embedContentsCheckBox.isSelected();
    }

    public String getFieldChildrenType() {
        return childrenTypeLabel.getText();
    }

    public String getFieldTitle() {
        return titleField.getText();
    }

    public String getFieldDescription() {
        return descField.getText();
    }

    /**
     * Show the dialog and return whether OK was clicked.
     * 
     * @param owner  The parent frame
     * @param schema The schema containing classes
     * @param field  The field to edit
     * @return The dialog instance if OK was clicked, null otherwise
     */
    public static FieldEditorDialog showDialog(Frame owner, DOSchema schema, DOSchemaField field) {
        FieldEditorDialog dialog = new FieldEditorDialog(owner, schema, field);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return dialog.isOkClicked() ? dialog : null;
    }
}
