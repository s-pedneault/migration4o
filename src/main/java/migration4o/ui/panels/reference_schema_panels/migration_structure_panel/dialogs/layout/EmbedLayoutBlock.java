package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import migration4o.models.ui.layout.DetailLayout;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.schema.modules.DOModuleService;

/**
 * Visual block for sections with layoutRef — a linked reference to another class's layout.
 * Shows a non-editable linked indicator. Double-click opens the referenced class's designer.
 */
public class EmbedLayoutBlock extends LayoutBlockPanel {

    private JLabel titleLabel;
    private JLabel refLabel;
    private JPanel headerPanel;

    private static final Color HEADER_BG = new Color(237, 233, 254); // light purple
    private static final Color HEADER_BORDER = new Color(196, 181, 253); // purple border
    private static final Color LINK_COLOR = new Color(109, 40, 217); // purple text

    public EmbedLayoutBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout());
        setBackground(new Color(250, 248, 255));

        headerPanel = new JPanel(new BorderLayout(6, 0));
        headerPanel.setBackground(HEADER_BG);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_BORDER), BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JLabel icon = new JLabel("\uD83D\uDD17"); // link emoji
        icon.setFont(icon.getFont().deriveFont(12f));
        headerPanel.add(icon, BorderLayout.WEST);

        titleLabel = new JLabel(node.prop("title", "Linked Layout"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(new Color(30, 41, 59));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Right-click context menu on header
        String layoutRefClassForMenu = node.prop("layoutRef", "");
        headerPanel.addMouseListener(new MouseAdapter() {
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e.getComponent(), e.getX(), e.getY(), layoutRefClassForMenu);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        });

        // Body — shows the layout reference info
        JPanel bodyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bodyPanel.setBackground(new Color(250, 248, 255));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        String layoutRefClass = node.prop("layoutRef", "");
        String simpleName = layoutRefClass.contains(".") ? layoutRefClass.substring(layoutRefClass.lastIndexOf('.') + 1) : layoutRefClass;

        refLabel = new JLabel("Uses layout of " + simpleName);
        refLabel.setForeground(LINK_COLOR);
        refLabel.setFont(refLabel.getFont().deriveFont(Font.ITALIC, 12f));
        refLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bodyPanel.add(refLabel);

        // Status indicator
        boolean hasLayout = checkReferencedLayoutExists(layoutRefClass);
        JLabel status = new JLabel(hasLayout ? "\u2714 Layout found" : "\u26A0 No layout defined");
        status.setForeground(hasLayout ? new Color(22, 163, 74) : new Color(202, 138, 4));
        status.setFont(status.getFont().deriveFont(11f));
        bodyPanel.add(status);

        add(bodyPanel, BorderLayout.CENTER);
    }

    private boolean checkReferencedLayoutExists(String className) {
        return DOModuleService.getInstance().getClassLayout(className) != null;
    }

    private void showContextMenu(Component invoker, int x, int y, String className) {
        JPopupMenu popup = new JPopupMenu();
        boolean hasLayout = checkReferencedLayoutExists(className);
        JMenuItem designItem = new JMenuItem(hasLayout ? "Edit Layout\u2026" : "Design Layout\u2026");
        designItem.addActionListener(e -> {
            if (getCanvas() != null)
                getCanvas().openEmbeddedLayoutDesigner(className);
        });
        popup.add(designItem);
        popup.show(invoker, x, y);
    }

    @Override
    public void refreshFromNode() {
        titleLabel.setText(layoutNode.prop("title", "Linked Layout"));
        String layoutRefClass = layoutNode.prop("layoutRef", "");
        String simpleName = layoutRefClass.contains(".") ? layoutRefClass.substring(layoutRefClass.lastIndexOf('.') + 1) : layoutRefClass;
        refLabel.setText("Uses layout of " + simpleName);
    }

    @Override
    public LayoutNode collectNode() {
        // layoutRef sections have no inline children — they reference another class
        return layoutNode;
    }

    @Override
    protected void onDoubleClick() {
        if (getCanvas() != null)
            getCanvas().showEmbedLayoutProperties(this);
    }
}
