package migration4o.ui.dialogs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import migration4o.models.schema.DOSchemaField;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterProvider;

/**
 * Dialog for confirming export operations with options for object limits.
 * Allows users to export all objects or limit to a maximum number per class.
 */
public class ExportConfirmationDialog extends JDialog {

    private boolean confirmed = false;
    private Integer maxObjectsPerClass = null; // null = all objects
    private boolean exportNativeIds = false;
    private String outputFormat = "XML";
    private List<DOSchemaField> availableSkipOptions;
    private List<DOSchemaField> selectedSkipOptions = new ArrayList<>();
    private boolean applyUserSelectedFieldExclusions = true;
    private boolean applySkipWhenConditions = true;
    private boolean applyExportCriteriaFilters = true;
    private boolean skipObjectsWithoutExportableFields = true;

    private JRadioButton allObjectsRadio;
    private JRadioButton limitObjectsRadio;
    private JSpinner limitSpinner;
    private JCheckBox exportNativeIdsCheckbox;
    private JComboBox<String> outputFormatCombo;
    private Map<DOSchemaField, JCheckBox> skipOptionCheckboxes = new HashMap<>();
    private JCheckBox applyUserSelectedFieldExclusionsCheckbox;
    private JCheckBox applySkipWhenConditionsCheckbox;
    private JCheckBox applyExportCriteriaFiltersCheckbox;
    private JCheckBox skipObjectsWithoutExportableFieldsCheckbox;

    /**
     * Creates a new export confirmation dialog.
     * 
     * @param parent               Parent frame
     * @param moduleCount          Number of modules to export
     * @param defaultLimit         Default limit value (used when "Max N objects" is
     *                             selected)
     * @param availableSkipOptions List of fields that can be skipped by user choice
     */
    public ExportConfirmationDialog(Frame parent, int moduleCount, Integer defaultLimit, List<DOSchemaField> availableSkipOptions, String defaultOutputFormat) {
        super(parent, "Confirm Bulk Export", true);

        if (defaultLimit == null || defaultLimit <= 0) {
            defaultLimit = 50; // Default to 50 if not provided
        }

        this.availableSkipOptions = availableSkipOptions != null ? availableSkipOptions : new ArrayList<>();
        if (defaultOutputFormat != null && !defaultOutputFormat.isBlank()) {
            this.outputFormat = defaultOutputFormat;
        }

        initComponents(moduleCount, defaultLimit);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents(int moduleCount, int defaultLimit) {
        setLayout(new BorderLayout(10, 10));

        // Main panel with padding
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Message
        JLabel messageLabel = new JLabel("Export " + moduleCount + " module(s)?");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.BOLD, 14f));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(messageLabel);

        mainPanel.add(Box.createVerticalStrut(15));

        // Options panel
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Export Options"));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Radio button group
        ButtonGroup exportGroup = new ButtonGroup();

        // All objects option
        allObjectsRadio = new JRadioButton("All objects");
        allObjectsRadio.setSelected(true); // Default selection
        allObjectsRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportGroup.add(allObjectsRadio);
        optionsPanel.add(allObjectsRadio);

        optionsPanel.add(Box.createVerticalStrut(8));

        // Limited objects option panel
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        limitPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        limitObjectsRadio = new JRadioButton("Max");
        exportGroup.add(limitObjectsRadio);
        limitPanel.add(limitObjectsRadio);

        // Spinner for limit value
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(defaultLimit, 1, 999999, 10);
        limitSpinner = new JSpinner(spinnerModel);
        limitSpinner.setPreferredSize(new Dimension(80, 25));
        limitSpinner.setEnabled(false); // Disabled until radio selected
        limitPanel.add(limitSpinner);

        limitPanel.add(new JLabel("objects per class"));

        optionsPanel.add(limitPanel);

        // Enable/disable spinner based on radio selection
        allObjectsRadio.addActionListener(e -> limitSpinner.setEnabled(false));
        limitObjectsRadio.addActionListener(e -> {
            limitSpinner.setEnabled(true);
            limitSpinner.requestFocus();
        });

        mainPanel.add(optionsPanel);

        mainPanel.add(Box.createVerticalStrut(10));

        // Additional export options
        JPanel additionalPanel = new JPanel();
        additionalPanel.setLayout(new BoxLayout(additionalPanel, BoxLayout.Y_AXIS));
        additionalPanel.setBorder(BorderFactory.createTitledBorder("Additional Options"));
        additionalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        exportNativeIdsCheckbox = new JCheckBox("Export native object IDs (adds DB4O id attribute to XML)");
        exportNativeIdsCheckbox.setSelected(false); // Off by default
        exportNativeIdsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        additionalPanel.add(exportNativeIdsCheckbox);

        additionalPanel.add(Box.createVerticalStrut(8));
        additionalPanel.add(createFieldExclusionsSection());
        additionalPanel.add(Box.createVerticalStrut(8));
        additionalPanel.add(createConditionalExclusionsSection());

