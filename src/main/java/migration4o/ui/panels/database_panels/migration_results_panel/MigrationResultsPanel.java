package migration4o.ui.panels.database_panels.migration_results_panel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ExportWarning;
import migration4o.migration.monitoring.ObjectReference;
import migration4o.migration.monitoring.ExportStatistics.ExportError;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DOSchemaService;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs.FieldEditorDialog;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Panel that displays migration export results in the Database tab. Shows
 * statistics, warnings, and errors from the most recent export. This is the
 * full-featured panel converted from ExportResultDialog.
 */
public class MigrationResultsPanel extends JPanel {
    private ExportStatistics result;
    private final Set<Integer> editedRows = new HashSet<>();
    private JTable warningsTable;

    public MigrationResultsPanel() {
        setLayout(new BorderLayout());
        showEmptyState();
    }

    /**
     * Shows a message when no export has been run yet.
     */
    private void showEmptyState() {
        JTextArea emptyText = new JTextArea();
        emptyText.setText("Migration Results\n\nNo export has been run yet.\nUse the Migration structure tab in the Schema section to configure and run an export.");
        emptyText.setEditable(false);
        emptyText.setOpaque(false);
        emptyText.setFont(new Font("Arial", Font.PLAIN, 14));
        emptyText.setMargin(new Insets(20, 20, 20, 20));

        removeAll();
        add(emptyText, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Updates the panel with new export results.
     * 
     * @param result The export statistics to display
     */
    public void updateResults(ExportStatistics result) {
        this.result = result;
        this.editedRows.clear();

        removeAll();
        setLayout(new BorderLayout(10, 10));

        // Header panel with summary
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Center panel - either success message or error/warning tabbed pane
        if (!result.errors.isEmpty() || !result.schemaWarnings.isEmpty()) {
            add(createIssuesPanel(), BorderLayout.CENTER);
        } else {
            add(createSuccessPanel(), BorderLayout.CENTER);
        }

        // Bottom button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        // Export name
        JLabel nameLabel = new JLabel(result.exportName);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);

        panel.add(Box.createVerticalStrut(5));

        // Output path
        JLabel pathLabel = new JLabel("Output: " + result.outputPath);
        pathLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        pathLabel.setForeground(Color.GRAY);
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(pathLabel);

        panel.add(Box.createVerticalStrut(15));

        // Statistics panel with color coding
        boolean hasWarnings = !result.schemaWarnings.isEmpty();
        int statBoxCount = hasWarnings ? 4 : 3;
        JPanel statsPanel = new JPanel(new GridLayout(1, statBoxCount, 15, 0));
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.setMaximumSize(new Dimension(800, 80));

        // Unique objects exported (deduplicated across all depths)
        statsPanel.add(createStatBox("Objects Exported", String.valueOf(result.getUniqueExportedCount()), new Color(34, 197, 94))); // Green

        // Total object writes (may include duplicates from multiple embedding
        // paths)
        statsPanel.add(createStatBox("Total Writes", String.valueOf(result.objectsSucceeded), new Color(100, 100, 100)));

        // Failed
        Color failedColor = !result.errors.isEmpty() ? new Color(239, 68, 68) : new Color(100, 100, 100);
        statsPanel.add(createStatBox("Objects Failed", String.valueOf(result.errors.size()), failedColor));

        // Warnings (only show if there are warnings)
        if (hasWarnings) {
            statsPanel.add(createStatBox("Schema Warnings", String.valueOf(result.schemaWarnings.size()), new Color(234, 179, 8))); // Yellow/amber
        }

        panel.add(statsPanel);

        // Status indicator
        panel.add(Box.createVerticalStrut(15));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusIcon;
        JLabel statusText;
        if (result.errors.isEmpty() && result.schemaWarnings.isEmpty()) {
            statusIcon = new JLabel("✓");
            statusIcon.setFont(new Font("Arial", Font.BOLD, 24));
            statusIcon.setForeground(new Color(34, 197, 94));
            statusText = new JLabel("Export completed successfully");
            statusText.setForeground(new Color(34, 197, 94));
        } else if (!result.errors.isEmpty()) {
            statusIcon = new JLabel("⚠");
            statusIcon.setFont(new Font("Arial", Font.BOLD, 24));
            statusIcon.setForeground(new Color(234, 179, 8));
            statusText = new JLabel("Export completed with errors - Data may be incomplete!");
            statusText.setForeground(new Color(239, 68, 68));
        } else {
            // Only warnings, no errors
            statusIcon = new JLabel("⚠");
            statusIcon.setFont(new Font("Arial", Font.BOLD, 24));
            statusIcon.setForeground(new Color(234, 179, 8));
            statusText = new JLabel("Export completed with schema warnings - Review recommended");
            statusText.setForeground(new Color(234, 179, 8));
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
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, 2), BorderFactory.createEmptyBorder(10, 10, 10, 10)));

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

