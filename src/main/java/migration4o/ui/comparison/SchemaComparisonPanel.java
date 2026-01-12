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

    private JTree differenceTree;
    private DefaultTreeModel treeModel;
    private JPanel detailPanel;
    private JTextArea summaryArea;
    private JCheckBox showCollectionTypeDifferencesCheckbox;

    public SchemaComparisonPanel(SchemaComparison comparison,
            BiConsumer<String, DOSchemaClass> onAddClass,
            BiConsumer<DOSchemaClass, DOSchemaField> onAddField) {
        this.comparison = comparison;
        this.onAddClass = onAddClass;
        this.onAddField = onAddField;

        initializeUI();
        buildTree();
        updateSummary();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        add(createHeader(), BorderLayout.NORTH);

        // Split pane with tree on left and details on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.4);

        // Tree on left
        splitPane.setLeftComponent(createTreePanel());

        // Details on right
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        splitPane.setRightComponent(detailPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("Schema Comparison");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        summaryArea = new JTextArea(3, 40);
        summaryArea.setEditable(false);
        summaryArea.setBackground(panel.getBackground());
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(summaryArea, BorderLayout.CENTER);

        // Add checkbox for filtering collection type differences
        showCollectionTypeDifferencesCheckbox = new JCheckBox("Show collection content type differences", false);
        showCollectionTypeDifferencesCheckbox
                .setToolTipText("When unchecked, hides field differences that only differ in childrenType");
        showCollectionTypeDifferencesCheckbox.addActionListener(e -> {
            buildTree(); // Rebuild tree when checkbox state changes
        });
        panel.add(showCollectionTypeDifferencesCheckbox, BorderLayout.SOUTH);

        return panel;
    }

    private JScrollPane createTreePanel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Differences");
        treeModel = new DefaultTreeModel(root);
        differenceTree = new JTree(treeModel);
        differenceTree.setRootVisible(true);
        differenceTree.setShowsRootHandles(true);

        differenceTree.addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                showDetails(node);
            }
        });

        JScrollPane scrollPane = new JScrollPane(differenceTree);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Differences Tree"));
        return scrollPane;
    }

    private void buildTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        List<ClassDifference> differences = comparison.getDifferences();

        // Group by difference type
        DefaultMutableTreeNode missingInRef = new DefaultMutableTreeNode(
                "Classes only in " + comparison.getComparedLabel() + " (" +
                        differences.stream().filter(ClassDifference::isOnlyInCompared).count() + ")");
        DefaultMutableTreeNode missingInCmp = new DefaultMutableTreeNode(
                "Classes only in " + comparison.getReferenceLabel() + " (" +
                        differences.stream().filter(ClassDifference::isOnlyInReference).count() + ")");
        DefaultMutableTreeNode withDifferences = new DefaultMutableTreeNode("Classes with field differences (TBD)");

        for (ClassDifference diff : differences) {
            DifferenceNode node = new DifferenceNode(diff);

            if (diff.isOnlyInCompared()) {
                missingInRef.add(node);
            } else if (diff.isOnlyInReference()) {
                missingInCmp.add(node);
            } else {
                // Has field differences
                if (!diff.getFieldsOnlyInCompared().isEmpty()) {
                    DefaultMutableTreeNode fieldsNode = new DefaultMutableTreeNode(
                            "Fields only in " + comparison.getComparedLabel() + " ("
                                    + diff.getFieldsOnlyInCompared().size() + ")");
                    for (DOSchemaField field : diff.getFieldsOnlyInCompared()) {
                        fieldsNode.add(new FieldNode(field, diff.getComparedClass()));
                    }
                    node.add(fieldsNode);
                }

                if (!diff.getFieldsOnlyInReference().isEmpty()) {
                    DefaultMutableTreeNode fieldsNode = new DefaultMutableTreeNode(
                            "Fields only in " + comparison.getReferenceLabel() + " ("
                                    + diff.getFieldsOnlyInReference().size() + ")");
                    for (DOSchemaField field : diff.getFieldsOnlyInReference()) {
                        fieldsNode.add(new FieldNode(field, diff.getReferenceClass()));
                    }
                    node.add(fieldsNode);
                }

                if (!diff.getFieldsWithDifferences().isEmpty()) {
                    DefaultMutableTreeNode propsNode = new DefaultMutableTreeNode(
                            "Fields with property differences (" + getFilteredPropertyDifferencesCount(diff) + ")");
                    for (String fieldName : diff.getFieldsWithDifferences().keySet()) {
                        FieldPropertyDifference propDiff = diff.getFieldsWithDifferences().get(fieldName);

                        // Filter out if only childrenType differs and checkbox is unchecked
                        if (!showCollectionTypeDifferencesCheckbox.isSelected()
                                && isOnlyChildrenTypeDifference(propDiff)) {
                            continue;
                        }

                        PropertyDifferenceNode propNode = new PropertyDifferenceNode(fieldName, propDiff, diff);
                        propsNode.add(propNode);
                    }

                    // Only add the node if it has children after filtering
                    if (propsNode.getChildCount() > 0) {
                        node.add(propsNode);
                    }
                }

                // Only add the class node if it has any children (actual differences after
                // filtering)
                if (node.getChildCount() > 0) {
                    withDifferences.add(node);
                }
            }
        }

        // Update the count in the withDifferences node label
        withDifferences.setUserObject("Classes with field differences (" + withDifferences.getChildCount() + ")");

        if (missingInRef.getChildCount() > 0)
            root.add(missingInRef);
        if (missingInCmp.getChildCount() > 0)
            root.add(missingInCmp);
        if (withDifferences.getChildCount() > 0)
            root.add(withDifferences);

        treeModel.reload();

        // Expand first level
        differenceTree.expandRow(0);
    }

    private void showDetails(DefaultMutableTreeNode node) {
        detailPanel.removeAll();

        if (node.getUserObject() instanceof ClassDifference) {
            ClassDifference diff = (ClassDifference) node.getUserObject();
            showClassDifferenceDetails(diff);
        } else if (node instanceof FieldNode) {
            FieldNode fieldNode = (FieldNode) node;
            showFieldDetails(fieldNode.field, fieldNode.parentClass);
        } else if (node instanceof PropertyDifferenceNode) {
            PropertyDifferenceNode propNode = (PropertyDifferenceNode) node;
            showPropertyDifferenceDetails(propNode);
        }

        detailPanel.revalidate();
        detailPanel.repaint();
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

    private static class DifferenceNode extends DefaultMutableTreeNode {
        public DifferenceNode(ClassDifference diff) {
            super(diff);
        }

        @Override
        public String toString() {
            ClassDifference diff = (ClassDifference) getUserObject();
            return diff.getClassName();
        }
    }

    private static class FieldNode extends DefaultMutableTreeNode {
        private final DOSchemaField field;
        private final DOSchemaClass parentClass;

        public FieldNode(DOSchemaField field, DOSchemaClass parentClass) {
            super(field);
            this.field = field;
            this.parentClass = parentClass;
        }

        @Override
        public String toString() {
            return field.getSource() + " : " + field.getType();
        }
    }

    private static class PropertyDifferenceNode extends DefaultMutableTreeNode {
        private final String fieldName;
        private final FieldPropertyDifference propertyDiff;
        private final ClassDifference classDiff;

        public PropertyDifferenceNode(String fieldName, FieldPropertyDifference propertyDiff,
                ClassDifference classDiff) {
            super(fieldName);
            this.fieldName = fieldName;
            this.propertyDiff = propertyDiff;
            this.classDiff = classDiff;
        }

        @Override
        public String toString() {
            int diffCount = propertyDiff.getDifferences().size();
            return fieldName + " (" + diffCount + " difference" + (diffCount > 1 ? "s" : "") + ")";
        }
    }

    private void showPropertyDifferenceDetails(PropertyDifferenceNode propNode) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Title
        JLabel titleLabel = new JLabel("Field: " + propNode.fieldName);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Property differences table
        List<String[]> rows = new ArrayList<>();

        for (Map.Entry<String, FieldPropertyDifference.PropertyDiff> entry : propNode.propertyDiff.getDifferences()
                .entrySet()) {
            String property = entry.getKey();
            FieldPropertyDifference.PropertyDiff diff = entry.getValue();

            String refValue = formatValue(diff.getReferenceValue());
            String cmpValue = formatValue(diff.getComparedValue());

            rows.add(new String[] {
                    property,
                    refValue,
                    cmpValue
            });
        }

        String[][] data = rows.toArray(new String[0][]);
        String[] columns = {
                "Property",
                comparison.getReferenceLabel(),
                comparison.getComparedLabel()
        };

        JTable table = new JTable(data, columns);
        table.setEnabled(false);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(25);

        // Highlight differences with color
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (column > 0 && row < data.length) {
                    String refVal = data[row][1];
                    String cmpVal = data[row][2];
                    if (!refVal.equals(cmpVal)) {
                        c.setBackground(new Color(255, 250, 205)); // Light yellow
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }

                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add explanation text
        JTextArea explanation = new JTextArea(
                "The table above shows properties that differ between the two schemas.\n" +
                        "Rows are highlighted in yellow to indicate differences.");
        explanation.setEditable(false);
        explanation.setBackground(panel.getBackground());
        explanation.setWrapStyleWord(true);
        explanation.setLineWrap(true);
        explanation.setRows(2);
        explanation.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        panel.add(explanation, BorderLayout.SOUTH);

        detailPanel.add(panel);
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
