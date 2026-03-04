
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.jdesktop.swingx.JXTreeTable;

import migration4o.database.DODatabaseService;
import migration4o.migration.ExportHistory;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.CategorizedClasses;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ClassNode;
import migration4o.models.ui.ClassTransferable;
import migration4o.models.ui.MigrationModule;
import migration4o.models.ui.ModuleNode;
import migration4o.schema.DOSchemaService;
import migration4o.schema.modules.DOModuleService;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.DetailLayoutDesigner;
import migration4o.ui.dialogs.ExportConfirmationDialog;
import migration4o.ui.main.MainWindow;
import migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs.ClassObjectsDialog;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.ClassExportConfigDialog;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.ModuleDialog;

/**
 * Panel for organizing classes into a migration structure with modules. Left
 * pane shows available classes, right pane shows export structure with modules.
 */
public class MigrationStructurePanel extends JPanel {
    private migration4o.database.DODatabaseContext activeContext;
    private JTree availableTree;
    private JXTreeTable exportTreeTable;
    private ExportTreeTableModel exportTreeTableModel;
    private DefaultTreeModel availableModel;
    private DefaultMutableTreeNode availableRoot;
    private DefaultMutableTreeNode exportedRoot;
    private DefaultMutableTreeNode exportStructureRoot;

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

    // Checkbox to include/exclude IDEntite classes
    private JCheckBox includeIDEntitesCheckbox;

    // Service classes for business logic
    private final MigrationServiceCallback exportOrchestrator;

    private static final String MIGRATION_FORMAT_FILE = "schema/migration-format.xml";

    public MigrationStructurePanel(DOSchema schema) {
        // Initialize service classes
        this.exportOrchestrator = new MigrationServiceCallback(this);

        // Set up export result callback
        setupExportStatisticsCallback();

        initializeUI();
        loadMigrationStructure();
        populateAvailableTree();
    }

