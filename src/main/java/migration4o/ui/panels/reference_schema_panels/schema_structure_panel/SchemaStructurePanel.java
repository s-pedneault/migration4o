package migration4o.ui.panels.reference_schema_panels.schema_structure_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaFieldReference;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.schema.modules.DOModuleService;
import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel that displays the schema structure as a tree, showing class
 * relationships through field references to help understand object
 * reachability.
 */
public class SchemaStructurePanel extends JPanel {

    /**
     * Custom tree node that stores both display text and full class name for
     * coloring.
     */
    private static class ClassTreeNode {
        final String displayText;
        final String fullClassName; // null if not a class reference
        final DOSchemaClass schemaClass; // The actual class, for lazy expansion
        final boolean isNonEmbeddedReference; // true if this is an IDEntite
                                              // with embedContents=false
        boolean childrenLoaded = false; // Track if children have been loaded

        ClassTreeNode(String displayText, String fullClassName, DOSchemaClass schemaClass, boolean isNonEmbeddedReference) {
            this.displayText = displayText;
            this.fullClassName = fullClassName;
            this.schemaClass = schemaClass;
            this.isNonEmbeddedReference = isNonEmbeddedReference;
        }

        ClassTreeNode(String displayText, DOSchemaClass schemaClass, boolean isNonEmbeddedReference) {
            this(displayText, schemaClass != null ? schemaClass.attributes.source : null, schemaClass, isNonEmbeddedReference);
        }

        ClassTreeNode(String displayText, DOSchemaClass schemaClass) {
            this(displayText, schemaClass, false);
        }

