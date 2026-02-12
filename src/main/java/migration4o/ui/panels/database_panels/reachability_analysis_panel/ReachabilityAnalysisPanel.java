package migration4o.ui.panels.database_panels.reachability_analysis_panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.modules.DOModuleService;
import migration4o.util.ModuleUtil;

/**
 * Panel for displaying reachability analysis.
 * Shows reference chains with module membership indicated by color coding.
 * Each top-level class shows the classes it references, recursively expanded
 * until a class in a module is reached.
 */
public class ReachabilityAnalysisPanel extends JPanel {

    private final DOSchema databaseSchema;
    private final DOSchema referenceSchema;

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private JRadioButton showAllRadio;
    private JRadioButton showReachedRadio;
    private JRadioButton showUnreachedRadio;

    private enum FilterMode {
        SHOW_ALL,
        SHOW_REACHED,
        SHOW_UNREACHED
    }

    private FilterMode currentFilterMode = FilterMode.SHOW_ALL;

    public ReachabilityAnalysisPanel(DOSchema databaseSchema, DOSchema referenceSchema) {
        this.databaseSchema = databaseSchema;
        this.referenceSchema = referenceSchema;

        try {
            initializeUI();
            buildTree();
        } catch (Exception e) {
            System.err.println("Error initializing ReachabilityAnalysisPanel: " + e.getMessage());
            e.printStackTrace();
            // Create minimal UI on error
            setLayout(new BorderLayout());
            JLabel errorLabel = new JLabel("Error loading reachability analysis: " + e.getMessage());
            errorLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            add(errorLabel, BorderLayout.CENTER);
        }
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create top panel for filter radio buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Filter"));

        showAllRadio = new JRadioButton("Show all", true);
        showReachedRadio = new JRadioButton("Show reached");
        showUnreachedRadio = new JRadioButton("Show unreached");

        ButtonGroup filterGroup = new ButtonGroup();
        filterGroup.add(showAllRadio);
        filterGroup.add(showReachedRadio);
        filterGroup.add(showUnreachedRadio);

        showAllRadio.addActionListener(e -> {
            currentFilterMode = FilterMode.SHOW_ALL;
            applyFilter();
        });

        showReachedRadio.addActionListener(e -> {
            currentFilterMode = FilterMode.SHOW_REACHED;
            applyFilter();
        });

        showUnreachedRadio.addActionListener(e -> {
            currentFilterMode = FilterMode.SHOW_UNREACHED;
            applyFilter();
        });

        topPanel.add(showAllRadio);
        topPanel.add(showReachedRadio);
        topPanel.add(showUnreachedRadio);

        // Create help label
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel helpLabel = new JLabel(
                "<html><b style='background-color: green; color: white; padding: 2px 4px;'>&nbsp;Green&nbsp;</b> Top-level class that reaches a module&nbsp;&nbsp;&nbsp;"
                        +
                        "<b style='background-color: #0064C8; color: white; padding: 2px 4px;'>&nbsp;Blue&nbsp;</b> Class found in modules&nbsp;&nbsp;&nbsp;"
                        +
                        "<b style='color: #0064C8;'>→ Module</b> Module name</html>");
        helpPanel.add(helpLabel);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topPanel, BorderLayout.NORTH);
        northPanel.add(helpPanel, BorderLayout.SOUTH);

        add(northPanel, BorderLayout.NORTH);

