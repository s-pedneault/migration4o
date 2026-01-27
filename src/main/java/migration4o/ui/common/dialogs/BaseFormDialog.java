package migration4o.ui.common.dialogs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Base class for form dialogs that provides common functionality:
 * - Standard OK/Cancel button panel
 * - GridBagLayout form builder utility methods
 * - Validation framework
 * - Modal dialog setup
 * 
 * Subclasses should:
 * 1. Call super constructor with owner and title
 * 2. Override buildFormPanel() to create the form content
 * 3. Optionally override validateInput() for form validation
 * 4. Optionally override getAdditionalButtons() for custom buttons (e.g.,
 * Delete)
 */
public abstract class BaseFormDialog extends JDialog {

    protected boolean confirmed = false;
    protected JPanel formPanel;

    /**
     * Creates a new base form dialog.
     * 
     * @param owner The parent window
     * @param title The dialog title
     */
    public BaseFormDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        initializeDialog();
    }

    /**
     * Initializes the dialog layout and components.
     */
    private void initializeDialog() {
        setLayout(new BorderLayout(10, 10));

        // Build the form panel (implemented by subclass)
        formPanel = buildFormPanel();
        add(formPanel, BorderLayout.CENTER);

        // Create button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getOwner());
    }

    /**
     * Builds the form panel with input fields.
     * Subclasses must implement this to create their specific form content.
     * 
     * @return The panel containing form fields
     */
    protected abstract JPanel buildFormPanel();

    /**
     * Validates the input fields. Override this to add custom validation logic.
     * The default implementation returns true (no validation).
     * 
     * @return true if validation passes, false otherwise
     */
    protected boolean validateInput() {
        return true;
    }

    /**
     * Gets additional buttons to add to the button panel (e.g., Delete button).
     * The default implementation returns an empty array.
     * 
     * @return Array of additional buttons, or empty array for none
     */
    protected JButton[] getAdditionalButtons() {
        return new JButton[0];
    }

    /**
     * Creates the standard button panel with OK, Cancel, and any additional
     * buttons.
     * 
     * @return The button panel
     */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Add any custom buttons from subclass (e.g., Delete)
        JButton[] additionalButtons = getAdditionalButtons();
        for (JButton button : additionalButtons) {
            buttonPanel.add(button);
        }

        // OK button
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        // Cancel button
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    /**
     * Checks if the user confirmed the dialog by clicking OK.
     * 
     * @return true if OK was clicked, false otherwise
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Utility method to create a GridBagLayout form panel with proper border.
     * 
     * @return A JPanel with GridBagLayout and padding
     */
    protected JPanel createGridBagFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    /**
     * Utility method to add a labeled row to a GridBagLayout form.
     * 
     * @param panel     The panel to add to
     * @param gbc       The GridBagConstraints to use (will be modified)
     * @param labelText The label text
     * @param component The input component
     */
    protected void addFormRow(JPanel panel, GridBagConstraints gbc, String labelText, JComponent component) {
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;

        JLabel label = new JLabel(labelText);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(component, gbc);

        gbc.gridy++;
    }

    /**
     * Utility method to create standard GridBagConstraints for form rows.
     * 
     * @return GridBagConstraints with standard settings for forms
     */
    protected GridBagConstraints createFormConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    /**
     * Utility method to show a validation error message.
     * 
     * @param message      The error message to display
     * @param fieldToFocus Optional field to focus after showing the message
     */
    protected void showValidationError(String message, JComponent fieldToFocus) {
        JOptionPane.showMessageDialog(this,
                message,
                "Validation Error",
                JOptionPane.ERROR_MESSAGE);
        if (fieldToFocus != null) {
            fieldToFocus.requestFocusInWindow();
        }
    }

    /**
     * Utility method to create a confirmation dialog.
     * 
     * @param message The confirmation message
     * @param title   The dialog title
     * @return true if user confirmed, false otherwise
     */
    protected boolean confirmAction(String message, String title) {
        int result = JOptionPane.showConfirmDialog(this,
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }
}
