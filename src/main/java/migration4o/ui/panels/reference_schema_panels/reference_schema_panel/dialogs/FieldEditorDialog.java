package migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.ui.common.PropertyPanel;
import migration4o.util.TypeUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final JPanel formatPanel;
    private final JCheckBox formatTrim;
    private final JCheckBox formatLowercase;
    private final JCheckBox formatUppercase;
    private final JCheckBox exportedCheckBox;
    private final JPanel skipWhenPanel;
    private final JCheckBox skipWhenNull;
    private final JCheckBox skipWhenZero;
    private final JCheckBox skipWhenMinusOne;
    private final JCheckBox skipWhenEmptyString;
    private final JCheckBox skipWhenEmptyCollection;
    private final JCheckBox skipWhenFalse;
    private final JCheckBox skipWhenDefault;
    private final JTextField skipUserOptionField;
    private final JCheckBox embedContentsCheckBox;
    private final JCheckBox collectionCheckBox;
    private final JPanel childrenTypePanel;
    private final JLabel childrenTypeLabel;
    private final JLabel childrenTypeStatusLabel;
    private final JButton createChildrenClassButton;
    private final JTextField titleField;
    private final JTextField descField;
    private final boolean isNewField;

    private final DefaultTableModel valueMappingTableModel;
    private final JTable valueMappingTable;

    private JPanel formPanel;

    private boolean okClicked = false;
    private boolean deleted = false;
    private String classToCreate = null;
    private String originalFieldDefinitionId = null; // Stores the shared field
                                                     // ID if this is a shared
                                                     // field

    public FieldEditorDialog(Frame owner, DOSchema schema, DOSchemaField field, boolean isNewField) {
        super(owner, isNewField ? "Add Field" : "Edit Field", true);
        this.schema = schema;
        this.isNewField = isNewField;
        this.originalFieldDefinitionId = field.definitionId; // Store original
                                                             // shared field ID
                                                             // if any

        setLayout(new BorderLayout(10, 10));

        // Initialize all fields first
        sourceField = new JTextField(field.source != null ? field.source : "", 30);
        destField = new JTextField(field.destinationName != null ? field.destinationName : "", 30);

        // Type - show as text with Edit button
        typePanel = new JPanel(new BorderLayout(5, 0));
        typeLabel = new JLabel(field.type != null ? field.type : "");
        typeLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        typePanel.add(typeLabel, BorderLayout.CENTER);

        JButton editTypeButton = new JButton("Edit");
        editTypeButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, typeLabel.getText());
            if (selected != null) {
                typeLabel.setText(selected);
                updateEmbedContentsState(); // Update embed contents state when
                                            // type changes
            }
        });
        typePanel.add(editTypeButton, BorderLayout.EAST);

        // Points To - show as text with Edit button
        pointsToPanel = new JPanel(new BorderLayout(5, 0));
        pointsToLabel = new JLabel(field.pointsTo != null ? field.pointsTo : "");
        pointsToLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        pointsToPanel.add(pointsToLabel, BorderLayout.CENTER);

        JButton editPointsToButton = new JButton("Edit");
        editPointsToButton.addActionListener(e -> {
            String selected = ClassFinderDialog.showDialog(owner, schema, pointsToLabel.getText());
            if (selected != null) {
                pointsToLabel.setText(selected);
            }
        });
        pointsToPanel.add(editPointsToButton, BorderLayout.EAST);

        // Create format checkboxes
        formatTrim = new JCheckBox("TRIM");
        formatLowercase = new JCheckBox("LOWERCASE");
        formatUppercase = new JCheckBox("UPPERCASE");

        // Parse existing format value and check appropriate boxes
        if (field.format != null && !field.format.trim().isEmpty()) {
            String[] keywords = field.format.split(",");
            for (String keyword : keywords) {
                String trimmed = keyword.trim();
                switch (trimmed) {
                case "TRIM":
                    formatTrim.setSelected(true);
                    break;
                case "LOWERCASE":
                    formatLowercase.setSelected(true);
                    break;
                case "UPPERCASE":
                    formatUppercase.setSelected(true);
                    break;
                }
            }
        }

        // Create panel with checkboxes in a grid layout
        formatPanel = new JPanel(new GridLayout(0, 3, 5, 2));
        formatPanel.add(formatTrim);
        formatPanel.add(formatLowercase);
        formatPanel.add(formatUppercase);
        formatPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        embedContentsCheckBox = new JCheckBox();
        embedContentsCheckBox.setSelected(field.embedContents);

        collectionCheckBox = new JCheckBox();
        collectionCheckBox.setSelected(field.isCollection);

        // Add listener to collection checkbox to update embed contents state
        // and
        // rebuild form
        collectionCheckBox.addActionListener(e -> {
            updateEmbedContentsState();
            rebuildForm();
        });

        titleField = new JTextField(field.title != null ? field.title : "", 30);
        descField = new JTextField(field.description != null ? field.description : "", 30);

        exportedCheckBox = new JCheckBox();
        exportedCheckBox.setSelected(field.isExported);

        // Create skip when checkboxes
        skipWhenNull = new JCheckBox("NULL");
        skipWhenZero = new JCheckBox("ZERO");
        skipWhenMinusOne = new JCheckBox("MINUS_ONE");
        skipWhenEmptyString = new JCheckBox("EMPTY_STRING");
        skipWhenEmptyCollection = new JCheckBox("EMPTY_COLLECTION");
        skipWhenFalse = new JCheckBox("FALSE");
        skipWhenDefault = new JCheckBox("DEFAULT");

        // Parse existing skipWhen value and check appropriate boxes
        if (field.skipWhen != null && !field.skipWhen.trim().isEmpty()) {
            String[] keywords = field.skipWhen.split(",");
            for (String keyword : keywords) {
                String trimmed = keyword.trim();
                switch (trimmed) {
                case "NULL":
                    skipWhenNull.setSelected(true);
                    break;
                case "ZERO":
                    skipWhenZero.setSelected(true);
                    break;
                case "MINUS_ONE":
                    skipWhenMinusOne.setSelected(true);
                    break;
                case "EMPTY_STRING":
                    skipWhenEmptyString.setSelected(true);
                    break;
                case "EMPTY_COLLECTION":
                    skipWhenEmptyCollection.setSelected(true);
                    break;
                case "FALSE":
                    skipWhenFalse.setSelected(true);
                    break;
                case "DEFAULT":
                    skipWhenDefault.setSelected(true);
                    break;
                }
            }
        }

        // Create panel with checkboxes in a grid layout
        skipWhenPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        skipWhenPanel.add(skipWhenNull);
        skipWhenPanel.add(skipWhenZero);
        skipWhenPanel.add(skipWhenMinusOne);
        skipWhenPanel.add(skipWhenEmptyString);
        skipWhenPanel.add(skipWhenEmptyCollection);
        skipWhenPanel.add(skipWhenFalse);
        skipWhenPanel.add(skipWhenDefault);
        skipWhenPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        skipUserOptionField = new JTextField(field.skipUserOption != null ? field.skipUserOption : "", 30);

        // Children Type - show as text with Edit button and status
        childrenTypePanel = new JPanel(new BorderLayout(5, 0));
        JPanel childrenTypeContentPanel = new JPanel(new BorderLayout(5, 0));

        childrenTypeLabel = new JLabel(field.childrenType != null ? field.childrenType : "");
        childrenTypeLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(3, 5, 3, 5)));
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

        // Value Mapping table
        valueMappingTableModel = new DefaultTableModel(new String[] { "From", "To" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        // Load existing value mappings
        if (field.valueMap != null && !field.valueMap.isEmpty()) {
            for (Map.Entry<String, String> entry : field.valueMap.entrySet()) {
                valueMappingTableModel.addRow(new Object[] { entry.getKey(), entry.getValue() });
            }
        }

        valueMappingTable = new JTable(valueMappingTableModel);
        valueMappingTable.setFillsViewportHeight(true);

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

        // Add shared field warning banner if this is a shared field
        DOSchemaField currentField = getCurrentFieldFromForm();
        if (currentField != null && currentField.isSharedField()) {
            JPanel warningPanel = new JPanel(new BorderLayout(10, 0));
            warningPanel.setBackground(new Color(200, 220, 255));
            warningPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(100, 150, 255), 2), BorderFactory.createEmptyBorder(10, 10, 10, 10)));

            JLabel iconLabel = new JLabel("ℹ");
            iconLabel.setFont(new Font("Dialog", Font.BOLD, 20));
            iconLabel.setForeground(new Color(50, 100, 200));
            warningPanel.add(iconLabel, BorderLayout.WEST);

            JLabel messageLabel = new JLabel("<html><b>Shared Field Definition:</b> " + currentField.definitionId + "<br>Changes made here will affect all classes using this shared field.</html>");
            messageLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
            warningPanel.add(messageLabel, BorderLayout.CENTER);

            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            innerPanel.add(warningPanel, gbc);
            gbc.gridy++;
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
        }

        // Add fields in order
        addFormRow(innerPanel, gbc, "Source:", sourceField);
        addFormRow(innerPanel, gbc, "Destination:", destField);
        addFormRow(innerPanel, gbc, "Type:", typePanel);
        addFormRow(innerPanel, gbc, "Points To:", pointsToPanel);
        addFormRow(innerPanel, gbc, "Title:", titleField);
        addFormRow(innerPanel, gbc, "Description:", descField);
        addFormRow(innerPanel, gbc, "Exported:", exportedCheckBox);
        addFormRow(innerPanel, gbc, "Skip When:", skipWhenPanel);
        addFormRow(innerPanel, gbc, "Skip User Option:", skipUserOptionField);
        addFormRow(innerPanel, gbc, "Format:", formatPanel);
        addFormRow(innerPanel, gbc, "Embed Contents:", embedContentsCheckBox);
        addFormRow(innerPanel, gbc, "Collection:", collectionCheckBox);

        // Only add collection-related fields if collection is checked
        if (collectionCheckBox.isSelected()) {
            addFormRow(innerPanel, gbc, "Children Type:", childrenTypePanel);
        }

        // Add value mapping section
        addValueMappingSection(innerPanel, gbc);

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

    private void addValueMappingSection(JPanel panel, GridBagConstraints gbc) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;

        JLabel label = new JLabel("Value Mappings (from database value to exported value):");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);
        gbc.gridy++;

        // Create table panel with scroll pane and buttons
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));

        JScrollPane scrollPane = new JScrollPane(valueMappingTable);
        scrollPane.setPreferredSize(new Dimension(400, 100));
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> {
            valueMappingTableModel.addRow(new Object[] { "", "" });
        });

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            int selectedRow = valueMappingTable.getSelectedRow();
            if (selectedRow >= 0) {
                valueMappingTableModel.removeRow(selectedRow);
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);

        tablePanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        panel.add(tablePanel, gbc);
        gbc.gridy++;

        // Reset gridwidth
        gbc.gridwidth = 1;
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
                    shouldEnable = typeClass.isIDEntite(schema) || typeClass.isEntite(schema) || !typeClass.isPrimitive();
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
            String shortName = cls.source.contains(".") ? cls.source.substring(cls.source.lastIndexOf('.') + 1) : cls.source;
            if (cls.source.equals(className) || shortName.equals(className)) {
                return cls;
            }
        }
        return null;
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
                String shortName = cls.source.contains(".") ? cls.source.substring(cls.source.lastIndexOf('.') + 1) : cls.source;
                if (cls.source.equals(childrenType) || shortName.equals(childrenType)) {
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
        int result = JOptionPane.showConfirmDialog(this, "Create a new class '" + className + "' in the schema?" + "\n\nThe class will be created when you save this field.", "Create Class", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Mark this class for creation
        classToCreate = className;

        JOptionPane.showMessageDialog(this, "Class '" + className + "' will be created when you save this field.", "Class Creation Scheduled", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Link/Unlink common field buttons
        if (!isNewField) {
            DOSchemaField currentField = getCurrentFieldFromForm();
            if (currentField.isSharedField()) {
                // Field is linked - show unlink button
                JButton unlinkButton = new JButton("Unlink from Common Field");
                unlinkButton.setToolTipText("Import common field properties into this class");
                unlinkButton.addActionListener(e -> unlinkFromCommonField());
                buttonPanel.add(unlinkButton);
            } else if (schema != null && schema.sharedFields != null && schema.sharedFields.containsKey(sourceField.getText())) {
                // Field is not linked but a common field exists - show link
                // button
                JButton linkButton = new JButton("Link to Common Field");
                linkButton.setToolTipText("Use shared field definition");
                linkButton.addActionListener(e -> linkToCommonField());
                buttonPanel.add(linkButton);
            }
        }

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
                int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this field?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

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

    private void linkToCommonField() {
        String fieldName = sourceField.getText();
        DOSchemaField commonField = schema.sharedFields.get(fieldName);

        if (commonField == null) {
            JOptionPane.showMessageDialog(this, "No common field definition found for '" + fieldName + "'.", "Link Failed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirm with user
        int result = JOptionPane.showConfirmDialog(this, "Link this field to the common field definition '" + fieldName + "'?\n\n" + "The field will inherit all properties from the shared definition.\n" + "Any custom properties in this field will be replaced.", "Confirm Link", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Copy all properties from common field
        destField.setText(commonField.destinationName != null ? commonField.destinationName : "");
        typeLabel.setText(commonField.type != null ? commonField.type : "");
        pointsToLabel.setText(commonField.pointsTo != null ? commonField.pointsTo : "");
        exportedCheckBox.setSelected(commonField.isExported);
        embedContentsCheckBox.setSelected(commonField.embedContents);
        collectionCheckBox.setSelected(commonField.isCollection);
        childrenTypeLabel.setText(commonField.childrenType != null ? commonField.childrenType : "");
        titleField.setText(commonField.title != null ? commonField.title : "");
        descField.setText(commonField.description != null ? commonField.description : "");
        skipUserOptionField.setText(commonField.skipUserOption != null ? commonField.skipUserOption : "");
        formatTrim.setSelected(false);
        formatLowercase.setSelected(false);
        formatUppercase.setSelected(false);

        if (commonField.format != null && !commonField.format.trim().isEmpty()) {
            String[] keywords = commonField.format.split(",");
            for (String keyword : keywords) {
                String trimmed = keyword.trim();
                switch (trimmed) {
                case "TRIM":
                    formatTrim.setSelected(true);
                    break;
                case "LOWERCASE":
                    formatLowercase.setSelected(true);
                    break;
                case "UPPERCASE":
                    formatUppercase.setSelected(true);
                    break;
                }
            }
        }

        // Parse and set skipWhen checkboxes
        skipWhenNull.setSelected(false);
        skipWhenZero.setSelected(false);
        skipWhenMinusOne.setSelected(false);
        skipWhenEmptyString.setSelected(false);
        skipWhenEmptyCollection.setSelected(false);
        skipWhenFalse.setSelected(false);
        skipWhenDefault.setSelected(false);

        if (commonField.skipWhen != null && !commonField.skipWhen.trim().isEmpty()) {
            String[] keywords = commonField.skipWhen.split(",");
            for (String keyword : keywords) {
                String trimmed = keyword.trim();
                switch (trimmed) {
                case "NULL":
                    skipWhenNull.setSelected(true);
                    break;
                case "ZERO":
                    skipWhenZero.setSelected(true);
                    break;
                case "MINUS_ONE":
                    skipWhenMinusOne.setSelected(true);
                    break;
                case "EMPTY_STRING":
                    skipWhenEmptyString.setSelected(true);
                    break;
                case "EMPTY_COLLECTION":
                    skipWhenEmptyCollection.setSelected(true);
                    break;
                case "FALSE":
                    skipWhenFalse.setSelected(true);
                    break;
                case "DEFAULT":
                    skipWhenDefault.setSelected(true);
                    break;
                }
            }
        }

        // Set the definition ID to mark as shared field
        originalFieldDefinitionId = fieldName;

        // Rebuild form to show shared field banner
        rebuildForm();

        JOptionPane.showMessageDialog(this, "Field linked to common definition '" + fieldName + "'.\n" + "Click OK to save changes.", "Link Successful", JOptionPane.INFORMATION_MESSAGE);
    }

    private void unlinkFromCommonField() {
        String definitionId = originalFieldDefinitionId;

        if (definitionId == null) {
            return;
        }

        // Confirm with user
        int result = JOptionPane.showConfirmDialog(this, "Unlink this field from common definition '" + definitionId + "'?\n\n" + "The current properties will be imported into this class's field definition.\n" + "Future changes to the common field will no longer affect this field.", "Confirm Unlink", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // Clear the definition ID to unlink
        originalFieldDefinitionId = null;

        // Rebuild form to hide shared field banner
        rebuildForm();

        JOptionPane.showMessageDialog(this, "Field unlinked from common definition.\n" + "The field now has its own independent definition.\n" + "Click OK to save changes.", "Unlink Successful", JOptionPane.INFORMATION_MESSAGE);
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

    public String getFieldFormat() {
        java.util.List<String> keywords = new java.util.ArrayList<>();
        if (formatTrim.isSelected())
            keywords.add("TRIM");
        if (formatLowercase.isSelected())
            keywords.add("LOWERCASE");
        if (formatUppercase.isSelected())
            keywords.add("UPPERCASE");
        return keywords.isEmpty() ? null : String.join(",", keywords);
    }

    public String getFieldSkipWhen() {
        java.util.List<String> keywords = new java.util.ArrayList<>();
        if (skipWhenNull.isSelected())
            keywords.add("NULL");
        if (skipWhenZero.isSelected())
            keywords.add("ZERO");
        if (skipWhenMinusOne.isSelected())
            keywords.add("MINUS_ONE");
        if (skipWhenEmptyString.isSelected())
            keywords.add("EMPTY_STRING");
        if (skipWhenEmptyCollection.isSelected())
            keywords.add("EMPTY_COLLECTION");
        if (skipWhenFalse.isSelected())
            keywords.add("FALSE");
        if (skipWhenDefault.isSelected())
            keywords.add("DEFAULT");
        return keywords.isEmpty() ? null : String.join(",", keywords);
    }

    public boolean isFieldCollection() {
        return collectionCheckBox.isSelected();
    }

    public String getFieldSkipUserOption() {
        String value = skipUserOptionField.getText();
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
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

    public Map<String, String> getValueMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (int i = 0; i < valueMappingTableModel.getRowCount(); i++) {
            String from = (String) valueMappingTableModel.getValueAt(i, 0);
            String to = (String) valueMappingTableModel.getValueAt(i, 1);
            if (from != null && !from.trim().isEmpty() && to != null && !to.trim().isEmpty()) {
                mappings.put(from.trim(), to.trim());
            }
        }
        return mappings.isEmpty() ? null : mappings;
    }

    /**
     * Helper method to get current field state from form (used for checking if
     * shared field).
     */
    private DOSchemaField getCurrentFieldFromForm() {
        DOSchemaField field = new DOSchemaField();
        field.source = getFieldSource();
        field.destinationName = getFieldDestination();
        field.type = getFieldType();
        field.format = getFieldFormat();
        field.isExported = isFieldExported();
        field.skipWhen = getFieldSkipWhen();
        field.skipUserOption = getFieldSkipUserOption();
        field.isCollection = isFieldCollection();
        field.embedContents = isFieldEmbedContents();
        field.childrenType = getFieldChildrenType();
        field.title = getFieldTitle();
        field.description = getFieldDescription();
        field.pointsTo = getFieldPointsTo();
        field.valueMap = getValueMappings();
        field.definitionId = originalFieldDefinitionId; // Preserve the shared
                                                        // field ID
        return field;
    }

    /**
     * Get the name of the class that should be created (if Create Class button
     * was clicked).
     * 
     * @return class name to create, or null if no class should be created
     */
    public String getClassToCreate() {
        return classToCreate;
    }

    /**
     * Get the field definition ID (for shared field references).
     * 
     * @return the definition ID if this field is linked to a common field, null
     * otherwise
     */
    public String getFieldDefinitionId() {
        return originalFieldDefinitionId;
    }

    /**
     * Show the dialog and return whether OK was clicked.
     * 
     * @param owner The parent frame
     * @param schema The schema containing classes
     * @param field The field to edit
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
