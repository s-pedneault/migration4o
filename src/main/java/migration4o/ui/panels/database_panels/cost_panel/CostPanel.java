package migration4o.ui.panels.database_panels.cost_panel;

import javax.swing.*;
import java.awt.*;

import migration4o.models.schema.DOSchema;

/**
 * Panel for displaying cost analysis and estimates for the migration.
 * This panel will be populated with cost calculations based on the database
 * schema.
 */
public class CostPanel extends JPanel {
    private DOSchema databaseSchema;
    private JLabel statusLabel;

    public CostPanel(DOSchema databaseSchema) {
        this.databaseSchema = databaseSchema;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("Migration Cost Analysis");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Center panel - placeholder for now
        JPanel centerPanel = new JPanel(new GridBagLayout());
        JLabel placeholderLabel = new JLabel("Cost analysis will be implemented here");
        placeholderLabel.setForeground(Color.GRAY);
        placeholderLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        centerPanel.add(placeholderLabel);
        add(centerPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * Update the panel when database schema changes.
     */
    public void refresh() {
        // TODO: Implement cost calculation logic
        statusLabel.setText("Schema loaded: " +
                (databaseSchema != null && databaseSchema.getClasses() != null
                        ? databaseSchema.getClasses().length + " classes"
                        : "0 classes"));
    }
}
