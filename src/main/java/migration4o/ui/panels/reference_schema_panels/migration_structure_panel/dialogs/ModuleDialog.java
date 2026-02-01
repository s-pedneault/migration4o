package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import java.awt.GridBagConstraints;
import java.awt.Window;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Dialog for creating or editing migration modules.
 * 
 * This dialog provides a form for entering module name and ID when creating
 * new modules or editing existing ones in the migration structure panel.
 */
public class ModuleDialog extends migration4o.ui.common.dialogs.BaseFormDialog {
    private JTextField nameField;
    private JTextField idField;
    private String initialName;
    private String initialId;

    /**
     * Creates a new module dialog.
     * 
     * @param owner       The parent window
     * @param title       The dialog title
     * @param initialName Initial module name (or null for new module)
     * @param initialId   Initial module ID (or null for new module)
     */
    public ModuleDialog(Window owner, String title, String initialName, String initialId) {
        super(owner, title);

        this.initialName = initialName;
        this.initialId = initialId;

        // Set focus to name field after dialog is built
        SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
    }

    @Override
    protected JPanel buildFormPanel() {
        JPanel panel = createGridBagFormPanel();
        GridBagConstraints gbc = createFormConstraints();

        // Initialize fields here, during buildFormPanel
        nameField = new JTextField(initialName != null ? initialName : "", 20);
        idField = new JTextField(initialId != null ? initialId : "", 20);

        addFormRow(panel, gbc, "Module Name:", nameField);
        addFormRow(panel, gbc, "Module ID:", idField);

        return panel;
    }

    @Override
    protected boolean validateInput() {
        if (nameField.getText().trim().isEmpty()) {
            showValidationError("Module name cannot be empty", nameField);
            return false;
        }

        if (idField.getText().trim().isEmpty()) {
            showValidationError("Module ID cannot be empty", idField);
            return false;
        }

        return true;
    }

    /**
     * Gets the module name entered by the user.
     * 
     * @return The module name (trimmed)
     */
    public String getModuleName() {
        return nameField.getText().trim();
    }

    /**
     * Gets the module ID entered by the user.
     * 
     * @return The module ID (trimmed)
     */
    public String getModuleId() {
        return idField.getText().trim();
    }
}
