package migration4o.ui.comparison;

import migration4o.models.schema.DOSchemaClass;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * A panel containing two synchronized JTrees that scroll and expand/collapse
 * together.
 * Handles ghost nodes for classes that exist in one tree but not the other.
 */
public class SynchronizedTreePanel extends JPanel {

    private JTree leftTree;
    private JTree rightTree;
    private DefaultTreeModel leftModel;
    private DefaultTreeModel rightModel;
    private JScrollPane leftScrollPane;
    private JScrollPane rightScrollPane;

    private String leftLabel;
    private String rightLabel;

    private boolean synchronizingExpansion = false;
    private boolean synchronizingSelection = false;
    private boolean synchronizingScroll = false;

    public SynchronizedTreePanel(String leftLabel, String rightLabel) {
        this.leftLabel = leftLabel;
        this.rightLabel = rightLabel;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new GridLayout(1, 2, 5, 0));

        // Create left tree
        DefaultMutableTreeNode leftRoot = new DefaultMutableTreeNode(leftLabel);
        leftModel = new DefaultTreeModel(leftRoot);
        leftTree = new JTree(leftModel);
        leftTree.setRootVisible(true);
        leftTree.setShowsRootHandles(true);
        leftTree.setCellRenderer(new SynchronizedTreeCellRenderer(true));

        leftScrollPane = new JScrollPane(leftTree);
        leftScrollPane.setBorder(BorderFactory.createTitledBorder(leftLabel));

        // Create right tree
        DefaultMutableTreeNode rightRoot = new DefaultMutableTreeNode(rightLabel);
        rightModel = new DefaultTreeModel(rightRoot);
        rightTree = new JTree(rightModel);
        rightTree.setRootVisible(true);
        rightTree.setShowsRootHandles(true);
        rightTree.setCellRenderer(new SynchronizedTreeCellRenderer(false));

        rightScrollPane = new JScrollPane(rightTree);
        rightScrollPane.setBorder(BorderFactory.createTitledBorder(rightLabel));

