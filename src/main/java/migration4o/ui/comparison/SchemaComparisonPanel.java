package migration4o.ui.comparison;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.ui.comparison.SchemaComparison.ClassDifference;
import migration4o.ui.comparison.SchemaComparison.FieldPropertyDifference;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Panel for displaying schema comparison results.
 * Shows differences between reference schema and compared schema,
 * with actions to add missing elements to the reference.
 */
public class SchemaComparisonPanel extends JPanel {

    private final SchemaComparison comparison;
    private final BiConsumer<String, DOSchemaClass> onAddClass;
    private final BiConsumer<DOSchemaClass, DOSchemaField> onAddField;

    private SynchronizedTreePanel syncTreePanel;
    private JPanel detailPanel;
    private JTextArea summaryArea;
    private JCheckBox showCollectionTypeDifferencesCheckbox;
    private JCheckBox groupByPackageCheckbox;

    public SchemaComparisonPanel(SchemaComparison comparison,
            BiConsumer<String, DOSchemaClass> onAddClass,
            BiConsumer<DOSchemaClass, DOSchemaField> onAddField) {
        this.comparison = comparison;
        this.onAddClass = onAddClass;
        this.onAddField = onAddField;

        initializeUI();
        buildTrees();
        updateSummary();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        add(createHeader(), BorderLayout.NORTH);

        // Split pane with synchronized trees on left and details on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(700);
        splitPane.setResizeWeight(0.6);

        // Synchronized trees on left
        syncTreePanel = new SynchronizedTreePanel(
                comparison.getReferenceLabel(),
                comparison.getComparedLabel());

        // Add selection listeners to both trees
        syncTreePanel.getLeftTree().addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                showDetails(node, true);
            }
        });

        syncTreePanel.getRightTree().addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                showDetails(node, false);
            }
        });

        splitPane.setLeftComponent(syncTreePanel);

        // Details on right
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        splitPane.setRightComponent(detailPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("Schema Comparison - Synchronized View");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        summaryArea = new JTextArea(3, 40);
        summaryArea.setEditable(false);
        summaryArea.setBackground(panel.getBackground());
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(summaryArea, BorderLayout.CENTER);

        // Add options panel with checkboxes
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        // Checkbox for filtering collection type differences
        showCollectionTypeDifferencesCheckbox = new JCheckBox("Show collection content type differences", false);
        showCollectionTypeDifferencesCheckbox
                .setToolTipText("When unchecked, hides field differences that only differ in childrenType");
        optionsPanel.add(showCollectionTypeDifferencesCheckbox);

        // Checkbox for grouping by package
        groupByPackageCheckbox = new JCheckBox("Group by package", false);
        groupByPackageCheckbox.setToolTipText("Group classes by package instead of inheritance");
        groupByPackageCheckbox.addActionListener(e -> buildTrees());
        optionsPanel.add(groupByPackageCheckbox);

        // Checkbox for showing all classes
        JCheckBox showAllClassesCheckbox = new JCheckBox("Show all classes", false);
        showAllClassesCheckbox.setToolTipText("Show all classes, not just those with differences");
        showAllClassesCheckbox.addActionListener(e -> {
            comparison.setShowAllClasses(showAllClassesCheckbox.isSelected());
            buildTrees();
            updateSummary();
        });
        optionsPanel.add(showAllClassesCheckbox);

        panel.add(optionsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void buildTrees() {
        boolean groupByPackage = groupByPackageCheckbox.isSelected();
        syncTreePanel.buildTrees(comparison.getDifferences(),
                comparison.getReferenceLabel(),
                comparison.getComparedLabel(),
                groupByPackage);
    }

    private void showDetails(DefaultMutableTreeNode node, boolean isLeftTree) {
        detailPanel.removeAll();

        Object userObject = node.getUserObject();
        if (userObject instanceof SynchronizedTreePanel.SyncTreeNode) {
            SynchronizedTreePanel.SyncTreeNode syncNode = (SynchronizedTreePanel.SyncTreeNode) userObject;
            ClassDifference diff = syncNode.getDifference();

            if (diff != null && !syncNode.isGhost()) {
                showClassDifferenceDetails(diff);
            } else if (diff != null && syncNode.isGhost()) {
                showGhostClassDetails(diff, isLeftTree);
            }
        }

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void showGhostClassDetails(ClassDifference diff, boolean isLeftTree) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        StringBuilder info = new StringBuilder();
        info.append("Class: ").append(diff.getClassName()).append("\n\n");

        if (isLeftTree) {
            // Ghost in reference (left), exists in compared (right)
            info.append("Status: Only exists in ").append(comparison.getComparedLabel()).append("\n");
            DOSchemaClass comparedClass = diff.getComparedClass();
            if (comparedClass != null) {
                info.append("Fields: ").append(
                        comparedClass.getFields() != null ? comparedClass.getFields().length : 0).append("\n");
            }
        } else {
            // Ghost in compared (right), exists in reference (left)
            info.append("Status: Only exists in ").append(comparison.getReferenceLabel()).append("\n");
            DOSchemaClass refClass = diff.getReferenceClass();
            if (refClass != null) {
                info.append("Fields: ").append(
                        refClass.getFields() != null ? refClass.getFields().length : 0).append("\n");
            }
        }

        infoArea.setText(info.toString());
        panel.add(infoArea, BorderLayout.NORTH);

        // Show field table if exists in compared schema
        if (diff.isOnlyInCompared() && diff.getComparedClass() != null &&
                diff.getComparedClass().getFields() != null && diff.getComparedClass().getFields().length > 0) {
            JPanel fieldsPanel = new JPanel(new BorderLayout());
            fieldsPanel.setBorder(BorderFactory.createTitledBorder("Fields in " + comparison.getComparedLabel()));
            fieldsPanel.add(createFieldsTable(diff.getComparedClass().getFields()), BorderLayout.CENTER);
            panel.add(fieldsPanel, BorderLayout.CENTER);

            // Add button to add class to reference
            if (onAddClass != null) {
                JButton addButton = new JButton("Add Class to " + comparison.getReferenceLabel());
                addButton.addActionListener(e -> {
                    onAddClass.accept(diff.getClassName(), diff.getComparedClass());
                    JOptionPane.showMessageDialog(this,
                            "Class '" + diff.getClassName() + "' has been added to the reference schema.\n" +
                                    "Please save the reference schema to persist changes.",
                            "Class Added", JOptionPane.INFORMATION_MESSAGE);
                });
                panel.add(addButton, BorderLayout.SOUTH);
            }
        }

        detailPanel.add(panel);
    }

    private void showClassDifferenceDetails(ClassDifference diff) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        StringBuilder info = new StringBuilder();
        info.append("Class: ").append(diff.getClassName()).append("\n\n");

        if (diff.isOnlyInCompared()) {
            info.append("Status: Only exists in ").append(comparison.getComparedLabel()).append("\n");
            info.append("Fields: ").append(
                    diff.getComparedClass().getFields() != null ? diff.getComparedClass().getFields().length : 0)
                    .append("\n");
        } else if (diff.isOnlyInReference()) {
            info.append("Status: Only exists in ").append(comparison.getReferenceLabel()).append("\n");
            info.append("Fields: ").append(
                    diff.getReferenceClass().getFields() != null ? diff.getReferenceClass().getFields().length : 0)
                    .append("\n");
        } else {
            info.append("Status: Exists in both schemas\n");
            info.append("Fields only in ").append(comparison.getComparedLabel()).append(": ")
                    .append(diff.getFieldsOnlyInCompared().size()).append("\n");
            info.append("Fields only in ").append(comparison.getReferenceLabel()).append(": ")
                    .append(diff.getFieldsOnlyInReference().size()).append("\n");
            info.append("Fields with differences: ").append(diff.getFieldsWithDifferences().size()).append("\n");
        }

        infoArea.setText(info.toString());
        infoArea.setRows(diff.isOnlyInCompared() || diff.isOnlyInReference() ? 3 : 5);
        panel.add(infoArea, BorderLayout.NORTH);

        // Show detailed field list if class only exists in one schema
        if (diff.isOnlyInCompared() && diff.getComparedClass().getFields() != null
                && diff.getComparedClass().getFields().length > 0) {
            JPanel fieldsPanel = new JPanel(new BorderLayout());
            fieldsPanel.setBorder(BorderFactory.createTitledBorder("Fields in " + comparison.getComparedLabel()));
            fieldsPanel.add(createFieldsTable(diff.getComparedClass().getFields()), BorderLayout.CENTER);
            panel.add(fieldsPanel, BorderLayout.CENTER);
        } else if (diff.isOnlyInReference() && diff.getReferenceClass().getFields() != null
                && diff.getReferenceClass().getFields().length > 0) {
            JPanel fieldsPanel = new JPanel(new BorderLayout());
            fieldsPanel.setBorder(BorderFactory.createTitledBorder("Fields in " + comparison.getReferenceLabel()));
            fieldsPanel.add(createFieldsTable(diff.getReferenceClass().getFields()), BorderLayout.CENTER);
            panel.add(fieldsPanel, BorderLayout.CENTER);
        }

        // Add action button if class is missing in reference
        if (diff.isOnlyInCompared() && onAddClass != null) {
            JButton addButton = new JButton("Add Class to " + comparison.getReferenceLabel());
            addButton.addActionListener(e -> {
                onAddClass.accept(diff.getClassName(), diff.getComparedClass());
                JOptionPane.showMessageDialog(this,
                        "Class '" + diff.getClassName() + "' has been added to the reference schema.\n" +
                                "Please save the reference schema to persist changes.",
                        "Class Added", JOptionPane.INFORMATION_MESSAGE);
            });
            panel.add(addButton, BorderLayout.SOUTH);
        }

        detailPanel.add(panel);
    }

    private void showFieldDetails(DOSchemaField field, DOSchemaClass parentClass) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Field properties table
        String[][] data = {
                { "Source", field.getSource() },
                { "Type", field.getType() },
                { "Collection", String.valueOf(field.isCollection()) },
                { "Children Type", field.getChildrenType() != null ? field.getChildrenType() : "" },
                { "Title", field.getTitle() != null ? field.getTitle() : "" },
                { "Description", field.getDescription() != null ? field.getDescription() : "" }
        };

        String[] columns = { "Property", "Value" };
        JTable table = new JTable(data, columns);
        table.setEnabled(false);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Add button if this field is missing in reference
        if (onAddField != null && parentClass != null) {
            JButton addButton = new JButton("Add Field to " + comparison.getReferenceLabel());
            addButton.addActionListener(e -> {
                onAddField.accept(parentClass, field);
                JOptionPane.showMessageDialog(this,
                        "Field '" + field.getSource() + "' has been added to class '" + parentClass.getSourceName()
                                + "'.\n" +
                                "Please save the reference schema to persist changes.",
                        "Field Added", JOptionPane.INFORMATION_MESSAGE);
            });
            panel.add(addButton, BorderLayout.SOUTH);
        }

        detailPanel.add(panel);
    }

    private void updateSummary() {
        List<ClassDifference> diffs = comparison.getDifferences();

        long missingInRef = diffs.stream().filter(ClassDifference::isOnlyInCompared).count();
        long missingInCmp = diffs.stream().filter(ClassDifference::isOnlyInReference).count();
        long withDiffs = diffs.stream().filter(d -> !d.isOnlyInCompared() && !d.isOnlyInReference()).count();

        String summary = String.format(
                "Reference: %s\nCompared: %s\n\n" +
                        "Classes only in compared: %d  |  Classes only in reference: %d  |  Classes with differences: %d",
                comparison.getReferenceLabel(),
                comparison.getComparedLabel(),
                missingInRef,
                missingInCmp,
                withDiffs);

        summaryArea.setText(summary);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "(null)";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof String) {
            String str = (String) value;
            return str.isEmpty() ? "(empty)" : str;
        }
        return value.toString();
    }

    /**
     * Creates a table showing field details.
     */
    private JScrollPane createFieldsTable(DOSchemaField[] fields) {
        String[] columns = { "Field", "Type", "Collection", "Children Type", "Exported", "Description" };
        Object[][] data = new Object[fields.length][columns.length];

        for (int i = 0; i < fields.length; i++) {
            DOSchemaField field = fields[i];
            data[i][0] = field.getSource();
            data[i][1] = field.getType();
            data[i][2] = field.isCollection() ? "Yes" : "No";
            data[i][3] = field.getChildrenType() != null && !field.getChildrenType().isEmpty() ? field.getChildrenType()
                    : "-";
            data[i][4] = field.isExported() ? "Yes" : "No";
            data[i][5] = field.getDescription() != null && !field.getDescription().isEmpty() ? field.getDescription()
                    : "-";
        }

        JTable table = new JTable(data, columns);
        table.setEnabled(false);
        table.setFont(new Font("Monospaced", Font.PLAIN, 11));
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(150); // Field name
        table.getColumnModel().getColumn(1).setPreferredWidth(150); // Type
        table.getColumnModel().getColumn(2).setPreferredWidth(70); // Collection
        table.getColumnModel().getColumn(3).setPreferredWidth(150); // Children Type
        table.getColumnModel().getColumn(4).setPreferredWidth(70); // Exported
        table.getColumnModel().getColumn(5).setPreferredWidth(200); // Description

        return new JScrollPane(table);
    }

    /**
     * Checks if the only difference in a field property difference is the
     * childrenType.
     */
    private boolean isOnlyChildrenTypeDifference(FieldPropertyDifference propDiff) {
        Map<String, FieldPropertyDifference.PropertyDiff> differences = propDiff.getDifferences();

        // If there's only one difference and it's childrenType, return true
        if (differences.size() == 1 && differences.containsKey("childrenType")) {
            return true;
        }

        return false;
    }

    /**
     * Gets the count of property differences after filtering based on checkbox
     * state.
     */
    private int getFilteredPropertyDifferencesCount(ClassDifference diff) {
        if (showCollectionTypeDifferencesCheckbox.isSelected()) {
            return diff.getFieldsWithDifferences().size();
        }

        int count = 0;
        for (FieldPropertyDifference propDiff : diff.getFieldsWithDifferences().values()) {
            if (!isOnlyChildrenTypeDifference(propDiff)) {
                count++;
            }
        }
        return count;
    }
}
