
package migration4o.ui.schema;

import migration4o.ui.schema.MigrationStructureUtils;

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
import java.util.Collections;
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
    private DOSchema schema; // Reference schema with class definitions
    private DOSchema databaseSchema; // Database schema with actual object IDs
    private String databasePath; // Path to the currently opened database
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
        this.databaseSchema = null;
        this.databasePath = null;
        initializeUI();
        loadMigrationStructure();
        populateAvailableTree();
    }

    /**
     * Updates the database path when a database is opened
     */
    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    /**
     * Sets the database schema which contains actual object IDs from the opened
     * database
     */
    public void setDatabaseSchema(DOSchema databaseSchema) {
        this.databaseSchema = databaseSchema;
        // Refresh trees to show updated object counts
        refreshAvailableTree();
        reloadExportTree();
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

        // Set up drag gesture recognizer for export tree (to move classes between
        // modules)
        DragSource exportDragSource = new DragSource();
        exportDragSource.createDefaultDragGestureRecognizer(
                exportTree,
                DnDConstants.ACTION_MOVE,
                new DragGestureListener() {
                    @Override
                    public void dragGestureRecognized(DragGestureEvent dge) {
                        TreePath path = exportTree.getSelectionPath();
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            if (node.getUserObject() instanceof ClassNode) {
                                ClassNode classNode = (ClassNode) node.getUserObject();
                                Transferable transferable = new ClassTransferable(classNode.getSchemaClass());
                                try {
                                    dge.startDrag(DragSource.DefaultMoveDrop, transferable);
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
                        dtde.acceptDrag(dtde.getDropAction());
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
                        dtde.acceptDrop(dtde.getDropAction());
                        DOSchemaClass schemaClass = (DOSchemaClass) transferable
                                .getTransferData(ClassTransferable.CLASS_FLAVOR);

                        // Check if this is a move within export tree (ACTION_MOVE)
                        if (dtde.getDropAction() == DnDConstants.ACTION_MOVE) {
                            // Find and remove the class from its current module
                            DefaultMutableTreeNode sourceNode = findClassNodeInExportTree(
                                    schemaClass.source);
                            if (sourceNode != null) {
                                DefaultMutableTreeNode sourceParent = (DefaultMutableTreeNode) sourceNode.getParent();
                                // Don't move if dropping on the same module
                                if (sourceParent == targetNode) {
                                    dtde.dropComplete(false);
                                    return;
                                }
                                // Save expanded state
                                List<TreePath> expandedPaths = new ArrayList<>();
                                Enumeration<TreePath> expandedEnum = exportTree
                                        .getExpandedDescendants(new TreePath(exportModel.getRoot()));
                                if (expandedEnum != null) {
                                    while (expandedEnum.hasMoreElements()) {
                                        expandedPaths.add(expandedEnum.nextElement());
                                    }
                                }
                                // Remove from source
                                sourceParent.remove(sourceNode);
                                // Add to target (without duplicate check since we're moving)
                                ClassNode classNode = new ClassNode(schemaClass);
                                DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                                targetNode.add(classTreeNode);
                                // Reload and restore expanded state
                                exportModel.reload();
                                for (TreePath expandedPath : expandedPaths) {
                                    exportTree.expandPath(expandedPath);
                                }
                                exportTree.expandPath(new TreePath(targetNode.getPath()));
                                dtde.dropComplete(true);
                                return;
                            }
                        } else {
                            // This is a copy from available tree
                            addClassToModule(schemaClass, targetNode);
                        }

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

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showExportContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showExportContextMenu(e);
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
            if (exportedClasses.contains(schemaClass.source)) {
                // Add to Exported section
                if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteContientID", schema)) {
                    exportedEntities.add(schemaClass);
                } else if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteParam", schema)) {
                    exportedParams.add(schemaClass);
                } else {
                    exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteContientID", schema)) {
                    availableEntities.add(schemaClass);
                } else if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteParam", schema)) {
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
            String packageName = MigrationStructureUtils.getPackageName(schemaClass.source);
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
            packageClasses.sort(Comparator.comparing(c -> MigrationStructureUtils.getSimpleName(c.source)));

            // Add classes to package node
            for (DOSchemaClass schemaClass : packageClasses) {
                ClassNode classNode = new ClassNode(schemaClass);
                DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(classNode);
                packageNode.add(treeNode);
            }
        }
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

        // Use databaseSchema if available (has object counts), otherwise use reference
        // schema
        DOSchema sourceSchema = (databaseSchema != null) ? databaseSchema : schema;
        for (DOSchemaClass schemaClass : sourceSchema.getClasses()) {
            if (exportedClasses.contains(schemaClass.source)) {
                // Add to Exported section
                if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteContientID", schema)) {
                    exportedEntities.add(schemaClass);
                } else if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteParam", schema)) {
                    exportedParams.add(schemaClass);
                } else {
                    exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteContientID", schema)) {
                    availableEntities.add(schemaClass);
                } else if (MigrationStructureUtils.isDescendantOf(schemaClass, "gest.gen.EntiteParam", schema)) {
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

            // Check if a module is selected - if so, add as child
            TreePath selectedPath = exportTree.getSelectionPath();
            if (selectedPath != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                // Only add as child if selected node is a module (not a class)
                if (selectedNode.getUserObject() instanceof ModuleNode || selectedNode == root) {
                    selectedNode.add(newModule);
                    exportModel.reload();
                    exportTree.expandPath(new TreePath(selectedNode.getPath()));
                    return;
                }
            }

            // Default: add to root
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
                exportedClasses.remove(classNode.getSchemaClass().source);
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
                "Select module for " + MigrationStructureUtils.getSimpleName(schemaClass.source),
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
        if (exportedClasses.contains(schemaClass.source)) {
            JOptionPane.showMessageDialog(this,
                    "Class '" + MigrationStructureUtils.getSimpleName(schemaClass.source)
                            + "' is already in the export structure.",
                    "Already Exported",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Create class node and add to module
        ClassNode newClassNode = new ClassNode(schemaClass);
        DefaultMutableTreeNode newTreeNode = new DefaultMutableTreeNode(newClassNode);
        targetModule.add(newTreeNode);

        // Mark as exported
        exportedClasses.add(schemaClass.source);

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
        exportedClasses.remove(schemaClass.source);

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

    private boolean isClassNode(DefaultMutableTreeNode node) {
        return node.getUserObject() instanceof ClassNode;
    }

    private DefaultMutableTreeNode findClassNodeInExportTree(String className) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
        return findClassNodeInTree(root, className);
    }

    private DefaultMutableTreeNode findClassNodeInTree(DefaultMutableTreeNode node, String className) {
        if (node.getUserObject() instanceof ClassNode) {
            ClassNode classNode = (ClassNode) node.getUserObject();
            if (classNode.getSchemaClass().source.equals(className)) {
                return node;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findClassNodeInTree(child, className);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Shows context menu for export tree with Export option
     */
    private void showExportContextMenu(MouseEvent e) {
        TreePath path = exportTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        exportTree.setSelectionPath(path);

        JPopupMenu contextMenu = new JPopupMenu();

        // Export option for modules
        if (node.getUserObject() instanceof ModuleNode) {
            ModuleNode moduleNode = (ModuleNode) node.getUserObject();
            JMenuItem exportModuleItem = new JMenuItem("Export Module to XML...");
            exportModuleItem.addActionListener(evt -> exportModule(node, moduleNode));
            contextMenu.add(exportModuleItem);
        }
        // Export option for classes
        else if (node.getUserObject() instanceof ClassNode) {
            ClassNode classNode = (ClassNode) node.getUserObject();
            JMenuItem exportClassItem = new JMenuItem("Export Class to XML...");
            exportClassItem.addActionListener(evt -> exportClass(classNode));
            contextMenu.add(exportClassItem);
        }

        if (contextMenu.getComponentCount() > 0) {
            contextMenu.show(exportTree, e.getX(), e.getY());
        }
    }

    /**
     * Exports a single class to XML
     */
    private void exportClass(ClassNode classNode) {
        if (databasePath == null || databasePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No database is currently open. Please open a database first.",
                    "No Database",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (databaseSchema == null) {
            JOptionPane.showMessageDialog(this,
                    "No database schema available. Please open a database first.",
                    "No Database Schema",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        DOSchemaClass schemaClass = classNode.getSchemaClass();
        String className = schemaClass.source;
        String simpleName = MigrationStructureUtils.getSimpleName(className);

        // Ask user for output file
        JFileChooser fileChooser = new JFileChooser("output/migration");
        fileChooser.setDialogTitle("Export " + simpleName + " to XML");
        fileChooser.setSelectedFile(new java.io.File(simpleName + "_export.xml"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String outputPath = fileChooser.getSelectedFile().getAbsolutePath();

            // Run export in background
            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    publish("Exporting " + simpleName + "...");
                    migration4o.engine.export.XMLExportEngine exporter = new migration4o.engine.export.XMLExportEngine(
                            schema, databaseSchema, databasePath);
                    exporter.exportClass(className, outputPath);
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String message : chunks) {
                        System.out.println(message);
                    }
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(MigrationStructurePanel.this,
                                "Successfully exported " + simpleName + " to:\n" + outputPath,
                                "Export Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(MigrationStructurePanel.this,
                                "Error during export: " + e.getMessage(),
                                "Export Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            worker.execute();
        }
    }

    /**
     * Exports an entire module (with all its classes and nested modules) to XML
     */
    private void exportModule(DefaultMutableTreeNode moduleTreeNode, ModuleNode moduleNode) {
        if (databasePath == null || databasePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No database is currently open. Please open a database first.",
                    "No Database",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (databaseSchema == null) {
            JOptionPane.showMessageDialog(this,
                    "No database schema available. Please open a database first.",
                    "No Database Schema",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Collect all class names in this module and its children
        List<String> classNames = new ArrayList<>();
        collectClassNamesFromModule(moduleTreeNode, classNames);

        if (classNames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Module '" + moduleNode.getName() + "' contains no classes.",
                    "Empty Module",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Ask user for output file
        JFileChooser fileChooser = new JFileChooser("output/migration");
        fileChooser.setDialogTitle("Export Module: " + moduleNode.getName());
        fileChooser.setSelectedFile(new java.io.File(moduleNode.getId() + "_export.xml"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String outputPath = fileChooser.getSelectedFile().getAbsolutePath();

            // Run export in background
            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    publish("Exporting module " + moduleNode.getName() + " (" + classNames.size() + " classes)...");
                    migration4o.engine.export.XMLExportEngine exporter = new migration4o.engine.export.XMLExportEngine(
                            schema, databaseSchema, databasePath);
                    exporter.exportModule(classNames, moduleNode.getName(), outputPath);
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String message : chunks) {
                        System.out.println(message);
                    }
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(MigrationStructurePanel.this,
                                "Successfully exported module '" + moduleNode.getName() + "' to:\n" + outputPath,
                                "Export Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(MigrationStructurePanel.this,
                                "Error during export: " + e.getMessage(),
                                "Export Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            worker.execute();
        }
    }

    /**
     * Recursively collects all class names from a module and its children
     */
    private void collectClassNamesFromModule(DefaultMutableTreeNode moduleNode, List<String> classNames) {
        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (child.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) child.getUserObject();
                classNames.add(classNode.getSchemaClass().source);
            } else if (child.getUserObject() instanceof ModuleNode) {
                // Recursively collect from child modules
                collectClassNamesFromModule(child, classNames);
            }
        }
    }

    private void loadMigrationStructure() {
        try {
            MigrationFormatReader reader = new MigrationFormatReader();
            List<MigrationModule> modules = reader.readMigrationFormat(MIGRATION_FORMAT_FILE);

            DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
            root.removeAllChildren();

            for (MigrationModule module : modules) {
                addModuleToTree(root, module);
            }

            exportModel.reload();
            exportTree.expandRow(0);

            System.out.println("Loaded " + modules.size() + " modules from " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            System.err.println("Error loading migration structure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addModuleToTree(DefaultMutableTreeNode parentNode, MigrationModule module) {
        ModuleNode moduleNode = new ModuleNode(module.getName(), module.getId());
        DefaultMutableTreeNode moduleTreeNode = new DefaultMutableTreeNode(moduleNode);
        parentNode.add(moduleTreeNode);

        // Add classes to module
        for (String className : module.getClassNames()) {
            DOSchemaClass schemaClass = findClassByName(className);
            if (schemaClass != null) {
                ClassNode classNode = new ClassNode(schemaClass);
                DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                moduleTreeNode.add(classTreeNode);
                exportedClasses.add(className);
            }
        }

        // Add child modules recursively
        for (MigrationModule childModule : module.getChildModules()) {
            addModuleToTree(moduleTreeNode, childModule);
        }
    }

    /**
     * Reloads the export tree to refresh object counts when database changes
     */
    private void reloadExportTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
        updateNodeCounts(root);
        exportModel.reload();
    }

    /**
     * Recursively updates object counts in tree nodes
     */
    private void updateNodeCounts(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof ClassNode) {
            ClassNode classNode = (ClassNode) userObject;
            // Find the class with updated counts from databaseSchema
            DOSchemaClass updatedClass = findClassByName(classNode.getSchemaClass().source);
            if (updatedClass != null) {
                // Create new ClassNode with updated class data
                node.setUserObject(new ClassNode(updatedClass));
            }
        }

        // Recursively update children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            updateNodeCounts(child);
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
                    modules.add(extractModule(moduleNode));
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

    private MigrationModule extractModule(DefaultMutableTreeNode moduleTreeNode) {
        ModuleNode module = (ModuleNode) moduleTreeNode.getUserObject();
        List<String> classNames = new ArrayList<>();
        List<MigrationModule> childModules = new ArrayList<>();

        Enumeration<?> children = moduleTreeNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
            if (childNode.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) childNode.getUserObject();
                classNames.add(classNode.getSchemaClass().source);
            } else if (childNode.getUserObject() instanceof ModuleNode) {
                childModules.add(extractModule(childNode));
            }
        }

        return new MigrationModule(module.getName(), module.getId(), classNames, childModules);
    }

    private DOSchemaClass findClassByName(String className) {
        // Try databaseSchema first if available (has object counts)
        if (databaseSchema != null && databaseSchema.getClasses() != null) {
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                if (schemaClass.source.equals(className)) {
                    return schemaClass;
                }
            }
        }

        // Fall back to reference schema
        if (schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.source.equals(className)) {
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
        private final List<MigrationModule> childModules;

        public MigrationModule(String name, String id, List<String> classNames) {
            this(name, id, classNames, new ArrayList<>());
        }

        public MigrationModule(String name, String id, List<String> classNames, List<MigrationModule> childModules) {
            this.name = name;
            this.id = id;
            this.classNames = classNames;
            this.childModules = childModules;
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

        public List<MigrationModule> getChildModules() {
            return childModules;
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
    public static class ClassNode {
        private DOSchemaClass schemaClass;

        public ClassNode(DOSchemaClass schemaClass) {
            this.schemaClass = schemaClass;
        }

        public DOSchemaClass getSchemaClass() {
            return schemaClass;
        }

        @Override
        public String toString() {
            String simpleName = schemaClass.source;
            if (simpleName.contains(".")) {
                simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
            }
            int objectCount = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0;
            if (objectCount > 0) {
                return simpleName + " (" + objectCount + " objects)";
            } else {
                return simpleName;
            }
        }
    }

    /**
     * Inner class to represent a module with name and ID
     */
    public static class ModuleNode {
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
