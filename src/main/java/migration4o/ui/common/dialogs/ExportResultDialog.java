package migration4o.ui.common.dialogs;

import migration4o.engine.export.ExportResult;
import migration4o.engine.export.ExportResult.ExportError;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog that displays detailed export results including success/error
 * statistics
 * and a breakdown of any errors encountered.
 */
public class ExportResultDialog extends JDialog {
    private final ExportResult result;

    public ExportResultDialog(Frame parent, ExportResult result) {
        super(parent, "Export Results", true);
        this.result = result;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));

        // Header panel with summary
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Center panel - either success message or error table
        if (result.hasErrors()) {
            add(createErrorPanel(), BorderLayout.CENTER);
        } else {
            add(createSuccessPanel(), BorderLayout.CENTER);
        }

        // Bottom button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(800, 600);
        setLocationRelativeTo(getParent());
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        // Export name
        JLabel nameLabel = new JLabel(result.getExportName());
        nameLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);

        panel.add(Box.createVerticalStrut(5));

        // Output path
        JLabel pathLabel = new JLabel("Output: " + result.getOutputPath());
        pathLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        pathLabel.setForeground(Color.GRAY);
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(pathLabel);

        panel.add(Box.createVerticalStrut(15));

        // Statistics panel with color coding
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.setMaximumSize(new Dimension(600, 80));

        // Attempted
        statsPanel.add(createStatBox("Objects Attempted",
                String.valueOf(result.getObjectsAttempted()),
                new Color(100, 100, 100)));

        // Succeeded
        statsPanel.add(createStatBox("Objects Succeeded",
                String.valueOf(result.getObjectsSucceeded()),
                new Color(34, 197, 94))); // Green

        // Failed
        Color failedColor = result.hasErrors() ? new Color(239, 68, 68) : new Color(100, 100, 100); // Red or gray
        statsPanel.add(createStatBox("Objects Failed",
                String.valueOf(result.getObjectsFailed()),
                failedColor));

        panel.add(statsPanel);

        // Status indicator
        panel.add(Box.createVerticalStrut(15));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusIcon;
        JLabel statusText;
        if (result.isSuccess()) {
            statusIcon = new JLabel("✓");
            statusIcon.setFont(new Font("Arial", Font.BOLD, 24));
            statusIcon.setForeground(new Color(34, 197, 94));
            statusText = new JLabel("Export completed successfully");
            statusText.setForeground(new Color(34, 197, 94));
        } else {
            statusIcon = new JLabel("⚠");
            statusIcon.setFont(new Font("Arial", Font.BOLD, 24));
            statusIcon.setForeground(new Color(234, 179, 8));
            statusText = new JLabel("Export completed with errors - Data may be incomplete!");
            statusText.setForeground(new Color(239, 68, 68));
        }
        statusText.setFont(new Font("Arial", Font.BOLD, 14));
        statusPanel.add(statusIcon);
        statusPanel.add(statusText);
        panel.add(statusPanel);

        return panel;
    }

    private JPanel createStatBox(String label, String value, Color color) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 32));
        valueLabel.setForeground(color);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        labelLabel.setForeground(Color.GRAY);
        labelLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(valueLabel);
        box.add(Box.createVerticalStrut(5));
        box.add(labelLabel);

        return box;
    }

    private JPanel createSuccessPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel successLabel = new JLabel("<html><div style='text-align: center;'>" +
                "<p style='font-size: 14px;'>All objects were exported successfully.</p>" +
                "<p style='font-size: 12px; color: gray; margin-top: 10px;'>The export file is ready to use.</p>" +
                "</div></html>");
        successLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(successLabel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createErrorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Group errors by message
        Map<String, List<ExportError>> errorsByMessage = new LinkedHashMap<>();
        for (ExportError error : result.getErrors()) {
            errorsByMessage.computeIfAbsent(error.getErrorMessage(), k -> new ArrayList<>()).add(error);
        }

        // Create table model
        String[] columnNames = { "Error Message", "Count", "Sample Object IDs", "Classes" };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Populate table
        for (Map.Entry<String, List<ExportError>> entry : errorsByMessage.entrySet()) {
            String errorMsg = entry.getKey();
            List<ExportError> errors = entry.getValue();

            // Get sample object IDs (first 5)
            StringBuilder sampleIds = new StringBuilder();
            int showCount = Math.min(5, errors.size());
            for (int i = 0; i < showCount; i++) {
                if (i > 0)
                    sampleIds.append(", ");
                sampleIds.append(errors.get(i).getObjectId());
            }
            if (errors.size() > showCount) {
                sampleIds.append(" ... and ").append(errors.size() - showCount).append(" more");
            }

            // Get unique class names
            Set<String> classNames = new TreeSet<>();
            for (ExportError error : errors) {
                if (error.getClassName() != null && !error.getClassName().isEmpty()) {
                    // Get simple class name
                    String className = error.getClassName();
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    classNames.add(className);
                }
            }
            String classNamesStr = classNames.isEmpty() ? "N/A" : String.join(", ", classNames);

            tableModel.addRow(new Object[] { errorMsg, errors.size(), sampleIds.toString(), classNamesStr });
        }

        // Create table
        JTable table = new JTable(tableModel);
        table.setRowHeight(40);
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);

        // Custom renderer for wrapped text
        table.setDefaultRenderer(Object.class, new MultiLineTableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add explanation label
        JLabel explanationLabel = new JLabel("<html><i>Errors are grouped by message. " +
                "Sample object IDs and affected classes are shown for each error type.</i></html>");
        explanationLabel.setForeground(Color.GRAY);
        explanationLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        panel.add(explanationLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

        if (result.hasErrors()) {
            JButton copyButton = new JButton("Copy Error Details");
            copyButton.addActionListener(e -> copyErrorDetailsToClipboard());
            panel.add(copyButton);
        }

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);

        return panel;
    }

    private void copyErrorDetailsToClipboard() {
        StringBuilder sb = new StringBuilder();
        sb.append("EXPORT ERROR DETAILS\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Export: ").append(result.getExportName()).append("\n");
        sb.append("Output: ").append(result.getOutputPath()).append("\n");
        sb.append("Objects Attempted: ").append(result.getObjectsAttempted()).append("\n");
        sb.append("Objects Succeeded: ").append(result.getObjectsSucceeded()).append("\n");
        sb.append("Objects Failed: ").append(result.getObjectsFailed()).append("\n\n");

        // Group errors by message
        Map<String, List<ExportError>> errorsByMessage = new LinkedHashMap<>();
        for (ExportError error : result.getErrors()) {
            errorsByMessage.computeIfAbsent(error.getErrorMessage(), k -> new ArrayList<>()).add(error);
        }

        sb.append("ERROR BREAKDOWN:\n");
        sb.append("-".repeat(80)).append("\n\n");

        for (Map.Entry<String, List<ExportError>> entry : errorsByMessage.entrySet()) {
            String errorMsg = entry.getKey();
            List<ExportError> errors = entry.getValue();

            sb.append("[").append(errors.size()).append(" occurrences] ").append(errorMsg).append("\n");

            // Sample object IDs
            sb.append("  Object IDs: ");
            int showCount = Math.min(10, errors.size());
            for (int i = 0; i < showCount; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(errors.get(i).getObjectId());
            }
            if (errors.size() > showCount) {
                sb.append(" ... and ").append(errors.size() - showCount).append(" more");
            }
            sb.append("\n");

            // Classes
            Set<String> classNames = new TreeSet<>();
            for (ExportError error : errors) {
                if (error.getClassName() != null) {
                    classNames.add(error.getClassName());
                }
            }
            if (!classNames.isEmpty()) {
                sb.append("  Classes: ").append(String.join(", ", classNames)).append("\n");
            }
            sb.append("\n");
        }

        // Copy to clipboard
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(sb.toString());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        JOptionPane.showMessageDialog(this,
                "Error details copied to clipboard",
                "Copied",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Custom table cell renderer that supports multi-line text wrapping.
     */
    private static class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }

            setFont(table.getFont());
            setText(value != null ? value.toString() : "");

            return this;
        }
    }
}
