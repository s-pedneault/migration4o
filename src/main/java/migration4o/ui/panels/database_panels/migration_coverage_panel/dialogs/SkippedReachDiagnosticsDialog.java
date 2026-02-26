package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Shows skipped-but-reached diagnostics aggregated by reason.
 */
public class SkippedReachDiagnosticsDialog extends JDialog {

    private final IDTracerDataService dataService;

    public SkippedReachDiagnosticsDialog(Frame parent) {
        super(parent, "Skipped-but-Reached Diagnostics", false);
        this.dataService = IDTracerDataService.getInstance();

        setLayout(new BorderLayout(8, 8));
        setSize(1100, 560);
        setLocationRelativeTo(parent);

        JLabel summary = new JLabel("Skipped relationships where child object was still reached");
        add(summary, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(new Object[] { "Reason", "Count", "Example" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        Map<String, Integer> counts = dataService.getSkippedButReachedReasonCounts();
        Map<String, String> examples = dataService.getSkippedButReachedReasonExamples(1);

        int total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            total += entry.getValue();
            model.addRow(new Object[] { entry.getKey(), entry.getValue(), examples.getOrDefault(entry.getKey(), "") });
        }

        summary.setText("Skipped-but-reached events: " + total + " across " + counts.size() + " reasons");

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(620);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(1080, 500));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
}
