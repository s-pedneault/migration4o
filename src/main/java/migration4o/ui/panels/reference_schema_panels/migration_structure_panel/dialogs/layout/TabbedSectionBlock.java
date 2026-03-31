package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import migration4o.models.ui.layout.LayoutNode;
import migration4o.models.ui.layout.LayoutNodeType;

/**
 * Visual block for TABBED_SECTION nodes. Wraps a JTabbedPane with editable tab titles.
 */
public class TabbedSectionBlock extends LayoutBlockPanel {

    private JTabbedPane tabbedPane;
    private List<TabPanel> tabPanels = new ArrayList<>();

    public TabbedSectionBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1), BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        // Optional title above tabs
        String title = node.prop("title", "");
        if (!title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
            titleLabel.setForeground(new Color(100, 116, 139));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
            add(titleLabel, BorderLayout.NORTH);
        }

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabbedPane, BorderLayout.CENTER);

        buildTabs();
    }

    private void buildTabs() {
        tabbedPane.removeAll();
        tabPanels.clear();

        // Ensure at least one tab
        if (layoutNode.children.isEmpty()) {
            LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
            tab.setProp("title", "Tab 1");
            layoutNode.children.add(tab);
        }

        for (LayoutNode tabNode : layoutNode.children) {
            if (tabNode.type != LayoutNodeType.TAB)
                continue;
            TabPanel tabPanel = new TabPanel(tabNode);
            tabPanels.add(tabPanel);
            tabbedPane.addTab(tabNode.prop("title", "Tab"), tabPanel);
        }
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public List<TabPanel> getTabPanels() {
        return tabPanels;
    }

    @Override
    public void refreshFromNode() {
        buildTabs();
        if (getCanvas() != null) {
            for (TabPanel tp : tabPanels)
                getCanvas().setupContainerDrop(tp);
        }
        revalidate();
        repaint();
    }

    @Override
    public LayoutNode collectNode() {
        layoutNode.children.clear();
        for (TabPanel tp : tabPanels) {
            layoutNode.children.add(tp.collectNode());
        }
        return layoutNode;
    }

    @Override
    protected void onDoubleClick() {
        // Double-click on a tab area might edit the tab title
        int idx = tabbedPane.getSelectedIndex();
        if (idx >= 0 && idx < tabPanels.size() && getCanvas() != null) {
            getCanvas().showTabProperties(tabPanels.get(idx));
        }
    }

    /**
     * Content panel for a single tab — vertical stacking layout like a section body.
     */
    public class TabPanel extends JPanel {
        private final LayoutNode tabNode;

        TabPanel(LayoutNode tabNode) {
            this.tabNode = tabNode;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        public LayoutNode getTabNode() {
            return tabNode;
        }

        /** Add a child block to this tab. */
        public void addChildBlock(LayoutBlockPanel child) {
            child.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(child);
            add(Box.createRigidArea(new Dimension(0, 3)));
        }

        /** Collect the LayoutNode tree from this tab. */
        public LayoutNode collectNode() {
            tabNode.children.clear();
            for (Component c : getComponents()) {
                if (c instanceof LayoutBlockPanel) {
                    tabNode.children.add(((LayoutBlockPanel) c).collectNode());
                }
            }
            return tabNode;
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
