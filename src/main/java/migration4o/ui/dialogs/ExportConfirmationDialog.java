package migration4o.ui.dialogs;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for confirming export operations with options for object limits.
 * Allows users to export all objects or limit to a maximum number per class.
 */
public class ExportConfirmationDialog extends JDialog {

    private boolean confirmed = false;
    private Integer maxObjectsPerClass = null; // null = all objects
    private boolean exportNativeIds = false;

    private JRadioButton allObjectsRadio;
    private JRadioButton limitObjectsRadio;
    private JSpinner limitSpinner;
    private JCheckBox exportNativeIdsCheckbox;

    /**
     * Creates a new export confirmation dialog.
     * 
     * @param parent       Parent frame
     * @param moduleCount  Number of modules to export
     * @param defaultLimit Default limit value (used when "Max N objects" is
     *                     selected)
     */
    public ExportConfirmationDialog(Frame parent, int moduleCount, Integer defaultLimit) {
        super(parent, "Confirm Bulk Export", true);

        if (defaultLimit == null || defaultLimit <= 0) {
            defaultLimit = 50; // Default to 50 if not provided
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
        JLabel messageLabel = new JLabel("Export " + moduleCount + " module(s) to XML?");
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

        mainPanel.add(additionalPanel);

        mainPanel.add(Box.createVerticalStrut(15));

        // Help text
        JLabel helpLabel = new JLabel(
                "<html><i>Note: Object limits apply per class (e.g., max 50 items from Class A, 50 from Class B, etc.)</i></html>");
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
     * Shows the dialog and returns the result.
     * Convenience method that shows the dialog modally.
     */
    public void showDialog() {
        setVisible(true);
    }
}
