package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.CategorizedClasses;
import migration4o.models.ui.ClassNode;
import migration4o.models.ui.MigrationModule;
import migration4o.models.ui.ModuleNode;
import migration4o.schema.MigrationFormatWriter;
import migration4o.util.SchemaUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Utility class for MigrationStructurePanel operations.
 * Contains helper methods for tree manipulation, class name handling,
 * and schema operations.
 */
public class MigrationStructurePanelUtil {

    /**
     * Checks if a tree node contains a ClassNode object.
     * 
     * @param node the tree node to check
     * @return true if the node's user object is a ClassNode
     */
    public static boolean isClassNode(DefaultMutableTreeNode node) {
        return node.getUserObject() instanceof ClassNode;
    }

    /**
     * Finds a class node in a tree by class name.
     * 
     * @param root      the root node to start searching from
     * @param className the absolute class name to find
     * @return the tree node containing the class, or null if not found
     */
    public static DefaultMutableTreeNode findClassNodeInTree(DefaultMutableTreeNode root, String className) {
        if (root.getUserObject() instanceof ClassNode) {
            ClassNode classNode = (ClassNode) root.getUserObject();
            if (classNode.getSchemaClass().source.equals(className)) {
                return root;
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            DefaultMutableTreeNode result = findClassNodeInTree(child, className);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Recursively collects all class names from a module and its children.
     * 
     * @param moduleNode the module tree node to collect from
     * @param classNames the list to add class names to (modified in place)
     */
    public static void collectClassNamesFromModule(DefaultMutableTreeNode moduleNode, List<String> classNames) {
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

    /**
     * Recursively updates object counts in tree nodes based on database schema.
     * 
     * @param node           the tree node to update
     * @param schema         the reference schema
     * @param databaseSchema the database schema with updated counts
     */
    public static void updateNodeCounts(DefaultMutableTreeNode node, DOSchema schema, DOSchema databaseSchema) {
        Object userObject = node.getUserObject();
        if (userObject instanceof ClassNode) {
            ClassNode classNode = (ClassNode) userObject;
            // Find the class with updated counts from databaseSchema
            DOSchemaClass updatedClass = SchemaUtil.findClassByName(classNode.getSchemaClass().source,
                    new DOSchema[] { schema,
                            databaseSchema });
            if (updatedClass != null) {
                // Create new ClassNode with updated class data
                node.setUserObject(new ClassNode(updatedClass));
            }
        }

        // Recursively update children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            updateNodeCounts(child, schema, databaseSchema);
        }
    }

    /**
     * Finds a class in the schema by its absolute name.
     * Searches databaseSchema first (if available) for object counts,
     * then falls back to reference schema.
     * 
     * @param className      the absolute class name to find
     * @param schema         the reference schema
     * @param databaseSchema the database schema (may be null)
     * @return the schema class, or null if not found
     */
    public static DOSchemaClass findClassByName(String className, DOSchema schema, DOSchema databaseSchema) {
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
     * Extracts a MigrationModule from a tree node and its children.
     * Recursively processes all child modules and classes.
     * 
     * @param moduleTreeNode the tree node representing the module
     * @return a MigrationModule object with all nested data
     */
    public static MigrationModule extractModule(DefaultMutableTreeNode moduleTreeNode) {
        ModuleNode module = (ModuleNode) moduleTreeNode.getUserObject();
        List<String> classNames = new ArrayList<>();
        List<MigrationModule> childModules = new ArrayList<>();

        Enumeration<?> children = moduleTreeNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
            if (childNode.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) childNode
                        .getUserObject();
                classNames.add(classNode.getSchemaClass().source);
            } else if (childNode.getUserObject() instanceof ModuleNode) {
                childModules.add(extractModule(childNode));
            }
        }

        return new MigrationModule(module.getName(), module.getId(), classNames, childModules);
    }

    /**
     * Saves the migration structure to the migration format file.
     * 
     * @param exportRoot     the root node of the export tree
     * @param formatFilePath the path to the migration format XML file
     * @throws Exception if save operation fails
     */
    public static void saveMigrationStructure(DefaultMutableTreeNode exportRoot, String formatFilePath)
            throws Exception {
        List<MigrationModule> modules = new ArrayList<>();

        Enumeration<?> children = exportRoot.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) children.nextElement();
            if (moduleNode.getUserObject() instanceof ModuleNode) {
                modules.add(extractModule(moduleNode));
            }
        }

        MigrationFormatWriter writer = new MigrationFormatWriter();
        writer.writeMigrationFormat(modules, formatFilePath);
    }