    /**
     * Sets up the callback for export results to update the migration coverage
     * panel.
     */
    private void setupExportStatisticsCallback() {
        exportOrchestrator.setResultCallback(new MigrationServiceCallback.ExportStatisticsCallback() {
            @Override
            public void onExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext) {
                java.awt.Window window = SwingUtilities.getWindowAncestor(MigrationStructurePanel.this);
                if (window instanceof MainWindow) {
                    MainWindow mainWindow = (MainWindow) window;
                    mainWindow.notifyExportCompleted(result, dbContext);
                }
            }

            @Override
            public void onExportError(Exception error) {
                JOptionPane.showMessageDialog(MigrationStructurePanel.this, "Error during export: " + error.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Called when database schema changes to refresh the UI.
     */
    public void setActiveContext(migration4o.database.DODatabaseContext ctx) {
        this.activeContext = ctx;
    }

    public void onDatabaseSchemaChanged() {
        // Update root node name with database folder name
        updateRootNodeName();

        // Refresh trees to show updated object counts
        refreshAvailableTree();
        reloadExportTree();
    }

    /**
     * Updates the root node name to show the database folder name.
     */
    private void updateRootNodeName() {
        // Nothing here anymore, since we disconnected the reference schema view from the specific database
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // Add info panel at top
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Organize classes into migration modules");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoPanel.add(infoLabel);

        // Add checkbox for including IDEntite classes
        includeIDEntitesCheckbox = new JCheckBox("Include IDEntites");
        includeIDEntitesCheckbox.setSelected(false);
        includeIDEntitesCheckbox.addActionListener(e -> refreshAvailableTree());
        infoPanel.add(includeIDEntitesCheckbox);

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

        // Create right tree table (Export structure with pricing columns)
        exportStructureRoot = new DefaultMutableTreeNode("Database");
        exportTreeTableModel = new ExportTreeTableModel(exportStructureRoot);
        exportTreeTable = new JXTreeTable(exportTreeTableModel);
        exportTreeTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        exportTreeTable.setRootVisible(true);
        exportTreeTable.setShowsRootHandles(true);
        exportTreeTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        exportTreeTable.setRowHeight(20);

        JScrollPane exportScrollPane = new JScrollPane(exportTreeTable);
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

        // Price list combo box
        JLabel priceListLabel = new JLabel("Price List:");
        toolbar.add(priceListLabel);

        String[] priceLists = { "Default" };
        JComboBox<String> priceListCombo = new JComboBox<>(priceLists);
        priceListCombo.setMaximumSize(new java.awt.Dimension(150, 25));
        priceListCombo.addActionListener(e -> {
            String selectedList = (String) priceListCombo.getSelectedItem();
            String priceListKey = "Default".equals(selectedList) ? "" : selectedList;
            exportTreeTableModel.setPriceListKey(priceListKey);
        });
        toolbar.add(priceListCombo);

        toolbar.addSeparator();

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

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveMigrationStructure());
        toolbar.add(saveButton);

        return toolbar;
    }

    private void setupDragAndDrop() {
        // Set up drag gesture recognizer for available tree (simpler approach)
        DragSource dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(availableTree, DnDConstants.ACTION_COPY, new DragGestureListener() {
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
        exportDragSource.createDefaultDragGestureRecognizer(exportTreeTable, DnDConstants.ACTION_MOVE, new DragGestureListener() {
            @Override
            public void dragGestureRecognized(DragGestureEvent dge) {
                TreePath path = getSelectedTreePath();
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
        new DropTarget(exportTreeTable, new DropTargetAdapter() {
            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                Point pt = dtde.getLocation();
                int row = exportTreeTable.rowAtPoint(pt);
                TreePath path = row >= 0 ? exportTreeTable.getPathForRow(row) : null;

                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    // Accept drop on root or module nodes
                    if (node.getUserObject() instanceof ModuleNode || node == getExportRoot()) {
                        dtde.acceptDrag(dtde.getDropAction());
                        setTreeSelection(path);
                        return;
                    }
                }
                dtde.rejectDrag();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                Point pt = dtde.getLocation();
                int row = exportTreeTable.rowAtPoint(pt);
                TreePath path = row >= 0 ? exportTreeTable.getPathForRow(row) : null;

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
                        DOSchemaClass schemaClass = (DOSchemaClass) transferable.getTransferData(ClassTransferable.CLASS_FLAVOR);

                        // Check if this is a move within export tree (ACTION_MOVE)
                        if (dtde.getDropAction() == DnDConstants.ACTION_MOVE) {
                            // Find and remove the class from its current module
                            DefaultMutableTreeNode sourceNode = findClassNodeInExportTree(schemaClass.source);
                            if (sourceNode != null) {
                                DefaultMutableTreeNode sourceParent = (DefaultMutableTreeNode) sourceNode.getParent();
                                // Don't move if dropping on the same module
                                if (sourceParent == targetNode) {
                                    dtde.dropComplete(false);
                                    return;
                                }
                                // Save expanded state
                                List<TreePath> expandedPaths = new ArrayList<>();
                                Enumeration<?> expandedEnum = exportTreeTable.getExpandedDescendants(new TreePath(getExportRoot().getPath()));
                                if (expandedEnum != null) {
                                    while (expandedEnum.hasMoreElements()) {
                                        expandedPaths.add((TreePath) expandedEnum.nextElement());
                                    }
                                }
                                // Remove from source
                                sourceParent.remove(sourceNode);
                                // Add to target (without duplicate check since we're moving)
                                ClassNode classNode = new ClassNode(schemaClass);
                                DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                                targetNode.add(classTreeNode);
                                // Reload and restore expanded state
                                reloadExportModel();
                                for (TreePath expandedPath : expandedPaths) {
                                    expandTreePath(expandedPath);
                                }
                                expandTreePath(new TreePath(targetNode.getPath()));
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

        // Context menu for available tree
        availableTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAvailableContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAvailableContextMenu(e);
                }
            }
        });

        // Context menu for export tree
        exportTreeTable.addMouseListener(new MouseAdapter() {

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

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Double-click: open configuration dialog for class
                    int row = exportTreeTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        TreePath path = exportTreeTable.getPathForRow(row);
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            if (node.getUserObject() instanceof ClassNode) {
                                configureClassExport(node);
                            }
                        }
                    }
                }
            }
        });
    }

    private void populateAvailableTree() {
        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        // Categorize classes using util, filtering IDEntites if checkbox is unchecked
        boolean includeIDEntites = includeIDEntitesCheckbox != null && includeIDEntitesCheckbox.isSelected();
        CategorizedClasses categorized = MigrationStructurePanelUtil.categorizeClasses(schema, exportedClasses, includeIDEntites);

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

        // Just use reference schema, as migration mapping belongs to the reference schema
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DOSchema sourceSchema = referenceSchema;

        // Categorize classes using util, filtering IDEntites if checkbox is unchecked
        boolean includeIDEntites = includeIDEntitesCheckbox != null && includeIDEntitesCheckbox.isSelected();
        CategorizedClasses categorized = MigrationStructurePanelUtil.categorizeClasses(sourceSchema, exportedClasses, includeIDEntites);

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

            DefaultMutableTreeNode root = getExportRoot();
            ModuleNode moduleNode = new ModuleNode(moduleName, moduleId);
            DefaultMutableTreeNode newModule = new DefaultMutableTreeNode(moduleNode);

            // Check if a module is selected - if so, add as child
            TreePath selectedPath = getSelectedTreePath();
            if (selectedPath != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                // Only add as child if selected node is a module (not a class)
                if (selectedNode.getUserObject() instanceof ModuleNode || selectedNode == root) {
                    selectedNode.add(newModule);
                    reloadExportModel();
                    expandTreePath(new TreePath(selectedNode.getPath()));
                    return;
                }
            }

            // Default: add to root
            root.add(newModule);
            reloadExportModel();
            expandTreePath(new TreePath(root.getPath()));
        }
    }

    private void renameSelectedNode() {
        TreePath path = getSelectedTreePath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof ModuleNode) {
            ModuleNode moduleNode = (ModuleNode) node.getUserObject();
            ModuleDialog dialog = new ModuleDialog(SwingUtilities.getWindowAncestor(this), "Rename Module", moduleNode.getName(), moduleNode.getId());
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                moduleNode.setName(dialog.getModuleName());
                moduleNode.setId(dialog.getModuleId());
                notifyNodeChanged(node);
            }
        }
    }

