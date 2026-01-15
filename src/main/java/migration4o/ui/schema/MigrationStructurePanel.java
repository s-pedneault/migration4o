package migration4o.ui.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.MigrationFormatReader;
import migration4o.schema.MigrationFormatWriter;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Panel for organizing classes into a migration structure with modules.
 * Left pane shows available classes, right pane shows export structure with
 * modules.
 */
public class MigrationStructurePanel extends JPanel {
    private DOSchema schema;
    private JTree availableTree;
    private JTree exportTree;
    private DefaultTreeModel availableModel;
    private DefaultTreeModel exportModel;
    private DefaultMutableTreeNode availableRoot;
    private DefaultMutableTreeNode exportedRoot;

    // Available tree nodes
    private DefaultMutableTreeNode availableEntitiesNode;
    private DefaultMutableTreeNode availableParamsNode;
    private DefaultMutableTreeNode availableOthersNode;

    // Exported tree nodes
    private DefaultMutableTreeNode exportedEntitiesNode;
    private DefaultMutableTreeNode exportedParamsNode;
    private DefaultMutableTreeNode exportedOthersNode;

    // Track which classes are exported
    private Set<String> exportedClasses = new HashSet<>();

    private static final String MIGRATION_FORMAT_FILE = "schema/migration-format.xml";

    public MigrationStructurePanel(DOSchema schema) {
        this.schema = schema;
        initializeUI();
        loadMigrationStructure();
        populateAvailableTree();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // Add info panel at top
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Organize classes into migration modules");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoPanel.add(infoLabel);
        add(infoPanel, BorderLayout.NORTH);

        // Create left tree structure (Available and Exported)
        DefaultMutableTreeNode leftRoot = new DefaultMutableTreeNode("Classes");

        // Available branch
        availableRoot = new DefaultMutableTreeNode("Available");
        availableEntitiesNode = new DefaultMutableTreeNode("Entities");
        availableParamsNode = new DefaultMutableTreeNode("Params");
        availableOthersNode = new DefaultMutableTreeNode("Others");
        availableRoot.add(availableEntitiesNode);
        availableRoot.add(availableParamsNode);
        availableRoot.add(availableOthersNode);

        // Exported branch
        exportedRoot = new DefaultMutableTreeNode("Exported");
        exportedEntitiesNode = new DefaultMutableTreeNode("Entities");
        exportedParamsNode = new DefaultMutableTreeNode("Params");
        exportedOthersNode = new DefaultMutableTreeNode("Others");
        exportedRoot.add(exportedEntitiesNode);
        exportedRoot.add(exportedParamsNode);
        exportedRoot.add(exportedOthersNode);

        leftRoot.add(availableRoot);
        leftRoot.add(exportedRoot);

        availableModel = new DefaultTreeModel(leftRoot);
        availableTree = new JTree(availableModel);
        availableTree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        availableTree.setRootVisible(false);
        availableTree.setShowsRootHandles(true);

        // Expand all nodes in available tree
        for (int i = 0; i < 10; i++) {
            availableTree.expandRow(i);
        }

        JScrollPane availableScrollPane = new JScrollPane(availableTree);
        availableScrollPane.setBorder(BorderFactory.createTitledBorder("Available Classes"));

        // Create right tree (Export structure)
        DefaultMutableTreeNode exportRoot = new DefaultMutableTreeNode("Migration Structure");
        exportModel = new DefaultTreeModel(exportRoot);
        exportTree = new JTree(exportModel);
        exportTree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        exportTree.setRootVisible(true);
        exportTree.setShowsRootHandles(true);

        JScrollPane exportScrollPane = new JScrollPane(exportTree);
        exportScrollPane.setBorder(BorderFactory.createTitledBorder("Export Structure"));

        // Add toolbar for export tree
        JPanel rightPanel = new JPanel(new BorderLayout());
        JToolBar toolbar = createExportToolbar();
        rightPanel.add(toolbar, BorderLayout.NORTH);
        rightPanel.add(exportScrollPane, BorderLayout.CENTER);

        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableScrollPane, rightPanel);
        splitPane.setResizeWeight(0.25);
        splitPane.setDividerLocation(0.25);

