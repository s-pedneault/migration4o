package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.CategorizedClasses;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ClassNode;
import migration4o.models.schema.DOSchemaModule;
import migration4o.schema.modules.DOModuleService;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Utility class for MigrationStructurePanel operations. Contains helper methods for tree manipulation, class name handling, and schema operations.
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
     * @param root the root node to start searching from
     * @param className the absolute class name to find
     * @return the tree node containing the class, or null if not found
     */
    public static DefaultMutableTreeNode findClassNodeInTree(DefaultMutableTreeNode root, String className) {
        if (root.getUserObject() instanceof ClassNode) {
            ClassNode classNode = (ClassNode) root.getUserObject();
            if (classNode.getSchemaClass().attributes.source.equals(className)) {
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
     * Recursively updates object counts in tree nodes based on database.
     * 
     * @param node the tree node to update
     * @param schema the reference schema
     * @param database the database with object counts (may be null)
     */
    public static void updateNodeCounts(DefaultMutableTreeNode node, DOSchema schema, DODatabase database) {
        Object userObject = node.getUserObject();
        if (userObject instanceof ClassNode) {
            ClassNode classNode = (ClassNode) userObject;
            String className = classNode.getSchemaClass().attributes.source;

            // Get from reference schema first (has destinationName)
            DOSchemaClass referenceClass = schema != null ? schema.findClassByName(className) : null;
            // Get from database (has object counts)
            DODatabaseClass dbClass = database != null ? database.findClassByName(className) : null;

            if (referenceClass != null) {
                // Use reference class with DODatabaseClass for counts
                ClassExportConfig existingConfig = classNode.getExportConfig();
                ClassNode newNode = new ClassNode(referenceClass, dbClass);
                newNode.setExportConfig(existingConfig);
                node.setUserObject(newNode);
            } else if (dbClass != null && dbClass.schemaClass != null) {
                // No reference class but we have a database class with schema link
                ClassExportConfig existingConfig = classNode.getExportConfig();
                ClassNode newNode = new ClassNode(dbClass.schemaClass, dbClass);
                newNode.setExportConfig(existingConfig);
                node.setUserObject(newNode);
            }
        }

        // Recursively update children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            updateNodeCounts(child, schema, database);
        }
    }

    /**
     * Finds a class in the schema by its absolute name. Searches databaseSchema first (if available) for object counts, then falls back to reference schema.
     * 
     * @param className the absolute class name to find
     * @param schema the reference schema
     * @param databaseSchema the database schema (may be null)
     * @return the schema class, or null if not found
     */

    /**
     * Extracts a MigrationModule from a tree node and its children. Recursively processes all child modules and classes.
     * 
     * @param moduleTreeNode the tree node representing the module
     * @param parentModule the parent module (may be null for root modules)
     * @return a MigrationModule object with all nested data
     */
    public static DOSchemaModule extractModule(DefaultMutableTreeNode moduleTreeNode, DOSchemaModule parentModule) {
        DOSchemaModule module = (DOSchemaModule) moduleTreeNode.getUserObject();
        List<ClassExportConfig> classConfigs = new ArrayList<>();
        List<DOSchemaModule> childModules = new ArrayList<>();

        // Create result first so it can be passed as parent to child modules
        DOSchemaModule result = new DOSchemaModule(parentModule);
        result.name = module.name;
        result.id = module.id;
        result.icon = module.icon;
        result.tileBg = module.tileBg;
        result.tileTextColor = module.tileTextColor;
        result.tileIconColor = module.tileIconColor;
        result.tileFontSize = module.tileFontSize;

        Enumeration<?> children = moduleTreeNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
            if (childNode.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) childNode.getUserObject();
                // Get the export config from the node, or create a simple one
                // if not set
                ClassExportConfig config = classNode.getExportConfig();
                if (config == null) {
                    // Create a simple config with just the class name (for
                    // backward compatibility)
                    config = new ClassExportConfig(classNode.getSchemaClass().attributes.source, null, new ArrayList<>());
                }
                classConfigs.add(config);
            } else if (childNode.getUserObject() instanceof DOSchemaModule) {
                childModules.add(extractModule(childNode, result));
            }
        }

        result.classConfigs = classConfigs;
        result.children = childModules;
        return result;
    }

    /**
     * Saves the migration structure to the migration format file.
     * 
     * @param exportRoot the root node of the export tree
     * @param formatFilePath the path to the migration format XML file
     * @throws Exception if save operation fails
     */
    public static void saveMigrationStructure(DefaultMutableTreeNode exportRoot, String formatFilePath) throws Exception {
        List<DOSchemaModule> modules = new ArrayList<>();

        Enumeration<?> children = exportRoot.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) children.nextElement();
            if (moduleNode.getUserObject() instanceof DOSchemaModule) {
                modules.add(extractModule(moduleNode, null));
            }
        }

        DOModuleService.getInstance().saveModuleStructure(modules, formatFilePath);
    }

    /**
     * Categorizes schema classes into Entities, Params, and Others, separated by exported vs available.
     * 
     * @param schema the schema containing classes
     * @param exportedClasses set of class names that have been exported
     * @param includeIDEntites whether to include IDEntite classes in the categorization
     * @return categorized classes ready for tree population
     */
    public static CategorizedClasses categorizeClasses(DOSchema schema, Set<String> exportedClasses, boolean includeIDEntites) {
        CategorizedClasses categorized = new CategorizedClasses();

        if (schema == null || schema.getClasses() == null) {
            return categorized;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            // Skip IDEntite classes if the flag is false
            if (!includeIDEntites && schemaClass.isIDEntite()) {
                continue;
            }

            if (exportedClasses.contains(schemaClass.attributes.source)) {
                // Add to Exported section
                if (schemaClass.isEntite()) {
                    categorized.exportedEntities.add(schemaClass);
                } else if (schemaClass.isParam()) {
                    categorized.exportedParams.add(schemaClass);
                } else {
                    categorized.exportedOthers.add(schemaClass);
                }
            } else {
                // Add to Available section
                if (schemaClass.isEntite()) {
                    categorized.availableEntities.add(schemaClass);
                } else if (schemaClass.isParam()) {
                    categorized.availableParams.add(schemaClass);
                } else {
                    categorized.availableOthers.add(schemaClass);
                }
            }
        }

        return categorized;
    }

    /**
     * Groups classes by package, sorts them, and adds to parent node. Creates package nodes with sorted class children.
     * 
     * @param parentNode the parent tree node
     * @param classes the classes to group and add
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
     * Adds a module and its classes/children to the tree structure. Recursively processes child modules and adds class names to exportedClasses set.
     * 
     * @param parentNode the parent tree node
     * @param module the migration module to add
     * @param schema the reference schema
     * @param database the database (may be null)
     * @param exportedClasses set to track exported class names (modified in place)
     */
    public static void addModuleToTree(DefaultMutableTreeNode parentNode, DOSchemaModule module, DOSchema schema, DODatabase database, Set<String> exportedClasses) {
        DefaultMutableTreeNode moduleTreeNode = new DefaultMutableTreeNode(module);
        parentNode.add(moduleTreeNode);

        // Add classes to module with their configurations
        for (ClassExportConfig config : module.classConfigs) {
            String className = config.getClassName();
            // Use reference schema first (has destinationName)
            DOSchemaClass schemaClass = schema != null ? schema.findClassByName(className) : null;
            // Look up database class for object counts
            DODatabaseClass dbClass = database != null ? database.findClassByName(className) : null;

            if (schemaClass == null && dbClass != null && dbClass.schemaClass != null) {
                schemaClass = dbClass.schemaClass;
            }
            if (schemaClass != null) {
                ClassNode classNode = new ClassNode(schemaClass, dbClass);
                // Store the configuration in the ClassNode
                classNode.setExportConfig(config);
                DefaultMutableTreeNode classTreeNode = new DefaultMutableTreeNode(classNode);
                moduleTreeNode.add(classTreeNode);
                exportedClasses.add(className);
            }
        }

        // Add child modules recursively
        for (DOSchemaModule childModule : module.children) {
            addModuleToTree(moduleTreeNode, childModule, schema, database, exportedClasses);
        }
    }

    /**
     * Collects all class names from a module's children and removes them from the exported set.
     * 
     * @param moduleNode the module tree node
     * @param exportedClasses set of exported class names (modified in place)
     */
    public static void removeModuleClassesFromExported(DefaultMutableTreeNode moduleNode, Set<String> exportedClasses) {
        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (child.getUserObject() instanceof ClassNode) {
                ClassNode classNode = (ClassNode) child.getUserObject();
                exportedClasses.remove(classNode.getSchemaClass().attributes.source);
            }
        }
    }

    /**
     * Adds a class to a module node in the tree structure.
     * 
     * @param schemaClass the schema class to add
     * @param targetModule the target module node
     * @param exportedClasses set of already exported class names
     * @return true if added successfully, false if already exported
     */
    public static boolean addClassToModule(DOSchemaClass schemaClass, DefaultMutableTreeNode targetModule, Set<String> exportedClasses) {
        // Check if already exported
        if (exportedClasses.contains(schemaClass.attributes.source)) {
            return false;
        }

        // Create class node and add to module
        ClassNode newClassNode = new ClassNode(schemaClass);
        DefaultMutableTreeNode newTreeNode = new DefaultMutableTreeNode(newClassNode);
        targetModule.add(newTreeNode);

        // Mark as exported
        exportedClasses.add(schemaClass.attributes.source);

        return true;
    }

    /**
     * Removes a class from the export tree.
     * 
     * @param treeNode the tree node containing the class
     * @param exportedClasses set of exported class names
     * @return true if removed successfully
     */
    public static boolean removeClassFromExport(DefaultMutableTreeNode treeNode, Set<String> exportedClasses) {
        if (!(treeNode.getUserObject() instanceof ClassNode)) {
            return false;
        }

        ClassNode classNode = (ClassNode) treeNode.getUserObject();
        DOSchemaClass schemaClass = classNode.getSchemaClass();

        // Remove from exported set
        exportedClasses.remove(schemaClass.attributes.source);

        // Remove from export tree
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) treeNode.getParent();
        if (parent != null) {
            parent.remove(treeNode);
        }

        return true;
    }

    /**
     * Helper class to hold module tree node information.
     */
    public static class ModuleTreeInfo {
        public final DefaultMutableTreeNode treeNode;
        public final DOSchemaModule moduleNode;

        public ModuleTreeInfo(DefaultMutableTreeNode treeNode, DOSchemaModule moduleNode) {
            this.treeNode = treeNode;
            this.moduleNode = moduleNode;
        }
    }

    /**
     * Helper class for module export information.
     */
    public static class ModuleExportInfo {
        public final String name;
        public final DOSchemaModule module;

        public ModuleExportInfo(String name, DOSchemaModule module) {
            this.name = name;
            this.module = module;
        }
    }

}
