package migration4o.ui.panels.database_panels.conformity_analysis_panel;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ClassDifference;
import migration4o.models.ui.SyncTreeNode;

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

    private JTree activeTree; // Track which tree was actively clicked
    private Runnable onActiveTreeChanged; // Callback when active tree changes

    public SynchronizedTreePanel(String leftLabel, String rightLabel) {
        this.leftLabel = leftLabel;
        this.rightLabel = rightLabel;
        initializeUI();
        // Default to left tree as active
        this.activeTree = leftTree;
    }

    private void initializeUI() {
        setLayout(new GridLayout(1, 2, 5, 0));

        // Create left tree
        DefaultMutableTreeNode leftRoot = new DefaultMutableTreeNode(leftLabel);
        leftModel = new DefaultTreeModel(leftRoot);
        leftTree = new JTree(leftModel);
        leftTree.setRootVisible(true);
        leftTree.setShowsRootHandles(true);
        leftTree.setCellRenderer(new SynchronizedTreeCellRenderer(true, this));

        leftScrollPane = new JScrollPane(leftTree);
        leftScrollPane.setBorder(BorderFactory.createTitledBorder(leftLabel));

        // Create right tree
        DefaultMutableTreeNode rightRoot = new DefaultMutableTreeNode(rightLabel);
        rightModel = new DefaultTreeModel(rightRoot);
        rightTree = new JTree(rightModel);
        rightTree.setRootVisible(true);
        rightTree.setShowsRootHandles(true);
        rightTree.setCellRenderer(new SynchronizedTreeCellRenderer(false, this));

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

        // Track which tree is actively clicked (for proper detail display and selection
        // highlighting)
        leftTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                activeTree = leftTree;
                leftTree.repaint();
                rightTree.repaint();
                // Always notify when clicking on a tree (even if already active)
                if (onActiveTreeChanged != null) {
                    onActiveTreeChanged.run();
                }
            }
        });

        rightTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                activeTree = rightTree;
                leftTree.repaint();
                rightTree.repaint();
                // Always notify when clicking on a tree (even if already active)
                if (onActiveTreeChanged != null) {
                    onActiveTreeChanged.run();
                }
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
    public void buildTrees(List<ClassDifference> differences,
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
        // Collapse all deeper levels to provide a concise initial view
        for (int i = leftTree.getRowCount() - 1; i >= 1; i--) {
            leftTree.collapseRow(i);
        }
        for (int i = rightTree.getRowCount() - 1; i >= 1; i--) {
            rightTree.collapseRow(i);
        }
    }

    private void buildTreesByPackage(List<ClassDifference> differences,
            DefaultMutableTreeNode leftRoot, DefaultMutableTreeNode rightRoot,
            String refLabel, String cmpLabel) {
        // Group by package
        Map<String, List<ClassDifference>> packageMap = new TreeMap<>();

        for (ClassDifference diff : differences) {
            String className = diff.getClassName();
            String packageName = "(default)";

            if (className != null && className.contains(".")) {
                packageName = className.substring(0, className.lastIndexOf('.'));
            }

            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(diff);
        }

        // Create package nodes
        for (Map.Entry<String, List<ClassDifference>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            List<ClassDifference> classes = entry.getValue();

            // Sort classes
            classes.sort(Comparator.comparing(ClassDifference::getClassName));

            // Analyze package contents to determine color
            boolean leftHasGhost = false;
            boolean leftHasDifferences = false;
            boolean leftHasOnlyInSchema = false;
            boolean leftOnlyHasNotExported = true; // Track if package only contains not exported classes
            boolean rightHasGhost = false;
            boolean rightHasDifferences = false;
            boolean rightOnlyHasNotExported = true; // Right tree should also track not exported status

            for (ClassDifference diff : classes) {
                // Check if this class is not exported (grey) - based on reference schema
                boolean isNotExported = (diff.getReferenceClass() != null && !diff.getReferenceClass().migrate);

                if (!isNotExported) {
                    // At least one exported class exists, so package is not "only grey"
                    leftOnlyHasNotExported = false;
                    rightOnlyHasNotExported = false;
                }

                // Skip grey classes when computing package color
                if (isNotExported) {
                    continue;
                }

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
                            leftHasGhost, leftHasDifferences, true, leftHasOnlyInSchema, false, leftOnlyHasNotExported,
                            null));
            DefaultMutableTreeNode rightPackage = new DefaultMutableTreeNode(
                    new SyncTreeNode(packageName, packageName + " (" + classes.size() + ")",
                            rightHasGhost, rightHasDifferences, true, false, false, rightOnlyHasNotExported, null));

            leftRoot.add(leftPackage);
            rightRoot.add(rightPackage);

            // Add classes
            for (ClassDifference diff : classes) {
                addClassNodes(diff, leftPackage, rightPackage);
            }
        }
    }

    private void buildTreesByInheritance(List<ClassDifference> differences,
            DefaultMutableTreeNode leftRoot, DefaultMutableTreeNode rightRoot,
            String refLabel, String cmpLabel) {
        // Build parent-child map
        Map<String, List<ClassDifference>> childrenMap = new HashMap<>();
        List<ClassDifference> rootClasses = new ArrayList<>();
        Map<String, ClassDifference> diffMap = new HashMap<>();

        for (ClassDifference diff : differences) {
            diffMap.put(diff.getClassName(), diff);

            String parentName = getParentClassName(diff);
            if (parentName == null || parentName.isEmpty()) {
                rootClasses.add(diff);
            } else {
                childrenMap.computeIfAbsent(parentName, k -> new ArrayList<>()).add(diff);
            }
        }

        // Sort root classes
        rootClasses.sort(Comparator.comparing(ClassDifference::getClassName));

        // Build tree recursively
        for (ClassDifference diff : rootClasses) {
            DefaultMutableTreeNode leftClass = new DefaultMutableTreeNode(
                    createClassNode(diff, true));
            DefaultMutableTreeNode rightClass = new DefaultMutableTreeNode(
                    createClassNode(diff, false));

            leftRoot.add(leftClass);
            rightRoot.add(rightClass);

            addChildClasses(diff, leftClass, rightClass, childrenMap);
        }
    }

    private void addClassNodes(ClassDifference diff,
            DefaultMutableTreeNode leftParent,
            DefaultMutableTreeNode rightParent) {
        DefaultMutableTreeNode leftClass = new DefaultMutableTreeNode(createClassNode(diff, true));
        DefaultMutableTreeNode rightClass = new DefaultMutableTreeNode(createClassNode(diff, false));

        leftParent.add(leftClass);
        rightParent.add(rightClass);

        // Add field children
        addFieldNodes(diff, leftClass, rightClass);
    }

    private void addChildClasses(ClassDifference parentDiff,
            DefaultMutableTreeNode leftParent,
            DefaultMutableTreeNode rightParent,
            Map<String, List<ClassDifference>> childrenMap) {
        List<ClassDifference> children = childrenMap.get(parentDiff.getClassName());
        if (children == null)
            return;

        children.sort(Comparator.comparing(ClassDifference::getClassName));

        for (ClassDifference child : children) {
            DefaultMutableTreeNode leftChild = new DefaultMutableTreeNode(createClassNode(child, true));
            DefaultMutableTreeNode rightChild = new DefaultMutableTreeNode(createClassNode(child, false));

            leftParent.add(leftChild);
            rightParent.add(rightChild);

            addChildClasses(child, leftChild, rightChild, childrenMap);
            // Add field nodes to child classes
            addFieldNodes(child, leftChild, rightChild);
        }
    }

    private void addFieldNodes(ClassDifference diff,
            DefaultMutableTreeNode leftParent,
            DefaultMutableTreeNode rightParent) {
        // Get all field names from both schemas
        Set<String> allFieldNames = new HashSet<>();

        // Get reference fields
        Map<String, DOSchemaField> refFieldMap = new HashMap<>();
        if (diff.getReferenceClass() != null && diff.getReferenceClass().fields != null) {
            for (DOSchemaField field : diff.getReferenceClass().fields) {
                refFieldMap.put(field.source, field);
                allFieldNames.add(field.source);
            }
        }

        // Get compared fields
        Map<String, DOSchemaField> cmpFieldMap = new HashMap<>();
        if (diff.getComparedClass() != null && diff.getComparedClass().fields != null) {
            for (DOSchemaField field : diff.getComparedClass().fields) {
                cmpFieldMap.put(field.source, field);
                allFieldNames.add(field.source);
            }
        }

        // Add fields from difference lists
        for (DOSchemaField field : diff.getFieldsOnlyInReference()) {
            allFieldNames.add(field.source);
        }
        for (DOSchemaField field : diff.getFieldsOnlyInCompared()) {
            allFieldNames.add(field.source);
        }

        // Sort field names for consistent display
        List<String> sortedFieldNames = new ArrayList<>(allFieldNames);
        Collections.sort(sortedFieldNames);

        // Create field nodes
        for (String fieldName : sortedFieldNames) {
            DOSchemaField refField = refFieldMap.get(fieldName);
            DOSchemaField cmpField = cmpFieldMap.get(fieldName);

            // Left tree (reference/schema)
            boolean leftIsGhost = (refField == null && diff.getReferenceClass() != null);
            boolean leftHasDiff = false;
            if (refField != null && cmpField != null) {
                // Check if this field has differences
                leftHasDiff = diff.getFieldsWithDifferences().containsKey(fieldName);
            }
            String leftDisplay = fieldName + " : " + (refField != null ? refField.type : "?");
            SyncTreeNode leftFieldNode = new SyncTreeNode(fieldName, leftDisplay, leftIsGhost, leftHasDiff, refField);
            leftParent.add(new DefaultMutableTreeNode(leftFieldNode));

            // Right tree (compared/database)
            boolean rightIsGhost = (cmpField == null && diff.getComparedClass() != null);
            boolean rightHasDiff = leftHasDiff; // Same difference status
            String rightDisplay = fieldName + " : " + (cmpField != null ? cmpField.type : "?");
            SyncTreeNode rightFieldNode = new SyncTreeNode(fieldName, rightDisplay, rightIsGhost, rightHasDiff,
                    cmpField);
            rightParent.add(new DefaultMutableTreeNode(rightFieldNode));
        }
    }

    private SyncTreeNode createClassNode(ClassDifference diff, boolean isLeft) {
        String className = getShortClassName(diff.getClassName());
        // Fix: isGhost means "missing from THIS side"
        // Left (reference): ghost if only in compared (missing from reference)
        // Right (compared): ghost if only in reference (missing from compared)
        boolean isGhost = isLeft ? diff.isOnlyInCompared() : diff.isOnlyInReference();
        boolean hasDifferences = !isGhost && (!diff.getFieldsOnlyInCompared().isEmpty() ||
                !diff.getFieldsOnlyInReference().isEmpty() ||
                !diff.getFieldsWithDifferences().isEmpty());

        // Check if class is not exported (isMigrate=false in reference schema)
        boolean isNotExported = false;
        if (diff.getReferenceClass() != null && !diff.getReferenceClass().migrate) {
            isNotExported = true;
            className += " (not exported)";
        }

        return new SyncTreeNode(diff.getClassName(), className, isGhost, hasDifferences, false, false, isNotExported,
                diff);
    }

    private String getShortClassName(String fullName) {
        if (fullName != null && fullName.contains(".")) {
            return fullName.substring(fullName.lastIndexOf('.') + 1);
        }
        return fullName;
    }

    private String getParentClassName(ClassDifference diff) {
        DOSchemaClass cls = diff.getReferenceClass() != null ? diff.getReferenceClass() : diff.getComparedClass();
        if (cls != null && cls.parentClassName != null && !cls.parentClassName.isEmpty()
                && !cls.parentClassName.equals("Undetermined")) {
            return cls.parentClassName;
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

    public JTree getActiveTree() {
        return activeTree;
    }

    public boolean isLeftTreeActive() {
        // Default to left tree if no tree has been clicked yet
        return activeTree == null || activeTree == leftTree;
    }

    public void setOnActiveTreeChanged(Runnable callback) {
        this.onActiveTreeChanged = callback;
    }

    /**
     * Node data class for synchronized trees
     */
    /**
     * Custom renderer for synchronized trees with context-aware color coding
     */
    private static class SynchronizedTreeCellRenderer extends DefaultTreeCellRenderer {
        private final boolean isLeftTree; // true for schema/reference, false for database/compared
        private final SynchronizedTreePanel panel; // Reference to panel for active tree checking

        // Custom selection colors
        private static final Color ACTIVE_SELECTION_BG = new Color(51, 153, 255); // Blue
        private static final Color PASSIVE_SELECTION_BG = new Color(200, 200, 200); // Gray
        private static final Color ACTIVE_SELECTION_FG = Color.WHITE;
        private static final Color PASSIVE_SELECTION_FG = Color.BLACK;

        public SynchronizedTreeCellRenderer(boolean isLeftTree, SynchronizedTreePanel panel) {
            this.isLeftTree = isLeftTree;
            this.panel = panel;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            // Determine if this tree is the active one
            boolean isActive = (isLeftTree && panel.isLeftTreeActive()) ||
                    (!isLeftTree && !panel.isLeftTreeActive());

            // Override selection colors based on active/passive state
            if (sel) {
                if (isActive) {
                    setBackgroundSelectionColor(ACTIVE_SELECTION_BG);
                    setTextSelectionColor(ACTIVE_SELECTION_FG);
                } else {
                    setBackgroundSelectionColor(PASSIVE_SELECTION_BG);
                    setTextSelectionColor(PASSIVE_SELECTION_FG);
                }
            }

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof SyncTreeNode) {
                    SyncTreeNode syncNode = (SyncTreeNode) userObject;
                    ClassDifference diff = syncNode.getDifference();

                    String color;
                    String style = "";
                    String suffix = "";

                    if (syncNode.isField()) {
                        // Field node - color based on field status
                        if (syncNode.isGhost()) {
                            color = "#CC0000"; // RED: Missing field
                            style = "i"; // italic
                            suffix = " (missing)";
                        } else if (syncNode.hasDifferences()) {
                            color = "#FF8C00"; // ORANGE: Field has differences
                        } else {
                            color = "#008000"; // GREEN: Field matches
                        }
                    } else if (syncNode.isPackage()) {
                        // Package node - color based on contents (priority: grey > red > orange > blue
                        // > green)
                        if (syncNode.hasOnlyNotExported()) {
                            color = "#969696"; // GREY: only contains not exported classes
                            style = "bi"; // bold italic
                        } else if (syncNode.isGhost()) {
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
                    } else if (syncNode.isNotExported()) {
                        // GREY: Not exported class (isMigrate=false)
                        color = "#969696"; // Grey (150, 150, 150)
                        style = "i"; // italic
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
