package migration4o.ui.migration;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

/**
 * Panel showing migration coverage analysis with combined class list.
 */
public class MigrationCoveragePanel extends JPanel {

    private JTable table;
    private DefaultTableModel tableModel;

    public MigrationCoveragePanel(DOSchema referenceSchema, DOSchema databaseSchema) {
        setLayout(new BorderLayout());

        // Create table model
        String[] columnNames = { "Class Name" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Create table
        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // Populate table with merged class list
        populateTable(referenceSchema, databaseSchema);

        // Add to scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Add summary panel at top
        add(createSummaryPanel(referenceSchema, databaseSchema), BorderLayout.NORTH);
    }

    private JPanel createSummaryPanel(DOSchema referenceSchema, DOSchema databaseSchema) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        int refCount = referenceSchema != null && referenceSchema.getClasses() != null
                ? referenceSchema.getClasses().length
                : 0;
        int dbCount = databaseSchema != null && databaseSchema.getClasses() != null ? databaseSchema.getClasses().length
                : 0;

        JLabel summaryLabel = new JLabel(String.format(
                "Reference Schema: %d classes  |  Database: %d classes  |  Total unique: %d classes",
                refCount, dbCount, tableModel.getRowCount()));
        summaryLabel.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(summaryLabel);

        return panel;
    }

    private void populateTable(DOSchema referenceSchema, DOSchema databaseSchema) {
        // Collect all unique class names
        Set<String> allClassNames = new TreeSet<>(); // TreeSet for sorted order

        if (referenceSchema != null && referenceSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                allClassNames.add(schemaClass.getAbsoluteName());
            }
        }

        if (databaseSchema != null && databaseSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                allClassNames.add(schemaClass.getAbsoluteName());
            }
        }

        // Add to table (already sorted by TreeSet)
        for (String className : allClassNames) {
            tableModel.addRow(new Object[] { className });
        }
    }
}