        add(splitPane, BorderLayout.CENTER);

        // Add mouse listeners for drag and drop functionality
        setupDragAndDrop();
    }

    private JToolBar createExportToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton addModuleButton = new JButton("Add Module");
        addModuleButton.addActionListener(e -> addModule());
        toolbar.add(addModuleButton);

        JButton renameButton = new JButton("Rename");
        renameButton.addActionListener(e -> renameSelectedNode());
        toolbar.add(renameButton);

        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteSelectedNode());
        toolbar.add(deleteButton);

        toolbar.addSeparator();

        JButton addClassButton = new JButton("Add Class →");
        addClassButton.addActionListener(e -> addSelectedClassToExport());
        toolbar.add(addClassButton);

        JButton removeClassButton = new JButton("← Remove Class");
        removeClassButton.addActionListener(e -> removeSelectedClassFromExport());
        toolbar.add(removeClassButton);

        toolbar.addSeparator();

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveMigrationStructure());
        toolbar.add(saveButton);

        return toolbar;
    }

    private void setupDragAndDrop() {
        // Set up drag gesture recognizer for available tree (simpler approach)
        DragSource dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(
                availableTree,
                DnDConstants.ACTION_COPY,
                new DragGestureListener() {
                    @Override
                    public void dragGestureRecognized(DragGestureEvent dge) {
                        TreePath path = availableTree.getSelectionPath();
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            if (node.getUserObject() instanceof ClassNode) {
                                ClassNode classNode = (ClassNode) node.getUserObject();
                                Transferable transferable = new ClassTransferable(classNode.getSchemaClass());
                                try {
                                    dge.startDrag(DragSource.DefaultCopyDrop, transferable);
                                } catch (Exception e) {
                                    // Ignore drag conflicts
                                }
                            }
                        }
                    }
                });

        // Set up drop target for export tree
        new DropTarget(exportTree, new DropTargetAdapter() {
            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                Point pt = dtde.getLocation();
                TreePath path = exportTree.getPathForLocation(pt.x, pt.y);

                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    // Accept drop on root or module nodes
                    if (node.getUserObject() instanceof ModuleNode ||
                            node == exportModel.getRoot()) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY);
                        exportTree.setSelectionPath(path);
                        return;
                    }
                }
                dtde.rejectDrag();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                Point pt = dtde.getLocation();
                TreePath path = exportTree.getPathForLocation(pt.x, pt.y);

                if (path == null) {
                    dtde.rejectDrop();
                    return;
                }

                DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) path.getLastPathComponent();

                // Only allow drop on module nodes
                if (!(targetNode.getUserObject() instanceof ModuleNode)) {
                    dtde.rejectDrop();
                    return;
                }

                try {
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(ClassTransferable.CLASS_FLAVOR)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        DOSchemaClass schemaClass = (DOSchemaClass) transferable
                                .getTransferData(ClassTransferable.CLASS_FLAVOR);

                        // Add class to the target module
                        addClassToModule(schemaClass, targetNode);

                        dtde.dropComplete(true);
                        return;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                dtde.rejectDrop();
            }
        });

        // Double-click on available tree to add to export
        availableTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addSelectedClassToExport();
                }
            }
        });

        // Double-click on export tree to remove
        exportTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = exportTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (isClassNode(node)) {
                            removeClassFromExport(node);
                        }
                    }
                }
            }
        });
    }

    private void populateAvailableTree() {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        // Collect classes by category
        java.util.List<DOSchemaClass> availableEntities = new ArrayList<>();
        java.util.List<DOSchemaClass> availableParams = new ArrayList<>();
        java.util.List<DOSchemaClass> availableOthers = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedEntities = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedParams = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedOthers = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (exportedClasses.contains(schemaClass.getAbsoluteName())) {
                // Add to Exported section
                if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                    exportedEntities.add(schemaClass);
                } else if (isDescendantOf(schemaClass, "gest.gen.EntiteParam")) {
                    exportedParams.add(schemaClass);
                } else {
                    exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                    availableEntities.add(schemaClass);
                } else if (isDescendantOf(schemaClass, "gest.gen.EntiteParam")) {
                    availableParams.add(schemaClass);
                } else {
                    availableOthers.add(schemaClass);
                }
            }
        }

        // Sort by package and add to tree
        addSortedClasses(availableEntitiesNode, availableEntities);
        addSortedClasses(availableParamsNode, availableParams);
        addSortedClasses(availableOthersNode, availableOthers);
        addSortedClasses(exportedEntitiesNode, exportedEntities);
        addSortedClasses(exportedParamsNode, exportedParams);
        addSortedClasses(exportedOthersNode, exportedOthers);

        availableModel.reload();

        // Expand all categories
        for (int i = 0; i < 10; i++) {
            availableTree.expandRow(i);
        }
    }

    private void addSortedClasses(DefaultMutableTreeNode parentNode, java.util.List<DOSchemaClass> classes) {
        // Group classes by package
        Map<String, java.util.List<DOSchemaClass>> packageMap = new TreeMap<>();

        for (DOSchemaClass schemaClass : classes) {
            String packageName = getPackageName(schemaClass.getAbsoluteName());
            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(schemaClass);
        }

        // Add package nodes and classes under them
        for (Map.Entry<String, java.util.List<DOSchemaClass>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            java.util.List<DOSchemaClass> packageClasses = entry.getValue();

            // Create package node
            DefaultMutableTreeNode packageNode = new DefaultMutableTreeNode(packageName);
            parentNode.add(packageNode);

            // Sort classes within package by simple name
            packageClasses.sort(Comparator.comparing(c -> getSimpleName(c.getAbsoluteName())));

            // Add classes to package node
            for (DOSchemaClass schemaClass : packageClasses) {
                ClassNode classNode = new ClassNode(schemaClass);
                DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(classNode);
                packageNode.add(treeNode);
            }
        }
    }

    private String getPackageName(String absoluteName) {
        int lastDot = absoluteName.lastIndexOf('.');
        if (lastDot > 0) {
            return absoluteName.substring(0, lastDot);
        }
        return "(default package)";
    }

    private void refreshAvailableTree() {
        // Clear all tree categories
        availableEntitiesNode.removeAllChildren();
        availableParamsNode.removeAllChildren();
        availableOthersNode.removeAllChildren();
        exportedEntitiesNode.removeAllChildren();
        exportedParamsNode.removeAllChildren();
        exportedOthersNode.removeAllChildren();

        // Collect classes by category
        java.util.List<DOSchemaClass> availableEntities = new ArrayList<>();
        java.util.List<DOSchemaClass> availableParams = new ArrayList<>();
        java.util.List<DOSchemaClass> availableOthers = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedEntities = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedParams = new ArrayList<>();
        java.util.List<DOSchemaClass> exportedOthers = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (exportedClasses.contains(schemaClass.getAbsoluteName())) {
                // Add to Exported section
                if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                    exportedEntities.add(schemaClass);
                } else if (isDescendantOf(schemaClass, "gest.gen.EntiteParam")) {
                    exportedParams.add(schemaClass);
                } else {
                    exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                    availableEntities.add(schemaClass);
                } else if (isDescendantOf(schemaClass, "gest.gen.EntiteParam")) {
                    availableParams.add(schemaClass);
                } else {
                    availableOthers.add(schemaClass);
                }
            }
        }

        // Sort by package and add to tree
        addSortedClasses(availableEntitiesNode, availableEntities);
        addSortedClasses(availableParamsNode, availableParams);
        addSortedClasses(availableOthersNode, availableOthers);
        addSortedClasses(exportedEntitiesNode, exportedEntities);
        addSortedClasses(exportedParamsNode, exportedParams);
        addSortedClasses(exportedOthersNode, exportedOthers);

        availableModel.reload();

        // Expand all categories
        for (int i = 0; i < 10; i++) {
            availableTree.expandRow(i);
        }
    }

    private void addModule() {
        ModuleDialog dialog = new ModuleDialog(SwingUtilities.getWindowAncestor(this), "Add Module", null, null);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String moduleName = dialog.getModuleName();
            String moduleId = dialog.getModuleId();

            DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
            ModuleNode moduleNode = new ModuleNode(moduleName, moduleId);
            DefaultMutableTreeNode newModule = new DefaultMutableTreeNode(moduleNode);
            root.add(newModule);
            exportModel.reload();
            exportTree.expandPath(new TreePath(root.getPath()));
        }
    }

    private void renameSelectedNode() {
        TreePath path = exportTree.getSelectionPath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof ModuleNode) {
            ModuleNode moduleNode = (ModuleNode) node.getUserObject();
            ModuleDialog dialog = new ModuleDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Rename Module",
                    moduleNode.getName(),
                    moduleNode.getId());
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                moduleNode.setName(dialog.getModuleName());
                moduleNode.setId(dialog.getModuleId());
                exportModel.nodeChanged(node);
            }
        }
    }

    private void deleteSelectedNode() {
        TreePath path = exportTree.getSelectionPath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getParent() == null) {
            JOptionPane.showMessageDialog(this, "Cannot delete root node", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete this node and all its children?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // If it's a module, move all its classes back to available
            if (node.getUserObject() instanceof ModuleNode) {
                moveChildrenToAvailable(node);
            } else if (isClassNode(node)) {
                // Single class node
                removeClassFromExport(node);
                return; // Don't delete the node, it's already moved
            }

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            parent.remove(node);
            exportModel.reload();
        }
    }

    private void moveChildrenToAvailable(DefaultMutableTreeNode moduleNode) {
        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (child.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) child.getUserObject();
                exportedClasses.remove(classNode.getSchemaClass().getAbsoluteName());
            }
        }
        refreshAvailableTree();
    }

    private void addSelectedClassToExport() {
        TreePath path = availableTree.getSelectionPath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) node.getUserObject();
        DOSchemaClass schemaClass = classNode.getSchemaClass();

        // Ask user to select a module or create new one
        DefaultMutableTreeNode exportRoot = (DefaultMutableTreeNode) exportModel.getRoot();

        if (exportRoot.getChildCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please create a module first using 'Add Module' button",
                    "No Modules",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Get list of modules
        java.util.List<DefaultMutableTreeNode> modules = new ArrayList<>();
        Enumeration<?> children = exportRoot.children();
        while (children.hasMoreElements()) {
            modules.add((DefaultMutableTreeNode) children.nextElement());
        }

        Object[] options = modules.toArray();
        Object selected = JOptionPane.showInputDialog(this,
                "Select module for " + getSimpleName(schemaClass.getAbsoluteName()),
                "Add to Module",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (selected != null) {
            DefaultMutableTreeNode targetModule = (DefaultMutableTreeNode) selected;
            addClassToModule(schemaClass, targetModule);
        }
    }

    private void addClassToModule(DOSchemaClass schemaClass, DefaultMutableTreeNode targetModule) {
        // Check if already exported
        if (exportedClasses.contains(schemaClass.getAbsoluteName())) {
            JOptionPane.showMessageDialog(this,
                    "Class '" + getSimpleName(schemaClass.getAbsoluteName()) + "' is already in the export structure.",
                    "Already Exported",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create class node and add to module
        ClassNode newClassNode = new ClassNode(schemaClass);
        DefaultMutableTreeNode newTreeNode = new DefaultMutableTreeNode(newClassNode);
        targetModule.add(newTreeNode);

        // Mark as exported
        exportedClasses.add(schemaClass.getAbsoluteName());

        // Refresh trees
        exportModel.reload();
        exportTree.expandPath(new TreePath(targetModule.getPath()));
        refreshAvailableTree();
    }

    private void removeSelectedClassFromExport() {
        TreePath path = exportTree.getSelectionPath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (isClassNode(node)) {
            removeClassFromExport(node);
        }
    }

    private void removeClassFromExport(DefaultMutableTreeNode treeNode) {
        if (!(treeNode.getUserObject() instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) treeNode.getUserObject();
        DOSchemaClass schemaClass = classNode.getSchemaClass();

        // Remove from exported set
        exportedClasses.remove(schemaClass.getAbsoluteName());

        // Remove from export tree
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) treeNode.getParent();
        if (parent != null) {
            // Save the expanded state before reload
            TreePath parentPath = new TreePath(parent.getPath());
            boolean wasExpanded = exportTree.isExpanded(parentPath);

            parent.remove(treeNode);
            exportModel.reload();

            // Restore the expanded state
            if (wasExpanded) {
                exportTree.expandPath(parentPath);
            }
        }

        // Refresh available tree
        refreshAvailableTree();
    }

    private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.getAbsoluteName();
        if (currentClassName.equals(ancestorClassName)) {
            return true;
        }

        String parentClassName = schemaClass.getParentClass();
        if (parentClassName == null || parentClassName.isEmpty()) {
            return false;
        }

        if (parentClassName.equals(ancestorClassName)) {
            return true;
        }

        // Look up parent class and recurse
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass candidate : schema.getClasses()) {
                if (candidate.getAbsoluteName().equals(parentClassName)) {
                    return isDescendantOf(candidate, ancestorClassName);
                }
            }
        }

        return false;
    }

    private boolean isClassNode(DefaultMutableTreeNode node) {
        return node.getUserObject() instanceof ClassNode;
    }

    private String getSimpleName(String absoluteName) {
        if (absoluteName.contains(".")) {
            return absoluteName.substring(absoluteName.lastIndexOf('.') + 1);
        }
        return absoluteName;
    }

    private void loadMigrationStructure() {
        try {
            MigrationFormatReader reader = new MigrationFormatReader();
            List<MigrationModule> modules = reader.readMigrationFormat(MIGRATION_FORMAT_FILE);

            DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
            root.removeAllChildren();

            for (MigrationModule module : modules) {
                ModuleNode moduleNode = new ModuleNode(module.getName(), module.getId());
                DefaultMutableTreeNode moduleTreeNode = new DefaultMutableTreeNode(moduleNode);
                root.add(moduleTreeNode);

                // Add classes to module
                for (String className : module.getClassNames()) {
                    // Find the DOSchemaClass
                    DOSchemaClass schemaClass = findClassByName(className);
                    if (schemaClass != null) {
                        ClassNode classNode = new ClassNode(schemaClass);
                        DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                        moduleTreeNode.add(classTreeNode);
                        exportedClasses.add(className);
                    }
                }
            }

            exportModel.reload();
            exportTree.expandRow(0);

            System.out.println("Loaded " + modules.size() + " modules from " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            System.err.println("Error loading migration structure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveMigrationStructure() {
        try {
            List<MigrationModule> modules = new ArrayList<>();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();

            Enumeration<?> children = root.children();
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) children.nextElement();
                if (moduleNode.getUserObject() instanceof ModuleNode) {
                    ModuleNode module = (ModuleNode) moduleNode.getUserObject();
                    List<String> classNames = new ArrayList<>();

                    Enumeration<?> classNodes = moduleNode.children();
                    while (classNodes.hasMoreElements()) {
                        DefaultMutableTreeNode classTreeNode = (DefaultMutableTreeNode) classNodes.nextElement();
                        if (classTreeNode.getUserObject() instanceof ClassNode) {
                            ClassNode classNode = (ClassNode) classTreeNode.getUserObject();
                            classNames.add(classNode.getSchemaClass().getAbsoluteName());
                        }
                    }

                    modules.add(new MigrationModule(module.getName(), module.getId(), classNames));
                }
            }

            MigrationFormatWriter writer = new MigrationFormatWriter();
            writer.writeMigrationFormat(modules, MIGRATION_FORMAT_FILE);

            JOptionPane.showMessageDialog(this,
                    "Migration structure saved successfully!\nBackup created.",
                    "Save Successful",
                    JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Saved " + modules.size() + " modules to " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error saving migration structure: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private DOSchemaClass findClassByName(String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.getAbsoluteName().equals(className)) {
                return schemaClass;
            }
        }

        return null;
    }

    /**
     * Data class representing a migration module
     */
    public static class MigrationModule {
        private final String name;
        private final String id;
        private final List<String> classNames;

        public MigrationModule(String name, String id, List<String> classNames) {
            this.name = name;
            this.id = id;
            this.classNames = classNames;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public List<String> getClassNames() {
            return classNames;
        }
    }

    /**
     * Dialog for creating or editing a module
     */
    private static class ModuleDialog extends JDialog {
        private JTextField nameField;
        private JTextField idField;
        private boolean confirmed = false;

        public ModuleDialog(Window owner, String title, String initialName, String initialId) {
            super(owner, title, ModalityType.APPLICATION_MODAL);

            setLayout(new BorderLayout(10, 10));

            // Create form panel
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Module Name
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0;
            formPanel.add(new JLabel("Module Name:"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            nameField = new JTextField(initialName != null ? initialName : "", 20);
            formPanel.add(nameField, gbc);

            // Module ID
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 0;
            formPanel.add(new JLabel("Module ID:"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            idField = new JTextField(initialId != null ? initialId : "", 20);
            formPanel.add(idField, gbc);

            add(formPanel, BorderLayout.CENTER);

            // Create button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");

            okButton.addActionListener(e -> {
                if (validateInput()) {
                    confirmed = true;
                    dispose();
                }
            });

            cancelButton.addActionListener(e -> {
                confirmed = false;
                dispose();
            });

            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(owner);

            // Set focus to name field
            nameField.requestFocusInWindow();
        }

        private boolean validateInput() {
            if (nameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Module name cannot be empty",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                nameField.requestFocusInWindow();
                return false;
            }

            if (idField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Module ID cannot be empty",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                idField.requestFocusInWindow();
                return false;
            }

            return true;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public String getModuleName() {
            return nameField.getText().trim();
        }

        public String getModuleId() {
            return idField.getText().trim();
        }
    }

    /**
     * Custom TransferHandler for class nodes
     */
    private static class ClassTransferHandler extends TransferHandler {
        @Override
        protected Transferable createTransferable(JComponent c) {
            if (c instanceof JTree) {
                JTree tree = (JTree) c;
                TreePath path = tree.getSelectionPath();
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof ClassNode) {
                        ClassNode classNode = (ClassNode) node.getUserObject();
                        return new ClassTransferable(classNode.getSchemaClass());
                    }
                }
            }
            return null;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }

    /**
     * Transferable wrapper for DOSchemaClass
     */
    private static class ClassTransferable implements Transferable {
        public static final DataFlavor CLASS_FLAVOR = new DataFlavor(DOSchemaClass.class, "Schema Class");
        private static final DataFlavor[] FLAVORS = { CLASS_FLAVOR };
        private final DOSchemaClass schemaClass;

        public ClassTransferable(DOSchemaClass schemaClass) {
            this.schemaClass = schemaClass;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FLAVORS;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return CLASS_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return schemaClass;
        }
    }

    /**
     * Inner class to represent a class node with proper display
     */
    private static class ClassNode {
        private DOSchemaClass schemaClass;

        public ClassNode(DOSchemaClass schemaClass) {
            this.schemaClass = schemaClass;
        }

        public DOSchemaClass getSchemaClass() {
            return schemaClass;
        }

        @Override
        public String toString() {
            String simpleName = schemaClass.getAbsoluteName();
            if (simpleName.contains(".")) {
                simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
            }
            return simpleName + " (" + schemaClass.getUniqueObjectCount() + " objects)";
        }
    }

    /**
     * Inner class to represent a module with name and ID
     */
    private static class ModuleNode {
        private String name;
        private String id;

        public ModuleNode(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return name + " [" + id + "]";
        }
    }
}