    private void deleteSelectedNode() {
        TreePath path = getSelectedTreePath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getParent() == null) {
            JOptionPane.showMessageDialog(this, "Cannot delete root node", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Delete this node and all its children?", "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // If it's a module, move all its classes back to available
            if (node.getUserObject() instanceof ModuleNode) {
                moveChildrenToAvailable(node);
            } else if (MigrationStructurePanelUtil.isClassNode(node)) {
                // Single class node
                removeClassFromExport(node);
                return; // Don't delete the node, it's already moved
            }

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            parent.remove(node);
            reloadExportModel();
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
        DefaultMutableTreeNode exportRoot = getExportRoot();

        if (exportRoot.getChildCount() == 0) {
            JOptionPane.showMessageDialog(this, "Please create a module first using 'Add Module' button", "No Modules", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Get list of modules
        java.util.List<DefaultMutableTreeNode> modules = new ArrayList<>();
        Enumeration<?> children = exportRoot.children();
        while (children.hasMoreElements()) {
            modules.add((DefaultMutableTreeNode) children.nextElement());
        }

        Object[] options = modules.toArray();
        Object selected = JOptionPane.showInputDialog(this, "Select module for " + schemaClass.getSourceName(), "Add to Module", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (selected != null) {
            DefaultMutableTreeNode targetModule = (DefaultMutableTreeNode) selected;
            addClassToModule(schemaClass, targetModule);
        }
    }

    private void addClassToModule(DOSchemaClass schemaClass, DefaultMutableTreeNode targetModule) {
        // Use util to add class
        boolean added = MigrationStructurePanelUtil.addClassToModule(schemaClass, targetModule, exportedClasses);

        if (!added) {
            JOptionPane.showMessageDialog(this, "Class '" + schemaClass.getSourceName() + "' is already in the export structure.", "Already Exported", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Refresh trees
        reloadExportModel();
        expandTreePath(new TreePath(targetModule.getPath()));
        refreshAvailableTree();
    }

    private void removeSelectedClassFromExport() {
        TreePath path = getSelectedTreePath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (MigrationStructurePanelUtil.isClassNode(node)) {
            removeClassFromExport(node);
        }
    }

    private void removeClassFromExport(DefaultMutableTreeNode treeNode) {
        if (!(treeNode.getUserObject() instanceof ClassNode)) {
            return;
        }

        // Save the expanded state before reload
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) treeNode.getParent();
        TreePath parentPath = null;
        boolean wasExpanded = false;
        if (parent != null) {
            parentPath = new TreePath(parent.getPath());
            wasExpanded = isTreePathExpanded(parentPath);
        }

        // Use util to remove class
        MigrationStructurePanelUtil.removeClassFromExport(treeNode, exportedClasses);

        // Reload and restore expanded state
        reloadExportModel();
        if (wasExpanded && parentPath != null) {
            expandTreePath(parentPath);
        }

        // Refresh available tree
        refreshAvailableTree();
    }

    /**
     * Removes all currently selected classes from export. Handles multiple class
     * selection.
     */
    private void removeSelectedClassesFromExport() {
        TreePath[] selectedPaths = getSelectedTreePaths();
        if (selectedPaths == null || selectedPaths.length == 0) {
            return;
        }

        // Collect all class nodes to remove
        List<DefaultMutableTreeNode> classNodesToRemove = new ArrayList<>();
        Set<TreePath> parentPaths = new LinkedHashSet<>();

        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof ClassNode) {
                classNodesToRemove.add(node);
                // Track parent for expanding later
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                if (parent != null) {
                    parentPaths.add(new TreePath(parent.getPath()));
                }
            }
        }

        if (classNodesToRemove.isEmpty()) {
            return;
        }

        // Remove all selected classes
        for (DefaultMutableTreeNode node : classNodesToRemove) {
            MigrationStructurePanelUtil.removeClassFromExport(node, exportedClasses);
        }

        // Reload and restore expanded state
        reloadExportModel();
        for (TreePath parentPath : parentPaths) {
            expandTreePath(parentPath);
        }

        // Refresh available tree
        refreshAvailableTree();
    }

    /**
     * Opens the configuration dialog for a class export. Allows editing destination
     * file name and export criteria.
     */
    private void configureClassExport(DefaultMutableTreeNode treeNode) {
        if (!(treeNode.getUserObject() instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) treeNode.getUserObject();
        DOSchemaClass schemaClass = classNode.getSchemaClass();

        // Get current configuration (if any stored in the node)
        ClassExportConfig currentConfig = null;
        if (classNode.getExportConfig() != null) {
            currentConfig = classNode.getExportConfig();
        }

        // Show configuration dialog
        ClassExportConfigDialog dialog = new ClassExportConfigDialog((Frame) SwingUtilities.getWindowAncestor(this), schemaClass, currentConfig);
        dialog.setVisible(true);

        // If user clicked OK, store the configuration in the ClassNode
        ClassExportConfig newConfig = dialog.getResult();
        if (newConfig != null) {
            classNode.setExportConfig(newConfig);

            // Update the tree display to show configuration indicator
            notifyNodeChanged(treeNode);

            // Show feedback message
            StringBuilder message = new StringBuilder("Configuration saved for " + schemaClass.source);
            if (newConfig.hasCustomDestination()) {
                message.append("\n→ Destination: ").append(newConfig.getDestinationFileName());
            }
            if (newConfig.hasCriteria()) {
                message.append("\n→ Criteria: ").append(newConfig.getCriteria().size()).append(" filter(s)");
            }

        }
    }

    private void openDetailLayoutDesigner(ClassNode classNode) {
        DOSchemaClass schemaClass = classNode.getSchemaClass();
        ClassExportConfig config = classNode.getExportConfig();
        if (config == null) {
            config = new ClassExportConfig(schemaClass.source);
            classNode.setExportConfig(config);
        }
        DOSchema refSchema = DOSchemaService.getInstance().getReferenceSchema();
        DetailLayoutDesigner designer = new DetailLayoutDesigner(config, schemaClass, refSchema);
        designer.setVisible(true);
    }

    private DefaultMutableTreeNode findClassNodeInExportTree(String className) {
        DefaultMutableTreeNode root = getExportRoot();
        return MigrationStructurePanelUtil.findClassNodeInTree(root, className);
    }

    /**
     * Shows context menu for available tree with Add to Export option
     */
    private void showAvailableContextMenu(MouseEvent e) {
        TreePath path = availableTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        availableTree.setSelectionPath(path);

        JPopupMenu contextMenu = new JPopupMenu();

        // Add to Export option for classes
        if (node.getUserObject() instanceof ClassNode) {
            JMenuItem addToExportItem = new JMenuItem("Add to Export...");
            addToExportItem.addActionListener(evt -> addSelectedClassToExport());
            contextMenu.add(addToExportItem);

            // Severed database tie: removed "View Objects" menu item which used DODatabaseService.
        }

        if (contextMenu.getComponentCount() > 0) {
            contextMenu.show(availableTree, e.getX(), e.getY());
        }
    }

    /**
     * Shows context menu for export tree with Export and Remove options
     */
    private void showExportContextMenu(MouseEvent e) {
        int row = exportTreeTable.rowAtPoint(e.getPoint());
        TreePath path = row >= 0 ? exportTreeTable.getPathForRow(row) : null;
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

        // Only change selection if the right-clicked node is not already selected
        // This preserves multi-selection when right-clicking on a selected item
        TreePath[] selectedPaths = getSelectedTreePaths();
        boolean isAlreadySelected = false;
        if (selectedPaths != null) {
            for (TreePath selectedPath : selectedPaths) {
                if (selectedPath.equals(path)) {
                    isAlreadySelected = true;
                    break;
                }
            }
        }
        if (!isAlreadySelected) {
            setTreeSelection(path);
        }

        JPopupMenu contextMenu = new JPopupMenu();

        // Export option for modules
        if (node.getUserObject() instanceof ModuleNode) {
            // Check if multiple modules are selected
            TreePath[] allSelectedPaths = getSelectedTreePaths();
            int moduleCount = 0;
            if (allSelectedPaths != null) {
                for (TreePath selectedPath : allSelectedPaths) {
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                    if (selectedNode.getUserObject() instanceof ModuleNode) {
                        moduleCount++;
                    }
                }
            }

            // Always use the same export method - handles 1 or multiple modules
            String menuText = moduleCount > 1 ? "Export " + moduleCount + " Modules to XML..." : "Export Module to XML...";
            JMenuItem exportModuleItem = new JMenuItem(menuText);
            exportModuleItem.addActionListener(evt -> exportSelectedModules(activeContext));
            contextMenu.add(exportModuleItem);
        }
        // Options for classes
        else if (node.getUserObject() instanceof ClassNode) {
            ClassNode classNode = (ClassNode) node.getUserObject();

            // Check if multiple classes are selected
            TreePath[] allSelectedPaths = getSelectedTreePaths();
            int classCount = 0;
            if (allSelectedPaths != null) {
                for (TreePath selectedPath : allSelectedPaths) {
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                    if (selectedNode.getUserObject() instanceof ClassNode) {
                        classCount++;
                    }
                }
            }

            JMenuItem configureItem = new JMenuItem("Configure Export...");
            configureItem.addActionListener(evt -> configureClassExport(node));
            contextMenu.add(configureItem);

            JMenuItem designLayoutItem = new JMenuItem("Design Detail View...");
            designLayoutItem.addActionListener(evt -> openDetailLayoutDesigner(classNode));
            contextMenu.add(designLayoutItem);

            contextMenu.addSeparator();

            String removeMenuText = classCount > 1 ? "Remove " + classCount + " Classes from Export" : "Remove from Export";
            JMenuItem removeFromExportItem = new JMenuItem(removeMenuText);
            removeFromExportItem.addActionListener(evt -> removeSelectedClassesFromExport());
            contextMenu.add(removeFromExportItem);

            // Severed database tie: removed "View Objects" menu item which used DODatabaseService.
        }

        if (contextMenu.getComponentCount() > 0) {
            contextMenu.show(exportTreeTable, e.getX(), e.getY());
        }
    }

    /**
     * Exports all currently selected modules in the export tree.
     */
    private void exportSelectedModules(migration4o.database.DODatabaseContext dbContext) {
        TreePath[] selectedPaths = getSelectedTreePaths();

        if (selectedPaths == null || selectedPaths.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select one or more modules to export.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Collect selected modules using util
        List<MigrationStructurePanelUtil.ModuleTreeInfo> moduleInfos = MigrationStructurePanelUtil.collectModulesFromSelection(selectedPaths);

        // Build modules for export using util
        List<MigrationStructurePanelUtil.ModuleExportInfo> modulesToExport = new ArrayList<>();
        for (MigrationStructurePanelUtil.ModuleTreeInfo info : moduleInfos) {
            MigrationModule module = MigrationStructurePanelUtil.buildModuleFromTree(info.treeNode, info.moduleNode);
            if (!module.getClassNames().isEmpty() || !module.getChildModules().isEmpty()) {
                // Build full hierarchical path (e.g., "Activités/Intervention")
                String fullPath = MigrationStructurePanelUtil.buildFullModulePath(info.treeNode);
                modulesToExport.add(new MigrationStructurePanelUtil.ModuleExportInfo(info.moduleNode.getName(), module, fullPath));
            }
        }

        if (modulesToExport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid modules selected. Please select modules (not classes).", "No Modules", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Validate prerequisites
        ValidationResult validation = exportOrchestrator.validateExportPrerequisites();

        if (!validation.isValid()) {
            JOptionPane.showMessageDialog(this, validation.getErrorMessage(), validation.getErrorTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get previous export limit from history (for default value)
        Integer defaultLimit = 50; // default
        String defaultOutputFormat = "XML";
        ExportHistory.ExportParams lastExport = ExportHistory.loadLastExport();
        if (lastExport != null && lastExport.maxObjectsPerClass != null) {
            defaultLimit = lastExport.maxObjectsPerClass;
        }
        if (lastExport != null && lastExport.outputFormat != null && !lastExport.outputFormat.isBlank()) {
            defaultOutputFormat = lastExport.outputFormat;
        }

        // Show confirmation dialog with object limit options
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);

        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();

        // Collect available skip options from the schema
        java.util.List<migration4o.models.schema.DOSchemaField> availableSkipOptions = migration4o.util.SchemaUtil.collectSkipUserOptions(referenceSchema);

        ExportConfirmationDialog confirmDialog = new ExportConfirmationDialog(parentFrame, modulesToExport.size(), defaultLimit, availableSkipOptions, defaultOutputFormat);
        confirmDialog.showDialog();

        if (!confirmDialog.isConfirmed()) {
            return;
        }

        Integer maxObjectsPerClass = confirmDialog.getMaxObjectsPerClass();
        boolean exportNativeIds = confirmDialog.getExportNativeIds();
        java.util.List<migration4o.models.schema.DOSchemaField> selectedSkipOptions = confirmDialog.getSelectedSkipOptions();
        List<String> outputOptions = confirmDialog.getSelectedOutputOptions();
        String outputPath = "output";
        ExportOptions exportOptions = new ExportOptions(maxObjectsPerClass, exportNativeIds, selectedSkipOptions, outputPath, outputOptions, confirmDialog.isApplyUserSelectedFieldExclusions(), confirmDialog.isApplySkipWhenConditions(), confirmDialog.isApplyExportCriteriaFilters(), confirmDialog.isSkipObjectsWithoutExportableFields());

        // Use orchestrator to run export asynchronously
        exportOrchestrator.exportModulesAsync(dbContext, modulesToExport, exportOptions);
    }

    /**
     * Exports all modules in the migration structure.
     */
    private void exportAllModules(migration4o.database.DODatabaseContext dbContext) {
        // Get the root node of the export tree
        DefaultMutableTreeNode root = getExportRoot();

        // Collect only root-level module nodes (not nested ones)
        List<TreePath> rootModulePaths = new ArrayList<>();
        MigrationStructurePanelUtil.collectRootModulePaths(root, new TreePath(root), rootModulePaths);

        if (rootModulePaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No modules found in the migration structure.", "No Modules", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Select all root module paths (nested modules will be exported as part of
        // their parents)
        TreePath[] paths = rootModulePaths.toArray(new TreePath[0]);
        if (paths.length > 0) {
            int[] rows = new int[paths.length];
            for (int i = 0; i < paths.length; i++) {
                rows[i] = exportTreeTable.getRowForPath(paths[i]);
            }
            for (int row : rows) {
                if (row >= 0) {
                    exportTreeTable.addRowSelectionInterval(row, row);
                }
            }
        }

        // Call the existing export selected modules method
        exportSelectedModules(dbContext);
    }

    /**
     * Public entry point to trigger the same behavior as "Export All Modules".
     */
    public void triggerExportAllModules(migration4o.database.DODatabaseContext dbContext) {
        exportAllModules(dbContext);
    }

    private void loadMigrationStructure() {
        try {
            List<MigrationModule> modules = DOModuleService.getInstance().loadModuleStructure(MIGRATION_FORMAT_FILE);

            DefaultMutableTreeNode root = getExportRoot();
            root.removeAllChildren();

            for (MigrationModule module : modules) {
                addModuleToTree(root, module);
            }

            reloadExportModel();
            expandTreePath(exportTreeTable.getPathForRow(0));

            System.out.println("Loaded " + modules.size() + " modules from " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            System.err.println("Error loading migration structure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addModuleToTree(DefaultMutableTreeNode parentNode, MigrationModule module) {
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DOSchema databaseSchema = null;
        MigrationStructurePanelUtil.addModuleToTree(parentNode, module, referenceSchema, databaseSchema, exportedClasses);
    }

    /**
     * Reloads the export tree to refresh object counts when database changes
     */
    private void reloadExportTree() {
        DefaultMutableTreeNode root = getExportRoot();
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DOSchema databaseSchema = null;
        MigrationStructurePanelUtil.updateNodeCounts(root, referenceSchema, databaseSchema);
        reloadExportModel();
    }

    private void saveMigrationStructure() {
        try {
            DefaultMutableTreeNode root = getExportRoot();
            MigrationStructurePanelUtil.saveMigrationStructure(root, MIGRATION_FORMAT_FILE);

            JOptionPane.showMessageDialog(this, "Migration structure saved successfully!\nBackup created.", "Save Successful", JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Saved migration structure to " + MIGRATION_FORMAT_FILE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving migration structure: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * Repeats the last export operation if history exists.
     */
    public void repeatLastExport() {
        // Validate export prerequisites
        ValidationResult validation = exportOrchestrator.validateExportPrerequisites();

        if (!validation.isValid()) {
            JOptionPane.showMessageDialog(this, validation.getErrorMessage(), validation.getErrorTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Use orchestrator to repeat last export
        exportOrchestrator.repeatLastExportAsync(activeContext);
    }

    /**
     * Helper method to get selected tree path from tree table.
     */
    private TreePath getSelectedTreePath() {
        int selectedRow = exportTreeTable.getSelectedRow();
        if (selectedRow >= 0) {
            return exportTreeTable.getPathForRow(selectedRow);
        }
        return null;
    }

    /**
     * Helper method to get all selected tree paths from tree table.
     */
    private TreePath[] getSelectedTreePaths() {
        int[] selectedRows = exportTreeTable.getSelectedRows();
        TreePath[] paths = new TreePath[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            paths[i] = exportTreeTable.getPathForRow(selectedRows[i]);
        }
        return paths;
    }

    /**
     * Helper method to expand a tree path in the tree table.
     */
    private void expandTreePath(TreePath path) {
        exportTreeTable.expandPath(path);
    }

    /**
     * Helper method to check if a path is expanded in the tree table.
     */
    private boolean isTreePathExpanded(TreePath path) {
        return exportTreeTable.isExpanded(path);
    }

    /**
     * Helper method to set selection to a tree path in the tree table.
     */
    private void setTreeSelection(TreePath path) {
        int row = exportTreeTable.getRowForPath(path);
        if (row >= 0) {
            exportTreeTable.setRowSelectionInterval(row, row);
        }
    }

    /**
     * Helper method to get the root node of the export structure.
     */
    private DefaultMutableTreeNode getExportRoot() {
        return (DefaultMutableTreeNode) exportTreeTableModel.getRoot();
    }

    /**
     * Helper method to reload the tree table model.
     */
    private void reloadExportModel() {
        exportTreeTableModel.reloadTree();
    }

    /**
     * Helper method to notify model that a node changed.
     */
    private void notifyNodeChanged(DefaultMutableTreeNode node) {
        exportTreeTableModel.nodeChanged(new TreePath(node.getPath()));
    }

    /**
     * Right-aligned table cell renderer for monetary values. Formats values as
     * "0.50 $"
     */
    private static class RightAlignedRenderer extends javax.swing.table.DefaultTableCellRenderer {
        public RightAlignedRenderer() {
            setHorizontalAlignment(javax.swing.JLabel.RIGHT);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            // Format monetary values if they're not already formatted
            if (value instanceof String && !value.toString().isEmpty()) {
                String strValue = value.toString();
                // If it's not already formatted with $, and it looks like a number, format it
                if (!strValue.contains("$") && strValue.matches(".*\\d.*")) {
                    try {
                        // Try to parse as money format - might already have $ or commas
                        String cleanValue = strValue.replace("$", "").replace(",", "").trim();
                        if (!cleanValue.isEmpty()) {
                            double amount = Double.parseDouble(cleanValue);
                            value = String.format("%.2f $", amount);
                        }
                    } catch (NumberFormatException e) {
                        // Keep original value if parsing fails
                    }
                }
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}
