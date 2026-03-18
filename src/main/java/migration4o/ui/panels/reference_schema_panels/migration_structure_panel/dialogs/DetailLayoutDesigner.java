package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.*;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.layout.*;
import migration4o.schema.modules.DOModuleService;
import migration4o.util.DatabaseUtil;

/**
 * Designer for record detail view layouts. Tree-based layout builder with field
 * palette, property panel, and live HTML preview.
 */
public class DetailLayoutDesigner extends JFrame {

    private final ClassExportConfig config;
    private final DOSchemaClass schemaClass;
    private final DOSchema refSchema;

    private JTree fieldPalette;
    private DefaultTreeModel fieldPaletteModel;
    private JTree layoutTree;
    private DefaultTreeModel layoutModel;
    private JPanel propertyPanel;
    private JEditorPane previewPane;

    private DefaultMutableTreeNode selectedLayoutNode;

    // DnD flavors
    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(FieldPaletteItem.class, "FieldPaletteItem");
    private static final DataFlavor LAYOUT_NODE_FLAVOR = new DataFlavor(DefaultMutableTreeNode.class, "LayoutTreeNode");

    public DetailLayoutDesigner(ClassExportConfig config, DOSchemaClass schemaClass, DOSchema refSchema) {
        super("Detail Layout Designer — " + schemaClass.attributes.destinationName);
        this.config = config;
        this.schemaClass = schemaClass;
        this.refSchema = refSchema;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        buildUI();
        loadExistingLayout();
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFieldPalette(), buildLayoutTreePanel());
        leftSplit.setDividerLocation(250);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, buildPropertyPanel());
        rightSplit.setDividerLocation(650);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightSplit, buildPreviewPanel());
        mainSplit.setDividerLocation(450);

        add(mainSplit, BorderLayout.CENTER);
    }

    // ── Toolbar ────────────────────────────────────────────────────

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        bar.add(makeBtn("+ Section", e -> addNode(LayoutNodeType.SECTION, "title", "Section")));
        bar.add(makeBtn("+ Columns", e -> addColumnsNode()));
        bar.add(makeBtn("+ Divider", e -> addNode(LayoutNodeType.DIVIDER)));
        bar.add(makeBtn("+ Table", e -> addTableNode()));
        bar.add(makeBtn("+ Tabs", e -> addTabbedSectionNode()));
        bar.add(makeBtn("+ Tab", e -> addTabNode()));
        bar.addSeparator();
        bar.add(makeBtn("Auto Layout", e -> autoLayout()));
        bar.addSeparator();
        bar.add(makeBtn("\u2717 Delete", e -> deleteNode()));
        bar.addSeparator();
        JButton saveBtn = makeBtn("Save", e -> save());
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
        bar.add(saveBtn);

        return bar;
    }

    private JButton makeBtn(String text, ActionListener action) {
        JButton btn = new JButton(text);
        btn.addActionListener(action);
        btn.setFocusPainted(false);
        return btn;
    }

    // ── Field Palette ──────────────────────────────────────────────

    private JScrollPane buildFieldPalette() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Fields");
        fieldPaletteModel = new DefaultTreeModel(root);
        populateFieldPalette(root);

        fieldPalette = new JTree(fieldPaletteModel);
        fieldPalette.setRootVisible(false);
        fieldPalette.setShowsRootHandles(true);

        // Double-click to add field to layout
        fieldPalette.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2)
                    addFieldFromPalette();
            }
        });

        // DnD from palette
        fieldPalette.setDragEnabled(true);
        fieldPalette.setTransferHandler(new FieldPaletteDragHandler());

        expandAllNodes(fieldPalette, 0, fieldPalette.getRowCount());

        JScrollPane sp = new JScrollPane(fieldPalette);
        sp.setBorder(BorderFactory.createTitledBorder("Available Fields"));
        sp.setPreferredSize(new Dimension(250, 400));
        return sp;
    }

    private void populateFieldPalette(DefaultMutableTreeNode root) {
        Set<String> usedRefs = collectUsedFieldRefs();
        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);

        DefaultMutableTreeNode directNode = new DefaultMutableTreeNode("Direct Fields");
        DefaultMutableTreeNode embeddedNode = new DefaultMutableTreeNode("Embedded Entities");
        DefaultMutableTreeNode collectionsNode = new DefaultMutableTreeNode("Collections");
        DefaultMutableTreeNode refsNode = new DefaultMutableTreeNode("References (IDEntite)");

        for (DOSchemaField field : allFields) {
            if (!field.attributes.isExported)
                continue;
            String dest = field.attributes.destinationName;
            if (dest == null || dest.isEmpty())
                continue;

            if (field.attributes.isCollection) {
                if (!usedRefs.contains(dest)) {
                    DefaultMutableTreeNode collNode = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), dest, true));
                    if (field.attributes.embedContents)
                        addEmbeddedSubFields(collNode, field.attributes.childrenType, dest, usedRefs);
                    collectionsNode.add(collNode);
                }
            } else if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type)) {
                // Embedded entity — show if it or any sub-field is not yet used
                DefaultMutableTreeNode embNode = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), dest, false));
                addEmbeddedSubFields(embNode, field.attributes.type, dest, usedRefs);
                if (!usedRefs.contains(dest) || embNode.getChildCount() > 0)
                    embeddedNode.add(embNode);
            } else if (isIDEntiteType(field)) {
                if (!usedRefs.contains(dest))
                    refsNode.add(new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), dest, false)));
            } else {
                if (!usedRefs.contains(dest))
                    directNode.add(new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), dest, false)));
            }
        }

        if (directNode.getChildCount() > 0)
            root.add(directNode);
        if (embeddedNode.getChildCount() > 0)
            root.add(embeddedNode);
        if (collectionsNode.getChildCount() > 0)
            root.add(collectionsNode);
        if (refsNode.getChildCount() > 0)
            root.add(refsNode);
    }

    private Set<String> collectUsedFieldRefs() {
        Set<String> refs = new HashSet<>();
        DefaultMutableTreeNode root = (layoutModel != null) ? (DefaultMutableTreeNode) layoutModel.getRoot() : null;
        if (root != null)
            collectUsedFieldRefsRecursive(root, refs);
        return refs;
    }

    private void collectUsedFieldRefsRecursive(DefaultMutableTreeNode treeNode, Set<String> refs) {
        LayoutNode node = getLayoutNodeFromTreeNode(treeNode);
        if (node != null) {
            if (node.type == LayoutNodeType.FIELD || node.type == LayoutNodeType.TABLE) {
                String ref = node.prop("ref");
                if (ref != null)
                    refs.add(ref);
            }
        }
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            collectUsedFieldRefsRecursive((DefaultMutableTreeNode) treeNode.getChildAt(i), refs);
        }
    }

    private void refreshFieldPalette() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) fieldPaletteModel.getRoot();
        root.removeAllChildren();
        populateFieldPalette(root);
        fieldPaletteModel.reload();
        expandAllNodes(fieldPalette, 0, fieldPalette.getRowCount());
    }

    private void addEmbeddedSubFields(DefaultMutableTreeNode parentNode, String typeName, String parentPath, Set<String> usedRefs) {
        if (typeName == null)
            return;
        DOSchemaClass embeddedClass = findClassByType(typeName);
        if (embeddedClass == null)
            return;

        List<DOSchemaField> subFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, refSchema);
        for (DOSchemaField sf : subFields) {
            if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                continue;
            String dotPath = parentPath + "." + sf.attributes.destinationName;
            if (usedRefs.contains(dotPath))
                continue;
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(sf), dotPath, sf.attributes.isCollection));
            if (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type) && !sf.attributes.isCollection) {
                addEmbeddedSubFields(child, sf.attributes.type, dotPath, usedRefs);
            }
            parentNode.add(child);
        }
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

    private boolean isPrimitiveType(String type) {
        if (type == null)
            return true;
        return type.equals("string") || type.equals("int") || type.equals("long") || type.equals("float") || type.equals("double") || type.equals("boolean") || type.equals("date") || type.equals("byte") || type.equals("short") || type.equals("char") || type.startsWith("java.lang.");
    }

    private boolean isIDEntiteType(DOSchemaField field) {
        if (field.attributes.type == null)
            return false;
        DOSchemaClass typeClass = findClassByType(field.attributes.type);
        if (typeClass == null)
            return false;
        return typeClass.isIDEntite(refSchema);
    }

    // ── Layout Tree ────────────────────────────────────────────────

    private JScrollPane buildLayoutTreePanel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Layout");
        layoutModel = new DefaultTreeModel(root);
        layoutTree = new JTree(layoutModel);
        layoutTree.setRootVisible(false);
        layoutTree.setShowsRootHandles(true);
        layoutTree.setCellRenderer(new LayoutTreeCellRenderer());
        layoutTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        layoutTree.addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            selectedLayoutNode = (path != null) ? (DefaultMutableTreeNode) path.getLastPathComponent() : null;
            updatePropertyPanel();
        });

        // Enable DnD — both palette drops and internal rearrangement
        layoutTree.setDragEnabled(true);
        layoutTree.setDropMode(DropMode.ON_OR_INSERT);
        layoutTree.setTransferHandler(new LayoutTreeDnDHandler());

        JScrollPane sp = new JScrollPane(layoutTree);
        sp.setBorder(BorderFactory.createTitledBorder("Layout Structure"));
        return sp;
    }

    // ── Property Panel ─────────────────────────────────────────────

    private JScrollPane buildPropertyPanel() {
        propertyPanel = new JPanel();
        propertyPanel.setLayout(new BoxLayout(propertyPanel, BoxLayout.Y_AXIS));
        showEmptyProperties();

        JScrollPane sp = new JScrollPane(propertyPanel);
        sp.setBorder(BorderFactory.createTitledBorder("Properties"));
        sp.setPreferredSize(new Dimension(300, 400));
        return sp;
    }

    private void showEmptyProperties() {
        propertyPanel.removeAll();
        JLabel lbl = new JLabel("Select a layout node to edit its properties");
        lbl.setForeground(Color.GRAY);
        lbl.setFont(lbl.getFont().deriveFont(Font.ITALIC));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10));
        propertyPanel.add(lbl);
        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    private void updatePropertyPanel() {
        propertyPanel.removeAll();
        if (selectedLayoutNode == null || !(selectedLayoutNode.getUserObject() instanceof LayoutNode)) {
            showEmptyProperties();
            return;
        }

        LayoutNode node = (LayoutNode) selectedLayoutNode.getUserObject();
        propertyPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        switch (node.type) {
        case SECTION:
            addPropField("Title", node, "title");
            addPropCheckbox("Collapsible", node, "collapsible");
            break;
        case COLUMNS:
            addPropField("Count", node, "count");
            addPropField("Sizes (%)", node, "sizes");
            break;
        case FIELD:
            addPropReadOnly("Ref", node.prop("ref", ""));
            addPropField("Label Override", node, "label");
            addFormatEditor(node);
            break;
        case TABLE:
            addTableEditor(node);
            break;
        case TABBED_SECTION:
            addPropField("Title", node, "title");
            break;
        case TAB:
            addPropField("Title", node, "title");
            break;
        case COLUMN:
        case DIVIDER:
            addPropReadOnly("Type", node.type.name());
            break;
        }

        // Style editing for all node types
        addStyleEditor(node);

        propertyPanel.add(Box.createVerticalGlue());
        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    private void addPropField(String label, LayoutNode node, String propKey) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setPreferredSize(new Dimension(100, 25));
        JTextField tf = new JTextField(node.prop(propKey, ""), 15);
        Runnable updater = () -> {
            node.setProp(propKey, tf.getText().trim());
            layoutModel.nodeChanged(selectedLayoutNode);
            updatePreview();
        };
        tf.addActionListener(e -> updater.run());
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                updater.run();
            }
        });
        row.add(lbl, BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        propertyPanel.add(row);
        propertyPanel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void addPropCheckbox(String label, LayoutNode node, String propKey) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setPreferredSize(new Dimension(100, 25));
        JCheckBox cb = new JCheckBox("", node.boolProp(propKey));
        cb.addActionListener(e -> {
            node.setProp(propKey, cb.isSelected() ? "true" : "");
            layoutModel.nodeChanged(selectedLayoutNode);
            updatePreview();
        });
        row.add(lbl, BorderLayout.WEST);
        row.add(cb, BorderLayout.CENTER);
        propertyPanel.add(row);
        propertyPanel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void addPropReadOnly(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setPreferredSize(new Dimension(100, 25));
        JTextField tf = new JTextField(value, 15);
        tf.setEditable(false);
        tf.setBackground(new Color(240, 240, 240));
        row.add(lbl, BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        propertyPanel.add(row);
        propertyPanel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    // ── Format Editor ──────────────────────────────────────────────

    private void addFormatEditor(LayoutNode node) {
        String ref = node.prop("ref", "");
        DOSchemaField field = resolveFieldByRef(ref);
        String fieldType = (field != null && field.attributes.type != null) ? field.attributes.type : "string";

        String currentFormat = node.prop("format", "");

        JPanel formatPanel = new JPanel();
        formatPanel.setLayout(new BoxLayout(formatPanel, BoxLayout.Y_AXIS));
        formatPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formatPanel.setBorder(BorderFactory.createTitledBorder("Format"));
        formatPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        if (fieldType.equals("date") || fieldType.contains("Date")) {
            addDateFormatEditor(formatPanel, node, currentFormat, "date");
        } else if (fieldType.equals("boolean")) {
            addBoolFormatEditor(formatPanel, node, currentFormat);
        } else if (fieldType.equals("long") || fieldType.equals("java.lang.Long")) {
            JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            boolean isLongDate = currentFormat.startsWith("longdate:");
            JRadioButton numRadio = new JRadioButton("Number", !isLongDate);
            JRadioButton dateRadio = new JRadioButton("As Date", isLongDate);
            ButtonGroup bg = new ButtonGroup();
            bg.add(numRadio);
            bg.add(dateRadio);
            radioPanel.add(numRadio);
            radioPanel.add(dateRadio);
            radioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            formatPanel.add(radioPanel);

            JPanel patternRow = new JPanel(new BorderLayout(5, 0));
            patternRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            String initPattern = isLongDate ? currentFormat.substring(9) : (currentFormat.startsWith("num:") ? currentFormat.substring(4) : "");
            JTextField patField = new JTextField(initPattern, 15);
            patternRow.add(new JLabel("Pattern:"), BorderLayout.WEST);
            patternRow.add(patField, BorderLayout.CENTER);
            formatPanel.add(patternRow);

            Runnable updater = () -> {
                String pat = patField.getText().trim();
                if (dateRadio.isSelected() && !pat.isEmpty())
                    node.setProp("format", "longdate:" + pat);
                else if (numRadio.isSelected() && !pat.isEmpty())
                    node.setProp("format", "num:" + pat);
                else
                    node.setProp("format", "");
                updatePreview();
            };
            patField.addActionListener(e -> updater.run());
            patField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    updater.run();
                }
            });
            numRadio.addActionListener(e -> updater.run());
            dateRadio.addActionListener(e -> updater.run());
        } else if (fieldType.equals("int") || fieldType.equals("float") || fieldType.equals("double") || fieldType.equals("short") || fieldType.equals("byte") || fieldType.equals("java.lang.Integer") || fieldType.equals("java.lang.Float") || fieldType.equals("java.lang.Double")) {
            addNumFormatEditor(formatPanel, node, currentFormat);
        } else {
            JLabel noFmt = new JLabel("No format options for type: " + fieldType);
            noFmt.setForeground(Color.GRAY);
            noFmt.setAlignmentX(Component.LEFT_ALIGNMENT);
            formatPanel.add(noFmt);
        }

        propertyPanel.add(formatPanel);
        propertyPanel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void addDateFormatEditor(JPanel panel, LayoutNode node, String currentFormat, String prefix) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        String initVal = currentFormat.startsWith(prefix + ":") ? currentFormat.substring(prefix.length() + 1) : "";
        JComboBox<String> combo = new JComboBox<>(new String[] { "", "yyyy-MM-dd", "dd/MM/yyyy", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss", "MMMM dd, yyyy" });
        combo.setEditable(true);
        combo.setSelectedItem(initVal);
        combo.addActionListener(e -> {
            String pat = (String) combo.getSelectedItem();
            node.setProp("format", (pat != null && !pat.isEmpty()) ? prefix + ":" + pat : "");
            updatePreview();
        });
        row.add(new JLabel("Pattern:"), BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        panel.add(row);
    }

    private void addBoolFormatEditor(JPanel panel, LayoutNode node, String currentFormat) {
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
            updatePreview();
        };
        for (JTextField tf : new JTextField[] { trueField, falseField }) {
            tf.addActionListener(e -> updater.run());
            tf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    updater.run();
                }
            });
        }

        JPanel trueRow = new JPanel(new BorderLayout(5, 0));
        trueRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        trueRow.add(new JLabel("True label:"), BorderLayout.WEST);
        trueRow.add(trueField, BorderLayout.CENTER);
        JPanel falseRow = new JPanel(new BorderLayout(5, 0));
        falseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        falseRow.add(new JLabel("False label:"), BorderLayout.WEST);
        falseRow.add(falseField, BorderLayout.CENTER);
        panel.add(trueRow);
        panel.add(falseRow);
    }

    private void addNumFormatEditor(JPanel panel, LayoutNode node, String currentFormat) {
        String initVal = currentFormat.startsWith("num:") ? currentFormat.substring(4) : "";
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField tf = new JTextField(initVal, 15);
        JLabel hint = new JLabel("<html><i>e.g. #,##0.0 Km</i></html>");
        hint.setForeground(Color.GRAY);

        Runnable updater = () -> {
            String pat = tf.getText().trim();
            node.setProp("format", !pat.isEmpty() ? "num:" + pat : "");
            updatePreview();
        };
        tf.addActionListener(e -> updater.run());
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                updater.run();
            }
        });

        row.add(new JLabel("Pattern:"), BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        panel.add(row);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);
    }

    // ── Style Editor ───────────────────────────────────────────────

    private void addStyleEditor(LayoutNode node) {
        JPanel stylePanel = new JPanel();
        stylePanel.setLayout(new BoxLayout(stylePanel, BoxLayout.Y_AXIS));
        stylePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stylePanel.setBorder(BorderFactory.createTitledBorder("Style"));
        stylePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        // Style combo
        JPanel styleRow = new JPanel(new BorderLayout(5, 0));
        styleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel styleLbl = new JLabel("Style:");
        styleLbl.setPreferredSize(new Dimension(80, 25));
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
        styleCombo.addActionListener(e -> {
            int idx = styleCombo.getSelectedIndex();
            node.setProp("style", idx >= 0 ? styleValues[idx] : "");
            layoutModel.nodeChanged(selectedLayoutNode);
            updatePreview();
        });
        styleRow.add(styleLbl, BorderLayout.WEST);
        styleRow.add(styleCombo, BorderLayout.CENTER);
        stylePanel.add(styleRow);
        stylePanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Text color
        addColorButton(stylePanel, "Text Color:", node, "color");
        stylePanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Highlight color
        addColorButton(stylePanel, "Highlight:", node, "hilite");

        propertyPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        propertyPanel.add(stylePanel);
    }

    private void addColorButton(JPanel parent, String label, LayoutNode node, String propKey) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(80, 25));

        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        String currentColor = node.prop(propKey, "");

        // Color swatch
        JPanel swatch = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(22, 22);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                String c = node.prop(propKey, "");
                if (!c.isEmpty()) {
                    try {
                        g.setColor(Color.decode(c));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(Color.GRAY);
                        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    } catch (NumberFormatException ex) {
                        /* ignore bad color */ }
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g.drawLine(0, 0, getWidth() - 1, getHeight() - 1);
                }
            }
        };

        JButton chooseBtn = new JButton("Choose...");
        chooseBtn.setMargin(new Insets(1, 6, 1, 6));
        chooseBtn.addActionListener(e -> {
            Color initial = null;
            try {
                if (!currentColor.isEmpty())
                    initial = Color.decode(currentColor);
            } catch (NumberFormatException ex) {
                /* ignore */ }
            Color chosen = JColorChooser.showDialog(this, "Choose " + label.replace(":", ""), initial);
            if (chosen != null) {
                String hex = String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(), chosen.getBlue());
                node.setProp(propKey, hex);
                swatch.repaint();
                layoutModel.nodeChanged(selectedLayoutNode);
                updatePreview();
            }
        });

        JButton clearBtn = new JButton("\u2717");
        clearBtn.setMargin(new Insets(1, 4, 1, 4));
        clearBtn.setToolTipText("Clear " + label.replace(":", ""));
        clearBtn.addActionListener(e -> {
            node.setProp(propKey, "");
            swatch.repaint();
            layoutModel.nodeChanged(selectedLayoutNode);
            updatePreview();
        });

        colorPanel.add(swatch);
        colorPanel.add(chooseBtn);
        colorPanel.add(clearBtn);

        row.add(lbl, BorderLayout.WEST);
        row.add(colorPanel, BorderLayout.CENTER);
        parent.add(row);
    }

    // ── Table Editor ───────────────────────────────────────────────

    private void addTableEditor(LayoutNode node) {
        // Collection field selector
        JPanel refRow = new JPanel(new BorderLayout(5, 0));
        refRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        refRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel refLbl = new JLabel("Collection:");
        refLbl.setFont(refLbl.getFont().deriveFont(Font.BOLD));
        refLbl.setPreferredSize(new Dimension(100, 25));

        // Build combo of collection fields
        List<String> collectionRefs = new ArrayList<>();
        collectionRefs.add(""); // allow empty
        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);
        for (DOSchemaField f : allFields) {
            if (f.attributes.isExported && f.attributes.isCollection && f.attributes.destinationName != null)
                collectionRefs.add(f.attributes.destinationName);
        }
        // Also check embedded entities for nested collections
        for (DOSchemaField f : allFields) {
            if (f.attributes.isExported && f.attributes.embedContents && !isPrimitiveType(f.attributes.type) && !f.attributes.isCollection) {
                DOSchemaClass embClass = findClassByType(f.attributes.type);
                if (embClass != null) {
                    for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embClass, refSchema)) {
                        if (sf.attributes.isExported && sf.attributes.isCollection && sf.attributes.destinationName != null)
                            collectionRefs.add(f.attributes.destinationName + "." + sf.attributes.destinationName);
                    }
                }
            }
        }

        JComboBox<String> refCombo = new JComboBox<>(collectionRefs.toArray(new String[0]));
        refCombo.setSelectedItem(node.prop("ref", ""));
        refRow.add(refLbl, BorderLayout.WEST);
        refRow.add(refCombo, BorderLayout.CENTER);
        propertyPanel.add(refRow);
        propertyPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Column configuration panel
        JPanel colsPanel = new JPanel();
        colsPanel.setLayout(new BoxLayout(colsPanel, BoxLayout.Y_AXIS));
        colsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        colsPanel.setBorder(BorderFactory.createTitledBorder("Columns"));

        Runnable rebuildColumns = () -> buildTableColumnsUI(colsPanel, node);
        rebuildColumns.run();

        refCombo.addActionListener(e -> {
            String newRef = (String) refCombo.getSelectedItem();
            node.setProp("ref", newRef != null ? newRef : "");
            layoutModel.nodeChanged(selectedLayoutNode);
            rebuildColumns.run();
            updatePreview();
        });

        propertyPanel.add(colsPanel);
    }

    private void buildTableColumnsUI(JPanel container, LayoutNode node) {
        container.removeAll();

        String ref = node.prop("ref", "");
        if (ref.isEmpty()) {
            container.add(new JLabel("Select a collection field first"));
            container.revalidate();
            container.repaint();
            return;
        }

        // Resolve the child type's fields
        DOSchemaField collField = resolveFieldByRef(ref);
        if (collField == null || collField.attributes.childrenType == null) {
            container.add(new JLabel("Cannot resolve children type"));
            container.revalidate();
            container.repaint();
            return;
        }

        DOSchemaClass childClass = findClassByType(collField.attributes.childrenType);
        if (childClass == null) {
            container.add(new JLabel("Unknown child class: " + collField.attributes.childrenType));
            container.revalidate();
            container.repaint();
            return;
        }

        List<DOSchemaField> childFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema);
        List<DOSchemaField> availableFields = new ArrayList<>();
        for (DOSchemaField sf : childFields) {
            if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                continue;
            if (sf.attributes.isCollection || (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type)))
                continue;
            availableFields.add(sf);
        }

        // Parse current columns, titles, widths
        String[] currentCols = node.prop("columns", "").isEmpty() ? new String[0] : node.prop("columns").split(",");
        String[] currentTitles = node.prop("columnTitles", "").isEmpty() ? new String[0] : node.prop("columnTitles").split(",", -1);
        String[] currentWidths = node.prop("widths", "").isEmpty() ? new String[0] : node.prop("widths").split(",", -1);

        Set<String> enabledCols = new LinkedHashSet<>(Arrays.asList(currentCols));
        for (String c : currentCols)
            enabledCols.add(c.trim());

        // Build ordered list: enabled columns first (in their order), then
        // remaining fields
        List<String> orderedNames = new ArrayList<>();
        for (String c : currentCols) {
            String name = c.trim();
            if (!name.isEmpty())
                orderedNames.add(name);
        }
        for (DOSchemaField sf : availableFields) {
            if (!orderedNames.contains(sf.attributes.destinationName))
                orderedNames.add(sf.attributes.destinationName);
        }

        Map<String, String> labelsByFieldName = new HashMap<>();
        for (DOSchemaField sf : availableFields) {
            labelsByFieldName.put(sf.attributes.destinationName, getFieldLabel(sf));
        }

        // Column rows — each is a panel with: [grip] [checkbox] [name] [title
        // field] [width field]
        List<JPanel> rowPanels = new ArrayList<>();
        List<JCheckBox> checkboxes = new ArrayList<>();
        List<JTextField> titleFields = new ArrayList<>();
        List<JTextField> widthFields = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>(orderedNames);

        for (int i = 0; i < orderedNames.size(); i++) {
            String fname = orderedNames.get(i);
            boolean enabled = enabledCols.contains(fname);
            int colIdx = Arrays.asList(currentCols).indexOf(fname);

            JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
            rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rowPanel.setOpaque(true);

            // Grip handle for DnD
            JLabel grip = new JLabel("\u2807");
            grip.setForeground(Color.GRAY);
            grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            grip.setToolTipText("Drag to reorder");

            JCheckBox cb = new JCheckBox("", enabled);
            JLabel nameLbl = new JLabel(labelsByFieldName.getOrDefault(fname, humanize(fname)));
            nameLbl.setPreferredSize(new Dimension(110, 20));
            nameLbl.setToolTipText(fname);

            String titleVal = (colIdx >= 0 && colIdx < currentTitles.length) ? currentTitles[colIdx].trim() : labelsByFieldName.getOrDefault(fname, "");
            JTextField titleTf = new JTextField(titleVal, 8);
            titleTf.setToolTipText("Column title override");

            String widthVal = (colIdx >= 0 && colIdx < currentWidths.length) ? currentWidths[colIdx].trim() : "";
            JTextField widthTf = new JTextField(widthVal, 3);
            widthTf.setToolTipText("Column width %");

            rowPanel.add(grip);
            rowPanel.add(cb);
            rowPanel.add(nameLbl);
            rowPanel.add(new JLabel("Title:"));
            rowPanel.add(titleTf);
            rowPanel.add(new JLabel("W:"));
            rowPanel.add(widthTf);

            rowPanels.add(rowPanel);
            checkboxes.add(cb);
            titleFields.add(titleTf);
            widthFields.add(widthTf);

            container.add(rowPanel);

            // Drag reorder via mouse listeners on the grip.
            // dragState[0] = startIdx (-1 when not dragging)
            // dragState[1] = last highlighted row index (-1 when none)
            // These are shared across all grips via the outer-scope arrays.
            final int rowIdx = i;

            // Helper: update row highlight during drag
            Runnable clearHighlights = () -> {
                for (JPanel rp : rowPanels) {
                    rp.setBackground(UIManager.getColor("Panel.background"));
                    rp.setBorder(null);
                }
            };

            grip.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    grip.putClientProperty("dragStart", rowIdx);
                    // Dim the row being dragged
                    rowPanel.setBackground(new Color(200, 210, 230));
                    rowPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 140, 200), 1));
                    container.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    clearHighlights.run();
                    Object startObj = grip.getClientProperty("dragStart");
                    if (startObj == null)
                        return;
                    int startIdx = (int) startObj;
                    // Find which row the mouse is over
                    Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), container);
                    int targetIdx = -1;
                    for (int r = 0; r < rowPanels.size(); r++) {
                        Rectangle bounds = rowPanels.get(r).getBounds();
                        if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                            targetIdx = r;
                            break;
                        }
                    }
                    grip.putClientProperty("dragStart", null);
                    if (targetIdx < 0 || targetIdx == startIdx)
                        return;
                    // Move startIdx to targetIdx with proper insert-and-shift.
                    // After removing the source item, indices above startIdx
                    // shift down
                    // by 1, so we must compensate when the target was after the
                    // source.
                    String movedName = fieldNames.remove(startIdx);
                    JPanel movedPanel = rowPanels.remove(startIdx);
                    JCheckBox movedCb = checkboxes.remove(startIdx);
                    JTextField movedTitle = titleFields.remove(startIdx);
                    JTextField movedWidth = widthFields.remove(startIdx);
                    int insertAt = targetIdx > startIdx ? targetIdx - 1 : targetIdx;
                    fieldNames.add(insertAt, movedName);
                    rowPanels.add(insertAt, movedPanel);
                    checkboxes.add(insertAt, movedCb);
                    titleFields.add(insertAt, movedTitle);
                    widthFields.add(insertAt, movedWidth);
                    // Rebuild UI
                    container.removeAll();
                    for (JPanel rp : rowPanels)
                        container.add(rp);
                    container.revalidate();
                    container.repaint();
                    serializeTableColumns(node, fieldNames, checkboxes, titleFields, widthFields);
                }
            });

            grip.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    Object startObj = grip.getClientProperty("dragStart");
                    if (startObj == null)
                        return;
                    // Find which row the cursor is currently over and highlight
                    // it
                    Point pt = SwingUtilities.convertPoint(grip, e.getPoint(), container);
                    clearHighlights.run();
                    // Keep dragged row highlighted
                    rowPanel.setBackground(new Color(200, 210, 230));
                    rowPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 140, 200), 1));
                    // Show insertion line at the drop target position
                    for (int r = 0; r < rowPanels.size(); r++) {
                        JPanel rp = rowPanels.get(r);
                        if (rp == rowPanel)
                            continue;
                        Rectangle bounds = rp.getBounds();
                        if (pt.y >= bounds.y && pt.y < bounds.y + bounds.height) {
                            boolean above = (pt.y - bounds.y) < bounds.height / 2;
                            if (above) {
                                rp.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(60, 120, 220)));
                            } else {
                                rp.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(60, 120, 220)));
                            }
                            break;
                        }
                    }
                    container.repaint();
                }
            });

            // Update on checkbox/field changes
            Runnable serialize = () -> serializeTableColumns(node, fieldNames, checkboxes, titleFields, widthFields);
            cb.addActionListener(e -> serialize.run());
            titleTf.addActionListener(e -> serialize.run());
            titleTf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    serialize.run();
                }
            });
            widthTf.addActionListener(e -> serialize.run());
            widthTf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    serialize.run();
                }
            });
        }

        container.revalidate();
        container.repaint();
    }

    private void serializeTableColumns(LayoutNode node, List<String> fieldNames, List<JCheckBox> checkboxes, List<JTextField> titleFields, List<JTextField> widthFields) {
        StringBuilder cols = new StringBuilder();
        StringBuilder titles = new StringBuilder();
        StringBuilder widths = new StringBuilder();
        boolean firstCol = true;

        for (int i = 0; i < fieldNames.size(); i++) {
            if (!checkboxes.get(i).isSelected())
                continue;
            if (!firstCol) {
                cols.append(',');
                titles.append(',');
                widths.append(',');
            }
            firstCol = false;
            cols.append(fieldNames.get(i));
            titles.append(titleFields.get(i).getText().trim());
            widths.append(widthFields.get(i).getText().trim());
        }

        node.setProp("columns", cols.toString());
        node.setProp("columnTitles", titles.toString());
        node.setProp("widths", widths.toString());
        layoutModel.nodeChanged(selectedLayoutNode);
        updatePreview();
    }

    private DOSchemaField resolveFieldByRef(String ref) {
        if (ref == null || ref.isEmpty())
            return null;
        String[] parts = ref.split("\\.");
        DOSchemaClass current = schemaClass;
        DOSchemaField field = null;
        for (int i = 0; i < parts.length; i++) {
            field = DatabaseUtil.findSchemaFieldByDestinationNameIncludingAncestors(current, parts[i], refSchema);
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

    // ── Preview ────────────────────────────────────────────────────

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Live Preview"));

        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        panel.add(new JScrollPane(previewPane), BorderLayout.CENTER);

        updatePreview();
        return panel;
    }

    private void updatePreview() {
        if (previewPane == null)
            return;
        DetailLayout layout = buildLayoutFromTree();
        if (layout == null || layout.isEmpty()) {
            previewPane.setText("<html><body style='font-family:sans-serif;color:gray;padding:20px'><i>Add layout elements to see a preview</i></body></html>");
            return;
        }
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-size:13px;margin:0;padding:12px;color:#1e293b;}");
        html.append(".section{margin:6px 0;border:1px solid #e2e8f0;border-radius:8px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}");
        html.append(".section-title{font-weight:bold;padding:8px 14px;background:linear-gradient(to bottom,#f8fafc,#f1f5f9);border-bottom:1px solid #e2e8f0;border-radius:8px 8px 0 0;font-size:13px;}");
        html.append(".section-body{padding:0;}");
        html.append(".field-row{display:flex;padding:5px 14px;border-bottom:1px solid #f1f5f9;align-items:baseline;}");
        html.append(".field-row:nth-child(even){background:#fafbfc;}");
        html.append(".field-label{font-weight:600;color:#64748b;font-size:12px;min-width:140px;flex:0 0 140px;}");
        html.append(".field-value{color:#1e293b;flex:1;}");
        html.append("hr.layout-divider{border:none;border-top:2px solid #cbd5e1;margin:14px 8px;}");
        html.append(".columns{display:flex;gap:0;} .column{flex:1;padding:0 6px;border-right:1px solid #f1f5f9;} .column:last-child{border-right:none;}");
        html.append("table{border-collapse:collapse;width:100%;font-size:12px;margin:4px 0;} th{text-align:left;background:linear-gradient(to bottom,#f8fafc,#eef2f7);padding:6px 10px;border-bottom:2px solid #d1d9e6;font-weight:700;font-size:11px;text-transform:uppercase;letter-spacing:0.03em;color:#475569;} td{padding:5px 10px;border-bottom:1px solid #f1f5f9;}");
        html.append(".tab-bar{display:flex;border-bottom:2px solid #e2e8f0;padding:0 8px;background:#fafbfc;}");
        html.append(".tab-bar span{padding:7px 16px;font-size:12px;cursor:pointer;} .tab-bar span.active{color:#3b82f6;font-weight:bold;border-bottom:2px solid #3b82f6;margin-bottom:-2px;}");
        html.append(".tab-bar span.inactive{color:#94a3b8;}");
        // Style classes
        html.append(".style-h1 .field-value{font-size:22px;font-weight:700;} .style-h1 .field-label{font-size:14px;}");
        html.append(".style-h2 .field-value{font-size:18px;font-weight:600;} .style-h2 .field-label{font-size:13px;}");
        html.append(".style-h3 .field-value{font-size:15px;font-weight:600;}");
        html.append(".style-h4 .field-value{font-size:13px;font-weight:600;}");
        html.append(".style-small .field-value{font-size:11px;} .style-small .field-label{font-size:10px;}");
        html.append(".style-caption .field-value{font-size:11px;font-style:italic;color:#64748b;} .style-caption .field-label{font-size:10px;font-style:italic;}");
        html.append("</style></head><body>");
        renderPreviewNodes(html, layout.nodes);
        html.append("</body></html>");
        previewPane.setText(html.toString());
        previewPane.setCaretPosition(0);
    }

    private void renderPreviewNodes(StringBuilder html, List<LayoutNode> nodes) {
        for (LayoutNode n : nodes)
            renderPreviewNode(html, n);
    }

    private void renderPreviewNode(StringBuilder html, LayoutNode node) {
        String style = node.prop("style", "");
        String color = node.prop("color", "");
        String hilite = node.prop("hilite", "");
        String inlineStyle = buildInlineStyle(color, hilite);
        String styleCls = style.isEmpty() ? "" : " style-" + style;

        switch (node.type) {
        case SECTION: {
            html.append("<div class='section'>");
            if (node.prop("title") != null) {
                String titleStyle = buildInlineStyle(color.isEmpty() ? node.prop("titleColor", "") : color, hilite);
                html.append("<div class='section-title'").append(styleCls.isEmpty() ? "" : " style='" + getStyleFontCss(style) + titleStyle + "'").append(">");
                html.append(escHtml(node.prop("title")));
                html.append("</div>");
            }
            html.append("<div class='section-body'>");
            renderPreviewNodes(html, node.children);
            html.append("</div></div>");
            break;
        }
        case COLUMNS:
            html.append("<div class='columns'>");
            for (LayoutNode child : node.children) {
                html.append("<div class='column'>");
                renderPreviewNodes(html, child.children);
                html.append("</div>");
            }
            html.append("</div>");
            break;
        case COLUMN:
            renderPreviewNodes(html, node.children);
            break;
        case FIELD:
            html.append("<div class='field-row").append(styleCls).append("'").append(inlineStyle.isEmpty() ? "" : " style='" + inlineStyle + "'").append(">");
            html.append("<span class='field-label'>").append(escHtml(node.prop("label", labelForRef(node.prop("ref", "?"))))).append(":</span> ");
            html.append("<span class='field-value'>sample</span></div>");
            break;
        case DIVIDER: {
            String divStyle = "";
            if (!color.isEmpty())
                divStyle = "border-top-color:" + escHtml(color) + ";";
            if (!style.isEmpty()) {
                if (style.equals("h1"))
                    divStyle += "border-top-width:4px;margin:20px 8px;";
                else if (style.equals("h2"))
                    divStyle += "border-top-width:3px;margin:16px 8px;";
                else if (style.equals("small"))
                    divStyle += "border-top-width:1px;margin:8px 8px;";
            }
            html.append("<hr class='layout-divider'").append(divStyle.isEmpty() ? "" : " style='" + divStyle + "'").append("/>");
            break;
        }
        case TABLE: {
            String tableTitle = labelForRef(node.prop("ref", "Table"));
            html.append("<div class='section'><div class='section-title'").append(inlineStyle.isEmpty() ? "" : " style='" + inlineStyle + "'").append(">");
            html.append(escHtml(tableTitle)).append(" (3 items)</div>");
            String cols = node.prop("columns", "");
            String colTitles = node.prop("columnTitles", "");
            if (!cols.isEmpty()) {
                String[] colArr = cols.split(",");
                String[] titleArr = colTitles.isEmpty() ? new String[0] : colTitles.split(",", -1);
                html.append("<table><tr>");
                for (int i = 0; i < colArr.length; i++) {
                    String th = (i < titleArr.length && !titleArr[i].trim().isEmpty()) ? titleArr[i].trim() : labelForRef(node.prop("ref", "") + "." + colArr[i].trim());
                    html.append("<th>").append(escHtml(th)).append("</th>");
                }
                html.append("</tr><tr>");
                for (int i = 0; i < colArr.length; i++)
                    html.append("<td>\u2014</td>");
                html.append("</tr></table>");
            }
            html.append("</div>");
            break;
        }
        case TABBED_SECTION: {
            if (node.prop("title") != null)
                html.append("<div style='font-weight:bold;padding:6px 12px;color:#64748b;font-size:13px;'>").append(escHtml(node.prop("title"))).append("</div>");
            html.append("<div class='tab-bar'>");
            for (int i = 0; i < node.children.size(); i++) {
                LayoutNode tab = node.children.get(i);
                String cls = i == 0 ? "active" : "inactive";
                html.append("<span class='").append(cls).append("'>").append(escHtml(tab.prop("title", "Tab " + (i + 1)))).append("</span>");
            }
            html.append("</div>");
            if (!node.children.isEmpty() && !node.children.get(0).children.isEmpty())
                renderPreviewNodes(html, node.children.get(0).children);
            break;
        }
        case TAB:
            renderPreviewNodes(html, node.children);
            break;
        }
    }

    private String buildInlineStyle(String color, String hilite) {
        StringBuilder sb = new StringBuilder();
        if (!color.isEmpty())
            sb.append("color:").append(escHtml(color)).append(';');
        if (!hilite.isEmpty())
            sb.append("background-color:").append(escHtml(hilite)).append(';');
        return sb.toString();
    }

    private String getStyleFontCss(String style) {
        switch (style) {
        case "h1":
            return "font-size:20px;font-weight:700;";
        case "h2":
            return "font-size:17px;font-weight:600;";
        case "h3":
            return "font-size:14px;font-weight:600;";
        case "h4":
            return "font-size:13px;font-weight:600;";
        case "small":
            return "font-size:11px;";
        case "caption":
            return "font-size:11px;font-style:italic;";
        default:
            return "";
        }
    }

    private static String escHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String humanize(String s) {
        if (s == null || s.isEmpty())
            return "";
        int dot = s.lastIndexOf('.');
        if (dot >= 0)
            s = s.substring(dot + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c))
                sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private String getFieldLabel(DOSchemaField field) {
        if (field == null)
            return "";
        if (field.attributes.title != null && !field.attributes.title.trim().isEmpty())
            return field.attributes.title.trim();
        if (field.attributes.destinationName != null && !field.attributes.destinationName.trim().isEmpty())
            return humanize(field.attributes.destinationName.trim());
        if (field.attributes.source != null && !field.attributes.source.trim().isEmpty())
            return humanize(field.attributes.source.trim());
        return "";
    }

    private String labelForRef(String ref) {
        DOSchemaField field = resolveFieldByRef(ref);
        if (field != null) {
            String label = getFieldLabel(field);
            if (!label.isEmpty())
                return label;
        }
        return humanize(ref);
    }

    // ── Auto Layout ────────────────────────────────────────────────

    private void autoLayout() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) layoutModel.getRoot();
        if (root.getChildCount() > 0) {
            int choice = JOptionPane.showConfirmDialog(this, "This will replace the current layout. Continue?", "Auto Layout", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION)
                return;
            root.removeAllChildren();
            layoutModel.reload();
        }

        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);
        List<DOSchemaField> primitives = new ArrayList<>();
        List<DOSchemaField> embedded = new ArrayList<>();
        List<DOSchemaField> collections = new ArrayList<>();

        for (DOSchemaField field : allFields) {
            if (!field.attributes.isExported)
                continue;
            if (field.attributes.destinationName == null || field.attributes.destinationName.isEmpty())
                continue;
            if (field.attributes.isCollection)
                collections.add(field);
            else if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type))
                embedded.add(field);
            else
                primitives.add(field);
        }

        for (DOSchemaField field : primitives) {
            LayoutNode node = new LayoutNode(LayoutNodeType.FIELD);
            node.setProp("ref", field.attributes.destinationName);
            applyAutoFormat(node, field);
            root.add(buildTreeNode(node));
        }

        for (DOSchemaField field : embedded) {
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", getFieldLabel(field));
            section.setProp("collapsible", "true");
            DOSchemaClass embeddedClass = findClassByType(field.attributes.type);
            if (embeddedClass != null) {
                for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, refSchema)) {
                    if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                        continue;
                    if (sf.attributes.isCollection) {
                        LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                        table.setProp("ref", field.attributes.destinationName + "." + sf.attributes.destinationName);
                        addAutoTableColumns(table, sf);
                        section.children.add(table);
                    } else {
                        LayoutNode fn = new LayoutNode(LayoutNodeType.FIELD);
                        fn.setProp("ref", field.attributes.destinationName + "." + sf.attributes.destinationName);
                        applyAutoFormat(fn, sf);
                        section.children.add(fn);
                    }
                }
            }
            root.add(buildTreeNode(section));
        }

        if (!collections.isEmpty() && (!primitives.isEmpty() || !embedded.isEmpty())) {
            root.add(buildTreeNode(new LayoutNode(LayoutNodeType.DIVIDER)));
        }

        for (DOSchemaField field : collections) {
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", field.attributes.destinationName);
            addAutoTableColumns(table, field);
            root.add(buildTreeNode(table));
        }

        layoutModel.reload();
        expandAllNodes(layoutTree, 0, layoutTree.getRowCount());
        refreshFieldPalette();
        updatePreview();
    }

    private void applyAutoFormat(LayoutNode node, DOSchemaField field) {
        if (field.attributes.type == null)
            return;
        switch (field.attributes.type) {
        case "date":
        case "java.util.Date":
        case "java.sql.Timestamp":
            node.setProp("format", "date:yyyy-MM-dd");
            break;
        case "boolean":
        case "java.lang.Boolean":
            node.setProp("format", "bool:Oui,Non");
            break;
        case "long":
        case "java.lang.Long":
            String name = field.attributes.destinationName.toLowerCase();
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance"))
                node.setProp("format", "longdate:yyyy-MM-dd HH:mm");
            break;
        }
    }

    private void addAutoTableColumns(LayoutNode table, DOSchemaField collectionField) {
        String childType = collectionField.attributes.childrenType;
        if (childType == null || isPrimitiveType(childType))
            return;
        DOSchemaClass childClass = findClassByType(childType);
        if (childClass == null)
            return;

        List<String> colNames = new ArrayList<>();
        List<String> colTitles = new ArrayList<>();
        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                continue;
            if (sf.attributes.isCollection || (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type)))
                continue;
            colNames.add(sf.attributes.destinationName);
            colTitles.add(sf.attributes.title != null ? sf.attributes.title : "");
        }
        if (!colNames.isEmpty()) {
            table.setProp("columns", String.join(",", colNames));
            if (colTitles.stream().anyMatch(t -> t != null && !t.isBlank())) {
                table.setProp("columnTitles", String.join(",", colTitles));
            }
        }
    }

    // ── Tree Operations ────────────────────────────────────────────

    private void addNode(LayoutNodeType type, String... propsKV) {
        LayoutNode node = new LayoutNode(type);
        for (int i = 0; i + 1 < propsKV.length; i += 2)
            node.setProp(propsKV[i], propsKV[i + 1]);
        insertNodeIntoTree(node);
    }

    private void addColumnsNode() {
        String countStr = JOptionPane.showInputDialog(this, "Number of columns:", "2");
        if (countStr == null)
            return;
        int count;
        try {
            count = Integer.parseInt(countStr.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (count < 1 || count > 6)
            return;

        LayoutNode cols = new LayoutNode(LayoutNodeType.COLUMNS);
        cols.setProp("count", String.valueOf(count));
        String[] sizes = new String[count];
        int each = 100 / count;
        Arrays.fill(sizes, String.valueOf(each));
        sizes[count - 1] = String.valueOf(100 - each * (count - 1));
        cols.setProp("sizes", String.join(",", sizes));
        for (int i = 0; i < count; i++)
            cols.children.add(new LayoutNode(LayoutNodeType.COLUMN));
        insertNodeIntoTree(cols);
    }

    private void addTableNode() {
        TreePath path = fieldPalette.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode palNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (palNode.getUserObject() instanceof FieldPaletteItem) {
                FieldPaletteItem item = (FieldPaletteItem) palNode.getUserObject();
                if (item.isCollection) {
                    LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                    table.setProp("ref", item.dotPath);
                    insertNodeIntoTree(table);
                    return;
                }
            }
        }
        String ref = JOptionPane.showInputDialog(this, "Collection field name (destinationName):");
        if (ref != null && !ref.trim().isEmpty()) {
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", ref.trim());
            insertNodeIntoTree(table);
        }
    }

    private void addTabbedSectionNode() {
        LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
        tabs.setProp("title", "Tabs");
        LayoutNode tab1 = new LayoutNode(LayoutNodeType.TAB);
        tab1.setProp("title", "Tab 1");
        LayoutNode tab2 = new LayoutNode(LayoutNodeType.TAB);
        tab2.setProp("title", "Tab 2");
        tabs.children.add(tab1);
        tabs.children.add(tab2);
        insertNodeIntoTree(tabs);
    }

    private void addTabNode() {
        if (selectedLayoutNode == null)
            return;
        LayoutNode parentObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
        if (parentObj == null || parentObj.type != LayoutNodeType.TABBED_SECTION) {
            if (selectedLayoutNode.getParent() != null) {
                DefaultMutableTreeNode parentTreeNode = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
                parentObj = getLayoutNodeFromTreeNode(parentTreeNode);
                if (parentObj == null || parentObj.type != LayoutNodeType.TABBED_SECTION) {
                    JOptionPane.showMessageDialog(this, "Select a Tabbed Section to add a tab to.", "Invalid Location", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                selectedLayoutNode = parentTreeNode;
            } else {
                JOptionPane.showMessageDialog(this, "Select a Tabbed Section to add a tab to.", "Invalid Location", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
        tab.setProp("title", "Tab " + (parentObj.children.size() + 1));
        parentObj.children.add(tab);
        DefaultMutableTreeNode tabTreeNode = new DefaultMutableTreeNode(tab);
        layoutModel.insertNodeInto(tabTreeNode, selectedLayoutNode, selectedLayoutNode.getChildCount());
        layoutTree.expandPath(new TreePath(selectedLayoutNode.getPath()));
        updatePreview();
    }

    private void addFieldFromPalette() {
        TreePath path = fieldPalette.getSelectionPath();
        if (path == null)
            return;
        DefaultMutableTreeNode palNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(palNode.getUserObject() instanceof FieldPaletteItem))
            return;
        FieldPaletteItem item = (FieldPaletteItem) palNode.getUserObject();

        if (item.isCollection) {
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", item.dotPath);
            insertNodeIntoTree(table);
        } else {
            LayoutNode field = new LayoutNode(LayoutNodeType.FIELD);
            field.setProp("ref", item.dotPath);
            insertNodeIntoTree(field);
        }
    }

    private void insertNodeIntoTree(LayoutNode node) {
        DefaultMutableTreeNode treeNode = buildTreeNode(node);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) layoutModel.getRoot();

        if (selectedLayoutNode != null && selectedLayoutNode != root) {
            LayoutNode parentObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
            if (parentObj != null && canContainChild(parentObj.type, node.type)) {
                parentObj.children.add(node);
                layoutModel.insertNodeInto(treeNode, selectedLayoutNode, selectedLayoutNode.getChildCount());
                layoutTree.expandPath(new TreePath(selectedLayoutNode.getPath()));
            } else {
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
                if (parent == null)
                    parent = root;
                LayoutNode parentLayoutNode = getLayoutNodeFromTreeNode(parent);
                int idx = parent.getIndex(selectedLayoutNode) + 1;
                if (parentLayoutNode != null)
                    parentLayoutNode.children.add(Math.min(idx, parentLayoutNode.children.size()), node);
                layoutModel.insertNodeInto(treeNode, parent, idx);
            }
        } else {
            layoutModel.insertNodeInto(treeNode, root, root.getChildCount());
        }

        layoutTree.setSelectionPath(new TreePath(treeNode.getPath()));
        refreshFieldPalette();
        updatePreview();
    }

    private boolean canContainChild(LayoutNodeType parent, LayoutNodeType child) {
        switch (parent) {
        case SECTION:
        case COLUMN:
        case TAB:
            return true;
        case COLUMNS:
            return child == LayoutNodeType.COLUMN;
        case TABBED_SECTION:
            return child == LayoutNodeType.TAB;
        default:
            return false;
        }
    }

    private void deleteNode() {
        if (selectedLayoutNode == null)
            return;
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
        if (parent == null)
            return;

        LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
        LayoutNode nodeObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
        if (parentObj != null && nodeObj != null)
            parentObj.children.remove(nodeObj);

        layoutModel.removeNodeFromParent(selectedLayoutNode);
        selectedLayoutNode = null;
        showEmptyProperties();
        refreshFieldPalette();
        updatePreview();
    }

    private DefaultMutableTreeNode buildTreeNode(LayoutNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (LayoutNode child : node.children)
            treeNode.add(buildTreeNode(child));
        return treeNode;
    }

    private LayoutNode getLayoutNodeFromTreeNode(DefaultMutableTreeNode treeNode) {
        if (treeNode == null)
            return null;
        Object obj = treeNode.getUserObject();
        return (obj instanceof LayoutNode) ? (LayoutNode) obj : null;
    }

    // ── Load / Save ────────────────────────────────────────────────

    private void loadExistingLayout() {
        DetailLayout layout = config.getLayout();
        if (layout == null)
            return;

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) layoutModel.getRoot();
        for (LayoutNode node : layout.nodes)
            root.add(buildTreeNode(node));
        layoutModel.reload();
        expandAllNodes(layoutTree, 0, layoutTree.getRowCount());
        refreshFieldPalette();
        updatePreview();
    }

    private void expandAllNodes(JTree tree, int startRow, int rowCount) {
        for (int i = startRow; i < rowCount; i++)
            tree.expandRow(i);
        if (tree.getRowCount() != rowCount)
            expandAllNodes(tree, rowCount, tree.getRowCount());
    }

    private DetailLayout buildLayoutFromTree() {
        DetailLayout layout = new DetailLayout();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) layoutModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            LayoutNode node = getLayoutNodeFromTreeNode(child);
            if (node != null)
                layout.nodes.add(node);
        }
        return layout;
    }

    private void save() {
        DetailLayout layout = buildLayoutFromTree();
        config.setLayout(layout.isEmpty() ? null : layout);

        try {
            DOModuleService svc = DOModuleService.getInstance();
            svc.saveModuleStructure(svc.getModules());
            JOptionPane.showMessageDialog(this, "Layout saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── DnD Handlers ───────────────────────────────────────────────

    /**
     * Drag handler for the field palette — creates COPY transfers of
     * FieldPaletteItem.
     */
    private class FieldPaletteDragHandler extends TransferHandler {
        @Override
        protected Transferable createTransferable(JComponent c) {
            TreePath path = fieldPalette.getSelectionPath();
            if (path == null)
                return null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!(node.getUserObject() instanceof FieldPaletteItem))
                return null;
            return new FieldTransferable((FieldPaletteItem) node.getUserObject());
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    /**
     * Unified DnD handler for the layout tree. Supports: - Drops from field
     * palette (creates new FIELD/TABLE nodes) - Internal tree rearrangement
     * (move nodes via drag & drop)
     */
    private class LayoutTreeDnDHandler extends TransferHandler {

        @Override
        protected Transferable createTransferable(JComponent c) {
            TreePath path = layoutTree.getSelectionPath();
            if (path == null)
                return null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!(node.getUserObject() instanceof LayoutNode))
                return null;
            return new LayoutNodeTransferable(node);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (support.isDataFlavorSupported(FIELD_FLAVOR))
                return true;
            if (support.isDataFlavorSupported(LAYOUT_NODE_FLAVOR)) {
                // Validate target — NOTE: do NOT call getTransferData() here;
                // it is
                // unreliable during the hover phase on some JVMs/platforms and
                // will
                // cause the entire drag to be rejected (canImport returns
                // false).
                // Instead we use the tree's current selection path to identify
                // the
                // dragged node, and defer full validation to importData.
                if (support.isDrop()) {
                    JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                    TreePath destPath = dl.getPath();
                    if (destPath == null)
                        return false;
                    DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) destPath.getLastPathComponent();

                    // Use the selection path to identify the source node safely
                    TreePath selPath = layoutTree.getSelectionPath();
                    if (selPath != null) {
                        DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                        // Don't allow dropping onto self or descendants
                        if (sourceNode == destNode)
                            return false;
                        TreeNode[] targetPath = destNode.getPath();
                        for (TreeNode tp : targetPath) {
                            if (tp == sourceNode)
                                return false;
                        }
                        // Check parent compatibility when dropping ON a node
                        if (dl.getChildIndex() == -1) {
                            LayoutNode destObj = getLayoutNodeFromTreeNode(destNode);
                            if (destObj != null) {
                                LayoutNode sourceObj = getLayoutNodeFromTreeNode(sourceNode);
                                if (sourceObj != null)
                                    return canContainChild(destObj.type, sourceObj.type);
                            }
                        }
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                if (support.isDataFlavorSupported(FIELD_FLAVOR)) {
                    return importPaletteField(support);
                }
                if (support.isDataFlavorSupported(LAYOUT_NODE_FLAVOR)) {
                    return importLayoutNode(support);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private boolean importPaletteField(TransferSupport support) throws Exception {
            FieldPaletteItem item = (FieldPaletteItem) support.getTransferable().getTransferData(FIELD_FLAVOR);
            LayoutNode node;
            if (item.isCollection) {
                node = new LayoutNode(LayoutNodeType.TABLE);
                node.setProp("ref", item.dotPath);
            } else {
                node = new LayoutNode(LayoutNodeType.FIELD);
                node.setProp("ref", item.dotPath);
            }

            if (support.isDrop()) {
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                insertNodeAtDropLocation(node, dl);
            } else {
                insertNodeIntoTree(node);
            }
            return true;
        }

        private boolean importLayoutNode(TransferSupport support) throws Exception {
            DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) support.getTransferable().getTransferData(LAYOUT_NODE_FLAVOR);
            LayoutNode sourceObj = getLayoutNodeFromTreeNode(sourceNode);
            if (sourceObj == null)
                return false;

            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath destPath = dl.getPath();
            if (destPath == null)
                return false;
            DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) destPath.getLastPathComponent();
            int childIndex = dl.getChildIndex();

            // Remove from old parent
            DefaultMutableTreeNode oldParent = (DefaultMutableTreeNode) sourceNode.getParent();
            LayoutNode oldParentObj = getLayoutNodeFromTreeNode(oldParent);
            if (oldParentObj != null)
                oldParentObj.children.remove(sourceObj);

            // Compute the source node's index in the parent BEFORE removal so
            // we
            // can correctly adjust the insertion index when moving within the
            // same parent.
            int sourceIdxInParent = (oldParent != null) ? oldParent.getIndex(sourceNode) : -1;

            // Save reference before removal
            DefaultMutableTreeNode movedNode = sourceNode;
            layoutModel.removeNodeFromParent(movedNode);

            // Insert at new location
            LayoutNode destObj = getLayoutNodeFromTreeNode(destNode);
            if (childIndex == -1) {
                // Drop ON the node — add as last child
                if (destObj != null) {
                    destObj.children.add(sourceObj);
                }
                layoutModel.insertNodeInto(movedNode, destNode, destNode.getChildCount());
            } else {
                // Drop BETWEEN nodes.
                // When the source and destination parent are the same node, the
                // childIndex from the drop location was computed BEFORE the
                // source
                // was removed. If the source was positioned before the drop
                // point
                // we must subtract 1 to obtain the correct post-removal index.
                int adjustedIdx = childIndex;
                if (destNode == oldParent && sourceIdxInParent >= 0 && sourceIdxInParent < childIndex)
                    adjustedIdx = childIndex - 1;

                if (destObj != null) {
                    int insertIdx = Math.min(adjustedIdx, destObj.children.size());
                    destObj.children.add(insertIdx, sourceObj);
                }
                int treeIdx = Math.min(adjustedIdx, destNode.getChildCount());
                layoutModel.insertNodeInto(movedNode, destNode, treeIdx);
            }

            layoutTree.setSelectionPath(new TreePath(movedNode.getPath()));
            refreshFieldPalette();
            updatePreview();
            return true;
        }

        private void insertNodeAtDropLocation(LayoutNode node, JTree.DropLocation dl) {
            DefaultMutableTreeNode treeNode = buildTreeNode(node);
            TreePath destPath = dl.getPath();
            DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) destPath.getLastPathComponent();
            int childIndex = dl.getChildIndex();

            LayoutNode destObj = getLayoutNodeFromTreeNode(destNode);
            if (childIndex == -1) {
                // Dropping ON a node
                if (destObj != null && canContainChild(destObj.type, node.type)) {
                    destObj.children.add(node);
                    layoutModel.insertNodeInto(treeNode, destNode, destNode.getChildCount());
                } else {
                    // Add as sibling
                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) destNode.getParent();
                    if (parent == null)
                        parent = (DefaultMutableTreeNode) layoutModel.getRoot();
                    LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
                    int idx = parent.getIndex(destNode) + 1;
                    if (parentObj != null)
                        parentObj.children.add(Math.min(idx, parentObj.children.size()), node);
                    layoutModel.insertNodeInto(treeNode, parent, Math.min(idx, parent.getChildCount()));
                }
            } else {
                // Dropping BETWEEN nodes
                if (destObj != null) {
                    int insertIdx = Math.min(childIndex, destObj.children.size());
                    destObj.children.add(insertIdx, node);
                }
                layoutModel.insertNodeInto(treeNode, destNode, Math.min(childIndex, destNode.getChildCount()));
            }

            layoutTree.setSelectionPath(new TreePath(treeNode.getPath()));
            refreshFieldPalette();
            updatePreview();
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            // Cleanup is handled in importData for MOVE operations
        }
    }

    // ── Helper classes ─────────────────────────────────────────────

    static class FieldPaletteItem implements java.io.Serializable {
        final String displayName;
        final String dotPath;
        final boolean isCollection;

        FieldPaletteItem(String displayName, String dotPath, boolean isCollection) {
            this.displayName = displayName;
            this.dotPath = dotPath;
            this.isCollection = isCollection;
        }

        @Override
        public String toString() {
            return displayName + (isCollection ? " [ ]" : "");
        }
    }

    static class FieldTransferable implements Transferable {
        private final FieldPaletteItem item;

        FieldTransferable(FieldPaletteItem item) {
            this.item = item;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { FIELD_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FIELD_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return item;
        }
    }

    static class LayoutNodeTransferable implements Transferable {
        private final DefaultMutableTreeNode node;

        LayoutNodeTransferable(DefaultMutableTreeNode node) {
            this.node = node;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { LAYOUT_NODE_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return LAYOUT_NODE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return node;
        }
    }

    static class LayoutTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object obj = ((DefaultMutableTreeNode) value).getUserObject();
                if (obj instanceof LayoutNode) {
                    LayoutNode node = (LayoutNode) obj;
                    setText(node.toString());
                    switch (node.type) {
                    case SECTION:
                        setIcon(UIManager.getIcon("FileView.directoryIcon"));
                        break;
                    case COLUMNS:
                        setIcon(UIManager.getIcon("Table.ascendingSortIcon"));
                        break;
                    case FIELD:
                        setIcon(UIManager.getIcon("FileView.fileIcon"));
                        break;
                    case DIVIDER:
                        setIcon(null);
                        break;
                    case TABLE:
                        setIcon(UIManager.getIcon("FileChooser.listViewIcon"));
                        break;
                    case TABBED_SECTION:
                        setIcon(UIManager.getIcon("FileView.computerIcon"));
                        break;
                    case TAB:
                        setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
                        break;
                    default:
                        break;
                    }
                }
            }
            return this;
        }
    }
}
