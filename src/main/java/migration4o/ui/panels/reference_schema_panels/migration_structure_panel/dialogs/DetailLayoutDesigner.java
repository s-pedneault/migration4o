package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import java.awt.*;
import java.awt.datatransfer.*;
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
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.*;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.popups.*;
import migration4o.util.DatabaseUtil;

/**
 * WYSIWYG designer for record detail view layouts.
 * Left panel: field palette. Center: visual canvas with drag-and-drop blocks.
 */
public class DetailLayoutDesigner extends JFrame implements LayoutCanvas.PropertyEditorCallback {

    private final ClassExportConfig config;
    private final DOSchemaClass schemaClass;
    private final DOSchema refSchema;

    private JTree fieldPalette;
    private DefaultTreeModel fieldPaletteModel;
    private LayoutCanvas canvas;

    // DnD flavors — javaJVMLocalObjectMimeType prevents AWT from serializing the transfer data
    public static final DataFlavor FIELD_FLAVOR;
    static {
        try {
            FIELD_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + FieldPaletteItem.class.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Static DnD ref — macOS native DnD routes through the system pasteboard even for
    // intra-JVM drags, causing serialization.  Set before startDrag, read in drop handler.
    public static FieldPaletteItem draggedFieldItem;

    public DetailLayoutDesigner(ClassExportConfig config, DOSchemaClass schemaClass, DOSchema refSchema) {
        super("Detail Layout Designer \u2014 " + schemaClass.attributes.destinationName);
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

        canvas = new LayoutCanvas(schemaClass, refSchema);
        canvas.setEditorCallback(this);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFieldPalette(), canvas);
        split.setDividerLocation(250);

        add(split, BorderLayout.CENTER);
    }

    // ── Toolbar ────────────────────────────────────────────────────

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        bar.add(makeBtn("+ Section", e -> addSection()));
        bar.add(makeBtn("+ Columns", e -> addColumns()));
        bar.add(makeBtn("+ Divider", e -> addDivider()));
        bar.add(makeBtn("+ Table", e -> addTable()));
        bar.add(makeBtn("+ Tabs", e -> addTabbedSection()));
        bar.addSeparator();
        bar.add(makeBtn("Auto Layout", e -> autoLayout()));
        bar.addSeparator();
        bar.add(makeBtn("\u2717 Delete", e -> canvas.deleteSelected()));
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

    // ── Toolbar Actions ────────────────────────────────────────────

    private void addSection() {
        LayoutNode node = new LayoutNode(LayoutNodeType.SECTION);
        node.setProp("title", "Section");
        addBlockToCanvas(node);
    }

    private void addColumns() {
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
        addBlockToCanvas(cols);
    }

    private void addDivider() {
        addBlockToCanvas(new LayoutNode(LayoutNodeType.DIVIDER));
    }

    private void addTable() {
        TreePath path = fieldPalette.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode palNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (palNode.getUserObject() instanceof FieldPaletteItem) {
                FieldPaletteItem item = (FieldPaletteItem) palNode.getUserObject();
                if (item.isCollection) {
                    LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                    table.setProp("ref", item.dotPath);
                    addBlockToCanvas(table);
                    return;
                }
            }
        }
        String ref = JOptionPane.showInputDialog(this, "Collection field source name:");
        if (ref != null && !ref.trim().isEmpty()) {
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", ref.trim());
            addBlockToCanvas(table);
        }
    }

    private void addTabbedSection() {
        LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
        tabs.setProp("title", "Tabs");
        LayoutNode tab1 = new LayoutNode(LayoutNodeType.TAB);
        tab1.setProp("title", "Tab 1");
        LayoutNode tab2 = new LayoutNode(LayoutNodeType.TAB);
        tab2.setProp("title", "Tab 2");
        tabs.children.add(tab1);
        tabs.children.add(tab2);
        addBlockToCanvas(tabs);
    }

    private void addBlockToCanvas(LayoutNode node) {
        LayoutBlockPanel block = canvas.createBlock(node);
        canvas.addBlockToContent(block);
        canvas.selectBlock(block);
        canvas.revalidate();
        canvas.repaint();
        refreshFieldPalette();
    }

    // ── PropertyEditorCallback ─────────────────────────────────────

    @Override
    public void editSectionProperties(LayoutBlockPanel block) {
        if (SectionPropertiesPopup.show(this, block)) {
            block.refreshFromNode();
            refreshFieldPalette();
        }
    }

