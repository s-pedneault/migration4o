package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.popups;

import java.awt.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.LayoutBlockPanel;

/**
 * Popup editor for SECTION properties: title, collapsible, title color.
 */
public class SectionPropertiesPopup {

    /**
     * Show a modal popup to edit section properties.
     * @return true if OK was pressed (properties were updated)
     */
    public static boolean show(Component parent, LayoutBlockPanel block) {
        LayoutNode node = block.getLayoutNode();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Title
        JTextField titleField = new JTextField(node.prop("title", ""), 25);
        addRow(panel, "Title:", titleField);

        // Collapsible
        JCheckBox collapsibleBox = new JCheckBox("Collapsible", node.boolProp("collapsible"));
        collapsibleBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(collapsibleBox);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Title color
        JTextField colorField = new JTextField(node.prop("titleColor", ""), 10);
        JButton colorBtn = new JButton("Pick...");
        JPanel colorRow = new JPanel(new BorderLayout(4, 0));
        colorRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        colorRow.add(new JLabel("Title Color:"), BorderLayout.WEST);
        colorRow.add(colorField, BorderLayout.CENTER);
        colorRow.add(colorBtn, BorderLayout.EAST);
        panel.add(colorRow);

        colorBtn.addActionListener(e -> {
            Color init = null;
            try {
                init = Color.decode(colorField.getText().trim());
            } catch (Exception ignore) {
            }
            Color chosen = JColorChooser.showDialog(parent, "Section Title Color", init);
            if (chosen != null) {
                colorField.setText(String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
            }
        });

        int result = JOptionPane.showConfirmDialog(parent, panel, "Section Properties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            node.setProp("title", titleField.getText().trim());
            node.setProp("collapsible", collapsibleBox.isSelected() ? "true" : "");
            node.setProp("titleColor", colorField.getText().trim());
            block.refreshFromNode();
            return true;
        }
        return false;
    }

    private static void addRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(90, 25));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createRigidArea(new Dimension(0, 6)));
    }
}
