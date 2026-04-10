package migration4o.ui.panels.database_panels.database_export_panel;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;

import migration4o.migration.OrganizationExportConfig;
import migration4o.migration.OrganizationExportMode;
import migration4o.migration.OrganizationInfo;

/**
 * Modal dialog shown when a database contains data for multiple organizations.
 * Lets the user choose between a single merged export or separate per-org exports,
 * select which organizations to include, and whether to include general data.
 */
public class OrganizationExportDialog extends JDialog {

    private boolean confirmed = false;
    private JRadioButton singleExportRadio;
    private JRadioButton separateExportRadio;
    private final Map<OrganizationInfo, JCheckBox> orgCheckBoxes = new LinkedHashMap<>();
    private JCheckBox includeGeneralDataCheckbox;

    public OrganizationExportDialog(Window owner, List<OrganizationInfo> organizations) {
        super(owner, "Organization Export Options", ModalityType.APPLICATION_MODAL);
        if (organizations == null || organizations.isEmpty()) {
            throw new IllegalArgumentException("organizations must not be empty");
        }
        initComponents(organizations);
        pack();
        setMinimumSize(new Dimension(420, 300));
        setLocationRelativeTo(owner);
    }

    private void initComponents(List<OrganizationInfo> organizations) {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        // Info label
        JLabel infoLabel = new JLabel("This database contains data for multiple organizations.");
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(infoLabel);

        mainPanel.add(Box.createVerticalStrut(12));

        // Export mode section
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
        modePanel.setBorder(BorderFactory.createTitledBorder("Export Mode"));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ButtonGroup modeGroup = new ButtonGroup();

        singleExportRadio = new JRadioButton("Merge into a single export");
        singleExportRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        singleExportRadio.setSelected(true);
        modeGroup.add(singleExportRadio);
        modePanel.add(singleExportRadio);

        modePanel.add(Box.createVerticalStrut(4));

        separateExportRadio = new JRadioButton("Create a separate export for each organization");
        separateExportRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeGroup.add(separateExportRadio);
        modePanel.add(separateExportRadio);

        mainPanel.add(modePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Separator
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        mainPanel.add(separator);
        mainPanel.add(Box.createVerticalStrut(10));

        // Organizations section
        JPanel orgsPanel = new JPanel();
        orgsPanel.setLayout(new BoxLayout(orgsPanel, BoxLayout.Y_AXIS));
        orgsPanel.setBorder(BorderFactory.createTitledBorder("Organizations to include"));
        orgsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (OrganizationInfo org : organizations) {
            JCheckBox cb = new JCheckBox(org.name() + " (id=" + org.idSSI() + ")");
            cb.setSelected(true);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            orgCheckBoxes.put(org, cb);
            orgsPanel.add(cb);
        }

        mainPanel.add(orgsPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Include general data checkbox
        includeGeneralDataCheckbox = new JCheckBox("Include general data (records not linked to any specific organization)");
        includeGeneralDataCheckbox.setSelected(true);
        includeGeneralDataCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(includeGeneralDataCheckbox);

        // Button row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        getContentPane().setLayout(new java.awt.BorderLayout(10, 10));
        getContentPane().add(mainPanel, java.awt.BorderLayout.CENTER);
        getContentPane().add(buttonPanel, java.awt.BorderLayout.SOUTH);
    }

    private boolean validateInput() {
        for (JCheckBox cb : orgCheckBoxes.values()) {
            if (cb.isSelected()) {
                return true;
            }
        }
        JOptionPane.showMessageDialog(this, "Please select at least one organization to include.", "Validation Error", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Returns {@code true} if the user clicked OK.
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the config reflecting the user's choices.
     * Call only after {@link #isConfirmed()} returns {@code true}.
     */
    public OrganizationExportConfig getConfig() {
        OrganizationExportMode mode = separateExportRadio.isSelected() ? OrganizationExportMode.SEPARATE_PER_ORGANIZATION : OrganizationExportMode.SINGLE_EXPORT;

        List<OrganizationInfo> selected = new ArrayList<>();
        for (Map.Entry<OrganizationInfo, JCheckBox> entry : orgCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }

        return new OrganizationExportConfig(mode, selected, includeGeneralDataCheckbox.isSelected());
    }
}