    @Override
    public void editFieldProperties(FieldBlock block) {
        String ref = block.getLayoutNode().prop("ref", "");
        DOSchemaField field = resolveFieldByRef(ref);
        String fieldType = (field != null && field.attributes.type != null) ? field.attributes.type : "string";
        if (FieldPropertiesPopup.show(this, block, fieldType)) {
            block.refreshFromNode();
        }
    }

    @Override
    public void editTableProperties(TableBlock block) {
        if (TablePropertiesPopup.show(this, block, schemaClass, refSchema)) {
            block.refreshFromNode();
            refreshFieldPalette();
        }
    }

    @Override
    public void editTabProperties(TabbedSectionBlock.TabPanel tabPanel) {
        String title = JOptionPane.showInputDialog(this, "Tab title:", tabPanel.getTabNode().prop("title", ""));
        if (title != null) {
            tabPanel.getTabNode().setProp("title", title.trim());
            // Update the JTabbedPane tab header via the parent TabbedSectionBlock
            Container parent = tabPanel.getParent();
            while (parent != null) {
                if (parent instanceof TabbedSectionBlock) {
                    ((TabbedSectionBlock) parent).refreshFromNode();
                    break;
                }
                parent = parent.getParent();
            }
        }
    }

    @Override
    public void openEmbeddedLayoutDesigner(String className) {
        DOSchemaClass embeddedClass = refSchema.findClassByName(className);
        if (embeddedClass == null) {
            JOptionPane.showMessageDialog(this, "Class not found in schema: " + className, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ClassExportConfig tempConfig = new ClassExportConfig(className, null, null, null, null);
        DetailLayoutDesigner designer = new DetailLayoutDesigner(tempConfig, embeddedClass, refSchema);
        designer.setVisible(true);
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

        fieldPalette.setCellRenderer(new FieldPaletteRenderer());
        expandAllNodes(fieldPalette, 0, fieldPalette.getRowCount());

        JScrollPane sp = new JScrollPane(fieldPalette);
        sp.setBorder(BorderFactory.createTitledBorder("Available Fields"));
        sp.setPreferredSize(new Dimension(250, 400));
        return sp;
    }

    private void populateFieldPalette(DefaultMutableTreeNode root) {
        Set<String> usedRefs = collectUsedFieldRefs();
        LinkedHashMap<String, List<DOSchemaField>> groups = buildFieldGroups();

        // Build tree nodes per group
        for (String[] entry : GROUP_ORDER) {
            String groupKey = entry[0];
            List<DOSchemaField> fields = groups.get(groupKey);
            if (fields == null || fields.isEmpty())
                continue;

            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupTitle(groupKey));
            for (DOSchemaField field : fields) {
                String src = field.attributes.source;
                if (usedRefs.contains(src))
                    continue;

                String ft = classifyFieldType(field);
                if (field.attributes.isCollection) {
                    DefaultMutableTreeNode collNode = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), src, true, ft));
                    if (field.attributes.embedContents)
                        addEmbeddedSubFields(collNode, field.attributes.childrenType, src, usedRefs);
                    groupNode.add(collNode);
                } else if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type)) {
                    DefaultMutableTreeNode embNode = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), src, false, ft));
                    addEmbeddedSubFields(embNode, field.attributes.type, src, usedRefs);
                    groupNode.add(embNode);
                } else {
                    groupNode.add(new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(field), src, false, ft)));
                }
            }

            if (groupNode.getChildCount() > 0)
                root.add(groupNode);
        }
    }

    private String classifyFieldType(DOSchemaField field) {
        if (field.attributes.isCollection)
            return "collection";
        if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type))
            return "embedded";
        if (isIDEntiteType(field))
            return "reference";
        String type = field.attributes.type;
        if (type == null)
            return "string";
        switch (type) {
        case "boolean":
        case "java.lang.Boolean":
            return "boolean";
        case "date":
        case "java.util.Date":
        case "java.sql.Timestamp":
            return "date";
        case "int":
        case "java.lang.Integer":
        case "short":
        case "java.lang.Short":
        case "byte":
        case "java.lang.Byte":
            return "int";
        case "long":
        case "java.lang.Long": {
            String name = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance"))
                return "date";
            return "long";
        }
        case "float":
        case "java.lang.Float":
        case "double":
        case "java.lang.Double":
            return "decimal";
        default:
            return "string";
        }
    }

    private Set<String> collectUsedFieldRefs() {
        if (canvas != null)
            return canvas.collectUsedFieldRefs();
        return new HashSet<>();
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
            if (!sf.attributes.isExported || sf.attributes.source == null)
                continue;
            String dotPath = parentPath + "." + sf.attributes.source;
            if (usedRefs.contains(dotPath))
                continue;
            String ft = classifyFieldType(sf);
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new FieldPaletteItem(getFieldLabel(sf), dotPath, sf.attributes.isCollection, ft));
            if (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type) && !sf.attributes.isCollection) {
                addEmbeddedSubFields(child, sf.attributes.type, dotPath, usedRefs);
            }
            parentNode.add(child);
        }
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
            addBlockToCanvas(table);
        } else {
            LayoutNode field = new LayoutNode(LayoutNodeType.FIELD);
            field.setProp("ref", item.dotPath);
            addBlockToCanvas(field);
        }
    }

    // ── Field Resolution & Helpers ─────────────────────────────────

    private DOSchemaField resolveFieldByRef(String ref) {
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
        return typeClass.isIDEntite();
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

    // ── Tree Utilities ─────────────────────────────────────────────

    private void expandAllNodes(JTree tree, int startRow, int rowCount) {
        for (int i = startRow; i < rowCount; i++)
            tree.expandRow(i);
        if (tree.getRowCount() != rowCount)
            expandAllNodes(tree, rowCount, tree.getRowCount());
    }

    // ── Load / Save ────────────────────────────────────────────────

    private void loadExistingLayout() {
        String className = config.getClassName();
        DetailLayout layout = (className != null) ? DOModuleService.getInstance().getClassLayout(className) : null;
        if (layout != null) {
            canvas.loadLayout(layout);
            refreshFieldPalette();
        }
    }

    private void save() {
        DetailLayout layout = canvas.buildLayout();
        String className = config.getClassName();

        try {
            DOModuleService svc = DOModuleService.getInstance();
            svc.setClassLayout(className, layout.isEmpty() ? null : layout);
            svc.saveClassLayouts();
            JOptionPane.showMessageDialog(this, "Layout saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Auto Layout ────────────────────────────────────────────────

    // Group display order and French titles for auto-layout sections
    private static final String[][] GROUP_ORDER = { { "identity", null }, { "text", "Texte" }, { "status", "\u00c9tat" }, { "dates", "Dates" }, { "contact", "Contact" }, { "address", "Adresse" }, { "reference", "R\u00e9f\u00e9rences" }, { "embedded", null }, { "attachment", "Pi\u00e8ces jointes" }, { "collections", null }, { "other", "Autres champs" }, };

    /** Bucket all exported fields (with a source name) by group, in GROUP_ORDER order. */
    private LinkedHashMap<String, List<DOSchemaField>> buildFieldGroups() {
        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);
        LinkedHashMap<String, List<DOSchemaField>> groups = new LinkedHashMap<>();
        for (String[] entry : GROUP_ORDER)
            groups.put(entry[0], new ArrayList<>());
        for (DOSchemaField field : allFields) {
            if (!field.attributes.isExported)
                continue;
            if (field.attributes.source == null || field.attributes.source.isEmpty())
                continue;
            // Skip embedded fields whose type class is not exported (e.g. java.util.UUID)
            if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type)) {
                DOSchemaClass typeClass = findClassByType(field.attributes.type);
                if (typeClass != null && !typeClass.attributes.migrate)
                    continue;
            }
            String group = inferGroup(field);
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(field);
        }
        return groups;
    }

    private String inferGroup(DOSchemaField field) {
        if (field.attributes.group != null && !field.attributes.group.isEmpty())
            return field.attributes.group;
        if (field.attributes.isCollection)
            return "collections";
        if (field.attributes.embedContents && !isPrimitiveType(field.attributes.type))
            return "embedded";
        if (field.attributes.type != null) {
            switch (field.attributes.type) {
            case "date":
            case "java.util.Date":
            case "java.sql.Timestamp":
                return "dates";
            case "boolean":
            case "java.lang.Boolean":
                return "status";
            }
            if (isIDEntiteType(field))
                return "reference";
        }
        if ("long".equals(field.attributes.type) || "java.lang.Long".equals(field.attributes.type)) {
            String name = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance"))
                return "dates";
        }
        return "other";
    }

    private void autoLayout() {
        DetailLayout existing = canvas.buildLayout();
        if (!existing.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this, "This will replace the current layout. Continue?", "Auto Layout", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION)
                return;
        }

        LinkedHashMap<String, List<DOSchemaField>> groups = buildFieldGroups();

        DetailLayout layout = new DetailLayout();

        // ── Phase 1: Identity fields — 2-column if ≥ 2 simple fields ──
        List<DOSchemaField> identityFields = groups.getOrDefault("identity", Collections.emptyList());
        if (!identityFields.isEmpty()) {
            if (identityFields.size() >= 2) {
                layout.nodes.add(makeColumnsFromFields(identityFields, null));
            } else {
                layout.nodes.add(makeFieldNode(identityFields.get(0)));
            }
        }

        // ── Phase 2: Middle groups — pair small groups, use columns for compactness ──
        // Groups: text, status, dates, contact, address, reference
        String[] middleGroups = { "text", "status", "dates", "contact", "address", "reference" };
        // Collect non-empty middle groups in order
        List<String[]> pendingSections = new ArrayList<>(); // [groupKey, title, ...]
        for (String gk : middleGroups) {
            List<DOSchemaField> fields = groups.get(gk);
            if (fields == null || fields.isEmpty())
                continue;
            // Separate flat fields from embedded sub-objects
            List<DOSchemaField> flatFields = new ArrayList<>();
            List<DOSchemaField> embeddedFields = new ArrayList<>();
            for (DOSchemaField f : fields) {
                if (f.attributes.embedContents && !isPrimitiveType(f.attributes.type))
                    embeddedFields.add(f);
                else
                    flatFields.add(f);
            }
            String title = groupTitle(gk);
            if (!flatFields.isEmpty()) {
                pendingSections.add(new String[] { gk, title, "flat" });
            }
            if (!embeddedFields.isEmpty()) {
                pendingSections.add(new String[] { gk, title, "embedded" });
            }
        }

        // Try to pair adjacent small flat groups side-by-side in columns
        int i = 0;
        while (i < pendingSections.size()) {
            String[] entry = pendingSections.get(i);
            String gk = entry[0];
            String title = entry[1];
            boolean isEmbedded = "embedded".equals(entry[2]);
            List<DOSchemaField> fields = groups.get(gk);
            List<DOSchemaField> flatFields = new ArrayList<>();
            List<DOSchemaField> embeddedFields = new ArrayList<>();
            for (DOSchemaField f : fields) {
                if (f.attributes.embedContents && !isPrimitiveType(f.attributes.type))
                    embeddedFields.add(f);
                else
                    flatFields.add(f);
            }

            if (isEmbedded) {
                for (DOSchemaField f : embeddedFields) {
                    layout.nodes.add(makeEmbeddedSection(f));
                }
                i++;
                continue;
            }

            // Groups ideal for 2-column layout (short values)
            boolean compactGroup = "dates".equals(gk) || "status".equals(gk) || "reference".equals(gk);

            // Check if we can pair this group with the next flat group
            if (flatFields.size() <= 4 && i + 1 < pendingSections.size()) {
                String[] nextEntry = pendingSections.get(i + 1);
                if ("flat".equals(nextEntry[2])) {
                    String nextGk = nextEntry[0];
                    List<DOSchemaField> nextFlat = new ArrayList<>();
                    for (DOSchemaField f : groups.get(nextGk)) {
                        if (!(f.attributes.embedContents && !isPrimitiveType(f.attributes.type)))
                            nextFlat.add(f);
                    }
                    if (nextFlat.size() <= 4) {
                        // Pair them as two columns, each with a titled section inside
                        layout.nodes.add(makeTwoGroupColumns(title, flatFields, groupTitle(nextGk), nextFlat));
                        i += 2;
                        continue;
                    }
                }
            }

            // Single group — use 2-column internal layout for compact types
            if (compactGroup && flatFields.size() >= 2) {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", title);
                section.setProp("collapsible", "true");
                section.children.add(makeColumnsFromFields(flatFields, null));
                layout.nodes.add(section);
            } else if (flatFields.size() == 1) {
                layout.nodes.add(makeFieldNode(flatFields.get(0)));
            } else {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", title);
                section.setProp("collapsible", "true");
                for (DOSchemaField f : flatFields) {
                    section.children.add(makeFieldNode(f));
                }
                layout.nodes.add(section);
            }
            i++;
        }

        // ── Phase 3: Embedded objects — tabs when multiple, section when single ──
        List<DOSchemaField> embeddedGroup = groups.getOrDefault("embedded", Collections.emptyList());
        if (embeddedGroup.size() >= 2) {
            int tabLimit = Math.min(embeddedGroup.size(), 5);
            LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
            for (int j = 0; j < tabLimit; j++) {
                DOSchemaField f = embeddedGroup.get(j);
                LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
                tab.setProp("title", getFieldLabel(f));
                populateEmbedded(tab, f);
                tabs.children.add(tab);
            }
            layout.nodes.add(tabs);
            // Overflow → individual collapsible sections
            for (int j = tabLimit; j < embeddedGroup.size(); j++) {
                layout.nodes.add(makeEmbeddedSection(embeddedGroup.get(j)));
            }
        } else if (embeddedGroup.size() == 1) {
            layout.nodes.add(makeEmbeddedSection(embeddedGroup.get(0)));
        }

        // ── Phase 4: Attachments ──
        List<DOSchemaField> attachments = groups.getOrDefault("attachment", Collections.emptyList());
        if (!attachments.isEmpty()) {
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", "Pièces jointes");
            section.setProp("collapsible", "true");
            for (DOSchemaField f : attachments) {
                section.children.add(makeFieldNode(f));
            }
            layout.nodes.add(section);
        }

        // ── Phase 5: Collections — tabs when multiple, section+table when single ──
        List<DOSchemaField> collections = groups.getOrDefault("collections", Collections.emptyList());
        if (collections.size() >= 2) {
            if (!layout.nodes.isEmpty())
                layout.nodes.add(new LayoutNode(LayoutNodeType.DIVIDER));
            int tabLimit = Math.min(collections.size(), 5);
            LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
            for (int j = 0; j < tabLimit; j++) {
                DOSchemaField f = collections.get(j);
                LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
                tab.setProp("title", getFieldLabel(f));
                LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                table.setProp("ref", f.attributes.source);
                addAutoTableColumns(table, f);
                tab.children.add(table);
                tabs.children.add(tab);
            }
            layout.nodes.add(tabs);
            // Overflow → individual collapsible sections
            for (int j = tabLimit; j < collections.size(); j++) {
                DOSchemaField f = collections.get(j);
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", getFieldLabel(f));
                section.setProp("collapsible", "true");
                LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                table.setProp("ref", f.attributes.source);
                addAutoTableColumns(table, f);
                section.children.add(table);
                layout.nodes.add(section);
            }
        } else if (collections.size() == 1) {
            if (!layout.nodes.isEmpty())
                layout.nodes.add(new LayoutNode(LayoutNodeType.DIVIDER));
            DOSchemaField f = collections.get(0);
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", getFieldLabel(f));
            section.setProp("collapsible", "true");
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", f.attributes.source);
            addAutoTableColumns(table, f);
            section.children.add(table);
            layout.nodes.add(section);
        }

        // ── Phase 6: Other (uncategorized) ──
        List<DOSchemaField> otherFields = groups.getOrDefault("other", Collections.emptyList());
        if (!otherFields.isEmpty()) {
            List<DOSchemaField> otherFlat = new ArrayList<>();
            List<DOSchemaField> otherEmbedded = new ArrayList<>();
            for (DOSchemaField f : otherFields) {
                if (f.attributes.embedContents && !isPrimitiveType(f.attributes.type))
                    otherEmbedded.add(f);
                else
                    otherFlat.add(f);
            }
            if (!otherFlat.isEmpty()) {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", "Autres champs");
                section.setProp("collapsible", "true");
                if (otherFlat.size() >= 4) {
                    section.children.add(makeColumnsFromFields(otherFlat, null));
                } else {
                    for (DOSchemaField f : otherFlat)
                        section.children.add(makeFieldNode(f));
                }
                layout.nodes.add(section);
            }
            for (DOSchemaField f : otherEmbedded) {
                layout.nodes.add(makeEmbeddedSection(f));
            }
        }

        canvas.loadLayout(layout);
        refreshFieldPalette();
    }

    // ── Auto-layout helper: field node ──

    private LayoutNode makeFieldNode(DOSchemaField field) {
        LayoutNode node = new LayoutNode(LayoutNodeType.FIELD);
        node.setProp("ref", field.attributes.source);
        applyAutoFormat(node, field);
        return node;
    }

    // ── Auto-layout helper: 2-column from a flat list of fields ──

    private LayoutNode makeColumnsFromFields(List<DOSchemaField> fields, String sectionTitle) {
        LayoutNode cols = new LayoutNode(LayoutNodeType.COLUMNS);
        cols.setProp("count", "2");
        cols.setProp("sizes", "50,50");
        LayoutNode left = new LayoutNode(LayoutNodeType.COLUMN);
        LayoutNode right = new LayoutNode(LayoutNodeType.COLUMN);
        int half = (fields.size() + 1) / 2;
        for (int j = 0; j < fields.size(); j++) {
            LayoutNode fn = makeFieldNode(fields.get(j));
            (j < half ? left : right).children.add(fn);
        }
        cols.children.add(left);
        cols.children.add(right);
        return cols;
    }

    // ── Auto-layout helper: pair two groups side-by-side ──

    private LayoutNode makeTwoGroupColumns(String titleA, List<DOSchemaField> fieldsA, String titleB, List<DOSchemaField> fieldsB) {
        LayoutNode cols = new LayoutNode(LayoutNodeType.COLUMNS);
        cols.setProp("count", "2");
        cols.setProp("sizes", "50,50");
        LayoutNode colA = new LayoutNode(LayoutNodeType.COLUMN);
        LayoutNode colB = new LayoutNode(LayoutNodeType.COLUMN);
        // Each column gets a titled section
        LayoutNode secA = new LayoutNode(LayoutNodeType.SECTION);
        secA.setProp("title", titleA);
        for (DOSchemaField f : fieldsA)
            secA.children.add(makeFieldNode(f));
        colA.children.add(secA);
        LayoutNode secB = new LayoutNode(LayoutNodeType.SECTION);
        secB.setProp("title", titleB);
        for (DOSchemaField f : fieldsB)
            secB.children.add(makeFieldNode(f));
        colB.children.add(secB);
        cols.children.add(colA);
        cols.children.add(colB);
        return cols;
    }

    // ── Auto-layout helper: embedded object section ──

    private LayoutNode makeEmbeddedSection(DOSchemaField field) {
        LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
        section.setProp("title", getFieldLabel(field));
        section.setProp("collapsible", "true");
        populateEmbedded(section, field);
        return section;
    }

    private void populateEmbedded(LayoutNode parent, DOSchemaField field) {
        DOSchemaClass embeddedClass = findClassByType(field.attributes.type);
        if (embeddedClass != null) {
            parent.setProp("layoutRef", embeddedClass.attributes.source);
            parent.setProp("ref", field.attributes.source);
        }
    }

    private String groupTitle(String groupKey) {
        for (String[] entry : GROUP_ORDER) {
            if (entry[0].equals(groupKey))
                return entry[1] != null ? entry[1] : humanize(groupKey);
        }
        return humanize(groupKey);
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
            String name = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";
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
            if (!sf.attributes.isExported || sf.attributes.source == null)
                continue;
            if (sf.attributes.isCollection || (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type)))
                continue;
            colNames.add(sf.attributes.source);
            colTitles.add(sf.attributes.title != null ? sf.attributes.title : "");
        }
        if (!colNames.isEmpty()) {
            table.setProp("columns", String.join(",", colNames));
            if (colTitles.stream().anyMatch(t -> t != null && !t.isBlank())) {
                table.setProp("columnTitles", String.join(",", colTitles));
            }
        }
    }

    // ── DnD Handler ────────────────────────────────────────────────

    // ── Field Palette Renderer ─────────────────────────────────────

    private static class FieldPaletteRenderer extends DefaultTreeCellRenderer {
        // Type icons — small painted icons indicating data type
        private static final Map<String, Icon> TYPE_ICONS = new HashMap<>();
        private static final Icon GROUP_ICON;

        static {
            TYPE_ICONS.put("string", makeTextIcon());
            TYPE_ICONS.put("int", makeNumberIcon());
            TYPE_ICONS.put("long", makeNumberIcon());
            TYPE_ICONS.put("decimal", makeNumberIcon());
            TYPE_ICONS.put("boolean", makeCheckIcon());
            TYPE_ICONS.put("date", makeCalendarIcon());
            TYPE_ICONS.put("collection", makeListIcon());
            TYPE_ICONS.put("embedded", makeBoxIcon());
            TYPE_ICONS.put("reference", makeLinkIcon());
            GROUP_ICON = makeFolderIcon();
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object obj = node.getUserObject();
            if (obj instanceof FieldPaletteItem) {
                FieldPaletteItem item = (FieldPaletteItem) obj;
                Icon icon = TYPE_ICONS.getOrDefault(item.fieldType, TYPE_ICONS.get("string"));
                setIcon(icon);
            } else {
                // Group header node
                setIcon(GROUP_ICON);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }

        // ── Icon Painters ──────────────────────────────────────────

        private static Icon makeTextIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(90, 90, 90));
                g.drawRoundRect(1, 2, w - 3, h - 5, 3, 3);
                g.setColor(new Color(70, 130, 180));
                g.drawLine(4, 6, w - 5, 6);
                g.drawLine(4, 9, w - 8, 9);
            });
        }

        private static Icon makeNumberIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
                g.setColor(new Color(180, 100, 40));
                g.drawString("#", 2, 12);
            });
        }

        private static Icon makeCheckIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(90, 90, 90));
                g.drawRoundRect(1, 2, 10, 10, 2, 2);
                g.setColor(new Color(50, 160, 50));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(3, 7, 5, 10);
                g.drawLine(5, 10, 10, 3);
            });
        }

        private static Icon makeCalendarIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(200, 60, 60));
                g.fillRect(1, 2, 12, 3);
                g.setColor(new Color(90, 90, 90));
                g.drawRect(1, 2, 12, 10);
                g.setColor(new Color(60, 60, 60));
                for (int r = 0; r < 2; r++)
                    for (int c = 0; c < 3; c++)
                        g.fillRect(3 + c * 4, 6 + r * 3, 2, 2);
            });
        }

        private static Icon makeListIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(100, 60, 160));
                for (int i = 0; i < 3; i++) {
                    int y = 3 + i * 4;
                    g.fillRect(2, y, 2, 2);
                    g.drawLine(6, y + 1, 12, y + 1);
                }
            });
        }

        private static Icon makeBoxIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(70, 130, 180));
                g.drawRoundRect(1, 1, 11, 11, 3, 3);
                g.setColor(new Color(140, 190, 220));
                g.fillRoundRect(2, 2, 10, 10, 3, 3);
                g.setColor(new Color(40, 80, 140));
                g.drawRoundRect(1, 1, 11, 11, 3, 3);
            });
        }

        private static Icon makeLinkIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(60, 140, 60));
                g.setStroke(new BasicStroke(1.5f));
                g.drawArc(1, 3, 7, 8, 90, 180);
                g.drawArc(5, 3, 7, 8, -90, 180);
            });
        }

        private static Icon makeFolderIcon() {
            return new PaintedIcon(14, 14, (g, w, h) -> {
                g.setColor(new Color(200, 180, 100));
                g.fillRoundRect(0, 3, 13, 9, 2, 2);
                g.setColor(new Color(180, 160, 80));
                g.fillRoundRect(0, 1, 6, 4, 2, 2);
                g.setColor(new Color(140, 120, 60));
                g.drawRoundRect(0, 3, 12, 8, 2, 2);
            });
        }

        // Small icon painted via Graphics2D
        private static class PaintedIcon implements Icon {
            private final int w, h;
            private final IconPainter painter;

            PaintedIcon(int w, int h, IconPainter painter) {
                this.w = w;
                this.h = h;
                this.painter = painter;
            }

            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.paint(g, w, h);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        }

        @FunctionalInterface
        private interface IconPainter {
            void paint(Graphics2D g, int w, int h);
        }
    }

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
            draggedFieldItem = item;
            return new FieldTransferable(item);
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            draggedFieldItem = null;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    // ── Helper Classes ─────────────────────────────────────────────

    public static class FieldPaletteItem implements java.io.Serializable {
        public final String displayName;
        public final String dotPath;
        public final boolean isCollection;
        public final String fieldType; // e.g. "string", "int", "date", "boolean", "embedded", "collection", "reference"

        FieldPaletteItem(String displayName, String dotPath, boolean isCollection, String fieldType) {
            this.displayName = displayName;
            this.dotPath = dotPath;
            this.isCollection = isCollection;
            this.fieldType = fieldType;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static class FieldTransferable implements Transferable {
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
}