        mainPanel.add(additionalPanel);

        mainPanel.add(Box.createVerticalStrut(10));

        JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        formatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formatPanel.add(new JLabel("Output format:"));

        List<StructuredWriterAPI> formats = StructuredWriterProvider.listFormats();
        java.util.List<String> formatNames = new java.util.ArrayList<>();
        for (StructuredWriterAPI format : formats) {
            if (format != null && format.getName() != null && !format.getName().isBlank()) {
                formatNames.add(format.getName());
            }
        }
        if (formatNames.isEmpty()) {
            formatNames.add("XML");
        }

        outputFormatCombo = new JComboBox<>(formatNames.toArray(new String[0]));
        outputFormatCombo.setPreferredSize(new Dimension(180, 25));
        outputFormatCombo.setSelectedItem(resolveDefaultFormat(formatNames, outputFormat));
        formatPanel.add(outputFormatCombo);

        mainPanel.add(formatPanel);

        mainPanel.add(Box.createVerticalStrut(15));

        // Help text
        JLabel helpLabel = new JLabel("<html><i>Note: Object limits apply per class (e.g., max 50 items from Class A, 50 from Class B, etc.)</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(11f));
        helpLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(helpLabel);

        add(mainPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton exportButton = new JButton("Export");
        exportButton.setPreferredSize(new Dimension(90, 30));
        exportButton.addActionListener(e -> {
            confirmed = true;

            // Set maxObjectsPerClass based on selection
            if (limitObjectsRadio.isSelected()) {
                maxObjectsPerClass = (Integer) limitSpinner.getValue();
            } else {
                maxObjectsPerClass = null; // All objects
            }

            // Get checkbox state
            exportNativeIds = exportNativeIdsCheckbox.isSelected();

            // Update selected skip options one last time
            updateSelectedSkipOptions();

            applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusionsCheckbox.isSelected();
            applySkipWhenConditions = applySkipWhenConditionsCheckbox.isSelected();
            applyExportCriteriaFilters = applyExportCriteriaFiltersCheckbox.isSelected();
            skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFieldsCheckbox.isSelected();

            // Read output format
            Object selected = outputFormatCombo.getSelectedItem();
            outputFormat = selected != null ? selected.toString() : "XML";

            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(90, 30));
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Set default button
        getRootPane().setDefaultButton(exportButton);

        // Handle ESC key
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> {
            confirmed = false;
            dispose();
        }, escapeKeyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Shows the dialog and waits for user response.
     * 
     * @return true if user confirmed export, false if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Gets the maximum objects per class limit.
     * 
     * @return Maximum objects per class, or null for all objects
     */
    public Integer getMaxObjectsPerClass() {
        return maxObjectsPerClass;
    }

    /**
     * Gets whether to export native DB4O object IDs.
     * 
     * @return true if DB4O IDs should be exported as XML attributes
     */
    public boolean getExportNativeIds() {
        return exportNativeIds;
    }

    /**
     * Gets the list of fields that the user has chosen to skip.
     * 
     * @return List of DOSchemaField objects to skip during export
     */
    public List<DOSchemaField> getSelectedSkipOptions() {
        return selectedSkipOptions;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public boolean isApplyUserSelectedFieldExclusions() {
        return applyUserSelectedFieldExclusions;
    }

    public boolean isApplySkipWhenConditions() {
        return applySkipWhenConditions;
    }

    public boolean isApplyExportCriteriaFilters() {
        return applyExportCriteriaFilters;
    }

    public boolean isSkipObjectsWithoutExportableFields() {
        return skipObjectsWithoutExportableFields;
    }

    private String resolveDefaultFormat(List<String> formatNames, String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            return formatNames.get(0);
        }

        for (String formatName : formatNames) {
            if (formatName.equalsIgnoreCase(requestedFormat)) {
                return formatName;
            }
        }

        for (String formatName : formatNames) {
            if ("XML".equalsIgnoreCase(formatName)) {
                return formatName;
            }
        }

        return formatNames.get(0);
    }

    /**
     * Updates the selected skip options list based on checkbox states.
     */
    private void updateSelectedSkipOptions() {
        selectedSkipOptions.clear();
        for (Map.Entry<DOSchemaField, JCheckBox> entry : skipOptionCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedSkipOptions.add(entry.getKey());
            }
        }
    }

    private JPanel createFieldExclusionsSection() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = createBulkActionsPanel(this::checkAllFieldExclusions, this::uncheckAllFieldExclusions);
        content.add(actions);

        if (availableSkipOptions != null && !availableSkipOptions.isEmpty()) {
            for (DOSchemaField field : availableSkipOptions) {
                String label = field.skipUserOption;
                JCheckBox skipCheckbox = new JCheckBox(label);
                skipCheckbox.setSelected(false);
                skipCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
                skipCheckbox.addActionListener(e -> updateSelectedSkipOptions());
                content.add(skipCheckbox);
                skipOptionCheckboxes.put(field, skipCheckbox);
            }
        } else {
            JLabel emptyLabel = new JLabel("No user-selectable field exclusions found.");
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(11f));
            content.add(emptyLabel);
        }

        return createCollapsibleSection("Field exclusions", content, true);
    }

