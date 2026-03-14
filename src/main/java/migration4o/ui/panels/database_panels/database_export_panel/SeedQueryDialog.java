package migration4o.ui.panels.database_panels.database_export_panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.SeedCondition;
import migration4o.models.ui.SeedQuery;
import migration4o.schema.DOSchemaService;
import migration4o.ui.common.FieldSelectorPanel;

/**
 * Modal dialog for adding or editing a single seed query.
 * Users select a class (shown by title) and define conditions using an
 * interactive field selector tree and a conditions table.
 */
public class SeedQueryDialog extends JDialog {

    private boolean confirmed = false;
    private JComboBox<String> classCombo;
    private DefaultTableModel conditionsModel;
    private JTable conditionsTable;
    private JPanel fieldSelectorContainer;
    private FieldSelectorPanel fieldSelectorPanel;

    // Sorted list — index matches classCombo index
    private final List<DOSchemaClass> exportedClasses = new ArrayList<>();

    public SeedQueryDialog(Frame parent, SeedQuery existing) {
        super(parent, existing != null ? "Edit Seed Query" : "Add Seed Query", true);
        initComponents(existing);
        pack();
        setMinimumSize(new Dimension(800, 450));
        setLocationRelativeTo(parent);
    }

    private void initComponents(SeedQuery existing) {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Class selector — shows titles
        JPanel classPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        classPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        classPanel.add(new JLabel("Class: "));
        classCombo = new JComboBox<>();
        classCombo.setPreferredSize(new Dimension(450, 25));
        populateClassCombo();
        classCombo.addActionListener(e -> onClassChanged());
        classPanel.add(classCombo);
        mainPanel.add(classPanel);

        mainPanel.add(Box.createVerticalStrut(10));

        // Split pane: left = field selector, right = conditions
        fieldSelectorContainer = new JPanel(new BorderLayout());
        fieldSelectorContainer.setBorder(BorderFactory.createTitledBorder("Fields (double-click to add condition)"));
        fieldSelectorContainer.setPreferredSize(new Dimension(300, 250));

        JPanel conditionsPanel = new JPanel();
        conditionsPanel.setLayout(new BoxLayout(conditionsPanel, BoxLayout.Y_AXIS));
        conditionsPanel.setBorder(BorderFactory.createTitledBorder("Conditions (all must match)"));

        conditionsModel = new DefaultTableModel(new String[] { "Field", "Operator", "Value" }, 0);
        conditionsTable = new JTable(conditionsModel);
        conditionsTable.setRowHeight(22);

        // Operator column uses combo editor
        JComboBox<String> opCombo = new JComboBox<>(new String[] { "EQUALS", "CONTAINS" });
        conditionsTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(opCombo));
        conditionsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        conditionsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        conditionsTable.getColumnModel().getColumn(2).setPreferredWidth(180);

        JScrollPane tableScroll = new JScrollPane(conditionsTable);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        conditionsPanel.add(tableScroll);

        conditionsPanel.add(Box.createVerticalStrut(4));