        JLabel successLabel = new JLabel("<html><div style='text-align: center;'>" + "<p style='font-size: 14px;'>All objects were exported successfully.</p>" + "<p style='font-size: 12px; color: gray; margin-top: 10px;'>The export file is ready to use.</p>" + "</div></html>");
        successLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(successLabel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createIssuesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // If we have both errors and warnings, use a tabbed pane
        if (!result.errors.isEmpty() && !result.schemaWarnings.isEmpty()) {
            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.addTab("Errors (" + result.errors.size() + ")", createErrorTable());
            tabbedPane.addTab("Schema Warnings (" + result.schemaWarnings.size() + ")", createSchemaWarningsPanel());
            panel.add(tabbedPane, BorderLayout.CENTER);
        } else if (!result.errors.isEmpty()) {
            panel.add(createErrorTable(), BorderLayout.CENTER);
        } else {
            panel.add(createSchemaWarningsPanel(), BorderLayout.CENTER);
        }

        return panel;
    }

    private JPanel createErrorTable() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Group errors by message
        Map<String, List<ExportError>> errorsByMessage = new LinkedHashMap<>();
        for (ExportError error : result.errors) {
            errorsByMessage.computeIfAbsent(error.errorMessage, k -> new ArrayList<>()).add(error);
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
                sampleIds.append(errors.get(i).objectId);
            }
            if (errors.size() > showCount) {
                sampleIds.append(" ... and ").append(errors.size() - showCount).append(" more");
            }

            // Get unique class names
            Set<String> classNames = new TreeSet<>();
            for (ExportError error : errors) {
                if (error.className != null && !error.className.isEmpty()) {
                    // Get simple class name
                    String className = error.className;
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
        JLabel explanationLabel = new JLabel("<html><i>Errors are grouped by message. " + "Sample object IDs and affected classes are shown for each error type.</i></html>");
        explanationLabel.setForeground(Color.GRAY);
        explanationLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        panel.add(explanationLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSchemaWarningsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        // Create table model - group by source class + source field combination
        String[] columnNames = { "Warning Type", "Count", "Sample Embedded Classes", "Sample Object IDs", "Source Class (from schema)", "Source Field (from schema)", "Fix" };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Only the Fix column (index 6) is editable
                return column == 6;
            }
        };

        // Group warnings by source class + source field combination
        Map<String, List<ExportWarning>> groupedWarnings = new LinkedHashMap<>();
        for (ExportWarning warning : result.schemaWarnings) {
            // Find first embedded field reference to use for grouping
            String sourceClass = "N/A";
            String sourceField = "N/A";
            for (ObjectReference ref : warning.references) {
                if (ref.isEmbeddedField()) {
                    sourceClass = ref.sourceContainingClass != null ? ref.sourceContainingClass : "N/A";
                    sourceField = ref.sourceFieldName != null ? ref.sourceFieldName : "N/A";
                    break;
                }
            }
            String groupKey = sourceClass + "::" + sourceField;
            groupedWarnings.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(warning);
        }

        // Add one row per unique source class + source field combination
        for (List<ExportWarning> warnings : groupedWarnings.values()) {
            ExportWarning firstWarning = warnings.get(0);

            // Warning type description
            String warningType = firstWarning.type == ExportWarning.WarningType.DUPLICATE_EMBEDDED_REFERENCE ? "Duplicate Embedded Reference" : firstWarning.type.toString();

            // Extract from first embedded field reference
            String sourceContainingClass = "N/A";
            String sourceFieldName = "N/A";
            for (ObjectReference ref : firstWarning.references) {
                if (ref.isEmbeddedField()) {
                    sourceContainingClass = ref.sourceContainingClass != null ? ref.sourceContainingClass : "N/A";
                    sourceFieldName = ref.sourceFieldName != null ? ref.sourceFieldName : "N/A";
                    break;
                }
            }

            // Collect sample embedded classes and object IDs
            Set<String> embeddedClasses = new LinkedHashSet<>();
            List<String> objectIds = new ArrayList<>();
            for (ExportWarning warning : warnings) {
                embeddedClasses.add(warning.className);
                objectIds.add(String.valueOf(warning.objectId));
            }

            // Limit samples to first 5 for display
            String embeddedClassesSample = embeddedClasses.stream().limit(5).collect(java.util.stream.Collectors.joining(", "));
            if (embeddedClasses.size() > 5) {
                embeddedClassesSample += "...";
            }

            String objectIdsSample = objectIds.stream().limit(5).collect(java.util.stream.Collectors.joining(", "));
            if (objectIds.size() > 5) {
                objectIdsSample += "...";
            }

            tableModel.addRow(new Object[] { warningType, String.valueOf(warnings.size()), embeddedClassesSample, objectIdsSample, sourceContainingClass, sourceFieldName, "Fix" // Button
                                                                                                                                                                                 // text
            });
        }

        // Create table
        JTable table = new JTable(tableModel);
        this.warningsTable = table; // Store reference for later updates
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(200);
        table.getColumnModel().getColumn(5).setPreferredWidth(180);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);

