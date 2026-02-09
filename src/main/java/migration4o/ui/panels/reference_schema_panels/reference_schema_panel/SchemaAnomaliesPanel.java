package migration4o.ui.panels.reference_schema_panels.reference_schema_panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import migration4o.models.schema.analysis.DOSchemaAnomaly;
import migration4o.models.schema.analysis.DOSchemaDataLossAnomaly;
import migration4o.models.schema.analysis.DOSchemaEmbeddingAnomaly;
import migration4o.models.schema.analysis.DOSchemaMissingFieldClass;
import migration4o.models.schema.analysis.DOSchemaReferenceAnomaly;
import migration4o.models.schema.analysis.DOSchemaSharedEmbeddedAnomaly;
import migration4o.models.schema.analysis.DOSchemaSharedNotExportedAnomaly;
import migration4o.models.schema.analysis.DOSchemaShouldBeEmbeddedAnomaly;
import migration4o.models.schema.analysis.DOSchemaShouldNotBeExportedAnomaly;
import migration4o.schema.DOReferenceSchemaWriter;

/**
 * Collapsible panel that displays schema anomalies detected during schema
 * loading.
 */
public class SchemaAnomaliesPanel extends JPanel {

    /**
     * Callback interface for handling anomaly navigation.
     */
    public interface AnomalyNavigationCallback {
        void navigateToAnomaly(DOSchemaAnomaly anomaly, boolean openEditor);
    }

    private final JLabel headerLabel;
    private final JPanel contentPanel;
    private final JTable anomaliesTable;
    private final DefaultTableModel tableModel;
    private boolean collapsed = false;
    private List<DOSchemaAnomaly> currentAnomalies;
    private AnomalyNavigationCallback navigationCallback;