        JPanel condButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        condButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        condButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JButton removeCondBtn = new JButton("Remove Condition");
        removeCondBtn.addActionListener(e -> {
            int row = conditionsTable.getSelectedRow();
            if (row >= 0)
                conditionsModel.removeRow(row);
        });
        condButtons.add(removeCondBtn);
        conditionsPanel.add(condButtons);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fieldSelectorContainer, conditionsPanel);
        splitPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.35);
        mainPanel.add(splitPane);

        add(mainPanel, BorderLayout.CENTER);

        // OK / Cancel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.setPreferredSize(new Dimension(80, 28));
        cancelButton.setPreferredSize(new Dimension(80, 28));
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        KeyStroke esc = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> {
            confirmed = false;
            dispose();
        }, esc, javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Populate from existing
        if (existing != null) {
            for (int i = 0; i < exportedClasses.size(); i++) {
                if (exportedClasses.get(i).source.equals(existing.getClassName())) {
                    classCombo.setSelectedIndex(i);
                    break;
                }
            }
            for (SeedCondition cond : existing.getConditions()) {
                conditionsModel.addRow(new Object[] { cond.getFieldPath(), cond.getOperator().name(), cond.getValue() });
            }
        }

        // Initialize field selector for the initially selected class
        onClassChanged();
    }

    private void populateClassCombo() {
        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        if (schema == null)
            return;

        List<DOSchemaClass> migratable = new ArrayList<>();
        for (DOSchemaClass cls : schema.classes) {
            if (cls.migrate)
                migratable.add(cls);
        }

        // Sort by title (falling back to simple name)
        migratable.sort(Comparator.comparing(c -> getClassDisplayTitle(c).toLowerCase()));

        for (DOSchemaClass cls : migratable) {
            exportedClasses.add(cls);
            classCombo.addItem(getClassDisplayTitle(cls));
        }
    }

    private String getClassDisplayTitle(DOSchemaClass cls) {
        String simpleName = cls.source;
        int dot = simpleName.lastIndexOf('.');
        if (dot >= 0)
            simpleName = simpleName.substring(dot + 1);

        if (cls.title != null && !cls.title.isBlank()) {
            return cls.title + "  (" + simpleName + ")";
        }
        return simpleName;
    }

    private void onClassChanged() {
        int idx = classCombo.getSelectedIndex();
        if (idx < 0 || idx >= exportedClasses.size())
            return;

        DOSchemaClass selectedClass = exportedClasses.get(idx);

        // Collect currently used field paths for visual marking
        List<String> usedPaths = new ArrayList<>();
        for (int r = 0; r < conditionsModel.getRowCount(); r++) {
            String f = (String) conditionsModel.getValueAt(r, 0);
            if (f != null && !f.isBlank())
                usedPaths.add(f);
        }

        fieldSelectorContainer.removeAll();
        fieldSelectorPanel = new FieldSelectorPanel(selectedClass, usedPaths, (fieldPath, fieldLabel) -> {
            // Double-click adds a condition row pre-filled with this field
            conditionsModel.addRow(new Object[] { fieldPath, "EQUALS", "" });
            // Update selected paths in the tree
            refreshFieldSelectorSelection();
        });
        fieldSelectorContainer.add(fieldSelectorPanel, BorderLayout.CENTER);
        fieldSelectorContainer.revalidate();
        fieldSelectorContainer.repaint();
    }

    private void refreshFieldSelectorSelection() {
        if (fieldSelectorPanel == null)
            return;
        List<String> usedPaths = new ArrayList<>();
        for (int r = 0; r < conditionsModel.getRowCount(); r++) {
            String f = (String) conditionsModel.getValueAt(r, 0);
            if (f != null && !f.isBlank())
                usedPaths.add(f);
        }
        fieldSelectorPanel.setSelectedPaths(usedPaths);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public SeedQuery getSeedQuery() {
        if (conditionsTable.isEditing()) {
            conditionsTable.getCellEditor().stopCellEditing();
        }

        int idx = classCombo.getSelectedIndex();
        if (idx < 0 || idx >= exportedClasses.size())
            return null;
        String className = exportedClasses.get(idx).source;
        SeedQuery query = new SeedQuery(className);

        for (int r = 0; r < conditionsModel.getRowCount(); r++) {
            String field = (String) conditionsModel.getValueAt(r, 0);
            String op = (String) conditionsModel.getValueAt(r, 1);
            String val = (String) conditionsModel.getValueAt(r, 2);
            if (field != null && !field.isBlank()) {
                SeedCondition.Operator operator;
                try {
                    operator = SeedCondition.Operator.valueOf(op);
                } catch (Exception e) {
                    operator = SeedCondition.Operator.EQUALS;
                }
                query.addCondition(new SeedCondition(field, operator, val != null ? val : ""));
            }
        }
        return query;
    }
}