        // Set custom renderer and editor for the Fix button column
        table.getColumnModel().getColumn(6).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ButtonEditor(new JCheckBox(), tableModel));

        // Custom renderer for wrapped text with highlighting for edited rows
        table.setDefaultRenderer(Object.class, new MultiLineTableCellRenderer());

        // Create context menu
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem listObjectsItem = new JMenuItem("List objects");
        listObjectsItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                showObjectsList(row, groupedWarnings);
            }
        });
        contextMenu.add(listObjectsItem);

        // Add mouse listener for both double-click and right-click
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        openFieldEditor(row, tableModel);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row);
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

        if (result != null && !result.errors.isEmpty()) {
            JButton copyButton = new JButton("Copy Error Details");
            copyButton.addActionListener(e -> copyErrorDetailsToClipboard());
            panel.add(copyButton);
        }

        return panel;
    }

    /**
     * Opens the field editor dialog for the selected warning row.
     */
    private void openFieldEditor(int row, DefaultTableModel tableModel) {
        String sourceClass = (String) tableModel.getValueAt(row, 4);
        String sourceField = (String) tableModel.getValueAt(row, 5);

        if ("N/A".equals(sourceClass) || "N/A".equals(sourceField)) {
            JOptionPane.showMessageDialog(this, "Cannot open field editor: Source class or field information is not available.", "Field Editor", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        if (schema == null) {
            JOptionPane.showMessageDialog(this, "Cannot open field editor: Reference schema is not loaded.", "Field Editor", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DOSchemaClass schemaClass = null;
        for (DOSchemaClass cls : schema.getClasses()) {
            if (cls.source.equals(sourceClass)) {
                schemaClass = cls;
                break;
            }
        }

        if (schemaClass == null) {
            JOptionPane.showMessageDialog(this, "Cannot open field editor: Class '" + sourceClass + "' not found in reference schema.", "Field Editor", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DOSchemaField field = null;
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (f.source.equals(sourceField)) {
                    field = f;
                    break;
                }
            }
        }

        if (field == null) {
            JOptionPane.showMessageDialog(this, "Cannot open field editor: Field '" + sourceField + "' not found in class '" + sourceClass + "'.", "Field Editor", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FieldEditorDialog dialog = new FieldEditorDialog(null, schema, field, false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (dialog.isOkClicked()) {
            // Apply all changes from the dialog to the field object
            field.source = dialog.getFieldSource();
            field.destinationName = dialog.getFieldDestination();
            field.type = dialog.getFieldType();
            field.format = dialog.getFieldFormat();
            field.isExported = dialog.isFieldExported();
            field.skipWhen = dialog.getFieldSkipWhen();
            field.isCollection = dialog.isFieldCollection();
            field.embedContents = dialog.isFieldEmbedContents();
            field.childrenType = dialog.getFieldChildrenType();
            field.title = dialog.getFieldTitle();
            field.description = dialog.getFieldDescription();
            field.pointsTo = dialog.getFieldPointsTo();
            field.valueMap = dialog.getValueMappings();

            // Mark this row as edited (highlight in green)
            editedRows.add(row);
            if (warningsTable != null) {
                warningsTable.repaint();
            }
        }
    }

    private void copyErrorDetailsToClipboard() {
        if (result == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append("EXPORT ERROR DETAILS\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Export: ").append(result.exportName).append("\n");
        sb.append("Output: ").append(result.outputPath).append("\n");
        sb.append("Objects Exported: ").append(result.getUniqueExportedCount()).append("\n");
        sb.append("Total Writes: ").append(result.objectsSucceeded).append("\n");
        sb.append("Objects Failed: ").append(result.errors.size()).append("\n\n");

        // Group errors by message
        Map<String, List<ExportError>> errorsByMessage = new LinkedHashMap<>();
        for (ExportError error : result.errors) {
            errorsByMessage.computeIfAbsent(error.errorMessage, k -> new ArrayList<>()).add(error);
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
                sb.append(errors.get(i).objectId);
            }
            if (errors.size() > showCount) {
                sb.append(" ... and ").append(errors.size() - showCount).append(" more");
            }
            sb.append("\n");

            // Classes
            Set<String> classNames = new TreeSet<>();
            for (ExportError error : errors) {
                if (error.className != null) {
                    classNames.add(error.className);
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

        JOptionPane.showMessageDialog(this, "Error details copied to clipboard", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Button renderer for the Fix column.
     */
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (editedRows.contains(row)) {
                setBackground(new Color(200, 255, 200));
                setEnabled(false);
                setText("Fixed");
            } else {
                setBackground(UIManager.getColor("Button.background"));
                setEnabled(true);
                setText((value == null) ? "Fix" : value.toString());
            }
            return this;
        }
    }

    /**
     * Button editor for the Fix column.
     */
    private class ButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private int currentRow;
        private DefaultTableModel tableModel;

        public ButtonEditor(JCheckBox checkBox, DefaultTableModel model) {
            super(checkBox);
            this.tableModel = model;
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentRow = row;
            label = (value == null) ? "Fix" : value.toString();
            button.setText(label);
            isPushed = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                applyFix(currentRow, tableModel);
            }
            isPushed = false;
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
    }

    /**
     * Applies the fix for the selected warning row by setting embedContents to
     * false.
     */
    private void applyFix(int row, DefaultTableModel tableModel) {
        String sourceClass = (String) tableModel.getValueAt(row, 4);
        String sourceField = (String) tableModel.getValueAt(row, 5);

        if ("N/A".equals(sourceClass) || "N/A".equals(sourceField)) {
            JOptionPane.showMessageDialog(this, "Cannot apply fix: Source class or field information is not available.", "Fix Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        if (schema == null) {
            JOptionPane.showMessageDialog(this, "Cannot apply fix: Reference schema is not loaded.", "Fix Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DOSchemaClass schemaClass = null;
        for (DOSchemaClass cls : schema.getClasses()) {
            if (cls.source.equals(sourceClass)) {
                schemaClass = cls;
                break;
            }
        }

        if (schemaClass == null) {
            JOptionPane.showMessageDialog(this, "Cannot apply fix: Class '" + sourceClass + "' not found in reference schema.", "Fix Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DOSchemaField field = null;
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (f.source.equals(sourceField)) {
                    field = f;
                    break;
                }
            }
        }

        if (field == null) {
            JOptionPane.showMessageDialog(this, "Cannot apply fix: Field '" + sourceField + "' not found in class '" + sourceClass + "'.", "Fix Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Apply the fix: set embedContents to false
        field.embedContents = false;

        // Mark this row as edited (highlight in green)
        editedRows.add(row);
        if (warningsTable != null) {
            warningsTable.repaint();
        }
    }

    /**
     * Shows a detailed list of all objects mentioned in the selected warning
     * group.
     */
    private void showObjectsList(int tableRow, Map<String, List<ExportWarning>> groupedWarnings) {
        List<ExportWarning> selectedGroupWarnings = new ArrayList<>(groupedWarnings.values()).get(tableRow);

        if (selectedGroupWarnings.isEmpty()) {
            return;
        }

        ExportWarning firstWarning = selectedGroupWarnings.get(0);

        // Extract source info from first embedded field reference
        String sourceClass = "N/A";
        String sourceField = "N/A";
        for (ObjectReference ref : firstWarning.references) {
            if (ref.isEmbeddedField()) {
                sourceClass = ref.sourceContainingClass != null ? ref.sourceContainingClass : "N/A";
                sourceField = ref.sourceFieldName != null ? ref.sourceFieldName : "N/A";
                break;
            }
        }

        // Create frame to display objects
        JFrame objectsFrame = new JFrame("Duplicate Objects: " + sourceClass + " → " + sourceField);
        objectsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        objectsFrame.setLayout(new BorderLayout(10, 10));

        // Create table model
        String[] columnNames = { "Object ID", "Class", "Export Count", "All Exports" };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Get ALL warnings across ALL groups
        List<ExportWarning> allWarnings = new ArrayList<>();
        for (List<ExportWarning> groupWarnings : groupedWarnings.values()) {
            allWarnings.addAll(groupWarnings);
        }

        // Collect unique object IDs from the selected group
        Set<Long> selectedObjectIds = new LinkedHashSet<>();
        for (ExportWarning warning : selectedGroupWarnings) {
            selectedObjectIds.add(warning.objectId);
        }

        // Now get ALL warnings for those objects (from any group)
        Map<Long, List<ExportWarning>> objectWarnings = new LinkedHashMap<>();
        for (Long objectId : selectedObjectIds) {
            for (ExportWarning warning : allWarnings) {
                if (warning.objectId == objectId) {
                    objectWarnings.computeIfAbsent(objectId, k -> new ArrayList<>()).add(warning);
                }
            }
        }

        // Add rows for each unique object
        for (Map.Entry<Long, List<ExportWarning>> entry : objectWarnings.entrySet()) {
            Long objectId = entry.getKey();
            List<ExportWarning> objectRefs = entry.getValue();
            ExportWarning firstRef = objectRefs.get(0);

            // Collect all references
            List<String> allReferences = new ArrayList<>();
            for (ExportWarning warning : objectRefs) {
                allReferences.addAll(warning.getReferenceDisplayStrings());
            }

            String referencesText = String.join("\n", allReferences);

            // Export count = actual number of times this object appears in XML
            int actualExportCount = allReferences.size();

            tableModel.addRow(new Object[] { objectId, firstRef.className, actualExportCount, referencesText });
        }

        // Create table
        JTable table = new JTable(tableModel);
        table.setRowHeight(80);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(600);

        // Multi-line renderer for "References" column
        table.getColumnModel().getColumn(3).setCellRenderer(new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JTextArea textArea = new JTextArea(value != null ? value.toString() : "");
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setOpaque(true);

                if (isSelected) {
                    textArea.setBackground(table.getSelectionBackground());
                    textArea.setForeground(table.getSelectionForeground());
                } else {
                    textArea.setBackground(table.getBackground());
                    textArea.setForeground(table.getForeground());
                }

                textArea.setFont(table.getFont());
                return textArea;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        objectsFrame.add(scrollPane, BorderLayout.CENTER);

        // Add info panel at top
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel(String.format("Objects referenced multiple times from: %s → %s", sourceClass, sourceField));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        infoPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel countLabel = new JLabel(String.format("Total duplicate objects: %d", objectWarnings.size()));
        countLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        countLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        infoPanel.add(countLabel, BorderLayout.CENTER);

        objectsFrame.add(infoPanel, BorderLayout.NORTH);

        // Add close button at bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> objectsFrame.dispose());
        buttonPanel.add(closeButton);
        objectsFrame.add(buttonPanel, BorderLayout.SOUTH);

        objectsFrame.setSize(1400, 600);
        objectsFrame.setLocationRelativeTo(this);
        objectsFrame.setVisible(true);
    }

    /**
     * Custom table cell renderer that supports multi-line text wrapping and
     * highlights edited rows in green.
     */
    private class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else if (editedRows.contains(row)) {
                // Highlight edited rows in light green
                setForeground(table.getForeground());
                setBackground(new Color(200, 255, 200));
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
