package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;

/**
 * Visual block for DIVIDER nodes. Thin horizontal separator line.
 */
public class DividerBlock extends LayoutBlockPanel {

    public DividerBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout());
        setBackground(new Color(250, 251, 252));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        setPreferredSize(new Dimension(300, 16));

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(new Color(203, 213, 225));
        add(sep, BorderLayout.CENTER);
    }

    @Override
    public void refreshFromNode() {
        // Divider has no editable display
    }
}