    public SchemaAnomaliesPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        // Header panel with toggle
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(240, 240, 240));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        headerLabel = new JLabel("▼ Schema Anomalies (0)");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(headerLabel, BorderLayout.WEST);

        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapsed();
            }
        });

        add(headerPanel, BorderLayout.NORTH);

        // Content panel with table
        contentPanel = new JPanel(new BorderLayout());

        String[] columnNames = { "Type", "Class", "Field", "Explanation" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        anomaliesTable = new JTable(tableModel);
        anomaliesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        anomaliesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        anomaliesTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        anomaliesTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        anomaliesTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        anomaliesTable.getColumnModel().getColumn(3).setPreferredWidth(400);

        // Custom renderer for type column
        anomaliesTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String type = (String) value;
                    if (type != null && type.contains("CRITICAL DATA LOSS")) {
                        c.setForeground(Color.RED); // Bright red for critical data loss
                        c.setFont(c.getFont().deriveFont(java.awt.Font.BOLD)); // Bold
                    } else if (type != null && type.contains("Reference")) {
                        c.setForeground(new Color(0, 100, 200)); // Blue for reference anomalies
                        c.setFont(c.getFont().deriveFont(java.awt.Font.PLAIN));
                    } else if (type != null && type.contains("Missing Field Class")) {
                        c.setForeground(new Color(200, 0, 0)); // Red for missing class anomalies
                        c.setFont(c.getFont().deriveFont(java.awt.Font.PLAIN));
                    } else if (type != null && type.contains("Embedding")) {
                        c.setForeground(new Color(200, 100, 0)); // Orange for embedding anomalies
                        c.setFont(c.getFont().deriveFont(java.awt.Font.PLAIN));
                    } else {
                        c.setForeground(Color.BLACK);
                        c.setFont(c.getFont().deriveFont(java.awt.Font.PLAIN));
                    }
                }
                return c;
            }
        });

        // Add mouse listener for navigation and context menu
        anomaliesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = anomaliesTable.rowAtPoint(e.getPoint());
                if (row >= 0 && currentAnomalies != null && row < currentAnomalies.size()
                        && navigationCallback != null) {
                    DOSchemaAnomaly anomaly = currentAnomalies.get(row);
                    // Single click: select class only, Double click: select class and open field
                    // editor
                    if (!SwingUtilities.isRightMouseButton(e)) {
                        navigationCallback.navigateToAnomaly(anomaly, e.getClickCount() == 2);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(anomaliesTable);
        scrollPane.setPreferredSize(new java.awt.Dimension(0, 150));
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * Set the callback for handling anomaly navigation.
     */
    public void setNavigationCallback(AnomalyNavigationCallback callback) {
        this.navigationCallback = callback;
    }

    /**
     * Update the panel with new anomalies.
     */
    public void setAnomalies(List<DOSchemaAnomaly> anomalies) {
        // Store anomalies for navigation
        this.currentAnomalies = anomalies;

        // Clear existing rows
        tableModel.setRowCount(0);

        if (anomalies == null || anomalies.isEmpty()) {
            headerLabel.setText("▼ Schema Anomalies (0)");
            setVisible(false);
            return;
        }

        // Add rows for each anomaly
        for (DOSchemaAnomaly anomaly : anomalies) {
            String type = getAnomalyType(anomaly);
            String className = anomaly.schemaClass != null ? anomaly.schemaClass.source : "";
            String fieldName = anomaly.schemaField != null ? anomaly.schemaField.source : "";
            String explanation = anomaly.explanation != null ? anomaly.explanation : "";

            tableModel.addRow(new Object[] { type, className, fieldName, explanation });
        }

        headerLabel.setText(String.format("▼ Schema Anomalies (%d)", anomalies.size()));
        setVisible(true);
    }

    /**
     * Show context menu for anomaly row.
     */
    private void showContextMenu(MouseEvent e) {
        int row = anomaliesTable.rowAtPoint(e.getPoint());
        if (row < 0 || currentAnomalies == null || row >= currentAnomalies.size()) {
            return;
        }

        // Select the row
        anomaliesTable.setRowSelectionInterval(row, row);

        DOSchemaAnomaly anomaly = currentAnomalies.get(row);

        JPopupMenu popup = new JPopupMenu();

        // "Edit field" menu item for all anomalies
        JMenuItem editFieldItem = new JMenuItem("Edit field");
        editFieldItem.addActionListener(evt -> {
            if (navigationCallback != null) {
                navigationCallback.navigateToAnomaly(anomaly, true);
            }
        });
        popup.add(editFieldItem);

        // Special menu items for data loss anomalies
        if (anomaly instanceof DOSchemaDataLossAnomaly) {
            popup.addSeparator();

            JMenuItem fixSingleItem = new JMenuItem("Make field contents embedded");
            fixSingleItem.addActionListener(evt -> {
                fixSingleDataLossAnomaly((DOSchemaDataLossAnomaly) anomaly);
            });
            popup.add(fixSingleItem);

            JMenuItem fixAllItem = new JMenuItem("Make ALL field contents embedded");
            fixAllItem.addActionListener(evt -> {
                fixAllDataLossAnomalies();
            });
            popup.add(fixAllItem);
        }

        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Fix a single data loss anomaly by setting embedContents=true on the field.
     */
    private void fixSingleDataLossAnomaly(DOSchemaDataLossAnomaly anomaly) {
        if (anomaly.schemaField == null) {
            JOptionPane.showMessageDialog(this,
                    "Cannot fix anomaly: No field information available.",
                    "Fix Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Set embedContents to true
        anomaly.schemaField.embedContents = true;

        JOptionPane.showMessageDialog(this,
                String.format(
                        "Field '%s' in class '%s' has been modified.\nembedContents is now set to true.\n\nDon't forget to save the schema!",
                        anomaly.schemaField.source,
                        anomaly.schemaClass != null ? anomaly.schemaClass.source : "Unknown"),
                "Field Modified",
                JOptionPane.INFORMATION_MESSAGE);

        // Notify callback to refresh the schema view
        if (navigationCallback != null) {
            navigationCallback.navigateToAnomaly(anomaly, false);
        }
    }

    /**
     * Fix all data loss anomalies by setting embedContents=true on all affected
     * fields.
     */
    private void fixAllDataLossAnomalies() {
        if (currentAnomalies == null || currentAnomalies.isEmpty()) {
            return;
        }

        // Count data loss anomalies
        int count = 0;
        for (DOSchemaAnomaly anomaly : currentAnomalies) {
            if (anomaly instanceof DOSchemaDataLossAnomaly && anomaly.schemaField != null) {
                count++;
            }
        }

        if (count == 0) {
            JOptionPane.showMessageDialog(this,
                    "No data loss anomalies found with field information.",
                    "No Anomalies to Fix",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Confirm with user
        int confirm = JOptionPane.showConfirmDialog(this,
                String.format(
                        "This will set embedContents=true on %d field(s) with critical data loss warnings.\n\n"
                                + "Do you want to continue?",
                        count),
                "Confirm Fix All",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Fix all data loss anomalies
        int fixed = 0;
        for (DOSchemaAnomaly anomaly : currentAnomalies) {
            if (anomaly instanceof DOSchemaDataLossAnomaly && anomaly.schemaField != null) {
                anomaly.schemaField.embedContents = true;
                fixed++;
            }
        }

        JOptionPane.showMessageDialog(this,
                String.format(
                        "Modified %d field(s).\nAll affected fields now have embedContents=true.\n\nDon't forget to save the schema!",
                        fixed),
                "Fields Modified",
                JOptionPane.INFORMATION_MESSAGE);

        // Refresh the anomalies list (they should be gone now)
        if (navigationCallback != null && !currentAnomalies.isEmpty()) {
            navigationCallback.navigateToAnomaly(currentAnomalies.get(0), false);
        }
    }

    private String getAnomalyType(DOSchemaAnomaly anomaly) {
        if (anomaly instanceof DOSchemaDataLossAnomaly) {
            return "⚠️ CRITICAL DATA LOSS";
        } else if (anomaly instanceof DOSchemaReferenceAnomaly) {
            return "Missing Reference";
        } else if (anomaly instanceof DOSchemaMissingFieldClass) {
            return "Missing Field Class";
        } else if (anomaly instanceof DOSchemaSharedEmbeddedAnomaly) {
            return "Shared Embedded";
        } else if (anomaly instanceof DOSchemaSharedNotExportedAnomaly) {
            return "Shared Not Exported";
        } else if (anomaly instanceof DOSchemaShouldBeEmbeddedAnomaly) {
            return "Should Be Embedded";
        } else if (anomaly instanceof DOSchemaShouldNotBeExportedAnomaly) {
            return "Should Not Be Exported";
        } else if (anomaly instanceof DOSchemaEmbeddingAnomaly) {
            return "Embedding Issue";
        } else {
            return "Unknown";
        }
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        contentPanel.setVisible(!collapsed);
        headerLabel.setText((collapsed ? "▶" : "▼") + headerLabel.getText().substring(1));
        revalidate();
        repaint();
    }
}
