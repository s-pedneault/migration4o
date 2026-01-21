package migration4o.ui.editors.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.ui.components.PropertyPanel;
import migration4o.util.TypeUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for editing a schema field.
 */
public class FieldEditorDialog extends JDialog {

    private final DOSchema schema;
    private final JTextField sourceField;
    private final JTextField destField;
    private final JPanel typePanel;
    private final JLabel typeLabel;
    private final JPanel pointsToPanel;
    private final JLabel pointsToLabel;
    private final JCheckBox exportedCheckBox;
    private final JCheckBox skipIfEmptyCheckBox;
    private final JCheckBox embedContentsCheckBox;
    private final JCheckBox collectionCheckBox;
    private final JPanel childrenTypePanel;
    private final JLabel childrenTypeLabel;
    private final JLabel childrenTypeStatusLabel;
    private final JButton createChildrenClassButton;
    private final JTextField titleField;
    private final JTextField descField;
    private final boolean isNewField;

    private JPanel formPanel;

    private boolean okClicked = false;
    private boolean deleted = false;
    private String classToCreate = null;

    public FieldEditorDialog(Frame owner, DOSchema schema, DOSchemaField field, boolean isNewField) {
        super(owner, isNewField ? "Add Field" : "Edit Field", true);
        this.schema = schema;
        this.isNewField = isNewField;

        setLayout(new BorderLayout(10, 10));

        // Initialize all fields first
        sourceField = new JTextField(field.source != null ? field.source : "", 30);
        destField = new JTextField(field.destinationName != null ? field.destinationName : "", 30);

        // Type - show as text with Edit button
        typePanel = new JPanel(new BorderLayout(5, 0));
        typeLabel = new JLabel(field.type != null ? field.type : "");
        typeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        typePanel.add(typeLabel, BorderLayout.CENTER);

        JButton editTypeButton = new JButton("Edit");
        editTypeButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, typeLabel.getText());
            if (selected != null) {
                typeLabel.setText(selected);
                updateEmbedContentsState(); // Update embed contents state when type changes
            }
        });
        typePanel.add(editTypeButton, BorderLayout.EAST);

        // Points To - show as text with Edit button
        pointsToPanel = new JPanel(new BorderLayout(5, 0));
        pointsToLabel = new JLabel(field.pointsTo != null ? field.pointsTo : "");
        pointsToLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        pointsToPanel.add(pointsToLabel, BorderLayout.CENTER);

        JButton editPointsToButton = new JButton("Edit");
        editPointsToButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, pointsToLabel.getText());
            if (selected != null) {
                pointsToLabel.setText(selected);
            }
        });
        pointsToPanel.add(editPointsToButton, BorderLayout.EAST);

        embedContentsCheckBox = new JCheckBox();
        embedContentsCheckBox.setSelected(field.embedContents);

        collectionCheckBox = new JCheckBox();
        collectionCheckBox.setSelected(field.isCollection);

        // Add listener to collection checkbox to update embed contents state and
        // rebuild form
        collectionCheckBox.addActionListener(e -> {
            updateEmbedContentsState();
            rebuildForm();
        });

        titleField = new JTextField(field.title != null ? field.title : "", 30);
        descField = new JTextField(field.description != null ? field.description : "", 30);

        exportedCheckBox = new JCheckBox();
        exportedCheckBox.setSelected(field.isExported);

        skipIfEmptyCheckBox = new JCheckBox();
        skipIfEmptyCheckBox.setSelected(field.skipIfEmpty);

        // Children Type - show as text with Edit button and status
        childrenTypePanel = new JPanel(new BorderLayout(5, 0));
        JPanel childrenTypeContentPanel = new JPanel(new BorderLayout(5, 0));

        childrenTypeLabel = new JLabel(field.childrenType != null ? field.childrenType : "");
        childrenTypeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        childrenTypeContentPanel.add(childrenTypeLabel, BorderLayout.CENTER);

        JButton editChildrenTypeButton = new JButton("Edit");
        editChildrenTypeButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, childrenTypeLabel.getText());
            if (selected != null) {
                childrenTypeLabel.setText(selected);
                updateChildrenTypeStatus();
            }
        });
        childrenTypeContentPanel.add(editChildrenTypeButton, BorderLayout.EAST);

        childrenTypePanel.add(childrenTypeContentPanel, BorderLayout.CENTER);

        // Status and Create button panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        childrenTypeStatusLabel = new JLabel();
        statusPanel.add(childrenTypeStatusLabel);

        createChildrenClassButton = new JButton("Create Class");
        createChildrenClassButton.setVisible(false);
        createChildrenClassButton.addActionListener(e -> createChildrenClass());
        statusPanel.add(createChildrenClassButton);

        childrenTypePanel.add(statusPanel, BorderLayout.SOUTH);

        // Build the form initially
        rebuildForm();

        // Create button panel
        add(createButtonPanel(), BorderLayout.SOUTH);
        updateEmbedContentsState();

        // Update initial status
        updateChildrenTypeStatus();

        pack();
    }

    private void rebuildForm() {
        // Remove old form if it exists
        if (formPanel != null) {
            remove(formPanel);
        }

        // Create new form panel with GridBagLayout
        JPanel innerPanel = new JPanel(new GridBagLayout());
        innerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(5, 10, 5, 10);

        // Add fields in order
        addFormRow(innerPanel, gbc, "Source:", sourceField);
        addFormRow(innerPanel, gbc, "Type:", typePanel);
        addFormRow(innerPanel, gbc, "Points To:", pointsToPanel);
        addFormRow(innerPanel, gbc, "Title:", titleField);
        addFormRow(innerPanel, gbc, "Description:", descField);
        addFormRow(innerPanel, gbc, "Exported:", exportedCheckBox);
        addFormRow(innerPanel, gbc, "Skip If Empty:", skipIfEmptyCheckBox);
        addFormRow(innerPanel, gbc, "Embed Contents:", embedContentsCheckBox);
        addFormRow(innerPanel, gbc, "Collection:", collectionCheckBox);

        // Only add collection-related fields if collection is checked
        if (collectionCheckBox.isSelected()) {
            addFormRow(innerPanel, gbc, "Children Type:", childrenTypePanel);
        }

        // Add a filler component to push everything to the top
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        innerPanel.add(Box.createVerticalGlue(), gbc);

        // Wrap in scroll pane for better sizing
        formPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(innerPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formPanel.add(scrollPane, BorderLayout.CENTER);

        add(formPanel, BorderLayout.CENTER);

        setMinimumSize(new Dimension(700, 500));
        setPreferredSize(new Dimension(800, 650));

        revalidate();
        repaint();
        pack();
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, String labelText, JComponent component) {
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(component, gbc);

        gbc.gridy++;
    }

    private void updateEmbedContentsState() {
        // Embed Contents is enabled if:
        // 1. Collection checkbox is checked, OR
        // 2. The selected type is an IDEntite or EntiteContientID descendant

        boolean shouldEnable = collectionCheckBox.isSelected();

        if (!shouldEnable) {
            // Check if the type is an IDEntite or EntiteContientID descendant
            String typeName = typeLabel.getText();
            if (typeName != null && !typeName.isEmpty() && schema != null) {
                DOSchemaClass typeClass = findClassByName(typeName);
                if (typeClass != null) {
                    shouldEnable = isDescendantOf(typeClass, "gest.gen.IDEntite") ||
                            isDescendantOf(typeClass, "gest.gen.EntiteContientID");
                }
            }
        }

        embedContentsCheckBox.setEnabled(shouldEnable);

        // If being disabled, uncheck it
        if (!shouldEnable && embedContentsCheckBox.isSelected()) {
            embedContentsCheckBox.setSelected(false);
        }
    }

    private DOSchemaClass findClassByName(String className) {
        if (schema == null || schema.getClasses() == null || className == null) {
            return null;
        }

        for (DOSchemaClass cls : schema.getClasses()) {
            if (cls.getAbsoluteName().equals(className) || cls.getShortName().equals(className)) {
                return cls;
            }
        }
        return null;
    }

    private boolean isDescendantOf(DOSchemaClass schemaClass, String parentClassName) {
        if (schemaClass == null || parentClassName == null) {
            return false;
        }

        String currentParent = schemaClass.getParentClass();
        while (currentParent != null && !currentParent.isEmpty() && !currentParent.equals("Undetermined")) {
            if (currentParent.equals(parentClassName)) {
                return true;
            }

            // Find parent class and continue up the hierarchy
            DOSchemaClass parentClass = findClassByName(currentParent);
            if (parentClass == null) {
                break;
            }
            currentParent = parentClass.getParentClass();
        }

        return false;
    }

    private void updateChildrenTypeStatus() {
        String childrenType = childrenTypeLabel.getText();

        if (childrenType == null || childrenType.isEmpty()) {
            childrenTypeStatusLabel.setText("");
            childrenTypeStatusLabel.setForeground(Color.BLACK);
            createChildrenClassButton.setVisible(false);
            return;
        }

        // Check if it's a primitive type
        if (TypeUtil.isPrimitiveType(childrenType)) {
            childrenTypeStatusLabel.setText("✓ Primitive type");
            childrenTypeStatusLabel.setForeground(new Color(0, 150, 0));
            createChildrenClassButton.setVisible(false);
            return;
        }

        // Check if it's a class in our schema
        boolean isResolved = false;
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                if (cls.getAbsoluteName().equals(childrenType) || cls.getShortName().equals(childrenType)) {
                    isResolved = true;
                    break;
                }
            }
        }

        if (isResolved) {
            childrenTypeStatusLabel.setText("✓ Class found");
            childrenTypeStatusLabel.setForeground(new Color(0, 150, 0));
            createChildrenClassButton.setVisible(false);
        } else {
            childrenTypeStatusLabel.setText("⚠ Unresolved type");
            childrenTypeStatusLabel.setForeground(new Color(200, 0, 0));
            createChildrenClassButton.setVisible(true);
        }
    }

    private void createChildrenClass() {
        String className = childrenTypeLabel.getText();
        if (className == null || className.isEmpty()) {
            return;
        }

        // Confirm with user
        int result = JOptionPane.showConfirmDialog(this,
                "Create a new class '" + className + "' in the schema?" +
                        "\n\nThe class will be created when you save this field.",
                "Create Class",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Mark this class for creation
        classToCreate = className;

        JOptionPane.showMessageDialog(this,
                "Class '" + className + "' will be created when you save this field.",
                "Class Creation Scheduled",
                JOptionPane.INFORMATION_MESSAGE);
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

        // Add delete button for existing fields
        if (!isNewField) {
            JButton deleteButton = new JButton("Delete");
            deleteButton.setForeground(Color.RED);
            deleteButton.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to delete this field?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    deleted = true;
                    okClicked = true;
                    dispose();
                }
            });
            buttonPanel.add(deleteButton);
        }

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public boolean isDeleted() {
        return deleted;
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

    public String getFieldPointsTo() {
        String pointsTo = pointsToLabel.getText();
        return (pointsTo != null && !pointsTo.isEmpty()) ? pointsTo : null;
    }

    /**
     * Get the name of the class that should be created (if Create Class button was
     * clicked).
     * 
     * @return class name to create, or null if no class should be created
     */
    public String getClassToCreate() {
        return classToCreate;
    }

    /**
     * Show the dialog and return whether OK was clicked.
     * 
     * @param owner      The parent frame
     * @param schema     The schema containing classes
     * @param field      The field to edit
     * @param isNewField Whether this is a new field being added
     * @return The dialog instance if OK was clicked, null otherwise
     */
    public static FieldEditorDialog showDialog(Frame owner, DOSchema schema, DOSchemaField field, boolean isNewField) {
        FieldEditorDialog dialog = new FieldEditorDialog(owner, schema, field, isNewField);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return dialog.isOkClicked() ? dialog : null;
    }
}