    private JPanel createConditionalExclusionsSection() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = createBulkActionsPanel(this::checkAllConditionalExclusions, this::uncheckAllConditionalExclusions);
        content.add(actions);

        applyUserSelectedFieldExclusionsCheckbox = new JCheckBox("Apply user-selected field exclusions");
        applyUserSelectedFieldExclusionsCheckbox.setSelected(true);
        applyUserSelectedFieldExclusionsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applyUserSelectedFieldExclusionsCheckbox);

        applySkipWhenConditionsCheckbox = new JCheckBox("Apply schema skipWhen conditions (NULL, ZERO, MINUS_ONE, EMPTY_*, FALSE, DEFAULT)");
        applySkipWhenConditionsCheckbox.setSelected(true);
        applySkipWhenConditionsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applySkipWhenConditionsCheckbox);

        applyExportCriteriaFiltersCheckbox = new JCheckBox("Apply class export criteria filters");
        applyExportCriteriaFiltersCheckbox.setSelected(true);
        applyExportCriteriaFiltersCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applyExportCriteriaFiltersCheckbox);

        skipObjectsWithoutExportableFieldsCheckbox = new JCheckBox("Skip objects with no exportable fields");
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(true);
        skipObjectsWithoutExportableFieldsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(skipObjectsWithoutExportableFieldsCheckbox);

        JLabel helpLabel = new JLabel("<html><i>Tip: Uncheck all to export with no conditional exclusions/filtering.</i></html>");
        helpLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpLabel.setFont(helpLabel.getFont().deriveFont(11f));
        content.add(Box.createVerticalStrut(4));
        content.add(helpLabel);

        return createCollapsibleSection("Conditional exclusions", content, true);
    }

    private JPanel createCollapsibleSection(String title, JPanel contentPanel, boolean collapsedByDefault) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        JToggleButton toggle = new JToggleButton();
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setFocusPainted(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);

        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(2, 16, 4, 0));
        contentPanel.setVisible(!collapsedByDefault);

        toggle.setSelected(!collapsedByDefault);
        updateCollapsibleTitle(toggle, title);
        toggle.addActionListener(e -> {
            contentPanel.setVisible(toggle.isSelected());
            updateCollapsibleTitle(toggle, title);
            pack();
        });

        container.add(toggle);
        container.add(contentPanel);
        return container;
    }

    private void updateCollapsibleTitle(AbstractButton button, String title) {
        button.setText((button.isSelected() ? "▾ " : "▸ ") + title);
    }

    private JPanel createBulkActionsPanel(Runnable checkAllAction, Runnable uncheckAllAction) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton checkAllButton = new JButton("Check all");
        checkAllButton.setMargin(new Insets(1, 6, 1, 6));
        checkAllButton.setFont(checkAllButton.getFont().deriveFont(11f));
        checkAllButton.addActionListener(e -> checkAllAction.run());

        JButton uncheckAllButton = new JButton("Uncheck all");
        uncheckAllButton.setMargin(new Insets(1, 6, 1, 6));
        uncheckAllButton.setFont(uncheckAllButton.getFont().deriveFont(11f));
        uncheckAllButton.addActionListener(e -> uncheckAllAction.run());

        actions.add(checkAllButton);
        actions.add(uncheckAllButton);
        return actions;
    }

    private void checkAllFieldExclusions() {
        for (JCheckBox checkBox : skipOptionCheckboxes.values()) {
            checkBox.setSelected(true);
        }
        updateSelectedSkipOptions();
    }

    private void uncheckAllFieldExclusions() {
        for (JCheckBox checkBox : skipOptionCheckboxes.values()) {
            checkBox.setSelected(false);
        }
        updateSelectedSkipOptions();
    }

    private void checkAllConditionalExclusions() {
        applyUserSelectedFieldExclusionsCheckbox.setSelected(true);
        applySkipWhenConditionsCheckbox.setSelected(true);
        applyExportCriteriaFiltersCheckbox.setSelected(true);
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(true);
    }

    private void uncheckAllConditionalExclusions() {
        applyUserSelectedFieldExclusionsCheckbox.setSelected(false);
        applySkipWhenConditionsCheckbox.setSelected(false);
        applyExportCriteriaFiltersCheckbox.setSelected(false);
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(false);
    }

    /**
     * Shows the dialog and returns the result. Convenience method that shows the
     * dialog modally.
     */
    public void showDialog() {
        setVisible(true);
    }
}
