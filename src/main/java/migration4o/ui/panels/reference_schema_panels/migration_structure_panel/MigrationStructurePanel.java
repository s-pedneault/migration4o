
package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import migration4o.engine.export.ExportHistory;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.migration.MigrationExportService;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.CategorizedClasses;
import migration4o.models.ui.ClassNode;
import migration4o.models.ui.ClassTransferable;
import migration4o.models.ui.MigrationModule;
import migration4o.models.ui.ModuleNode;
import migration4o.schema.modules.DOModuleStructureReader;
import migration4o.ui.common.dialogs.ExportResultDialog;
import migration4o.ui.main.MainWindow;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.ModuleDialog;

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

        // Categorize classes using util
        CategorizedClasses categorized = MigrationStructurePanelUtil
                .categorizeClasses(schema, exportedClasses);

        // Sort by package and add to tree
        MigrationStructurePanelUtil.addSortedClassesToNode(availableEntitiesNode, categorized.availableEntities);
        MigrationStructurePanelUtil.addSortedClassesToNode(availableParamsNode, categorized.availableParams);
        MigrationStructurePanelUtil.addSortedClassesToNode(availableOthersNode, categorized.availableOthers);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedEntitiesNode, categorized.exportedEntities);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedParamsNode, categorized.exportedParams);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedOthersNode, categorized.exportedOthers);

        availableModel.reload();

        // Expand all categories
        for (int i = 0; i < 10; i++) {
            availableTree.expandRow(i);
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

        // Use databaseSchema if available (has object counts), otherwise use reference
        // schema
        DOSchema sourceSchema = (databaseSchema != null) ? databaseSchema : schema;

        // Categorize classes using util
        CategorizedClasses categorized = MigrationStructurePanelUtil
                .categorizeClasses(sourceSchema, exportedClasses);

        // Sort by package and add to tree
        MigrationStructurePanelUtil.addSortedClassesToNode(availableEntitiesNode, categorized.availableEntities);
        MigrationStructurePanelUtil.addSortedClassesToNode(availableParamsNode, categorized.availableParams);
        MigrationStructurePanelUtil.addSortedClassesToNode(availableOthersNode, categorized.availableOthers);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedEntitiesNode, categorized.exportedEntities);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedParamsNode, categorized.exportedParams);
        MigrationStructurePanelUtil.addSortedClassesToNode(exportedOthersNode, categorized.exportedOthers);

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
        MigrationStructurePanelUtil.removeModuleClassesFromExported(moduleNode, exportedClasses);
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
                "Select module for " + schemaClass.getSourceName(),
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
                    "Class '" + schemaClass.getSourceName()
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
        return MigrationStructurePanelUtil.isClassNode(node);
    }

    private DefaultMutableTreeNode findClassNodeInExportTree(String className) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
        return MigrationStructurePanelUtil.findClassNodeInTree(root, className);
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
        // Create export service and validate prerequisites
        MigrationExportService exportService = new MigrationExportService(schema, databaseSchema, databasePath);
        MigrationExportService.ValidationResult validation = exportService.validateExportPrerequisites();

        if (!validation.isValid()) {
            JOptionPane.showMessageDialog(this,
                    validation.getErrorMessage(),
                    validation.getErrorTitle(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        DOSchemaClass schemaClass = classNode.getSchemaClass();
        String simpleName = schemaClass.getSourceName();

        // Use default output directory automatically (output/<db-folder>/Data and
        // Definitions)
        String outputPath = "output";

        // Run export in background
        SwingWorker<ExportResult, String> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                publish("Exporting " + simpleName + "...");
                return exportService.exportClass(schemaClass, outputPath);
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
                    ExportResult result = get();
                    // Show detailed result dialog
                    ExportResultDialog dialog = new ExportResultDialog(
                            (Frame) SwingUtilities.getWindowAncestor(MigrationStructurePanel.this),
                            result);
                    dialog.setVisible(true);

                    // Update migration coverage with exported object counts
                    if (result.errors.isEmpty() && !result.exportedClassCounts.isEmpty()) {
                        System.out.println("DEBUG MigrationStructurePanel (CLASS): Notifying MainWindow with " +
                                result.exportedClassCounts.size() + " classes");
                        java.awt.Window window = SwingUtilities.getWindowAncestor(MigrationStructurePanel.this);
                        if (window instanceof MainWindow) {
                            MainWindow mainWindow = (MainWindow) window;
                            mainWindow.notifyExportCompleted(result.exportedClassCounts);
                        } else {
                            System.out.println("DEBUG: Window is not MainWindow: " +
                                    (window != null ? window.getClass().getName() : "null"));
                        }
                    } else {
                        System.out.println("DEBUG MigrationStructurePanel (CLASS): Not updating coverage - success="
                                +
                                result.errors.isEmpty() + ", counts size=" + result.exportedClassCounts.size());
                    }
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

    /**
     * Exports an entire module (with all its classes and nested modules) to XML
     */
    private void exportModule(DefaultMutableTreeNode moduleTreeNode, ModuleNode moduleNode) {
        // Create export service and validate prerequisites
        MigrationExportService exportService = new MigrationExportService(schema, databaseSchema, databasePath);
        MigrationExportService.ValidationResult validation = exportService.validateExportPrerequisites();

        if (!validation.isValid()) {
            JOptionPane.showMessageDialog(this,
                    validation.getErrorMessage(),
                    validation.getErrorTitle(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Build the module structure from the tree
        MigrationModule module = buildModuleFromTree(moduleTreeNode, moduleNode);

        if (module.getClassNames().isEmpty() && module.getChildModules().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Module '" + moduleNode.getName() + "' contains no classes.",
                    "Empty Module",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Use default output directory automatically (output/<db-folder>/Data and
        // Definitions)
        String outputPath = "output";

        // Run export in background
        SwingWorker<ExportResult, String> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                publish("Exporting module " + moduleNode.getName() + " with folder structure...");
                return exportService.exportModuleStructured(module, outputPath);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    System.out.println(message);
                }
            }

            @Override
            protected void done() {
                System.out.println("DEBUG: MODULE export done() called");
                try {
                    ExportResult result = get();
                    System.out.println("DEBUG: Got export result, showing dialog...");
                    // Show detailed result dialog
                    ExportResultDialog dialog = new ExportResultDialog(
                            (Frame) SwingUtilities.getWindowAncestor(MigrationStructurePanel.this),
                            result);
                    dialog.setVisible(true);
                    System.out.println("DEBUG: Dialog closed, continuing...");

                    // Update migration coverage with exported object counts
                    if (result.errors.isEmpty() && !result.exportedClassCounts.isEmpty()) {
                        System.out.println("DEBUG MigrationStructurePanel (MODULE): Notifying MainWindow with " +
                                result.exportedClassCounts.size() + " classes");
                        java.awt.Window window = SwingUtilities.getWindowAncestor(MigrationStructurePanel.this);
                        if (window instanceof MainWindow) {
                            MainWindow mainWindow = (MainWindow) window;
                            mainWindow.notifyExportCompleted(result.exportedClassCounts);
                        } else {
                            System.out.println("DEBUG: Window is not MainWindow: " +
                                    (window != null ? window.getClass().getName() : "null"));
                        }
                    } else {
                        System.out.println(
                                "DEBUG MigrationStructurePanel (MODULE): Not updating coverage - success=" +
                                        result.errors.isEmpty() + ", counts size="
                                        + result.exportedClassCounts.size());
                    }
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

    /**
     * Builds a MigrationModule from a tree node with all its children.
     */
    private MigrationModule buildModuleFromTree(DefaultMutableTreeNode moduleTreeNode, ModuleNode moduleNode) {
        List<String> classNames = new ArrayList<>();
        List<MigrationModule> childModules = new ArrayList<>();

        // Iterate through children
        for (int i = 0; i < moduleTreeNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) moduleTreeNode.getChildAt(i);
            Object userObject = childNode.getUserObject();

            if (userObject instanceof ClassNode) {
                // Add class name
                ClassNode classNode = (ClassNode) userObject;
                classNames.add(classNode.getSchemaClass().source);
            } else if (userObject instanceof ModuleNode) {
                // Recursively build child module
                ModuleNode childModuleNode = (ModuleNode) userObject;
                MigrationModule childModule = buildModuleFromTree(childNode, childModuleNode);
                childModules.add(childModule);
            }
        }

        return new MigrationModule(moduleNode.getName(), moduleNode.getId(), classNames, childModules);
    }

    /**
     * Recursively collects all class names from a module and its children
     */
    private void collectClassNamesFromModule(DefaultMutableTreeNode moduleNode, List<String> classNames) {
        MigrationStructurePanelUtil.collectClassNamesFromModule(moduleNode, classNames);
    }

    private void loadMigrationStructure() {
        try {
            DOModuleStructureReader reader = new DOModuleStructureReader();
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
        MigrationStructurePanelUtil.addModuleToTree(parentNode, module, schema, databaseSchema, exportedClasses);
    }

    /**
     * Reloads the export tree to refresh object counts when database changes
     */
    private void reloadExportTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
        MigrationStructurePanelUtil.updateNodeCounts(root, schema, databaseSchema);
        exportModel.reload();
    }

    private void saveMigrationStructure() {
        try {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) exportModel.getRoot();
            MigrationStructurePanelUtil.saveMigrationStructure(root, MIGRATION_FORMAT_FILE);

            JOptionPane.showMessageDialog(this,
                    "Migration structure saved successfully!\nBackup created.",
                    "Save Successful",
                    JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Saved migration structure to " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error saving migration structure: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Repeats the last export operation if history exists.
     */
    public void repeatLastExport() {
        // Create export service and validate prerequisites
        MigrationExportService exportService = new MigrationExportService(schema, databaseSchema, databasePath);
        MigrationExportService.ValidationResult validation = exportService.validateExportPrerequisites();

        if (!validation.isValid()) {
            JOptionPane.showMessageDialog(this,
                    validation.getErrorMessage(),
                    validation.getErrorTitle(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if history exists
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();

        if (params == null) {
            JOptionPane.showMessageDialog(this,
                    "No export history found.",
                    "No History",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Execute the export (no confirmation needed - command-line flag is explicit
        // intent)
        System.out.println("Repeating export: " + params.getDescription());
        SwingWorker<ExportResult, String> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                if (params.type == ExportHistory.ExportType.CLASS) {
                    publish("Repeating class export: " + params.targetName);
                } else {
                    publish("Repeating module export: " + params.targetName);
                }
                return exportService.repeatLastExport();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    System.out.println(message);
                }
            }

            @Override
            protected void done() {
                System.out.println("DEBUG: REPEAT export done() called");
                try {
                    ExportResult result = get();
                    System.out.println("DEBUG: Got repeat export result, showing dialog...");
                    // Don't save to history again - we're repeating
                    // Show detailed result dialog
                    ExportResultDialog dialog = new ExportResultDialog(
                            (Frame) SwingUtilities.getWindowAncestor(MigrationStructurePanel.this),
                            result);
                    dialog.setVisible(true);
                    System.out.println("DEBUG: Dialog closed, continuing...");

                    // Update migration coverage with exported object counts
                    if (result.errors.isEmpty() && !result.exportedClassCounts.isEmpty()) {
                        System.out.println("DEBUG MigrationStructurePanel (REPEAT): Notifying MainWindow with " +
                                result.exportedClassCounts.size() + " classes");
                        java.awt.Window window = SwingUtilities.getWindowAncestor(MigrationStructurePanel.this);
                        if (window instanceof MainWindow) {
                            MainWindow mainWindow = (MainWindow) window;
                            mainWindow.notifyExportCompleted(result.exportedClassCounts);
                        } else {
                            System.out.println("DEBUG: Window is not MainWindow: " +
                                    (window != null ? window.getClass().getName() : "null"));
                        }
                    } else {
                        System.out.println("DEBUG MigrationStructurePanel (REPEAT): Not updating coverage - success=" +
                                result.errors.isEmpty() + ", counts size=" + result.exportedClassCounts.size());
                    }
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
