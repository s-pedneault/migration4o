package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.layout.*;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.DetailLayoutDesigner;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.DetailLayoutDesigner.FieldPaletteItem;
import migration4o.util.DatabaseUtil;

/**
 * Central WYSIWYG canvas for the layout designer.
 * Scrollable vertical stack of visual blocks. Manages selection, DnD, insertion indicators,
 * and converts between visual blocks and DetailLayout model.
 */
public class LayoutCanvas extends JPanel {

    private JPanel contentPanel;
    private LayoutBlockPanel selectedBlock;
    private final DOSchemaClass schemaClass;
    private final DOSchema refSchema;

    // DnD support — javaJVMLocalObjectMimeType prevents AWT from serializing the transfer data
    public static final DataFlavor BLOCK_FLAVOR;
    static {
        try {
            BLOCK_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + LayoutBlockPanel.class.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    // Active drop target state — shared across all ContainerDropHandlers so only one
    // container shows the insertion indicator at a time.
    private JPanel activeDropContainer;
    private int activeDropIndex = -1;

    // Static DnD refs — macOS native DnD routes through the system pasteboard even for
    // intra-JVM drags, triggering serialization.  We bypass Transferable.getTransferData()
    // entirely: store the dragged object here before startDrag, read it in the drop handler.
    static LayoutBlockPanel draggedBlock;

    // Callback interface for property popups (implemented by the designer frame)
    private PropertyEditorCallback editorCallback;

    public interface PropertyEditorCallback {
        void editSectionProperties(LayoutBlockPanel block);

        void editFieldProperties(FieldBlock block);

        void editTableProperties(TableBlock block);

        void editTabProperties(TabbedSectionBlock.TabPanel tabPanel);

        default void openEmbeddedLayoutDesigner(String className) {
        }
    }

    public LayoutCanvas(DOSchemaClass schemaClass, DOSchema refSchema) {
        this.schemaClass = schemaClass;
        this.refSchema = refSchema;

        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Empty state hint
        showEmptyHint();

        // Click on empty space deselects
        contentPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectBlock(null);
            }
        });

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Set up as drop target for block DnD
        setupContainerDrop(contentPanel);
    }

    public void setEditorCallback(PropertyEditorCallback callback) {
        this.editorCallback = callback;
    }

    // ── Selection ──────────────────────────────────────────────────

    public void selectBlock(LayoutBlockPanel block) {
        if (selectedBlock != null)
            selectedBlock.setSelected(false);
        selectedBlock = block;
        if (selectedBlock != null)
            selectedBlock.setSelected(true);
    }

    public LayoutBlockPanel getSelectedBlock() {
        return selectedBlock;
    }

    // ── Load / Build Layout ────────────────────────────────────────

    /** Load a DetailLayout into the canvas, creating visual blocks. */
    public void loadLayout(DetailLayout layout) {
        contentPanel.removeAll();
        if (layout == null || layout.isEmpty()) {
            showEmptyHint();
            revalidate();
            repaint();
            return;
        }
        for (LayoutNode node : layout.nodes) {
            LayoutBlockPanel block = createBlock(node);
            addBlockToContent(block);
        }
        revalidate();
        repaint();
    }

    /** Collect the current visual layout back into a DetailLayout model. */
    public DetailLayout buildLayout() {
        DetailLayout layout = new DetailLayout();
        for (Component c : contentPanel.getComponents()) {
            if (c instanceof LayoutBlockPanel) {
                layout.nodes.add(((LayoutBlockPanel) c).collectNode());
            }
        }
        return layout;
    }

    /** Replace the canvas content with a new auto-generated layout. */
    public void replaceLayout(DetailLayout layout) {
        loadLayout(layout);
    }

    // ── Block Factory ──────────────────────────────────────────────

    /** Create the appropriate visual block for a LayoutNode, recursively building children. */
    public LayoutBlockPanel createBlock(LayoutNode node) {
        LayoutBlockPanel block;
        switch (node.type) {
        case SECTION: {
            if (node.prop("layoutRef") != null) {
                // Linked layout reference — special visual block
                block = new EmbedLayoutBlock(node);
            } else {
                SectionBlock sb = new SectionBlock(node);
                for (LayoutNode child : node.children) {
                    LayoutBlockPanel childBlock = createBlock(child);
                    sb.addChildBlock(childBlock);
                }
                setupContainerDrop(sb.getBodyPanel());
                block = sb;
            }
            break;
        }
        case FIELD: {
            FieldBlock fb = new FieldBlock(node);
            applyFieldTypeColor(fb);
            block = fb;
            break;
        }
        case DIVIDER:
            block = new DividerBlock(node);
            break;
        case TABLE: {
            TableBlock tb = new TableBlock(node);
            applyTableTitle(tb);
            block = tb;
            break;
        }
        case COLUMNS: {
            ColumnsBlock cb = new ColumnsBlock(node);
            // Populate column children
            for (int i = 0; i < cb.getColumnPanels().size(); i++) {
                ColumnsBlock.ColumnPanel colPanel = cb.getColumnPanels().get(i);
                LayoutNode colNode = colPanel.getColumnNode();
                for (LayoutNode child : colNode.children) {
                    LayoutBlockPanel childBlock = createBlock(child);
                    colPanel.addChildBlock(childBlock);
                }
            }
            for (ColumnsBlock.ColumnPanel cp : cb.getColumnPanels())
                setupContainerDrop(cp);
            block = cb;
            break;
        }
        case TABBED_SECTION: {
            TabbedSectionBlock tsb = new TabbedSectionBlock(node);
            // Populate tab children
            for (TabbedSectionBlock.TabPanel tabPanel : tsb.getTabPanels()) {
                LayoutNode tabNode = tabPanel.getTabNode();
                for (LayoutNode child : tabNode.children) {
                    LayoutBlockPanel childBlock = createBlock(child);
                    tabPanel.addChildBlock(childBlock);
                }
            }
            for (TabbedSectionBlock.TabPanel tp : tsb.getTabPanels())
                setupContainerDrop(tp);
            block = tsb;
            break;
        }
        default:
            // Unknown type — use a simple label
            block = new LayoutBlockPanel(node) {
                {
                    add(new JLabel("Unknown: " + node.type));
                }

                @Override
                public void refreshFromNode() {
                }
            };
            break;
        }
        block.setCanvas(this);
        initBlockDrag(block);
        return block;
    }

    private void applyFieldTypeColor(FieldBlock fb) {
        String ref = fb.getLayoutNode().prop("ref", "");
        DOSchemaField field = resolveFieldByRef(ref);
        if (field != null) {
            if (field.attributes.type != null)
                fb.setFieldTypeColor(field.attributes.type);
            // Display the schema title instead of the raw source name
            String title = field.attributes.title;
            if (title != null && !title.trim().isEmpty())
                fb.setResolvedTitle(title.trim());
        }
    }

    private void applyTableTitle(TableBlock tb) {
        String ref = tb.getLayoutNode().prop("ref", "");
        DOSchemaField field = resolveFieldByRef(ref);
        if (field != null) {
            String title = field.attributes.title;
            if (title != null && !title.trim().isEmpty())
                tb.setResolvedTitle(title.trim());
        }
    }

    // ── Block Operations (public API for top-level canvas) ─────────

    /** Add a block to the top-level content panel. */
    public void addBlockToContent(LayoutBlockPanel block) {
        removeEmptyHint();
        addBlockToContainer(contentPanel, block);
    }

    /** Insert a block at a specific index in the top-level content. */
    public void insertBlockAt(LayoutBlockPanel block, int index) {
        removeEmptyHint();
        insertBlockInContainer(contentPanel, block, index);
    }

    /** Remove a block from wherever it is in the hierarchy. */
    public void removeBlock(LayoutBlockPanel block) {
        removeBlockFromParent(block);
        if (getTopLevelBlocks().isEmpty())
            showEmptyHint();
        revalidateUp(contentPanel);
    }

    /** Delete the currently selected block. */
    public void deleteSelected() {
        if (selectedBlock != null) {
            removeBlock(selectedBlock);
        }
    }

    /** Get all top-level blocks. */
    public List<LayoutBlockPanel> getTopLevelBlocks() {
        return getBlocksIn(contentPanel);
    }

    // ── Generic Container Operations ───────────────────────────────
    // Reusable methods for ANY vertical-stacking drop zone:
    // contentPanel, SectionBlock body, ColumnPanel, TabPanel.

    /** Get all LayoutBlockPanel children from a container. */
    static List<LayoutBlockPanel> getBlocksIn(Container container) {
        List<LayoutBlockPanel> blocks = new ArrayList<>();
        for (Component c : container.getComponents()) {
            if (c instanceof LayoutBlockPanel)
                blocks.add((LayoutBlockPanel) c);
        }
        return blocks;
    }

    /** Append a block to a container with spacer. */
    void addBlockToContainer(JPanel container, LayoutBlockPanel block) {
        block.setCanvas(this);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(block);
        container.add(Box.createRigidArea(new Dimension(0, 3)));
    }

    /** Insert a block at index in a container with spacer. */
    void insertBlockInContainer(JPanel container, LayoutBlockPanel block, int index) {
        block.setCanvas(this);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Find the actual component position by counting LayoutBlockPanels,
        // instead of fragile index*2 arithmetic that assumes perfect block-spacer alternation.
        int componentIndex = componentIndexOfNthBlock(container, index);
        container.add(block, componentIndex);
        container.add(Box.createRigidArea(new Dimension(0, 3)), componentIndex + 1);
    }

    /** Find the component index of the N-th LayoutBlockPanel in a container. */
    private static int componentIndexOfNthBlock(Container container, int blockIndex) {
        int blockCount = 0;
        Component[] comps = container.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof LayoutBlockPanel) {
                if (blockCount == blockIndex)
                    return i;
                blockCount++;
            }
        }
        return container.getComponentCount();
    }

    /** Remove a block from whatever container it's in, including spacer cleanup.
     *  Does NOT revalidate/repaint — caller is responsible for that after the
     *  block is re-inserted (avoids painting detached JTabbedPane during mid-DnD). */
    void removeBlockFromParent(LayoutBlockPanel block) {
        Container parent = block.getParent();
        if (parent == null)
            return;

        // Find block position before removal
        Component[] comps = parent.getComponents();
        int blockIdx = -1;
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == block) {
                blockIdx = i;
                break;
            }
        }
        if (blockIdx < 0)
            return;

        parent.remove(block);
        // After removal, components shifted left. Clean up adjacent spacer.
        if (blockIdx < parent.getComponentCount()) {
            // Spacer that was AFTER the block is now at blockIdx
            Component next = parent.getComponent(blockIdx);
            if (next instanceof Box.Filler)
                parent.remove(next);
        } else if (blockIdx > 0 && blockIdx - 1 < parent.getComponentCount()) {
            // Block was last — remove preceding spacer to avoid orphan
            Component prev = parent.getComponent(blockIdx - 1);
            if (prev instanceof Box.Filler)
                parent.remove(prev);
        }

        if (selectedBlock == block)
            selectedBlock = null;
    }

    /** Revalidate from a component all the way up to this LayoutCanvas.
     *  Ensures parent blocks with dynamic maxSize recalculate their heights. */
    private void revalidateUp(Component from) {
        Component c = from;
        while (c != null) {
            if (c instanceof JComponent) {
                ((JComponent) c).revalidate();
            }
            if (c == LayoutCanvas.this)
                break;
            c = c.getParent();
        }
        repaint();
    }

    /** Get the set of field refs already used in the layout. */
    public Set<String> collectUsedFieldRefs() {
        Set<String> refs = new HashSet<>();
        DetailLayout layout = buildLayout();
        for (LayoutNode node : layout.nodes) {
            collectRefsRecursive(node, refs);
        }
        return refs;
    }

    private void collectRefsRecursive(LayoutNode node, Set<String> refs) {
        String ref = node.prop("ref");
        if (ref != null && (node.type == LayoutNodeType.FIELD || node.type == LayoutNodeType.TABLE || node.prop("layoutRef") != null))
            refs.add(ref);
        for (LayoutNode child : node.children) {
            collectRefsRecursive(child, refs);
        }
    }

    // ── Property Popup Triggers (called by blocks on double-click) ─

    void showSectionProperties(LayoutBlockPanel block) {
        if (editorCallback != null)
            editorCallback.editSectionProperties(block);
    }

    void showEmbedLayoutProperties(EmbedLayoutBlock block) {
        if (editorCallback != null)
            editorCallback.editSectionProperties(block);
    }

    void openEmbeddedLayoutDesigner(String className) {
        if (editorCallback != null)
            editorCallback.openEmbeddedLayoutDesigner(className);
    }

    void showFieldProperties(FieldBlock block) {
        if (editorCallback != null)
            editorCallback.editFieldProperties(block);
    }

    void showTableProperties(TableBlock block) {
        if (editorCallback != null)
            editorCallback.editTableProperties(block);
    }

    void showTabProperties(TabbedSectionBlock.TabPanel tabPanel) {
        if (editorCallback != null)
            editorCallback.editTabProperties(tabPanel);
    }

    // ── Field Resolution ───────────────────────────────────────────

    DOSchemaField resolveFieldByRef(String ref) {
        if (ref == null || ref.isEmpty())
            return null;
        String[] parts = ref.split("\\.");
        DOSchemaClass current = schemaClass;
        DOSchemaField field = null;
        for (int i = 0; i < parts.length; i++) {
            field = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(current, parts[i], refSchema);
            if (field == null)
                return null;
            if (i < parts.length - 1) {
                String nextType = field.attributes.isCollection && field.attributes.childrenType != null ? field.attributes.childrenType : field.attributes.type;
                current = findClassByType(nextType);
                if (current == null)
                    return null;
            }
        }
        return field;
    }

    private DOSchemaClass findClassByType(String typeName) {
        if (typeName == null || refSchema == null)
            return null;
        DOSchemaClass cls = refSchema.findClassByName(typeName);
        if (cls != null)
            return cls;
        String shortName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        for (DOSchemaClass c : refSchema.getClasses()) {
            if (c.attributes.source != null && c.attributes.source.endsWith("." + shortName))
                return c;
        }
        return null;
    }

    // ── Empty State ────────────────────────────────────────────────

    private JLabel emptyHintLabel;

    private void showEmptyHint() {
        if (emptyHintLabel == null) {
            emptyHintLabel = new JLabel("Drag fields from the palette or click Auto Layout");
            emptyHintLabel.setForeground(new Color(148, 163, 184));
            emptyHintLabel.setFont(emptyHintLabel.getFont().deriveFont(Font.ITALIC, 13f));
            emptyHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyHintLabel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));
        }
        contentPanel.add(emptyHintLabel);
    }

    private void removeEmptyHint() {
        if (emptyHintLabel != null) {
            contentPanel.remove(emptyHintLabel);
        }
    }

    // ── DnD Visual Feedback ──────────────────────────────────────────

    private static final Color DROP_LINE_COLOR = new Color(59, 130, 246);
    private static final Color DROP_HIGHLIGHT_COLOR = new Color(59, 130, 246, 30);
    private static final BasicStroke DROP_LINE_STROKE = new BasicStroke(2);
    private static final BasicStroke DROP_BORDER_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 6, new float[] { 6, 4 }, 0);

    /** Paint the insertion line and container highlight on the currently active drop target. */
    private void paintDropFeedback(Graphics2D g2, JPanel container) {
        if (container != activeDropContainer || activeDropIndex < 0)
            return;

        // Convert container bounds to LayoutCanvas coordinate space
        Rectangle bounds = SwingUtilities.convertRectangle(container, new Rectangle(0, 0, container.getWidth(), container.getHeight()), this);

        // 1. Container highlight background
        g2.setColor(DROP_HIGHLIGHT_COLOR);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        // 2. Dashed border around the active container
        g2.setColor(DROP_LINE_COLOR);
        g2.setStroke(DROP_BORDER_STROKE);
        g2.drawRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 6, 6);

        // 3. Insertion line inside the container
        List<LayoutBlockPanel> blocks = getBlocksIn(container);
        int localY;
        if (blocks.isEmpty()) {
            localY = 4;
        } else if (activeDropIndex < blocks.size()) {
            localY = blocks.get(activeDropIndex).getY() - 2;
        } else {
            LayoutBlockPanel last = blocks.get(blocks.size() - 1);
            localY = last.getY() + last.getHeight() + 2;
        }
        // Convert local Y to canvas coordinates
        Point lineStart = SwingUtilities.convertPoint(container, 4, localY, this);
        int lineWidth = bounds.width - 8;
        g2.setStroke(DROP_LINE_STROKE);
        g2.setColor(DROP_LINE_COLOR);
        g2.drawLine(lineStart.x, lineStart.y, lineStart.x + lineWidth, lineStart.y);
        g2.fillOval(lineStart.x - 2, lineStart.y - 3, 6, 6);
        g2.fillOval(lineStart.x + lineWidth - 4, lineStart.y - 3, 6, 6);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        // Paint drop feedback overlay on top of everything
        if (activeDropContainer != null && activeDropIndex >= 0) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintDropFeedback(g2, activeDropContainer);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Clear drop feedback from the previous container and set it on the new one. */
    private void setActiveDropTarget(JPanel container, int index) {
        JPanel prev = activeDropContainer;
        activeDropContainer = container;
        activeDropIndex = index;
        if (prev != container && prev != null)
            repaint();
        repaint();
    }

    /** Clear all drop feedback. */
    private void clearActiveDropTarget() {
        activeDropContainer = null;
        activeDropIndex = -1;
        repaint();
    }

    /** Attach a DragGestureRecognizer so blocks can be dragged for reorder/move. */
    private void initBlockDrag(LayoutBlockPanel block) {
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(block, DnDConstants.ACTION_MOVE, evt -> {
            draggedBlock = block;
            try {
                evt.startDrag(DragSource.DefaultMoveDrop, new BlockTransferable(block), new DragSourceAdapter() {
                    @Override
                    public void dragDropEnd(DragSourceDropEvent dsde) {
                        draggedBlock = null;
                    }
                });
            } catch (Exception ignored) {
                draggedBlock = null;
            }
        });
    }

    /** Strongly-typed Transferable for block DnD.
     *  getTransferData returns a serializable placeholder — actual data is passed
     *  via the static {@code draggedBlock} field to avoid macOS pasteboard serialization. */
    private static class BlockTransferable implements Transferable {

        BlockTransferable(LayoutBlockPanel block) {
            /* reference kept in static field */ }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { BLOCK_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor f) {
            return BLOCK_FLAVOR.equals(f);
        }

        @Override
        public Object getTransferData(DataFlavor f) {
            return "block-ref";
        }
    }

    /**
     * Set up a DropTarget on any vertical-stacking container panel so it accepts
     * both palette field drops (FIELD_FLAVOR) and block move/reorder drops (BLOCK_FLAVOR).
     * Called for: canvas contentPanel, SectionBlock.bodyPanel, ColumnPanel, TabPanel.
     */
    public void setupContainerDrop(JPanel container) {
        new DropTarget(container, DnDConstants.ACTION_COPY_OR_MOVE, new ContainerDropHandler(container));
    }

    // ── Drop Handler ───────────────────────────────────────────────

    /**
     * Unified drop handler for ANY layout container. Handles:
     * <ul>
     *   <li>Palette drops (FIELD_FLAVOR → FieldPaletteItem) — creates FIELD or TABLE block</li>
     *   <li>Block drops (BLOCK_FLAVOR → LayoutBlockPanel) — moves block to new position</li>
     * </ul>
     * Reused for: canvas contentPanel, SectionBlock body, ColumnPanel, TabPanel.
     */
    private class ContainerDropHandler extends DropTargetAdapter {
        private final JPanel container;

        ContainerDropHandler(JPanel container) {
            this.container = container;
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
            int newIndex = computeIndex(dtde.getLocation());
            setActiveDropTarget(container, newIndex);
            dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }

        @Override
        public void dragExit(DropTargetEvent dte) {
            if (activeDropContainer == container) {
                clearActiveDropTarget();
            }
        }

        @Override
        public void drop(DropTargetDropEvent dtde) {
            clearActiveDropTarget();
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                Point pt = dtde.getLocation();

                // Block move / reorder — read from static field (bypasses macOS pasteboard serialization)
                if (draggedBlock != null) {
                    LayoutBlockPanel block = draggedBlock;
                    handleBlockDrop(block, pt);
                    dtde.dropComplete(true);
                    return;
                }

                // Palette field drop — read from static field
                if (DetailLayoutDesigner.draggedFieldItem != null) {
                    FieldPaletteItem item = DetailLayoutDesigner.draggedFieldItem;
                    DetailLayoutDesigner.draggedFieldItem = null;
                    handlePaletteDrop(item, pt);
                    dtde.dropComplete(true);
                    return;
                }

                dtde.dropComplete(false);
            } catch (Exception e) {
                e.printStackTrace();
                dtde.dropComplete(false);
            }
        }

        /** Create a new block from a palette item and place it at the drop position. */
        private void handlePaletteDrop(FieldPaletteItem item, Point pt) {
            LayoutNode node = item.isCollection ? new LayoutNode(LayoutNodeType.TABLE) : new LayoutNode(LayoutNodeType.FIELD);
            node.setProp("ref", item.dotPath);
            placeBlock(createBlock(node), computeIndex(pt));
        }

        /** Move an existing block into this container at the drop position. */
        private void handleBlockDrop(LayoutBlockPanel block, Point pt) {
            List<LayoutBlockPanel> blocks = getBlocksIn(container);
            int fromIndex = blocks.indexOf(block);
            int toIndex = computeIndex(pt);
            // Adjust for downward shift when moving within the same container
            if (fromIndex >= 0 && fromIndex < toIndex)
                toIndex--;
            // No-op if already at target position in same container
            if (fromIndex >= 0 && fromIndex == toIndex)
                return;
            removeBlockFromParent(block);
            placeBlock(block, toIndex);
            // Show empty hint on top panel if it became empty
            if (getTopLevelBlocks().isEmpty())
                showEmptyHint();
        }

        /** Insert a block at the given index, or append if past the end.
         *  Triggers revalidateUp to propagate layout changes through the hierarchy. */
        private void placeBlock(LayoutBlockPanel block, int index) {
            if (container == contentPanel)
                removeEmptyHint();
            List<LayoutBlockPanel> current = getBlocksIn(container);
            if (index >= 0 && index < current.size()) {
                insertBlockInContainer(container, block, index);
            } else {
                addBlockToContainer(container, block);
            }
            selectBlock(block);
            revalidateUp(container);
        }

        /** Compute insertion index from drop Y coordinate relative to existing blocks. */
        private int computeIndex(Point pt) {
            List<LayoutBlockPanel> blocks = getBlocksIn(container);
            for (int i = 0; i < blocks.size(); i++) {
                Rectangle bounds = blocks.get(i).getBounds();
                if (pt.y < bounds.y + bounds.height / 2)
                    return i;
            }
            return blocks.size();
        }
    }
}
