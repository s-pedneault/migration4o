package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;
import migration4o.models.ui.layout.LayoutNodeType;

/**
 * Visual block for COLUMNS nodes. Horizontal split panel with visible column separators.
 * Each column is a drop zone that accepts child blocks.
 */
public class ColumnsBlock extends LayoutBlockPanel {

    private JPanel columnsContainer;
    private List<ColumnPanel> columnPanels = new ArrayList<>();

    private static final Color COL_BORDER = new Color(226, 232, 240);
    private static final Color COL_EMPTY_BG = new Color(248, 250, 252);

    public ColumnsBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(COL_BORDER, 1), BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        columnsContainer = new JPanel();
        columnsContainer.setOpaque(false);
        add(columnsContainer, BorderLayout.CENTER);

        buildColumns();
    }

    private void buildColumns() {
        columnsContainer.removeAll();
        columnPanels.clear();

        int count = 2;
        try {
            count = Integer.parseInt(layoutNode.prop("count", "2"));
        } catch (NumberFormatException ignore) {
        }

        columnsContainer.setLayout(new GridLayout(1, count, 4, 0));

        // Create or reuse LayoutNode children for each column
        while (layoutNode.children.size() < count) {
            layoutNode.children.add(new LayoutNode(LayoutNodeType.COLUMN));
        }

        for (int i = 0; i < count; i++) {
            LayoutNode colNode = layoutNode.children.get(i);
            ColumnPanel colPanel = new ColumnPanel(colNode, i);
            columnPanels.add(colPanel);
            columnsContainer.add(colPanel);
        }
    }

    public List<ColumnPanel> getColumnPanels() {
        return columnPanels;
    }

    @Override
    public void refreshFromNode() {
        buildColumns();
        if (getCanvas() != null) {
            for (ColumnPanel cp : columnPanels)
                getCanvas().setupContainerDrop(cp);
        }
        revalidate();
        repaint();
    }

    @Override
    public LayoutNode collectNode() {
        layoutNode.children.clear();
        for (ColumnPanel cp : columnPanels) {
            layoutNode.children.add(cp.collectNode());
        }
        return layoutNode;
    }

    /**
     * A single column drop zone inside a ColumnsBlock.
     */
    public class ColumnPanel extends JPanel {
        private final LayoutNode columnNode;
        private final int columnIndex;

        ColumnPanel(LayoutNode columnNode, int columnIndex) {
            this.columnNode = columnNode;
            this.columnIndex = columnIndex;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(COL_EMPTY_BG);
            setBorder(BorderFactory.createDashedBorder(new Color(203, 213, 225), 2, 4, 3, true));
            setMinimumSize(new Dimension(80, 40));
        }

        public LayoutNode getColumnNode() {
            return columnNode;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        /** Add a child block to this column. */
        public void addChildBlock(LayoutBlockPanel child) {
            child.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(child);
            add(Box.createRigidArea(new Dimension(0, 3)));
            // Remove empty appearance
            if (getComponentCount() > 0) {
                setBorder(BorderFactory.createLineBorder(COL_BORDER, 1));
                setBackground(Color.WHITE);
            }
        }

        /** Collect the LayoutNode tree from this column. */
        public LayoutNode collectNode() {
            columnNode.children.clear();
            for (Component c : getComponents()) {
                if (c instanceof LayoutBlockPanel) {
                    columnNode.children.add(((LayoutBlockPanel) c).collectNode());
                }
            }
            return columnNode;
        }

        /** Returns child blocks in order. */
        public List<LayoutBlockPanel> getChildBlocks() {
            List<LayoutBlockPanel> blocks = new ArrayList<>();
            for (Component c : getComponents()) {
                if (c instanceof LayoutBlockPanel)
                    blocks.add((LayoutBlockPanel) c);
            }
            return blocks;
        }
    }
}
