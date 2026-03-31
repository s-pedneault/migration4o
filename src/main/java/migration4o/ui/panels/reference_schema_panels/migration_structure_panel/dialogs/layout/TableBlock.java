package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;

/**
 * Visual block for TABLE nodes. Card showing collection name + column count badge.
 */
public class TableBlock extends LayoutBlockPanel {

    private JLabel nameLabel;
    private JLabel badgeLabel;
    private String resolvedTitle;

    private static final Color TABLE_BG = new Color(240, 245, 255);
    private static final Color TABLE_BORDER = new Color(191, 219, 254);

    public TableBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout(8, 0));
        setBackground(TABLE_BG);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TABLE_BORDER, 1), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        setPreferredSize(new Dimension(300, 40));

        // Table icon
        JLabel icon = new JLabel("\u2637"); // trigram
        icon.setFont(icon.getFont().deriveFont(16f));
        icon.setForeground(new Color(59, 130, 246));
        add(icon, BorderLayout.WEST);

        // Collection name
        nameLabel = new JLabel(getDisplayName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(new Color(30, 41, 59));
        add(nameLabel, BorderLayout.CENTER);

        // Column count badge
        String cols = node.prop("columns", "");
        int colCount = cols.isEmpty() ? 0 : cols.split(",").length;
        badgeLabel = new JLabel(colCount + " cols");
        badgeLabel.setFont(badgeLabel.getFont().deriveFont(10f));
        badgeLabel.setForeground(new Color(71, 85, 105));
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground(new Color(226, 232, 240));
        badgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(badgeLabel, BorderLayout.EAST);
    }

    /** Set the resolved display title from the reference schema. */
    public void setResolvedTitle(String title) {
        this.resolvedTitle = title;
        nameLabel.setText(getDisplayName());
    }

    private String getDisplayName() {
        if (resolvedTitle != null && !resolvedTitle.isEmpty())
            return resolvedTitle;
        return layoutNode.prop("ref", "Collection");
    }

    @Override
    public void refreshFromNode() {
        nameLabel.setText(getDisplayName());
        String cols = layoutNode.prop("columns", "");
        int colCount = cols.isEmpty() ? 0 : cols.split(",").length;
        badgeLabel.setText(colCount + " cols");
    }

    @Override
    protected void onDoubleClick() {
        if (getCanvas() != null)
            getCanvas().showTableProperties(this);
    }
}