        // Synchronize expansion
        leftTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                if (!synchronizingExpansion) {
                    synchronizingExpansion = true;
                    syncExpansion(event.getPath(), true, leftTree, rightTree);
                    synchronizingExpansion = false;
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (!synchronizingExpansion) {
                    synchronizingExpansion = true;
                    syncExpansion(event.getPath(), false, leftTree, rightTree);
                    synchronizingExpansion = false;
                }
            }
        });

        rightTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                if (!synchronizingExpansion) {
                    synchronizingExpansion = true;
                    syncExpansion(event.getPath(), true, rightTree, leftTree);
                    synchronizingExpansion = false;
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (!synchronizingExpansion) {
                    synchronizingExpansion = true;
                    syncExpansion(event.getPath(), false, rightTree, leftTree);
                    synchronizingExpansion = false;
                }
            }
        });

        // Synchronize scrolling
        leftScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!synchronizingScroll) {
                synchronizingScroll = true;
                rightScrollPane.getVerticalScrollBar().setValue(e.getValue());
                synchronizingScroll = false;
            }
        });

        rightScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!synchronizingScroll) {
                synchronizingScroll = true;
                leftScrollPane.getVerticalScrollBar().setValue(e.getValue());
                synchronizingScroll = false;
            }
        });

        // Synchronize selection
        leftTree.addTreeSelectionListener(e -> {
            if (!synchronizingSelection) {
                synchronizingSelection = true;
                syncSelection(leftTree, rightTree);
                synchronizingSelection = false;
            }
        });

        rightTree.addTreeSelectionListener(e -> {
            if (!synchronizingSelection) {
                synchronizingSelection = true;
                syncSelection(rightTree, leftTree);
                synchronizingSelection = false;
            }
        });

        add(leftScrollPane);
        add(rightScrollPane);
    }

    private void syncExpansion(TreePath sourcePath, boolean expand, JTree sourceTree, JTree targetTree) {
        // Find corresponding path in target tree
        TreePath targetPath = findCorrespondingPath(sourcePath, sourceTree, targetTree);
        if (targetPath != null) {
            if (expand) {
                targetTree.expandPath(targetPath);
            } else {
                targetTree.collapsePath(targetPath);
            }
        }
    }

    private void syncSelection(JTree sourceTree, JTree targetTree) {
        TreePath sourcePath = sourceTree.getSelectionPath();
        if (sourcePath != null) {
            TreePath targetPath = findCorrespondingPath(sourcePath, sourceTree, targetTree);
            if (targetPath != null) {
                targetTree.setSelectionPath(targetPath);
                targetTree.scrollPathToVisible(targetPath);
            }
        }
    }

    private TreePath findCorrespondingPath(TreePath sourcePath, JTree sourceTree, JTree targetTree) {
        Object[] sourcePathArray = sourcePath.getPath();
        DefaultMutableTreeNode targetRoot = (DefaultMutableTreeNode) targetTree.getModel().getRoot();

        // Start from root
        DefaultMutableTreeNode currentTarget = targetRoot;
        TreePath currentPath = new TreePath(currentTarget);

        // Skip root (index 0) and match subsequent nodes
        for (int i = 1; i < sourcePathArray.length; i++) {
            DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) sourcePathArray[i];
            String sourceKey = getNodeKey(sourceNode);

            // Find matching child in target
            boolean found = false;
            for (int j = 0; j < currentTarget.getChildCount(); j++) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) currentTarget.getChildAt(j);
                String childKey = getNodeKey(childNode);

                if (sourceKey.equals(childKey)) {
                    currentTarget = childNode;
                    currentPath = currentPath.pathByAddingChild(currentTarget);
                    found = true;
                    break;
                }
            }

            if (!found) {
                return null; // Path doesn't exist in target tree
            }
        }

        return currentPath;
    }

    private String getNodeKey(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof SyncTreeNode) {
            return ((SyncTreeNode) userObject).getKey();
        }
        return userObject != null ? userObject.toString() : "";
    }

    /**
     * Build synchronized trees from class differences.
     * Creates aligned trees with ghost nodes for missing classes.
     */
    public void buildTrees(List<SchemaComparison.ClassDifference> differences,
            String refLabel, String cmpLabel,
            boolean groupByPackage) {
        DefaultMutableTreeNode leftRoot = (DefaultMutableTreeNode) leftModel.getRoot();
        DefaultMutableTreeNode rightRoot = (DefaultMutableTreeNode) rightModel.getRoot();

        leftRoot.removeAllChildren();
        rightRoot.removeAllChildren();

        if (groupByPackage) {
            buildTreesByPackage(differences, leftRoot, rightRoot, refLabel, cmpLabel);
        } else {
            buildTreesByInheritance(differences, leftRoot, rightRoot, refLabel, cmpLabel);
        }

        leftModel.reload();
        rightModel.reload();

        // Expand root
        leftTree.expandRow(0);
        rightTree.expandRow(0);
    }

    private void buildTreesByPackage(List<SchemaComparison.ClassDifference> differences,
            DefaultMutableTreeNode leftRoot, DefaultMutableTreeNode rightRoot,
            String refLabel, String cmpLabel) {
        // Group by package
        Map<String, List<SchemaComparison.ClassDifference>> packageMap = new TreeMap<>();

        for (SchemaComparison.ClassDifference diff : differences) {
            String className = diff.getClassName();
            String packageName = "(default)";

            if (className != null && className.contains(".")) {
                packageName = className.substring(0, className.lastIndexOf('.'));
            }

            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(diff);
        }

        // Create package nodes
        for (Map.Entry<String, List<SchemaComparison.ClassDifference>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            List<SchemaComparison.ClassDifference> classes = entry.getValue();

            // Sort classes
            classes.sort(Comparator.comparing(SchemaComparison.ClassDifference::getClassName));

            // Analyze package contents to determine color
            boolean leftHasGhost = false;
            boolean leftHasDifferences = false;
            boolean leftHasOnlyInSchema = false;
            boolean rightHasGhost = false;
            boolean rightHasDifferences = false;

            for (SchemaComparison.ClassDifference diff : classes) {
                // For left tree (schema/reference)
                if (diff.isOnlyInReference()) {
                    leftHasOnlyInSchema = true;
                } else if (diff.isOnlyInCompared()) {
                    leftHasGhost = true;
                } else if (!diff.getFieldsOnlyInCompared().isEmpty() ||
                        !diff.getFieldsOnlyInReference().isEmpty() ||
                        !diff.getFieldsWithDifferences().isEmpty()) {
                    leftHasDifferences = true;
                }

                // For right tree (database/compared)
                if (diff.isOnlyInCompared()) {
                    // Only in compared is normal for right tree
                } else if (diff.isOnlyInReference()) {
                    rightHasGhost = true;
                } else if (!diff.getFieldsOnlyInCompared().isEmpty() ||
                        !diff.getFieldsOnlyInReference().isEmpty() ||
                        !diff.getFieldsWithDifferences().isEmpty()) {
                    rightHasDifferences = true;
                }
            }

            // Create package nodes with status
            DefaultMutableTreeNode leftPackage = new DefaultMutableTreeNode(
                    new SyncTreeNode(packageName, packageName + " (" + classes.size() + ")",
                            leftHasGhost, leftHasDifferences, true, leftHasOnlyInSchema, null));
            DefaultMutableTreeNode rightPackage = new DefaultMutableTreeNode(
                    new SyncTreeNode(packageName, packageName + " (" + classes.size() + ")",
                            rightHasGhost, rightHasDifferences, true, false, null));

            leftRoot.add(leftPackage);
            rightRoot.add(rightPackage);

            // Add classes
            for (SchemaComparison.ClassDifference diff : classes) {
                addClassNodes(diff, leftPackage, rightPackage);
            }
        }
    }

    private void buildTreesByInheritance(List<SchemaComparison.ClassDifference> differences,
            DefaultMutableTreeNode leftRoot, DefaultMutableTreeNode rightRoot,
            String refLabel, String cmpLabel) {
        // Build parent-child map
        Map<String, List<SchemaComparison.ClassDifference>> childrenMap = new HashMap<>();
        List<SchemaComparison.ClassDifference> rootClasses = new ArrayList<>();
        Map<String, SchemaComparison.ClassDifference> diffMap = new HashMap<>();

        for (SchemaComparison.ClassDifference diff : differences) {
            diffMap.put(diff.getClassName(), diff);

            String parentName = getParentClassName(diff);
            if (parentName == null || parentName.isEmpty()) {
                rootClasses.add(diff);
            } else {
                childrenMap.computeIfAbsent(parentName, k -> new ArrayList<>()).add(diff);
            }
        }

        // Sort root classes
        rootClasses.sort(Comparator.comparing(SchemaComparison.ClassDifference::getClassName));

        // Build tree recursively
        for (SchemaComparison.ClassDifference diff : rootClasses) {
            DefaultMutableTreeNode leftClass = new DefaultMutableTreeNode(
                    createClassNode(diff, true));
            DefaultMutableTreeNode rightClass = new DefaultMutableTreeNode(
                    createClassNode(diff, false));

            leftRoot.add(leftClass);
            rightRoot.add(rightClass);

            addChildClasses(diff, leftClass, rightClass, childrenMap);
        }
    }

    private void addClassNodes(SchemaComparison.ClassDifference diff,
            DefaultMutableTreeNode leftParent,
            DefaultMutableTreeNode rightParent) {
        DefaultMutableTreeNode leftClass = new DefaultMutableTreeNode(createClassNode(diff, true));
        DefaultMutableTreeNode rightClass = new DefaultMutableTreeNode(createClassNode(diff, false));

        leftParent.add(leftClass);
        rightParent.add(rightClass);
    }

    private void addChildClasses(SchemaComparison.ClassDifference parentDiff,
            DefaultMutableTreeNode leftParent,
            DefaultMutableTreeNode rightParent,
            Map<String, List<SchemaComparison.ClassDifference>> childrenMap) {
        List<SchemaComparison.ClassDifference> children = childrenMap.get(parentDiff.getClassName());
        if (children == null)
            return;

        children.sort(Comparator.comparing(SchemaComparison.ClassDifference::getClassName));

        for (SchemaComparison.ClassDifference child : children) {
            DefaultMutableTreeNode leftChild = new DefaultMutableTreeNode(createClassNode(child, true));
            DefaultMutableTreeNode rightChild = new DefaultMutableTreeNode(createClassNode(child, false));

            leftParent.add(leftChild);
            rightParent.add(rightChild);

            addChildClasses(child, leftChild, rightChild, childrenMap);
        }
    }

    private SyncTreeNode createClassNode(SchemaComparison.ClassDifference diff, boolean isLeft) {
        String className = getShortClassName(diff.getClassName());
        boolean isGhost = isLeft ? diff.isOnlyInReference() : diff.isOnlyInCompared();
        boolean hasDifferences = !isGhost && (!diff.getFieldsOnlyInCompared().isEmpty() ||
                !diff.getFieldsOnlyInReference().isEmpty() ||
                !diff.getFieldsWithDifferences().isEmpty());

        return new SyncTreeNode(diff.getClassName(), className, isGhost, hasDifferences, diff);
    }

    private String getShortClassName(String fullName) {
        if (fullName != null && fullName.contains(".")) {
            return fullName.substring(fullName.lastIndexOf('.') + 1);
        }
        return fullName;
    }

    private String getParentClassName(SchemaComparison.ClassDifference diff) {
        DOSchemaClass cls = diff.getReferenceClass() != null ? diff.getReferenceClass() : diff.getComparedClass();
        if (cls != null && cls.getParentClass() != null && !cls.getParentClass().isEmpty()
                && !cls.getParentClass().equals("Undetermined")) {
            return cls.getParentClass();
        }
        return null;
    }

    public void addLeftTreeSelectionListener(javax.swing.event.TreeSelectionListener listener) {
        leftTree.addTreeSelectionListener(listener);
    }

    public void addRightTreeSelectionListener(javax.swing.event.TreeSelectionListener listener) {
        rightTree.addTreeSelectionListener(listener);
    }

    public JTree getLeftTree() {
        return leftTree;
    }

    public JTree getRightTree() {
        return rightTree;
    }

    /**
     * Node data class for synchronized trees
     */
    public static class SyncTreeNode {
        private String key; // Unique key for matching nodes
        private String displayName; // Display text
        private boolean isGhost; // True if class doesn't exist in this schema
        private boolean hasDifferences; // True if class has field differences
        private boolean isPackage; // True if this is a package node
        private boolean hasOnlyInSchema; // True if package contains classes only in schema (for blue color)
        private SchemaComparison.ClassDifference difference; // Associated difference object

        public SyncTreeNode(String key, String displayName, boolean isGhost, boolean hasDifferences) {
            this(key, displayName, isGhost, hasDifferences, false, false, null);
        }

        public SyncTreeNode(String key, String displayName, boolean isGhost,
                boolean hasDifferences, SchemaComparison.ClassDifference difference) {
            this(key, displayName, isGhost, hasDifferences, false, false, difference);
        }

        public SyncTreeNode(String key, String displayName, boolean isGhost,
                boolean hasDifferences, boolean isPackage, boolean hasOnlyInSchema,
                SchemaComparison.ClassDifference difference) {
            this.key = key;
            this.displayName = displayName;
            this.isGhost = isGhost;
            this.hasDifferences = hasDifferences;
            this.isPackage = isPackage;
            this.hasOnlyInSchema = hasOnlyInSchema;
            this.difference = difference;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isGhost() {
            return isGhost;
        }

        public boolean hasDifferences() {
            return hasDifferences;
        }

        public SchemaComparison.ClassDifference getDifference() {
            return difference;
        }

        public boolean isPackage() {
            return isPackage;
        }

        public boolean hasOnlyInSchema() {
            return hasOnlyInSchema;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Custom renderer for synchronized trees with context-aware color coding
     */
    private static class SynchronizedTreeCellRenderer extends DefaultTreeCellRenderer {
        private final boolean isLeftTree; // true for schema/reference, false for database/compared

        public SynchronizedTreeCellRenderer(boolean isLeftTree) {
            this.isLeftTree = isLeftTree;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof SyncTreeNode) {
                    SyncTreeNode syncNode = (SyncTreeNode) userObject;
                    SchemaComparison.ClassDifference diff = syncNode.getDifference();

                    String color;
                    String style = "";
                    String suffix = "";

                    if (syncNode.isPackage()) {
                        // Package node - color based on contents (priority: red > orange > blue >
                        // green)
                        if (syncNode.isGhost()) {
                            color = "#CC0000"; // RED: contains missing classes
                            style = "b";
                        } else if (syncNode.hasDifferences()) {
                            color = "#FF8C00"; // ORANGE: contains classes with differences
                            style = "b";
                        } else if (syncNode.hasOnlyInSchema()) {
                            color = "#0066CC"; // BLUE: contains classes only in schema
                            style = "b";
                        } else {
                            color = "#008000"; // GREEN: all classes match
                            style = "b";
                        }
                    } else if (syncNode.isGhost()) {
                        // RED: Missing class (ghost in this tree)
                        color = "#CC0000";
                        style = "i"; // italic
                        suffix = " (missing)";
                    } else if (syncNode.hasDifferences()) {
                        // ORANGE: Field differences
                        color = "#FF8C00";
                        style = "b"; // bold
                    } else if (diff != null) {
                        if (isLeftTree && diff.isOnlyInReference()) {
                            // BLUE: Valid class, found only in schema (left tree only)
                            color = "#0066CC";
                        } else if (!isLeftTree && diff.isOnlyInCompared()) {
                            // This case shouldn't show as green on right since it's only in DB
                            // We'll show it as normal since it's valid in the database
                            color = "#008000"; // GREEN for valid
                        } else {
                            // GREEN: Valid class, found in both with no differences
                            color = "#008000";
                        }
                    } else {
                        // Default: normal text
                        setText(syncNode.getDisplayName());
                        return this;
                    }

                    // Build HTML string
                    StringBuilder html = new StringBuilder("<html>");
                    if (!style.isEmpty()) {
                        html.append("<").append(style).append(">");
                    }
                    html.append("<font color='").append(color).append("'>");
                    html.append(syncNode.getDisplayName()).append(suffix);
                    html.append("</font>");
                    if (!style.isEmpty()) {
                        html.append("</").append(style).append(">");
                    }
                    html.append("</html>");

                    setText(html.toString());
                }
            }

            return this;
        }
    }
}
