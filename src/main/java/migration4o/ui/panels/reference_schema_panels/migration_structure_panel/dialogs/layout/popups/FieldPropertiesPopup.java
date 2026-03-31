package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.popups;

import java.awt.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.FieldBlock;

/**
 * Popup editor for FIELD properties: label override, format, style.
 */
public class FieldPropertiesPopup {

    /**
     * Show a modal popup to edit field properties.
     * @param fieldType the resolved type of the field (e.g. "date", "boolean") — may be null
     * @return true if OK was pressed
     */
    public static boolean show(Component parent, FieldBlock block, String fieldType) {
        LayoutNode node = block.getLayoutNode();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Ref (read-only)
        JTextField refField = new JTextField(node.prop("ref", ""), 20);
        refField.setEditable(false);
        refField.setBackground(new Color(240, 240, 240));
        addRow(panel, "Ref:", refField);

        // Label override
        JTextField labelField = new JTextField(node.prop("label", ""), 20);
        addRow(panel, "Label:", labelField);

        // Format
        JPanel formatPanel = buildFormatEditor(node, fieldType);
        if (formatPanel != null) {
            formatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(formatPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        // Style
        String[] styles = { "Normal", "Header 1", "Header 2", "Header 3", "Header 4", "Small", "Caption" };
        String[] styleValues = { "", "h1", "h2", "h3", "h4", "small", "caption" };
        JComboBox<String> styleCombo = new JComboBox<>(styles);
        String currentStyle = node.prop("style", "");
        for (int i = 0; i < styleValues.length; i++) {
            if (styleValues[i].equals(currentStyle)) {
                styleCombo.setSelectedIndex(i);
                break;
            }
        }
        addRow(panel, "Style:", styleCombo);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Field Properties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            node.setProp("label", labelField.getText().trim());
            int styleIdx = styleCombo.getSelectedIndex();
            node.setProp("style", styleIdx >= 0 ? styleValues[styleIdx] : "");
            // Format is updated directly by format editor components
            block.refreshFromNode();
            return true;
        }
        return false;
    }

    private static JPanel buildFormatEditor(LayoutNode node, String fieldType) {
        if (fieldType == null)
            return null;

        JPanel formatPanel = new JPanel();
        formatPanel.setLayout(new BoxLayout(formatPanel, BoxLayout.Y_AXIS));
        formatPanel.setBorder(BorderFactory.createTitledBorder("Format"));
        formatPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        String currentFormat = node.prop("format", "");

        switch (fieldType) {
        case "date":
        case "java.util.Date":
        case "java.sql.Timestamp": {
            JComboBox<String> combo = new JComboBox<>(new String[] { "", "yyyy-MM-dd", "dd/MM/yyyy", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss" });
            combo.setEditable(true);
            String initVal = currentFormat.startsWith("date:") ? currentFormat.substring(5) : "";
            combo.setSelectedItem(initVal);
            combo.addActionListener(e -> {
                String pat = (String) combo.getSelectedItem();
                node.setProp("format", (pat != null && !pat.isEmpty()) ? "date:" + pat : "");
            });
            addSubRow(formatPanel, "Pattern:", combo);
            break;
        }
        case "boolean":
        case "java.lang.Boolean": {
            String trueVal = "", falseVal = "";
            if (currentFormat.startsWith("bool:")) {
                String[] parts = currentFormat.substring(5).split(",", 2);
                trueVal = parts[0];
                falseVal = parts.length > 1 ? parts[1] : "";
            }
            JTextField trueField = new JTextField(trueVal, 10);
            JTextField falseField = new JTextField(falseVal, 10);
            Runnable updater = () -> {
                String t = trueField.getText().trim(), f = falseField.getText().trim();
                node.setProp("format", (!t.isEmpty() || !f.isEmpty()) ? "bool:" + t + "," + f : "");
            };
            trueField.addActionListener(e -> updater.run());
            falseField.addActionListener(e -> updater.run());
            addSubRow(formatPanel, "True:", trueField);
            addSubRow(formatPanel, "False:", falseField);
            break;
        }
        case "long":
        case "java.lang.Long": {
            boolean isLongDate = currentFormat.startsWith("longdate:");
            JRadioButton numRadio = new JRadioButton("Number", !isLongDate);
            JRadioButton dateRadio = new JRadioButton("As Date", isLongDate);
            ButtonGroup bg = new ButtonGroup();
            bg.add(numRadio);
            bg.add(dateRadio);
            JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            radioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            radioPanel.add(numRadio);
            radioPanel.add(dateRadio);
            formatPanel.add(radioPanel);

            String initPattern = isLongDate ? currentFormat.substring(9) : (currentFormat.startsWith("num:") ? currentFormat.substring(4) : "");
            JTextField patField = new JTextField(initPattern, 15);
            Runnable updater = () -> {
                String pat = patField.getText().trim();
                if (dateRadio.isSelected() && !pat.isEmpty())
                    node.setProp("format", "longdate:" + pat);
                else if (numRadio.isSelected() && !pat.isEmpty())
                    node.setProp("format", "num:" + pat);
                else
                    node.setProp("format", "");
            };
            patField.addActionListener(e -> updater.run());
            numRadio.addActionListener(e -> updater.run());
            dateRadio.addActionListener(e -> updater.run());
            addSubRow(formatPanel, "Pattern:", patField);
            break;
        }
        case "int":
        case "float":
        case "double":
        case "short":
        case "java.lang.Integer":
        case "java.lang.Float":
        case "java.lang.Double": {
            String initVal = currentFormat.startsWith("num:") ? currentFormat.substring(4) : "";
            JTextField patField = new JTextField(initVal, 15);
            patField.addActionListener(e -> {
                String pat = patField.getText().trim();
                node.setProp("format", !pat.isEmpty() ? "num:" + pat : "");
            });
            addSubRow(formatPanel, "Pattern:", patField);
            break;
        }
        default:
            return null;
        }

        return formatPanel;
    }

    private static void addRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(70, 25));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private static void addSubRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
    }
}