        // Create tree
        rootNode = new DefaultMutableTreeNode("Reference Hierarchy");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ReachabilityTreeCellRenderer());

        // Add double-click listener to navigate to class in schema editor
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        Object userObject = node.getUserObject();

                        if (userObject instanceof ClassNodeData) {
                            ClassNodeData data = (ClassNodeData) userObject;
                            String className = data.getClassName();

                            // Get MainWindow instance and navigate to the class
                            migration4o.ui.main.MainWindow mainWindow = migration4o.ui.main.MainWindow.getInstance();
                            if (mainWindow != null) {
                                mainWindow.detachAndNavigateToReferenceSchemaClass(className);
                            }
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void buildTree() {
        rootNode.removeAllChildren();

        // Get all classes from database schema, sorted alphabetically
        DOSchemaClass[] classes = databaseSchema.getClasses();
        if (classes == null || classes.length == 0) {
            treeModel.reload();
            return;
        }

        // Sort classes alphabetically by source name
        DOSchemaClass[] sortedClasses = classes.clone();
        Arrays.sort(sortedClasses, Comparator.comparing(c -> c.source));

        // Create top-level nodes for each class
        for (DOSchemaClass dbClass : sortedClasses) {
            DefaultMutableTreeNode topLevelNode = new DefaultMutableTreeNode(
                    new ClassNodeData(dbClass.source, true));

            // Check if the top-level class itself is in a module
            DOSchemaClass refClass = referenceSchema.findClassByName(dbClass.source);
            boolean topLevelInModule = false;
            String topLevelModuleName = null;

            if (refClass != null) {
                topLevelInModule = ModuleUtil.isClassListedInAnyModule(refClass);
                if (topLevelInModule) {
                    topLevelModuleName = ModuleUtil.findModuleForClass(refClass);
                    ((ClassNodeData) topLevelNode.getUserObject()).setInModule(true);
                    ((ClassNodeData) topLevelNode.getUserObject()).setReached(true); // Blue classes are reached
                }
            }

            // If top-level is in a module, add module name node and don't expand further
            if (topLevelInModule && topLevelModuleName != null) {
                DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                        new ModuleNodeData(topLevelModuleName));
                topLevelNode.add(moduleNode);
            } else {
                // Build reference chain for this class
                Set<String> visited = new HashSet<>();
                visited.add(dbClass.source); // Prevent cycles
                buildReferenceChain(topLevelNode, dbClass.source, visited);

                // Check if any node in the branch is in modules (makes top-level GREEN)
                boolean hasModuleClass = checkForModuleClasses(topLevelNode);
                ((ClassNodeData) topLevelNode.getUserObject()).setReached(hasModuleClass);
            }

            rootNode.add(topLevelNode);
        }

        treeModel.reload();
        collapseAll();
    }

    /**
     * Recursively builds the reference chain by following the class's
     * schemaReferences
     */
    private void buildReferenceChain(DefaultMutableTreeNode parentNode, String className, Set<String> visited) {
        // Find the class in reference schema to get its references
        DOSchemaClass refClass = referenceSchema.findClassByName(className);
        if (refClass == null || refClass.schemaReferences == null) {
            return;
        }

        for (var reference : refClass.schemaReferences) {
            String referencedClassName = reference.className;

            // Avoid cycles
            if (visited.contains(referencedClassName)) {
                continue;
            }

            // Create node for referenced class
            DefaultMutableTreeNode referencedNode = new DefaultMutableTreeNode(
                    new ClassNodeData(referencedClassName, false));

            // Check if referenced class is in any module
            DOSchemaClass referencedClass = referenceSchema.findClassByName(referencedClassName);
            boolean inModule = false;
            String moduleName = null;

            if (referencedClass != null) {
                inModule = ModuleUtil.isClassListedInAnyModule(referencedClass);
                if (inModule) {
                    moduleName = ModuleUtil.findModuleForClass(referencedClass);
                    ((ClassNodeData) referencedNode.getUserObject()).setInModule(true);
                }
            }

            parentNode.add(referencedNode);

            // If this class is in a module, add a child node showing the module name
            if (inModule && moduleName != null) {
                DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                        new ModuleNodeData(moduleName));
                referencedNode.add(moduleNode);
            }

            // Don't continue expanding if we reached a module class
            // (the module node is the end of the chain)
            if (!inModule) {
                // Recursively follow this class's references
                Set<String> newVisited = new HashSet<>(visited);
                newVisited.add(referencedClassName);
                buildReferenceChain(referencedNode, referencedClassName, newVisited);
            }
        }
    }

    // /**
    // * Finds all classes in the reference schema that reference the given class
    // */
    // private List<String> findClassesThatReference(String targetClassName) {
    // List<String> referencingClasses = new ArrayList<>();

    // DOSchemaClass[] classes = referenceSchema.getClasses();
    // if (classes == null) {
    // return referencingClasses;
    // }

    // for (DOSchemaClass cls : classes) {
    // if (cls.schemaReferences != null) {
    // for (var reference : cls.schemaReferences) {
    // if (targetClassName.equals(reference.className)) {
    // // This class references our target class
    // referencingClasses.add(cls.source);
    // break; // Only add each class once
    // }
    // }
    // }
    // }

    // return referencingClasses;
    // }

    /**
     * Recursively checks if any node in the tree contains a class that's in modules
     */
    private boolean checkForModuleClasses(DefaultMutableTreeNode node) {
        ClassNodeData data = (ClassNodeData) node.getUserObject();

        // Check current node
        if (data.isInModule()) {
            return true;
        }

        // Check children recursively
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (checkForModuleClasses(child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Applies the current filter mode to show/hide nodes
     */
    private void applyFilter() {
        buildTree(); // Rebuild to apply filter

        // After rebuild, expand/collapse based on filter
        if (currentFilterMode != FilterMode.SHOW_ALL) {
            // Remove nodes that don't match filter
            List<TreeNode> nodesToRemove = new ArrayList<>();

            for (int i = 0; i < rootNode.getChildCount(); i++) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                ClassNodeData data = (ClassNodeData) node.getUserObject();

                boolean shouldShow = false;
                if (currentFilterMode == FilterMode.SHOW_REACHED && data.isReached()) {
                    shouldShow = true;
                } else if (currentFilterMode == FilterMode.SHOW_UNREACHED && !data.isReached()) {
                    shouldShow = true;
                }

                if (!shouldShow) {
                    nodesToRemove.add(node);
                }
            }

            // Remove filtered nodes
            for (TreeNode node : nodesToRemove) {
                rootNode.remove((DefaultMutableTreeNode) node);
            }
        }

        treeModel.reload();
        collapseAll();
    }

    /**
     * Collapses all nodes in the tree
     */
    private void collapseAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.collapseRow(i);
        }
    }

    /**
     * Data object for tree nodes
     */
    private static class ClassNodeData {
        private final String className;
        private final boolean isTopLevel;
        private boolean inModule = false;
        private boolean reached = false;

        public ClassNodeData(String className, boolean isTopLevel) {
            this.className = className;
            this.isTopLevel = isTopLevel;
        }

        public String getClassName() {
            return className;
        }

        public boolean isTopLevel() {
            return isTopLevel;
        }

        public boolean isInModule() {
            return inModule;
        }

        public void setInModule(boolean inModule) {
            this.inModule = inModule;
        }

        public boolean isReached() {
            return reached;
        }

        public void setReached(boolean reached) {
            this.reached = reached;
        }

        @Override
        public String toString() {
            return className;
        }
    }

    /**
     * Data object for module name nodes
     */
    private static class ModuleNodeData {
        private final String moduleName;

        public ModuleNodeData(String moduleName) {
            this.moduleName = moduleName;
        }

        public String getModuleName() {
            return moduleName;
        }

        @Override
        public String toString() {
            return "→ " + moduleName;
        }
    }

    /**
     * Custom cell renderer for color-coding nodes
     */
    private static class ReachabilityTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof ClassNodeData) {
                    ClassNodeData data = (ClassNodeData) userObject;

                    if (!selected) {
                        // Nodes that are in modules: White text on blue background
                        // (this takes priority over top-level reached)
                        if (data.isInModule()) {
                            setForeground(Color.WHITE);
                            setBackground(new Color(0, 200, 100)); // Blue
                            setBackgroundNonSelectionColor(new Color(0, 100, 200));
                            setOpaque(true);
                        }
                        // Top-level nodes that are reached: White text on green background
                        else if (data.isTopLevel() && data.isReached()) {
                            setForeground(Color.WHITE);
                            setBackground(new Color(0, 128, 0)); // Dark green
                            setBackgroundNonSelectionColor(new Color(0, 128, 0));
                            setOpaque(true);
                        }
                        // Default color for other nodes
                        else {
                            setForeground(Color.BLACK);
                            setBackground(tree.getBackground());
                            setBackgroundNonSelectionColor(tree.getBackground());
                            setOpaque(false);
                        }
                    } else {
                        // When selected, use default selection colors
                        setOpaque(true);
                    }
                } else if (userObject instanceof ModuleNodeData) {
                    // Module name nodes: Blue text, no special background
                    if (!selected) {
                        setForeground(new Color(0, 100, 200)); // Blue
                        setBackground(tree.getBackground());
                        setBackgroundNonSelectionColor(tree.getBackground());
                        setOpaque(false);
                    } else {
                        setOpaque(true);
                    }
                }
            }

            return this;
        }
    }
}