        ClassTreeNode(String displayText) {
            this(displayText, null, null, false);
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    private DOSchema schema;
    private JTree structureTree;
    private DefaultTreeModel treeModel;
    private Set<String> unreachedClasses; // Track classes not reached during
                                          // tree building
    private Set<String> classesInModules; // Track classes that are listed in
                                          // any module

    public SchemaStructurePanel(DOSchema schema) {
        this.schema = schema;
        this.unreachedClasses = new HashSet<>();
        this.classesInModules = new HashSet<>();
        initializeUI();
        loadClassesFromModules();
        buildSchemaStructure();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // Create tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Schema Structure");
        treeModel = new DefaultTreeModel(root);
        structureTree = new JTree(treeModel);
        structureTree.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Set custom renderer to color classes that are in modules
        structureTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                    Object userObject = node.getUserObject();

                    // Check if this node contains a class that's in a module
                    if (userObject instanceof ClassTreeNode) {
                        ClassTreeNode classNode = (ClassTreeNode) userObject;
                        if (classNode.fullClassName != null && classesInModules.contains(classNode.fullClassName)) {
                            // Don't color if this is a non-embedded reference
                            // (IDEntite with
                            // embedContents=false)
                            if (classNode.isNonEmbeddedReference) {
                                // Keep default color - it's just a reference by
                                // ID, not an embedded export
                            } else {
                                // Check if this class already appears in the
                                // path from root to this node
                                boolean isFirstOccurrence = isFirstOccurrenceInBranch(node, classNode.fullClassName);

                                if (isFirstOccurrence) {
                                    // Check if there are any red nodes in
                                    // descendants
                                    boolean hasRedDescendants = hasRedDescendants(node);
                                    if (hasRedDescendants) {
                                        setForeground(Color.RED);
                                    } else {
                                        setForeground(Color.BLUE);
                                    }
                                } else {
                                    setForeground(Color.RED);
                                }
                            }
                        } else {
                            // Not in modules, but check if it has red
                            // descendants
                            if (hasRedDescendants(node)) {
                                setForeground(Color.RED);
                            }
                        }
                    }
                }

                return this;
            }
        });

        // Add tree expansion listener for lazy loading
        structureTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                Object userObject = node.getUserObject();

                System.out.println("[EXPAND EVENT] Node type: " + userObject.getClass().getSimpleName());

                // If this is a class node that hasn't loaded its children yet,
                // load them now
                if (userObject instanceof ClassTreeNode) {
                    ClassTreeNode classNode = (ClassTreeNode) userObject;

                    // Check if children are already loaded by examining the
                    // actual tree node
                    // Children are loaded if: node has children AND they're not
                    // just the
                    // "Loading..." placeholder
                    boolean hasRealChildren = false;
                    if (node.getChildCount() > 0) {
                        DefaultMutableTreeNode firstChild = (DefaultMutableTreeNode) node.getChildAt(0);
                        Object firstChildObj = firstChild.getUserObject();
                        if (firstChildObj instanceof ClassTreeNode) {
                            ClassTreeNode firstChildClassNode = (ClassTreeNode) firstChildObj;
                            // If it's not "Loading...", then we have real
                            // children
                            hasRealChildren = !firstChildClassNode.displayText.equals("Loading...");
                        }
                    }

                    System.out.println("Expanding node: " + classNode.displayText + ", schemaClass=" + (classNode.schemaClass != null ? classNode.schemaClass.attributes.source : "null") + ", hasRealChildren=" + hasRealChildren);

                    // Only load children if not already loaded
                    if (!hasRealChildren && classNode.schemaClass != null) {
                        // Check for loop: if this class already appeared in
                        // ancestors, veto the
                        // expansion
                        System.out.println("  [CHECKING] Should we allow expansion of " + classNode.schemaClass.attributes.source + "?");
                        boolean allowExpansion = shouldAllowExpansion(classNode.schemaClass, node);
                        System.out.println("  [RESULT] shouldAllowExpansion = " + allowExpansion);
                        if (!allowExpansion) {
                            System.out.println("  [EXPANSION VETOED] Class " + classNode.schemaClass.attributes.source + " already in ancestor chain - preventing loop");
                            throw new ExpandVetoException(event);
                        }

                        // Remove placeholder if any
                        node.removeAllChildren();
                        // Load the class fields
                        expandClassFields(node, classNode.schemaClass);
                        treeModel.nodeStructureChanged(node);
                        System.out.println("  -> Loaded " + node.getChildCount() + " children");
                    } else {
                        System.out.println("  -> Children already loaded, skipping");
                    }
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                // Nothing to do on collapse
            }
        });

        // Add to scroll pane
        JScrollPane scrollPane = new JScrollPane(structureTree);
        add(scrollPane, BorderLayout.CENTER);

        // Add info panel at top
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel infoLabel = new JLabel("Schema structure showing class relationships through field references");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoPanel.add(infoLabel);
        add(infoPanel, BorderLayout.NORTH);
    }

    /**
     * Loads all class names from all modules to track which classes are
     * exported.
     */
    private void loadClassesFromModules() {
        classesInModules.clear();

        try {
            List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();
            for (DOSchemaModule module : modules) {
                // Get all class names from this module and its children
                List<String> classNames = new ArrayList<>();
                collectAllClassNames(module, classNames);
                classesInModules.addAll(classNames);
            }
            System.out.println("Loaded " + classesInModules.size() + " classes from modules for highlighting");
        } catch (Exception e) {
            System.err.println("Error loading classes from modules: " + e.getMessage());
        }
    }

    /** Recursively collects all class names from a module and its children. */
    private void collectAllClassNames(DOSchemaModule module, List<String> classNames) {
        for (ClassExportConfig config : module.classConfigs) {
            classNames.add(config.getClassName());
        }
        for (DOSchemaModule child : module.children) {
            collectAllClassNames(child, classNames);
        }
    }

    /**
     * Checks if this is the first class in modules encountered in the branch
     * from root to this node.
     * 
     * @param node The current node
     * @param className The full class name to check (must be in modules)
     * @return true if no other module class appears in ancestors, false if any
     * module class appeared earlier
     */
    private boolean isFirstOccurrenceInBranch(DefaultMutableTreeNode node, String className) {
        // Walk up the tree from parent to root, checking if ANY class in
        // modules
        // appears
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

        while (parent != null) {
            Object parentUserObject = parent.getUserObject();
            if (parentUserObject instanceof ClassTreeNode) {
                ClassTreeNode parentClassNode = (ClassTreeNode) parentUserObject;
                // Check if this parent is ANY class that's in modules
                if (parentClassNode.fullClassName != null && classesInModules.contains(parentClassNode.fullClassName)) {
                    // Found another module class higher up in the branch
                    return false;
                }
            }
            parent = (DefaultMutableTreeNode) parent.getParent();
        }

        // No module class found in ancestors, this is the first
        return true;
    }

    /**
     * Recursively checks if this node or any of its descendants contains a red
     * node (a module class that appears after another module class in the
     * branch).
     * 
     * @param node The node to check
     * @return true if any descendant is a red node
     */
    private boolean hasRedDescendants(DefaultMutableTreeNode node) {
        // Check all children recursively
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object childUserObject = child.getUserObject();

            if (childUserObject instanceof ClassTreeNode) {
                ClassTreeNode childClassNode = (ClassTreeNode) childUserObject;

                // Check if this child is a module class
                if (childClassNode.fullClassName != null && classesInModules.contains(childClassNode.fullClassName) && !childClassNode.isNonEmbeddedReference) {

                    // Check if it's a red node (not the first occurrence)
                    if (!isFirstOccurrenceInBranch(child, childClassNode.fullClassName)) {
                        return true;
                    }
                }
            }

            // Recursively check descendants
            if (hasRedDescendants(child)) {
                return true;
            }
        }

        return false;
    }

    private void buildSchemaStructure() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();

        // Initialize unreached classes with all schema classes
        unreachedClasses.clear();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            unreachedClasses.add(schemaClass.attributes.source);
        }

        // Count classes in each category
        int entitiesCount = countEntities();
        int paramsCount = countParams();

        // Create three main branches with counts
        DefaultMutableTreeNode entitiesNode = new DefaultMutableTreeNode(new ClassTreeNode("Entities (" + entitiesCount + ")"));
        DefaultMutableTreeNode paramsNode = new DefaultMutableTreeNode(new ClassTreeNode("Params (" + paramsCount + ")"));
        DefaultMutableTreeNode unreachedNode = new DefaultMutableTreeNode(new ClassTreeNode("Unreached"));

        root.add(entitiesNode);
        root.add(paramsNode);
        root.add(unreachedNode);

        // Build Entities branch - all classes descending from EntiteContientID
        buildEntitiesBranch(entitiesNode);

        // Build Params branch - all classes descending from EntiteParam
        buildParamsBranch(paramsNode);

        // Build Unreached branch with all classes not visited
        buildUnreachedBranch(unreachedNode);

        // Expand the root node
        structureTree.expandRow(0);
    }

    /**
     * Builds the Entities branch by finding all classes that descend from
     * EntiteContientID and recursively showing their field references.
     */
    private void buildEntitiesBranch(DefaultMutableTreeNode parentNode) {
        List<DOSchemaClass> entiteContientIDClasses = new ArrayList<>();

        // Find all classes that descend from EntiteContientID
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.isEntite(schema)) {
                entiteContientIDClasses.add(schemaClass);
            }
        }

        // Group by package
        Map<String, List<DOSchemaClass>> packageMap = new TreeMap<>();
        for (DOSchemaClass schemaClass : entiteContientIDClasses) {
            String packageName = ClassUtil.getPackageName(schemaClass.attributes.source);
            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(schemaClass);
        }

        // Add package nodes and classes under them
        for (Map.Entry<String, List<DOSchemaClass>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            List<DOSchemaClass> packageClasses = entry.getValue();

            // Create package node
            DefaultMutableTreeNode packageNode = new DefaultMutableTreeNode(packageName);
            parentNode.add(packageNode);

            // Sort classes within package by simple name
            packageClasses.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.attributes.source)));

            // Add each class to the package node (Entities branch)
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = ClassUtil.getSimpleName(schemaClass.attributes.source);
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassTreeNode(simpleName, schemaClass));
                packageNode.add(classNode);

                // Mark this class as reached
                unreachedClasses.remove(schemaClass.attributes.source);

                // Add a placeholder child to make the node expandable
                // Actual children will be loaded on expansion
                if (hasExpandableFields(schemaClass)) {
                    classNode.add(new DefaultMutableTreeNode(new ClassTreeNode("Loading...")));
                }
            }
        }
    }

    /**
     * Builds the Params branch by finding all classes that descend from
     * EntiteParam and recursively showing their field references.
     */
    private void buildParamsBranch(DefaultMutableTreeNode parentNode) {
        List<DOSchemaClass> entiteParamClasses = new ArrayList<>();

        // Find all classes that descend from EntiteParam
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.isParam(schema)) {
                entiteParamClasses.add(schemaClass);
            }
        }

        // Group by package
        Map<String, List<DOSchemaClass>> packageMap = new TreeMap<>();
        for (DOSchemaClass schemaClass : entiteParamClasses) {
            String packageName = ClassUtil.getPackageName(schemaClass.attributes.source);
            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(schemaClass);
        }

        // Add package nodes and classes under them
        for (Map.Entry<String, List<DOSchemaClass>> entry : packageMap.entrySet()) {
            String packageName = entry.getKey();
            List<DOSchemaClass> packageClasses = entry.getValue();

            // Create package node
            DefaultMutableTreeNode packageNode = new DefaultMutableTreeNode(packageName);
            parentNode.add(packageNode);

            // Sort classes within package by simple name
            packageClasses.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.attributes.source)));

            // Add each class to the package node (Params branch)
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = ClassUtil.getSimpleName(schemaClass.attributes.source);
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassTreeNode(simpleName, schemaClass));
                packageNode.add(classNode);

                // Mark this class as reached
                unreachedClasses.remove(schemaClass.attributes.source);

                // Add a placeholder child to make the node expandable
                // Actual children will be loaded on expansion
                if (hasExpandableFields(schemaClass)) {
                    classNode.add(new DefaultMutableTreeNode(new ClassTreeNode("Loading...")));
                }
            }
        }
    }

    /**
     * Expands a class node by showing its fields that reference other schema
     * classes.
     */
    private void expandClassFields(DefaultMutableTreeNode classNode, DOSchemaClass schemaClass) {
        DOSchemaField[] fields = schemaClass.fields;
        if (fields == null || fields.length == 0) {
            return;
        }

        for (DOSchemaField field : fields) {
            String fieldType = field.attributes.type;
            if (fieldType == null) {
                continue;
            }

            // Check if this is a collection - look at childrenType
            if (field.attributes.isCollection) {
                String childrenType = field.attributes.childrenType;
                if (childrenType != null) {
                    DOSchemaClass childClass = SchemaUtil.findClassByName(childrenType, schema);
                    if (childClass != null) {
                        // Check if children type is an IDEntite descendant
                        if (childClass.isIDEntite(schema)) {
                            // Special handling for IDEntite collections
                            handleIDEntiteField(classNode, field, childClass);
                        } else {
                            // Regular collection with object references
                            handleRegularField(classNode, field, childClass);
                        }
                    }
                }
            } else {
                // Non-collection field - check the field type itself
                DOSchemaClass referencedClass = SchemaUtil.findClassByName(fieldType, schema);
                if (referencedClass != null) {
                    // Check if this is an IDEntite descendant
                    if (referencedClass.isIDEntite(schema)) {
                        // Special handling for IDEntite fields
                        handleIDEntiteField(classNode, field, referencedClass);
                    } else {
                        // Regular field reference
                        handleRegularField(classNode, field, referencedClass);
                    }
                }
            }
        }
    }

    /**
     * Handles an IDEntite field by showing what EntiteContientID class it
     * refers to.
     */
    private void handleIDEntiteField(DefaultMutableTreeNode classNode, DOSchemaField field, DOSchemaClass idEntiteClass) {
        // Mark the IDEntite class as reached
        unreachedClasses.remove(idEntiteClass.attributes.source);

        // Get the target class from the pointsTo attribute (preferred)
        // or fall back to name extraction for backwards compatibility
        String targetClassName = idEntiteClass.attributes.pointsTo;
        if (targetClassName == null) {
            // Fallback: extract from field name or class name
            targetClassName = extractExpectedTypeFromFieldName(field.attributes.source, idEntiteClass.attributes.source);
        }

        if (targetClassName != null) {
            // Find the target class by absolute name (with simple name fallback
            // in
            // SchemaUtil)
            DOSchemaClass targetClass = SchemaUtil.findClassByName(targetClassName, schema);

            if (targetClass != null && (targetClass.isEntite(schema) || targetClass.isParam(schema))) {
                // Mark the target class as reached
                unreachedClasses.remove(targetClass.attributes.source);

                String fieldLabel = "Field: " + field.attributes.source + " → " + ClassUtil.getSimpleName(targetClass.attributes.source);

                // Check if this field has embedContents = false (reference
                // only, not embedded)
                boolean isNonEmbedded = !field.attributes.embedContents;

                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(new ClassTreeNode(fieldLabel, targetClass, isNonEmbedded));
                classNode.add(fieldNode);

                // Add placeholder for lazy expansion (only if not too deep in
                // recursion)
                if (hasExpandableFields(targetClass) && shouldAddPlaceholder(targetClass, fieldNode)) {
                    fieldNode.add(new DefaultMutableTreeNode(new ClassTreeNode("Loading...")));
                }
            }
        }
    }

    /**
     * Handles a regular (non-IDEntite) field reference.
     */
    private void handleRegularField(DefaultMutableTreeNode classNode, DOSchemaField field, DOSchemaClass referencedClass) {
        // Mark the referenced class as reached
        unreachedClasses.remove(referencedClass.attributes.source);

        String referencedSimpleName = ClassUtil.getSimpleName(referencedClass.attributes.source);
        String fieldLabel = "Field: " + field.attributes.source + " → " + referencedSimpleName;
        DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(new ClassTreeNode(fieldLabel, referencedClass));
        classNode.add(fieldNode);

        // Add placeholder for lazy expansion (only if not too deep in
        // recursion)
        if (hasExpandableFields(referencedClass) && shouldAddPlaceholder(referencedClass, fieldNode)) {
            fieldNode.add(new DefaultMutableTreeNode(new ClassTreeNode("Loading...")));
        }
    }

    /**
     * Checks if we should allow expansion of a node. Returns false if the class
     * has already appeared once in the ancestor chain.
     */
    private boolean shouldAllowExpansion(DOSchemaClass schemaClass, DefaultMutableTreeNode node) {
        if (schemaClass == null) {
            return true;
        }

        String className = schemaClass.attributes.source;
        System.out.println("    [shouldAllowExpansion] Checking for " + className + " in ancestors");
        DefaultMutableTreeNode ancestor = (DefaultMutableTreeNode) node.getParent();

        int depth = 0;
        while (ancestor != null) {
            Object ancestorUserObject = ancestor.getUserObject();
            if (ancestorUserObject instanceof ClassTreeNode) {
                ClassTreeNode ancestorClassNode = (ClassTreeNode) ancestorUserObject;
                System.out.println("      [Depth " + depth + "] Ancestor class: " + ancestorClassNode.fullClassName);
                if (className.equals(ancestorClassNode.fullClassName)) {
                    // Already appeared once in ancestors, don't allow expansion
                    System.out.println("      [MATCH FOUND] " + className + " matches ancestor at depth " + depth);
                    return false;
                }
            }
            ancestor = (DefaultMutableTreeNode) ancestor.getParent();
            depth++;
        }

        System.out.println("    [shouldAllowExpansion] No match found, allowing expansion");
        return true;
    }

    /**
     * Checks if a class should be expandable. Any class in the schema can be
     * expanded, but only if it hasn't appeared more than once already in the
     * ancestor path (prevents infinite loops).
     */
    private boolean hasExpandableFields(DOSchemaClass schemaClass) {
        // Any class in the schema can be expanded
        return schemaClass != null;
    }

    /**
     * Checks if we should add an expandable placeholder for this class. Returns
     * false if the class has already appeared once in the branch (prevents
     * infinite loops like A→B→A→B→A...).
     */
    private boolean shouldAddPlaceholder(DOSchemaClass schemaClass, DefaultMutableTreeNode fieldNode) {
        if (schemaClass == null) {
            return false;
        }

        // Count how many times this class appears in the ancestor path
        // Start from the parent of fieldNode (not fieldNode itself, as that's
        // the node
        // we're adding)
        int occurrenceCount = 0;
        String className = schemaClass.attributes.source;

        DefaultMutableTreeNode ancestor = (DefaultMutableTreeNode) fieldNode.getParent();
        while (ancestor != null) {
            Object ancestorUserObject = ancestor.getUserObject();
            if (ancestorUserObject instanceof ClassTreeNode) {
                ClassTreeNode ancestorClassNode = (ClassTreeNode) ancestorUserObject;
                if (className.equals(ancestorClassNode.fullClassName)) {
                    occurrenceCount++;
                    System.out.println("  [LOOP CHECK] Found " + className + " in ancestor chain (count=" + occurrenceCount + ")");
                    if (occurrenceCount >= 1) {
                        // Already appeared once in ancestors, don't allow loop
                        // back (A→B→A is the
                        // limit)
                        System.out.println("  [LOOP BLOCKED] Not adding placeholder for " + className + " - already in ancestor chain");
                        return false;
                    }
                }
            }
            ancestor = (DefaultMutableTreeNode) ancestor.getParent();
        }

        return true;
    }

    /**
     * Builds the Unreached branch showing all classes not encountered during
     * tree building.
     */
    private void buildUnreachedBranch(DefaultMutableTreeNode parentNode) {
        // Convert unreached class names to DOSchemaClass objects and sort
        List<DOSchemaClass> unreachedClassList = new ArrayList<>();
        for (String className : unreachedClasses) {
            DOSchemaClass schemaClass = SchemaUtil.findClassByName(className, schema);
            if (schemaClass != null) {
                unreachedClassList.add(schemaClass);
            }
        }

        // Update parent node label with count
        parentNode.setUserObject("Unreached (" + unreachedClassList.size() + ")");

        // Sort by simple name for easier navigation
        unreachedClassList.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.attributes.source)));

        // Add each unreached class to the tree
        for (DOSchemaClass schemaClass : unreachedClassList) {
            String simpleName = ClassUtil.getSimpleName(schemaClass.attributes.source);
            String fullName = schemaClass.attributes.source;
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(simpleName + " (" + fullName + ")");
            parentNode.add(classNode);

            // Drill backwards to find what references this class
            Set<String> visitedBackward = new HashSet<>();
            visitedBackward.add(schemaClass.attributes.source);
            drillBackwards(classNode, schemaClass, visitedBackward);
        }
    }

    /**
     * Drills backwards from an unreached class to find all fields that
     * reference it. Continues recursively to understand the chain of
     * unreachability.
     */
    private void drillBackwards(DefaultMutableTreeNode classNode, DOSchemaClass targetClass, Set<String> visitedBackward) {
        String targetClassName = targetClass.attributes.source;

        // Find all classes and fields that reference this target class
        List<DOSchemaFieldReference> references = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            DOSchemaField[] fields = schemaClass.fields;
            if (fields == null) {
                continue;
            }

            for (DOSchemaField field : fields) {
                String fieldType = field.attributes.type;
                if (fieldType == null) {
                    continue;
                }

                boolean isMatch = false;

                // Check if this field's direct type references our target class
                if (fieldType.equals(targetClassName)) {
                    isMatch = true;
                }
                // Check if it's a collection with childrenType matching our
                // target
                else if (field.attributes.isCollection) {
                    String childrenType = field.attributes.childrenType;
                    if (childrenType != null && childrenType.equals(targetClassName)) {
                        isMatch = true;
                    }
                    // Also check if children type is IDEntite pointing to our
                    // target
                    else if (childrenType != null) {
                        DOSchemaClass childTypeClass = SchemaUtil.findClassByName(childrenType, schema);
                        if (childTypeClass != null && childTypeClass.isIDEntite(schema)) {
                            // Use pointsTo if available, otherwise fall back to
                            // name extraction
                            String pointsTo = childTypeClass.attributes.pointsTo;
                            if (pointsTo != null && pointsTo.equals(targetClassName)) {
                                isMatch = true;
                            } else if (pointsTo == null) {
                                // Fallback to name extraction
                                String expectedType = extractExpectedTypeFromFieldName(field.attributes.source, childrenType);
                                if (expectedType != null) {
                                    String targetSimpleName = ClassUtil.getSimpleName(targetClassName);
                                    if (targetSimpleName.equals(expectedType)) {
                                        isMatch = true;
                                    }
                                }
                            }
                        }
                    }
                }
                // Check if it's an IDEntite that might point to this class
                else {
                    DOSchemaClass fieldTypeClass = SchemaUtil.findClassByName(fieldType, schema);
                    if (fieldTypeClass != null && fieldTypeClass.isIDEntite(schema)) {
                        // Use pointsTo if available, otherwise fall back to
                        // name extraction
                        String pointsTo = fieldTypeClass.attributes.pointsTo;
                        if (pointsTo != null && pointsTo.equals(targetClassName)) {
                            isMatch = true;
                        } else if (pointsTo == null) {
                            // Fallback to name extraction
                            String expectedType = extractExpectedTypeFromFieldName(field.attributes.source, fieldType);
                            if (expectedType != null) {
                                String targetSimpleName = ClassUtil.getSimpleName(targetClassName);
                                if (targetSimpleName.equals(expectedType)) {
                                    isMatch = true;
                                }
                            }
                        }
                    }
                }

                if (isMatch) {
                    references.add(new DOSchemaFieldReference(schemaClass, field));
                }
            }
        }

        // Add found references to the tree
        for (DOSchemaFieldReference ref : references) {
            String refClassName = ref.schemaClass.attributes.source;
            String refSimpleName = ClassUtil.getSimpleName(refClassName);
            String fieldLabel = "← " + refSimpleName + "." + ref.field.attributes.source;
            DefaultMutableTreeNode refNode = new DefaultMutableTreeNode(fieldLabel);
            classNode.add(refNode);

            // Continue drilling backwards if this referencing class hasn't been
            // visited
            // and is also unreachable (to understand the chain)
            if (!visitedBackward.contains(refClassName) && unreachedClasses.contains(refClassName)) {
                visitedBackward.add(refClassName);
                drillBackwards(refNode, ref.schemaClass, visitedBackward);
            }
        }
    }

    /**
     * Helper class to store field reference information.
     */
    /**
     * Extracts expected EntiteContientID type from field name. Example:
     * "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
     */
    private String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        // If field name starts with "mID", extract the part after it
        if (fieldName.startsWith("mID")) {
            return fieldName.substring(3); // Remove "mID" prefix
        }
        // Otherwise try to extract from the ID class name
        // "IDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
        String simpleClassName = ClassUtil.getSimpleName(idClassName);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2); // Remove "ID" prefix
        }
        return null;
    }

    /**
     * Counts how many classes are entities (descendants of EntiteContientID).
     */
    private int countEntities() {
        int count = 0;
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.isEntite(schema)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts how many classes are params (descendants of EntiteParam).
     */
    private int countParams() {
        int count = 0;
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.isParam(schema)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Updates the schema and rebuilds the tree.
     */
    public void setSchema(DOSchema schema) {
        this.schema = schema;
        buildSchemaStructure();
        treeModel.reload();
    }
}
