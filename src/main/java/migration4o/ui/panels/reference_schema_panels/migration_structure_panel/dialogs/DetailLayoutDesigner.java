package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Designer for record detail view layouts.
 * Tree-based layout builder with field palette, property panel, and HTML preview.
 */
public class DetailLayoutDesigner extends JFrame {

    private final ClassExportConfig config;
    private final DOSchemaClass schemaClass;
    private final DOSchema refSchema;

    private JTree fieldPalette;
    private JTree layoutTree;
    private DefaultTreeModel layoutModel;
    private JPanel propertyPanel;
    private JEditorPane previewPane;

    private DefaultMutableTreeNode selectedLayoutNode;

    public DetailLayoutDesigner(ClassExportConfig config, DOSchemaClass schemaClass, DOSchema refSchema) {
        super("Detail Layout Designer — " + schemaClass.destinationName);
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

        // Toolbar
        add(buildToolbar(), BorderLayout.NORTH);

        // Main split: [palette | layout tree | properties]
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFieldPalette(), buildLayoutTreePanel());
        leftSplit.setDividerLocation(250);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, buildPropertyPanel());
        rightSplit.setDividerLocation(650);

        // Top/bottom split: [editor | preview]
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
        bar.add(makeBtn("\u2191 Up", e -> moveNode(-1)));
        bar.add(makeBtn("\u2193 Down", e -> moveNode(1)));
        bar.add(makeBtn("\u2190 Outdent", e -> outdentNode()));
        bar.add(makeBtn("\u2192 Indent", e -> indentNode()));
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
        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);

        DefaultMutableTreeNode directNode = new DefaultMutableTreeNode("Direct Fields");
        DefaultMutableTreeNode embeddedNode = new DefaultMutableTreeNode("Embedded Entities");
        DefaultMutableTreeNode collectionsNode = new DefaultMutableTreeNode("Collections");
        DefaultMutableTreeNode refsNode = new DefaultMutableTreeNode("References (IDEntite)");

        for (DOSchemaField field : allFields) {
            if (!field.isExported)
                continue;
            String dest = field.destinationName;
            if (dest == null || dest.isEmpty())
                continue;

            if (field.isCollection) {
                DefaultMutableTreeNode collNode = new DefaultMutableTreeNode(new FieldPaletteItem(dest, dest, true));
                // Add sub-fields if embedded collection
                if (field.embedContents) {
                    addEmbeddedSubFields(collNode, field.childrenType, dest);
                }
                collectionsNode.add(collNode);
            } else if (field.embedContents && !isPrimitiveType(field.type)) {
                DefaultMutableTreeNode embNode = new DefaultMutableTreeNode(new FieldPaletteItem(dest, dest, false));
                addEmbeddedSubFields(embNode, field.type, dest);
                embeddedNode.add(embNode);
            } else if (isIDEntiteType(field)) {
                refsNode.add(new DefaultMutableTreeNode(new FieldPaletteItem(dest, dest, false)));
            } else {
                directNode.add(new DefaultMutableTreeNode(new FieldPaletteItem(dest, dest, false)));
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

        fieldPalette = new JTree(root);
        fieldPalette.setRootVisible(false);
        fieldPalette.setShowsRootHandles(true);
        fieldPalette.expandRow(0);
        fieldPalette.expandRow(1);

        // Double-click to add field to layout
        fieldPalette.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addFieldFromPalette();
                }
            }
        });

        // DnD from palette
        fieldPalette.setDragEnabled(true);
        fieldPalette.setTransferHandler(new FieldPaletteDragHandler());

        JScrollPane sp = new JScrollPane(fieldPalette);
        sp.setBorder(BorderFactory.createTitledBorder("Available Fields"));
        sp.setPreferredSize(new Dimension(250, 400));
        return sp;
    }

    private void addEmbeddedSubFields(DefaultMutableTreeNode parentNode, String typeName, String parentPath) {
        if (typeName == null)
            return;
        DOSchemaClass embeddedClass = findClassByType(typeName);
        if (embeddedClass == null)
            return;

        List<DOSchemaField> subFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, refSchema);
        for (DOSchemaField sf : subFields) {
            if (!sf.isExported || sf.destinationName == null)
                continue;
            String dotPath = parentPath + "." + sf.destinationName;
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FieldPaletteItem(sf.destinationName, dotPath, sf.isCollection));
            // One level of recursion for nested embedded entities
            if (sf.embedContents && !isPrimitiveType(sf.type) && !sf.isCollection) {
                addEmbeddedSubFields(child, sf.type, dotPath);
            }
            parentNode.add(child);
        }
    }

    private DOSchemaClass findClassByType(String typeName) {
        if (typeName == null || refSchema == null)
            return null;
        // Try finding by source name
        DOSchemaClass cls = refSchema.findClassByName(typeName);
        if (cls != null)
            return cls;
        // Try short name match
        String shortName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        for (DOSchemaClass c : refSchema.getClasses()) {
            if (c.source != null && c.source.endsWith("." + shortName))
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
        if (field.type == null)
            return false;
        DOSchemaClass typeClass = findClassByType(field.type);
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
            if (path != null) {
                selectedLayoutNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            } else {
                selectedLayoutNode = null;
            }
            updatePropertyPanel();
        });

        // Accept drops
        layoutTree.setTransferHandler(new LayoutTreeDropHandler());
        layoutTree.setDropMode(DropMode.ON_OR_INSERT);

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
        sp.setPreferredSize(new Dimension(280, 400));
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
            addPropField("Title Color", node, "titleColor");
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
            addPropReadOnly("Ref", node.prop("ref", ""));
            addPropField("Columns", node, "columns");
            addPropField("Widths (%)", node, "widths");
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
        tf.addActionListener(e -> {
            node.setProp(propKey, tf.getText().trim());
            layoutModel.nodeChanged(selectedLayoutNode);
            updatePreview();
        });
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                node.setProp(propKey, tf.getText().trim());
                layoutModel.nodeChanged(selectedLayoutNode);
                updatePreview();
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

    private void addFormatEditor(LayoutNode node) {
        String ref = node.prop("ref", "");
        DOSchemaField field = resolveFieldByRef(ref);
        String fieldType = (field != null) ? field.type : "string";
        if (fieldType == null)
            fieldType = "string";

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
            // Long can be a number or a date
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
            JLabel patLbl = new JLabel("Pattern:");
            String initPattern = isLongDate ? currentFormat.substring(9) : (currentFormat.startsWith("num:") ? currentFormat.substring(4) : "");
            JTextField patField = new JTextField(initPattern, 15);
            patternRow.add(patLbl, BorderLayout.WEST);
            patternRow.add(patField, BorderLayout.CENTER);
            formatPanel.add(patternRow);

            Runnable updater = () -> {
                String pat = patField.getText().trim();
                if (dateRadio.isSelected() && !pat.isEmpty()) {
                    node.setProp("format", "longdate:" + pat);
                } else if (numRadio.isSelected() && !pat.isEmpty()) {
                    node.setProp("format", "num:" + pat);
                } else {
                    node.setProp("format", "");
                }
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
            JLabel noFmt = new JLabel("No format options for this field type (" + fieldType + ")");
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
        JLabel lbl = new JLabel("Pattern:");
        String initVal = currentFormat.startsWith(prefix + ":") ? currentFormat.substring(prefix.length() + 1) : "";
        JComboBox<String> combo = new JComboBox<>(new String[] { "", "yyyy-MM-dd", "dd/MM/yyyy", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss", "MMMM dd, yyyy" });
        combo.setEditable(true);
        combo.setSelectedItem(initVal);
        combo.addActionListener(e -> {
            String pat = (String) combo.getSelectedItem();
            node.setProp("format", (pat != null && !pat.isEmpty()) ? prefix + ":" + pat : "");
            updatePreview();
        });
        row.add(lbl, BorderLayout.WEST);
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
        JPanel trueRow = new JPanel(new BorderLayout(5, 0));
        trueRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField trueField = new JTextField(trueVal, 10);
        trueRow.add(new JLabel("True label:"), BorderLayout.WEST);
        trueRow.add(trueField, BorderLayout.CENTER);

        JPanel falseRow = new JPanel(new BorderLayout(5, 0));
        falseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField falseField = new JTextField(falseVal, 10);
        falseRow.add(new JLabel("False label:"), BorderLayout.WEST);
        falseRow.add(falseField, BorderLayout.CENTER);

        Runnable updater = () -> {
            String t = trueField.getText().trim(), f = falseField.getText().trim();
            node.setProp("format", (!t.isEmpty() || !f.isEmpty()) ? "bool:" + t + "," + f : "");
            updatePreview();
        };
        trueField.addActionListener(e -> updater.run());
        trueField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                updater.run();
            }
        });
        falseField.addActionListener(e -> updater.run());
        falseField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                updater.run();
            }
        });

        panel.add(trueRow);
        panel.add(falseRow);
    }

    private void addNumFormatEditor(JPanel panel, LayoutNode node, String currentFormat) {
        String initVal = currentFormat.startsWith("num:") ? currentFormat.substring(4) : "";
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel("Pattern:");
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

        row.add(lbl, BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        panel.add(row);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);
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
            // Navigate into embedded type for intermediate segments
            if (i < parts.length - 1) {
                current = findClassByType(field.type);
                if (current == null)
                    return null;
            }
        }
        return field;
    }

    // ── Preview ────────────────────────────────────────────────────

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Preview"));

        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        panel.add(new JScrollPane(previewPane), BorderLayout.CENTER);

        JButton previewBtn = new JButton("Preview in Browser");
        previewBtn.addActionListener(e -> openBrowserPreview());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(previewBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

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
        html.append(".field-row{padding:4px 12px;border-bottom:1px solid #f1f5f9;}");
        html.append(".field-label{font-weight:bold;color:#64748b;font-size:12px;}");
        html.append(".field-value{color:#1e293b;}");
        html.append("hr.layout-divider{border:none;border-top:1px solid #cbd5e1;margin:12px 0;}");
        html.append("table{border-collapse:collapse;width:100%;font-size:12px;} th{text-align:left;background:#f8fafc;padding:4px 8px;border-bottom:2px solid #e2e8f0;} td{padding:4px 8px;border-bottom:1px solid #f1f5f9;}");
        html.append("</style></head><body>");
        renderPreviewNodes(html, layout.nodes);
        html.append("</body></html>");
        previewPane.setText(html.toString());
        previewPane.setCaretPosition(0);
    }

    private void renderPreviewNodes(StringBuilder html, List<LayoutNode> nodes) {
        for (LayoutNode node : nodes)
            renderPreviewNode(html, node);
    }

    private void renderPreviewNode(StringBuilder html, LayoutNode node) {
        switch (node.type) {
        case SECTION: {
            String titleColor = node.prop("titleColor");
            String color = titleColor != null ? "color:" + escHtml(titleColor) + ";" : "";
            html.append("<div style='margin:4px 0;'>");
            if (node.prop("title") != null)
                html.append("<div style='font-weight:bold;padding:6px 12px;background:#f8fafc;border-bottom:1px solid #e2e8f0;").append(color).append("'>").append(escHtml(node.prop("title"))).append("</div>");
            renderPreviewNodes(html, node.children);
            html.append("</div>");
            break;
        }
        case COLUMNS:
            html.append("<table width='100%' cellpadding='0' cellspacing='0'><tr>");
            for (LayoutNode child : node.children) {
                html.append("<td valign='top' style='padding:0 4px;border-right:1px solid #f1f5f9;'>");
                renderPreviewNodes(html, child.children);
                html.append("</td>");
            }
            html.append("</tr></table>");
            break;
        case COLUMN:
            renderPreviewNodes(html, node.children);
            break;
        case FIELD:
            html.append("<div class='field-row'><span class='field-label'>").append(escHtml(node.prop("label", humanize(node.prop("ref", "?"))))).append(":</span> <span class='field-value'>sample</span></div>");
            break;
        case DIVIDER:
            html.append("<hr class='layout-divider'/>");
            break;
        case TABLE: {
            html.append("<div style='margin:4px 0;padding:6px 12px;font-weight:bold;background:#f8fafc;border-bottom:1px solid #e2e8f0;'>").append(escHtml(humanize(node.prop("ref", "Table")))).append(" (3 items)</div>");
            String cols = node.prop("columns", "");
            if (!cols.isEmpty()) {
                html.append("<table><tr>");
                for (String col : cols.split(","))
                    html.append("<th>").append(escHtml(humanize(col.trim()))).append("</th>");
                html.append("</tr><tr>");
                for (String col : cols.split(","))
                    html.append("<td>\u2014</td>");
                html.append("</tr></table>");
            }
            break;
        }
        case TABBED_SECTION: {
            if (node.prop("title") != null)
                html.append("<div style='font-weight:bold;padding:6px 12px;color:#64748b;'>").append(escHtml(node.prop("title"))).append("</div>");
            html.append("<div style='border-bottom:2px solid #e2e8f0;padding:4px 8px;'>");
            for (int i = 0; i < node.children.size(); i++) {
                LayoutNode tab = node.children.get(i);
                String style = i == 0 ? "color:#3b82f6;font-weight:bold;border-bottom:2px solid #3b82f6;" : "color:#94a3b8;";
                html.append("<span style='padding:6px 14px;font-size:12px;").append(style).append("'>").append(escHtml(tab.prop("title", "Tab " + (i + 1)))).append("</span>");
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

    private void openBrowserPreview() {
        DetailLayout layout = buildLayoutFromTree();
        if (layout == null || layout.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add some layout elements first.", "Empty Layout", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            String css = loadResource("/templates/sidebar.css");
            String js = loadResource("/templates/sidebar-nav.js");
            String layoutJson = layout.toJson();
            String mockData = buildMockData(layout);

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
            html.append("<title>Layout Preview — ").append(escHtml(schemaClass.destinationName)).append("</title>\n");
            html.append("<style>\n").append(css).append("\n</style>\n");
            html.append("<style>\n");
            html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 0; background: #f8fafc; color: #1e293b; }\n");
            html.append(".preview-wrapper { max-width: 800px; margin: 20px auto; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden; }\n");
            html.append(".detail-header { padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }\n");
            html.append(".detail-header h2 { margin: 0; font-size: 18px; color: #1e293b; }\n");
            html.append(".detail-subtitle { font-size: 13px; color: #64748b; margin-top: 4px; }\n");
            html.append(".detail-scroll { padding: 0; }\n");
            html.append("</style>\n</head>\n<body>\n");
            html.append("<div class=\"preview-wrapper\">\n");
            html.append("  <div class=\"detail-header\"><h2>").append(escHtml(schemaClass.destinationName)).append("</h2>");
            html.append("<div class=\"detail-subtitle\">Layout Preview — sample data</div></div>\n");
            html.append("  <div class=\"detail-scroll\" id=\"detailContainer\"></div>\n");
            html.append("</div>\n");

            // Provide globals the JS expects
            html.append("<script>\n");
            html.append("var DETAIL_LAYOUT = ").append(layoutJson).append(";\n");
            html.append("var MOCK_DATA = ").append(mockData).append(";\n");
            html.append("</script>\n");

            // Extract only the layout rendering functions from sidebar-nav.js
            // and run them in a standalone context
            html.append("<script>\n");
            html.append(extractLayoutFunctions(js));
            html.append("\n\n// Render the preview\n");
            html.append("(function() {\n");
            html.append("  var container = document.getElementById('detailContainer');\n");
            html.append("  container.innerHTML = renderLayoutDetail(MOCK_DATA, DETAIL_LAYOUT);\n");
            html.append("  bindTabEvents();\n");
            html.append("})();\n");
            html.append("</script>\n");
            html.append("</body>\n</html>");

            File tempFile = File.createTempFile("layout-preview-", ".html");
            tempFile.deleteOnExit();
            Files.write(tempFile.toPath(), html.toString().getBytes(StandardCharsets.UTF_8));
            Desktop.getDesktop().browse(tempFile.toURI());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to open preview: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null)
                throw new IOException("Resource not found: " + path);
            byte[] bytes = readAllBytes(is);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1)
            buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    /**
     * Extract the layout rendering functions from sidebar-nav.js so they can run standalone.
     * We need: resolveFieldValue, formatDatePattern, fmtValueWithFormat, fmtValue, esc,
     * renderLayoutDetail, renderLayoutNode, renderLayoutChildren, bindTabEvents,
     * and the collection/table helpers.
     */
    private String extractLayoutFunctions(String js) {
        StringBuilder sb = new StringBuilder();

        // Provide globals the layout renderer depends on
        sb.append("var collectionViewState = {};\n");
        sb.append("var collectionIdCounter = 1;\n");
        sb.append("var currentLanguage = 'fr';\n");

        // I18N (just the keys the layout renderer uses)
        sb.append("var I18N = {fr:{elements:'éléments',noItems:'Aucun élément'},en:{elements:'items',noItems:'No items'}};\n");

        String[] functions = { "function t(", "function esc(", "function normalizeFieldPath(", "function humanizeFieldName(", "function formatSectionTitle(", "function displayFieldLabel(", "function fmtValue(", "function resolveFieldValue(", "function formatDatePattern(", "function fmtValueWithFormat(", "function renderLayoutDetail(", "function renderLayoutNode(", "function renderLayoutChildren(", "function renderCollectionTableBody(", "function bindTabEvents(" };
        for (String sig : functions) {
            appendFunctionBlock(sb, js, sig);
        }

        return sb.toString();
    }

    /**
     * Find a function in the JS source by its signature (ignoring leading whitespace)
     * and extract the full function body (brace-matching). Appends as a top-level function.
     */
    private void appendFunctionBlock(StringBuilder sb, String js, String signature) {
        // Search ignoring leading whitespace
        int start = -1;
        int searchFrom = 0;
        while (searchFrom < js.length()) {
            int idx = js.indexOf(signature, searchFrom);
            if (idx < 0)
                break;
            // Accept if it's at the start or preceded by whitespace/newline
            if (idx == 0 || Character.isWhitespace(js.charAt(idx - 1))) {
                start = idx;
                break;
            }
            searchFrom = idx + 1;
        }
        if (start < 0)
            return;

        // Find the opening brace
        int braceStart = js.indexOf('{', start);
        if (braceStart < 0)
            return;

        // Match braces to find the end, ignoring braces inside strings and template literals
        int depth = 0;
        int end = braceStart;
        boolean inSingleQuote = false, inDoubleQuote = false, inTemplate = false;
        for (int i = braceStart; i < js.length(); i++) {
            char c = js.charAt(i);
            char prev = i > 0 ? js.charAt(i - 1) : 0;
            if (prev == '\\')
                continue; // skip escaped chars

            if (!inDoubleQuote && !inTemplate && c == '\'')
                inSingleQuote = !inSingleQuote;
            else if (!inSingleQuote && !inTemplate && c == '"')
                inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote && c == '`')
                inTemplate = !inTemplate;
            else if (!inSingleQuote && !inDoubleQuote && !inTemplate) {
                if (c == '{')
                    depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
        }

        sb.append(js, start, end).append('\n');
    }

    /**
     * Build a JSON object with mock/sample data for the fields used in the layout.
     */
    private String buildMockData(DetailLayout layout) {
        Map<String, String> mockFields = new LinkedHashMap<>();
        collectFieldRefs(layout.nodes, mockFields);
        return buildNestedJson(mockFields);
    }

    private void collectFieldRefs(List<LayoutNode> nodes, Map<String, String> mockFields) {
        for (LayoutNode node : nodes) {
            if (node.type == LayoutNodeType.FIELD) {
                String ref = node.prop("ref", "");
                if (!ref.isEmpty()) {
                    buildNestedMockValue(ref, node, mockFields);
                }
            } else if (node.type == LayoutNodeType.TABLE) {
                String ref = node.prop("ref", "");
                if (!ref.isEmpty()) {
                    // Build a sample array with 3 rows
                    String columns = node.prop("columns", "");
                    StringBuilder row = new StringBuilder("{");
                    if (!columns.isEmpty()) {
                        boolean f = true;
                        for (String col : columns.split(",")) {
                            if (!f)
                                row.append(",");
                            f = false;
                            row.append("\"").append(escJsonKey(col.trim())).append("\":\"sample\"");
                        }
                    } else {
                        row.append("\"id\":\"1\",\"value\":\"sample\"");
                    }
                    row.append("}");
                    mockFields.put(ref, "[" + row + "," + row + "," + row + "]");
                }
            }
            collectFieldRefs(node.children, mockFields);
        }
    }

    private void buildNestedMockValue(String ref, LayoutNode node, Map<String, String> mockFields) {
        if (!ref.contains(".")) {
            // Simple field — produce a sample value based on format
            mockFields.put(ref, generateSampleValue(ref, node));
            return;
        }
        // Dot-path: build nested object structure
        // e.g. "adresse.ville" → { "adresse": { "ville": "sample" } }
        String[] parts = ref.split("\\.", 2);
        String topKey = parts[0];
        String rest = parts[1];
        // For simplicity, put the full path in a flat map — the JS resolveFieldValue handles dot-paths
        mockFields.put(ref, generateSampleValue(ref, node));
        // Also ensure the parent object exists
        if (!mockFields.containsKey(topKey)) {
            // Will be built as a nested JSON by the toNestedJson conversion
        }
    }

    private String generateSampleValue(String ref, LayoutNode node) {
        String format = node != null ? node.prop("format", "") : "";

        if (format.startsWith("date:") || format.startsWith("longdate:")) {
            return "\"2025-06-15T10:30:00\"";
        }
        if (format.startsWith("bool:")) {
            return "true";
        }
        if (format.startsWith("num:")) {
            return "42.5";
        }

        // Infer from field schema if possible
        DOSchemaField field = resolveFieldByRef(ref);
        if (field != null) {
            String type = field.type;
            if (type != null) {
                if (type.contains("Date") || type.equals("date"))
                    return "\"2025-06-15T10:30:00\"";
                if (type.equals("boolean"))
                    return "true";
                if (type.equals("int") || type.equals("short") || type.equals("byte"))
                    return "42";
                if (type.equals("long") || type.equals("java.lang.Long"))
                    return "1718445000000";
                if (type.equals("float") || type.equals("double"))
                    return "3.14";
            }
        }
        return "\"Sample " + humanize(ref) + "\"";
    }

    /**
     * Override buildMockData to produce proper nested JSON from dot-path fields.
     */
    @SuppressWarnings("unchecked")
    private String buildNestedJson(Map<String, String> flatMap) {
        // Build a tree of maps for nested paths
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            String[] path = entry.getKey().split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < path.length - 1; i++) {
                Object existing = current.get(path[i]);
                if (existing instanceof Map) {
                    current = (Map<String, Object>) existing;
                } else {
                    Map<String, Object> child = new LinkedHashMap<>();
                    current.put(path[i], child);
                    current = child;
                }
            }
            current.put(path[path.length - 1], entry.getValue()); // raw JSON value
        }
        return mapToJson(root);
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append("\"").append(escJsonKey(entry.getKey())).append("\":");
            if (entry.getValue() instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) entry.getValue()));
            } else {
                sb.append(entry.getValue()); // already raw JSON
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escJsonKey(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String humanize(String s) {
        if (s == null || s.isEmpty())
            return "";
        // Take last segment of dot-path
        int dot = s.lastIndexOf('.');
        if (dot >= 0)
            s = s.substring(dot + 1);
        // camelCase to "Camel Case"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c))
                sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    // ── Auto Layout ────────────────────────────────────────────────

    private void autoLayout() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) layoutModel.getRoot();
        if (root.getChildCount() > 0) {
            int choice = JOptionPane.showConfirmDialog(this, "This will replace the current layout. Continue?", "Auto Layout", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION)
                return;
            // Clear existing
            root.removeAllChildren();
            layoutModel.reload();
        }

        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);

        List<DOSchemaField> primitives = new ArrayList<>();
        List<DOSchemaField> embedded = new ArrayList<>();
        List<DOSchemaField> collections = new ArrayList<>();

        for (DOSchemaField field : allFields) {
            if (!field.isExported)
                continue;
            if (field.destinationName == null || field.destinationName.isEmpty())
                continue;

            if (field.isCollection) {
                collections.add(field);
            } else if (field.embedContents && !isPrimitiveType(field.type)) {
                embedded.add(field);
            } else {
                primitives.add(field);
            }
        }

        // 1. Add all primitive fields at top level
        for (DOSchemaField field : primitives) {
            LayoutNode node = new LayoutNode(LayoutNodeType.FIELD);
            node.setProp("ref", field.destinationName);
            applyAutoFormat(node, field);
            root.add(buildTreeNode(node));
        }

        // 2. Add embedded entities as collapsible sections
        for (DOSchemaField field : embedded) {
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", humanize(field.destinationName));
            section.setProp("collapsible", "true");

            DOSchemaClass embeddedClass = findClassByType(field.type);
            if (embeddedClass != null) {
                List<DOSchemaField> subFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, refSchema);
                for (DOSchemaField sf : subFields) {
                    if (!sf.isExported || sf.destinationName == null)
                        continue;
                    if (sf.isCollection) {
                        LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                        table.setProp("ref", field.destinationName + "." + sf.destinationName);
                        addAutoTableColumns(table, sf);
                        section.children.add(table);
                    } else {
                        LayoutNode fn = new LayoutNode(LayoutNodeType.FIELD);
                        fn.setProp("ref", field.destinationName + "." + sf.destinationName);
                        applyAutoFormat(fn, sf);
                        section.children.add(fn);
                    }
                }
            }
            root.add(buildTreeNode(section));
        }

        // 3. Add divider before collections (if we have both primitives/embedded and collections)
        if (!collections.isEmpty() && (!primitives.isEmpty() || !embedded.isEmpty())) {
            root.add(buildTreeNode(new LayoutNode(LayoutNodeType.DIVIDER)));
        }

        // 4. Add collections as tables
        for (DOSchemaField field : collections) {
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", field.destinationName);
            addAutoTableColumns(table, field);
            root.add(buildTreeNode(table));
        }

        layoutModel.reload();
        expandAllNodes(layoutTree, 0, layoutTree.getRowCount());
        updatePreview();
    }

    private void applyAutoFormat(LayoutNode node, DOSchemaField field) {
        if (field.type == null)
            return;
        switch (field.type) {
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
            // Long fields named with date-like names are timestamps
            String name = field.destinationName.toLowerCase();
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance")) {
                node.setProp("format", "longdate:yyyy-MM-dd HH:mm");
            }
            break;
        }
    }

    private void addAutoTableColumns(LayoutNode table, DOSchemaField collectionField) {
        String childType = collectionField.childrenType;
        if (childType == null || isPrimitiveType(childType))
            return;
        DOSchemaClass childClass = findClassByType(childType);
        if (childClass == null)
            return;

        List<DOSchemaField> subFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema);
        List<String> colNames = new ArrayList<>();
        for (DOSchemaField sf : subFields) {
            if (!sf.isExported || sf.destinationName == null)
                continue;
            if (sf.isCollection || (sf.embedContents && !isPrimitiveType(sf.type)))
                continue;
            colNames.add(sf.destinationName);
        }
        if (!colNames.isEmpty()) {
            StringBuilder cols = new StringBuilder();
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0)
                    cols.append(',');
                cols.append(colNames.get(i));
            }
            table.setProp("columns", cols.toString());
        }
    }

    // ── Tree Operations ────────────────────────────────────────────

    private void addNode(LayoutNodeType type, String... propsKV) {
        LayoutNode node = new LayoutNode(type);
        for (int i = 0; i + 1 < propsKV.length; i += 2) {
            node.setProp(propsKV[i], propsKV[i + 1]);
        }
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
        // Adjust last to make 100%
        sizes[count - 1] = String.valueOf(100 - each * (count - 1));
        cols.setProp("sizes", String.join(",", sizes));

        for (int i = 0; i < count; i++) {
            cols.children.add(new LayoutNode(LayoutNodeType.COLUMN));
        }
        insertNodeIntoTree(cols);
    }

    private void addTableNode() {
        // Pick a collection field from palette
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
        // Start with 2 tabs
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
        // Must be inside a TABBED_SECTION
        if (parentObj == null || parentObj.type != LayoutNodeType.TABBED_SECTION) {
            // Try parent
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
                // Add as sibling after selected
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
                if (parent == null)
                    parent = root;
                LayoutNode parentLayoutNode = getLayoutNodeFromTreeNode(parent);
                int idx = parent.getIndex(selectedLayoutNode) + 1;
                if (parentLayoutNode != null) {
                    parentLayoutNode.children.add(Math.min(idx, parentLayoutNode.children.size()), node);
                }
                layoutModel.insertNodeInto(treeNode, parent, idx);
            }
        } else {
            // Add to root - store in a virtual root layout
            layoutModel.insertNodeInto(treeNode, root, root.getChildCount());
        }

        layoutTree.setSelectionPath(new TreePath(treeNode.getPath()));
        updatePreview();
    }

    private boolean canContainChild(LayoutNodeType parent, LayoutNodeType child) {
        switch (parent) {
        case SECTION:
        case COLUMN:
        case TAB:
            return true; // Can contain anything
        case COLUMNS:
            return child == LayoutNodeType.COLUMN;
        case TABBED_SECTION:
            return child == LayoutNodeType.TAB;
        default:
            return false;
        }
    }

    private void moveNode(int direction) {
        if (selectedLayoutNode == null)
            return;
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
        if (parent == null)
            return;

        int idx = parent.getIndex(selectedLayoutNode);
        int newIdx = idx + direction;
        if (newIdx < 0 || newIdx >= parent.getChildCount())
            return;

        LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
        if (parentObj != null && idx < parentObj.children.size() && newIdx < parentObj.children.size()) {
            Collections.swap(parentObj.children, idx, newIdx);
        }

        DefaultMutableTreeNode movedNode = selectedLayoutNode;
        layoutModel.removeNodeFromParent(movedNode);
        layoutModel.insertNodeInto(movedNode, parent, newIdx);
        layoutTree.setSelectionPath(new TreePath(movedNode.getPath()));
        updatePreview();
    }

    private void outdentNode() {
        if (selectedLayoutNode == null)
            return;
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
        if (parent == null)
            return;
        DefaultMutableTreeNode grandparent = (DefaultMutableTreeNode) parent.getParent();
        if (grandparent == null)
            return; // already at top level

        // Remove from current parent's model children
        LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
        LayoutNode nodeObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
        if (parentObj != null && nodeObj != null) {
            parentObj.children.remove(nodeObj);
        }

        // Insert after the parent in grandparent
        int parentIdx = grandparent.getIndex(parent);
        LayoutNode grandparentObj = getLayoutNodeFromTreeNode(grandparent);
        if (grandparentObj != null) {
            grandparentObj.children.add(Math.min(parentIdx + 1, grandparentObj.children.size()), nodeObj);
        }

        DefaultMutableTreeNode movedNode = selectedLayoutNode;
        layoutModel.removeNodeFromParent(movedNode);
        layoutModel.insertNodeInto(movedNode, grandparent, parentIdx + 1);
        layoutTree.setSelectionPath(new TreePath(movedNode.getPath()));
        updatePreview();
    }

    private void indentNode() {
        if (selectedLayoutNode == null)
            return;
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
        if (parent == null)
            return;

        int idx = parent.getIndex(selectedLayoutNode);
        if (idx <= 0)
            return; // no previous sibling to indent into

        DefaultMutableTreeNode prevSibling = (DefaultMutableTreeNode) parent.getChildAt(idx - 1);
        LayoutNode prevObj = getLayoutNodeFromTreeNode(prevSibling);
        if (prevObj == null || !canContainChild(prevObj.type, getLayoutNodeFromTreeNode(selectedLayoutNode).type))
            return;

        // Remove from current parent's model children
        LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
        LayoutNode nodeObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
        if (parentObj != null && nodeObj != null) {
            parentObj.children.remove(nodeObj);
        }
        prevObj.children.add(nodeObj);

        DefaultMutableTreeNode movedNode = selectedLayoutNode;
        layoutModel.removeNodeFromParent(movedNode);
        layoutModel.insertNodeInto(movedNode, prevSibling, prevSibling.getChildCount());
        layoutTree.expandPath(new TreePath(prevSibling.getPath()));
        layoutTree.setSelectionPath(new TreePath(movedNode.getPath()));
        updatePreview();
    }

    private void deleteNode() {
        if (selectedLayoutNode == null)
            return;
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedLayoutNode.getParent();
        if (parent == null)
            return;

        LayoutNode parentObj = getLayoutNodeFromTreeNode(parent);
        LayoutNode nodeObj = getLayoutNodeFromTreeNode(selectedLayoutNode);
        if (parentObj != null && nodeObj != null) {
            parentObj.children.remove(nodeObj);
        }

        layoutModel.removeNodeFromParent(selectedLayoutNode);
        selectedLayoutNode = null;
        showEmptyProperties();
        updatePreview();
    }

    private DefaultMutableTreeNode buildTreeNode(LayoutNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (LayoutNode child : node.children) {
            treeNode.add(buildTreeNode(child));
        }
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
        for (LayoutNode node : layout.nodes) {
            root.add(buildTreeNode(node));
        }
        layoutModel.reload();
        expandAllNodes(layoutTree, 0, layoutTree.getRowCount());
        updatePreview();
    }

    private void expandAllNodes(JTree tree, int startRow, int rowCount) {
        for (int i = startRow; i < rowCount; i++) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
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

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(FieldPaletteItem.class, "FieldPaletteItem");

    private class FieldPaletteDragHandler extends TransferHandler {
        @Override
        protected Transferable createTransferable(JComponent c) {
            TreePath path = fieldPalette.getSelectionPath();
            if (path == null)
                return null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!(node.getUserObject() instanceof FieldPaletteItem))
                return null;
            FieldPaletteItem item = (FieldPaletteItem) node.getUserObject();
            return new FieldTransferable(item);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    private class LayoutTreeDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(FIELD_FLAVOR) || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                if (support.isDataFlavorSupported(FIELD_FLAVOR)) {
                    FieldPaletteItem item = (FieldPaletteItem) support.getTransferable().getTransferData(FIELD_FLAVOR);
                    LayoutNode node;
                    if (item.isCollection) {
                        node = new LayoutNode(LayoutNodeType.TABLE);
                        node.setProp("ref", item.dotPath);
                    } else {
                        node = new LayoutNode(LayoutNodeType.FIELD);
                        node.setProp("ref", item.dotPath);
                    }
                    insertNodeIntoTree(node);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
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