    /**
     * Categorizes schema classes into Entities, Params, and Others,
     * separated by exported vs available.
     * 
     * @param schema          the schema containing classes
     * @param exportedClasses set of class names that have been exported
     * @return categorized classes ready for tree population
     */
    public static CategorizedClasses categorizeClasses(DOSchema schema, Set<String> exportedClasses) {
        CategorizedClasses categorized = new CategorizedClasses();

        if (schema == null || schema.getClasses() == null) {
            return categorized;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (exportedClasses.contains(schemaClass.source)) {
                // Add to Exported section
                if (schemaClass.isEntite(schema)) {
                    categorized.exportedEntities.add(schemaClass);
                } else if (schemaClass.isParam(schema)) {
                    categorized.exportedParams.add(schemaClass);
                } else {
                    categorized.exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (schemaClass.isEntite(schema)) {
                    categorized.availableEntities.add(schemaClass);
                } else if (schemaClass.isParam(schema)) {
                    categorized.availableParams.add(schemaClass);
                } else {
                    categorized.availableOthers.add(schemaClass);
                }
            }
        }

        return categorized;
    }

    /**
     * Groups classes by package, sorts them, and adds to parent node.
     * Creates package nodes with sorted class children.
     * 
     * @param parentNode the parent tree node
     * @param classes    the classes to group and add
     */
    public static void addSortedClassesToNode(DefaultMutableTreeNode parentNode, List<DOSchemaClass> classes) {
        // Group classes by package
        Map<String, List<DOSchemaClass>> packageMap = new TreeMap<>();

        for (DOSchemaClass schemaClass : classes) {
            String packageName = schemaClass.getSourcePackage();
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
            packageClasses.sort(Comparator.comparing(c -> c.getSourceName()));

            // Add classes to package node
            for (DOSchemaClass schemaClass : packageClasses) {
                ClassNode classNode = new ClassNode(schemaClass);
                DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(classNode);
                packageNode.add(treeNode);
            }
        }
    }

    /**
     * Adds a module and its classes/children to the tree structure.
     * Recursively processes child modules and adds class names to exportedClasses
     * set.
     * 
     * @param parentNode      the parent tree node
     * @param module          the migration module to add
     * @param schema          the reference schema
     * @param databaseSchema  the database schema (may be null)
     * @param exportedClasses set to track exported class names (modified in place)
     */
    public static void addModuleToTree(DefaultMutableTreeNode parentNode,
            MigrationModule module,
            DOSchema schema, DOSchema databaseSchema, Set<String> exportedClasses) {
        ModuleNode moduleNode = new ModuleNode(module.getName(),
                module.getId());
        DefaultMutableTreeNode moduleTreeNode = new DefaultMutableTreeNode(moduleNode);
        parentNode.add(moduleTreeNode);

        // Add classes to module
        for (String className : module.getClassNames()) {
            DOSchemaClass schemaClass = findClassByName(className, schema, databaseSchema);
            if (schemaClass != null) {
                ClassNode classNode = new ClassNode(schemaClass);
                DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                moduleTreeNode.add(classTreeNode);
                exportedClasses.add(className);
            }
        }

        // Add child modules recursively
        for (MigrationModule childModule : module.getChildModules()) {
            addModuleToTree(moduleTreeNode, childModule, schema, databaseSchema, exportedClasses);
        }
    }

    /**
     * Collects all class names from a module's children and removes them from the
     * exported set.
     * 
     * @param moduleNode      the module tree node
     * @param exportedClasses set of exported class names (modified in place)
     */
    public static void removeModuleClassesFromExported(DefaultMutableTreeNode moduleNode, Set<String> exportedClasses) {
        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (child.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) child
                        .getUserObject();
                exportedClasses.remove(classNode.getSchemaClass().source);
            }
        }
    }
}
