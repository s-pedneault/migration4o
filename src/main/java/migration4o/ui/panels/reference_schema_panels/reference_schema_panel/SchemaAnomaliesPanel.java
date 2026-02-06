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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import migration4o.models.schema.DOSchemaAnomaly;
import migration4o.models.schema.DOSchemaEmbeddingAnomaly;
import migration4o.models.schema.DOSchemaReferenceAnomaly;
import migration4o.models.schema.DOSchemaSharedEmbeddedAnomaly;
import migration4o.models.schema.DOSchemaSharedNotExportedAnomaly;
import migration4o.models.schema.DOSchemaShouldBeEmbeddedAnomaly;
import migration4o.models.schema.DOSchemaShouldNotBeExportedAnomaly;

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
                    if (type != null && type.contains("Reference")) {
                        c.setForeground(new Color(0, 100, 200)); // Blue for reference anomalies
                    } else if (type != null && type.contains("Embedding")) {
                        c.setForeground(new Color(200, 100, 0)); // Orange for embedding anomalies
                    } else {
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        // Add mouse listener for navigation
        anomaliesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = anomaliesTable.rowAtPoint(e.getPoint());
                if (row >= 0 && currentAnomalies != null && row < currentAnomalies.size()
                        && navigationCallback != null) {
                    DOSchemaAnomaly anomaly = currentAnomalies.get(row);
                    // Single click: select class only, Double click: select class and open field
                    // editor
                    navigationCallback.navigateToAnomaly(anomaly, e.getClickCount() == 2);
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

    private String getAnomalyType(DOSchemaAnomaly anomaly) {
        if (anomaly instanceof DOSchemaReferenceAnomaly) {
            return "Missing Reference";
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
