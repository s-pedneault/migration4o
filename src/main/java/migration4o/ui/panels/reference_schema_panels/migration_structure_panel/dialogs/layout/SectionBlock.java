package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;
import migration4o.models.ui.layout.LayoutNodeType;

/**
 * Visual block for SECTION nodes. Colored header bar with title + vertical child container.
 */
public class SectionBlock extends LayoutBlockPanel {

    private JLabel titleLabel;
    private JPanel headerPanel;
    private JPanel bodyPanel;

    private static final Color HEADER_BG = new Color(241, 245, 249);
    private static final Color HEADER_BORDER = new Color(226, 232, 240);

    public SectionBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Header bar
        headerPanel = new JPanel(new BorderLayout(6, 0));
        headerPanel.setBackground(HEADER_BG);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_BORDER), BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JLabel icon = new JLabel("\u25BC"); // down chevron
        icon.setForeground(new Color(100, 116, 139));
        icon.setFont(icon.getFont().deriveFont(10f));
        headerPanel.add(icon, BorderLayout.WEST);

        titleLabel = new JLabel(node.prop("title", "Section"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(new Color(30, 41, 59));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Body — stacks children vertically
        bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setBackground(Color.WHITE);
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(bodyPanel, BorderLayout.CENTER);
    }

    public JPanel getBodyPanel() {
        return bodyPanel;
    }

    /** Add a child block to this section's body. */
    public void addChildBlock(LayoutBlockPanel child) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyPanel.add(child);
        bodyPanel.add(Box.createRigidArea(new Dimension(0, 3)));
    }

    @Override
    public void refreshFromNode() {
        String title = layoutNode.prop("title", "Section");
        String color = layoutNode.prop("titleColor", "");
        titleLabel.setText(title);
        if (!color.isEmpty()) {
            try {
                headerPanel.setBackground(Color.decode(color));
                titleLabel.setForeground(Color.WHITE);
            } catch (NumberFormatException ignore) {
            }
        } else {
            headerPanel.setBackground(HEADER_BG);
            titleLabel.setForeground(new Color(30, 41, 59));
        }
    }

    @Override
    public LayoutNode collectNode() {
        layoutNode.children.clear();
        for (Component c : bodyPanel.getComponents()) {
            if (c instanceof LayoutBlockPanel) {
                layoutNode.children.add(((LayoutBlockPanel) c).collectNode());
            }
        }
        return layoutNode;
    }

    /** Returns child blocks in order. */
    public List<LayoutBlockPanel> getChildBlocks() {
        List<LayoutBlockPanel> blocks = new ArrayList<>();
        for (Component c : bodyPanel.getComponents()) {
            if (c instanceof LayoutBlockPanel)
                blocks.add((LayoutBlockPanel) c);
        }
        return blocks;
    }

    @Override
    protected void onDoubleClick() {
        if (getCanvas() != null)
            getCanvas().showSectionProperties(this);
    }
}
