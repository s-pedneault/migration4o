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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Panel for displaying schema comparison results.
 * Shows differences between reference schema and compared schema,
 * with actions to add missing elements to the reference.
 */
public class SchemaComparisonPanel extends JPanel {

    private SchemaComparison comparison;
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

    /**
     * Update the comparison with new schemas and refresh the display.
     */
    public void updateComparison(SchemaComparison newComparison) {
        this.comparison = newComparison;

        // Preserve the show all classes setting
        boolean showAllClasses = comparison.isShowAllClasses();
        newComparison.setShowAllClasses(showAllClasses);

        // Rebuild trees and update summary
        buildTrees();
        updateSummary();

        // Clear details panel
        detailPanel.removeAll();
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        add(createHeader(), BorderLayout.NORTH);

        // Split pane with synchronized trees on left and details on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.4);

        // Set divider location after component is visible
        splitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean initialized = false;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!initialized && splitPane.getWidth() > 0) {
                    splitPane.setDividerLocation(0.4); // 40% for trees, 60% for details
                    initialized = true;
                }
            }
        });

        // Synchronized trees on left
        syncTreePanel = new SynchronizedTreePanel(
                comparison.getReferenceLabel(),
                comparison.getComparedLabel());

        // Add selection listeners to both trees
        // Note: These are mainly for keyboard navigation
        // Mouse clicks are handled by the onActiveTreeChanged callback below
        syncTreePanel.getLeftTree().addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null && syncTreePanel.isLeftTreeActive()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                showDetails(node, true);
            }
        });

        syncTreePanel.getRightTree().addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null && !syncTreePanel.isLeftTreeActive()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                showDetails(node, false);
            }
        });

        // Listen for active tree changes (primarily from mouse clicks)
        // This ensures details update when clicking on the same node in different trees
        syncTreePanel.setOnActiveTreeChanged(() -> {
            JTree activeTree = syncTreePanel.getActiveTree();
            if (activeTree != null) {
                TreePath path = activeTree.getSelectionPath();
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    showDetails(node, syncTreePanel.isLeftTreeActive());
                }
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
        groupByPackageCheckbox = new JCheckBox("Group by package", true);
        groupByPackageCheckbox.setToolTipText("Group classes by package instead of inheritance");
        groupByPackageCheckbox.addActionListener(e -> buildTrees());
        optionsPanel.add(groupByPackageCheckbox);

        // Checkbox for showing all classes
        JCheckBox showAllClassesCheckbox = new JCheckBox("Show all classes", true);
        showAllClassesCheckbox.setToolTipText("Show all classes, not just those with differences");
        showAllClassesCheckbox.addActionListener(e -> {
            comparison.setShowAllClasses(showAllClassesCheckbox.isSelected());
            buildTrees();
            updateSummary();
        });
        optionsPanel.add(showAllClassesCheckbox);

        // Initialize with show all classes enabled
        comparison.setShowAllClasses(true);

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

            // Handle field node - show parent class's fields table
            if (syncNode.isField()) {
                // Get parent node which is the class
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                if (parentNode != null && parentNode.getUserObject() instanceof SynchronizedTreePanel.SyncTreeNode) {
                    SynchronizedTreePanel.SyncTreeNode parentSyncNode = (SynchronizedTreePanel.SyncTreeNode) parentNode
                            .getUserObject();
                    ClassDifference parentDiff = parentSyncNode.getDifference();
                    if (parentDiff != null) {
                        showClassFieldsTable(parentDiff, syncNode.getFieldData(), isLeftTree);
                    }
                }
            }
            // Handle class node
            else if (diff != null && !syncNode.isGhost()) {
                showClassFieldsTable(diff, null, isLeftTree);
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

    /**
     * Shows a comprehensive fields table for the selected class.
     * Similar to the schema editor view.
     */
    private void showClassFieldsTable(ClassDifference diff, DOSchemaField selectedField, boolean isLeftTree) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Schema source header
        String schemaSource = isLeftTree ? comparison.getReferenceLabel() : comparison.getComparedLabel();
        JLabel sourceLabel = new JLabel("Showing: " + schemaSource);
        sourceLabel.setFont(new Font("Arial", Font.BOLD, 13));
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        panel.add(sourceLabel, BorderLayout.NORTH);

        // Class info and fields table panel
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));

        // Class info header
        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        infoArea.setRows(2);

        StringBuilder info = new StringBuilder();
        info.append("Class: ").append(diff.getClassName()).append("\n");

        DOSchemaClass classToShow = isLeftTree ? diff.getReferenceClass() : diff.getComparedClass();
        if (classToShow != null) {
            info.append("Fields: ").append(classToShow.getFields() != null ? classToShow.getFields().length : 0);
        }

        infoArea.setText(info.toString());
        contentPanel.add(infoArea, BorderLayout.NORTH);

        // Fields table
        if (classToShow != null && classToShow.getFields() != null && classToShow.getFields().length > 0) {
            // Sort fields according to reference schema order
            DOSchemaField[] sortedFields = sortFieldsByReferenceOrder(classToShow.getFields(),
                    diff.getReferenceClass());
            JScrollPane fieldsTablePane = createFieldsTable(sortedFields);
            contentPanel.add(fieldsTablePane, BorderLayout.CENTER);

            // If a specific field was selected, try to highlight it in the table
            if (selectedField != null && fieldsTablePane.getViewport().getView() instanceof JTable) {
                JTable table = (JTable) fieldsTablePane.getViewport().getView();
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (selectedField.getSource().equals(table.getValueAt(row, 0))) {
                        table.setRowSelectionInterval(row, row);
                        table.scrollRectToVisible(table.getCellRect(row, 0, true));
                        break;
                    }
                }
            }
        }

        panel.add(contentPanel, BorderLayout.CENTER);

        detailPanel.add(panel);
    }

    /**
     * Sorts fields according to the order they appear in the reference schema.
     * Fields not in reference appear at the end, sorted alphabetically.
     */
    private DOSchemaField[] sortFieldsByReferenceOrder(DOSchemaField[] fields, DOSchemaClass referenceClass) {
        if (fields == null || fields.length == 0) {
            return fields;
        }

        // Create a map of field names to their order in reference schema
        Map<String, Integer> referenceOrder = new HashMap<>();
        if (referenceClass != null && referenceClass.getFields() != null) {
            DOSchemaField[] refFields = referenceClass.getFields();
            for (int i = 0; i < refFields.length; i++) {
                referenceOrder.put(refFields[i].getSource(), i);
            }
        }

        // Sort fields: first by reference order, then alphabetically for fields not in
        // reference
        DOSchemaField[] sortedFields = fields.clone();
        Arrays.sort(sortedFields, (f1, f2) -> {
            Integer order1 = referenceOrder.get(f1.getSource());
            Integer order2 = referenceOrder.get(f2.getSource());

            // Both in reference: sort by reference order
            if (order1 != null && order2 != null) {
                return order1.compareTo(order2);
            }
            // Only f1 in reference: f1 comes first
            if (order1 != null) {
                return -1;
            }
            // Only f2 in reference: f2 comes first
            if (order2 != null) {
                return 1;
            }
            // Neither in reference: sort alphabetically
            return f1.getSource().compareTo(f2.getSource());
        });

        return sortedFields;
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
