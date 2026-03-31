package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;

/**
 * Abstract base for all WYSIWYG layout block components.
 * Each block wraps a LayoutNode and provides selection, drag handle, and double-click editing.
 */
public abstract class LayoutBlockPanel extends JPanel {

    protected LayoutNode layoutNode;
    private boolean selected;
    private LayoutCanvas canvas; // back-reference for selection management

    private static final Color SELECTED_BORDER = new Color(59, 130, 246);
    private static final Color HOVER_BORDER = new Color(148, 163, 184);
    private static final Color DEFAULT_BORDER = new Color(226, 232, 240);

    protected LayoutBlockPanel(LayoutNode node) {
        this.layoutNode = node;
        setOpaque(true);
        setBorder(BorderFactory.createLineBorder(DEFAULT_BORDER, 1));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (canvas != null)
                    canvas.selectBlock(LayoutBlockPanel.this);
                if (e.getClickCount() == 2)
                    onDoubleClick();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected)
                    setBorder(BorderFactory.createLineBorder(HOVER_BORDER, 1));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected)
                    setBorder(BorderFactory.createLineBorder(DEFAULT_BORDER, 1));
            }
        });
    }

    public LayoutNode getLayoutNode() {
        return layoutNode;
    }

    public void setLayoutNode(LayoutNode node) {
        this.layoutNode = node;
    }

    public void setCanvas(LayoutCanvas canvas) {
        this.canvas = canvas;
    }

    public LayoutCanvas getCanvas() {
        return canvas;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        setBorder(BorderFactory.createLineBorder(selected ? SELECTED_BORDER : DEFAULT_BORDER, selected ? 2 : 1));
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public Dimension getMaximumSize() {
        // Dynamic max: full width, current preferred height.
        // This ensures container blocks (Section, Tabs, Columns) grow/shrink
        // when children are added or removed, instead of being frozen at initial size.
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** Override to handle double-click editing (popup property editor). */
    protected void onDoubleClick() {
        // subclasses override
    }

    /** Refresh the visual display from the underlying LayoutNode data. */
    public abstract void refreshFromNode();

    /**
     * Recursively collect the LayoutNode tree from this block.
     * For leaf blocks, just returns the node. For containers, recurses into children.
     */
    public LayoutNode collectNode() {
        return layoutNode;
    }
}
