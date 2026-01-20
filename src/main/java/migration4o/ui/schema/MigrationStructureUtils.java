package migration4o.ui.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;
import java.util.List;

/**
 * Utility class for MigrationStructurePanel operations.
 * Contains helper methods for tree manipulation, class name handling,
 * and schema operations.
 */
public class MigrationStructureUtils {

    /**
     * Extracts the package name from an absolute class name.
     * 
     * @param absoluteName the absolute class name (e.g., "com.example.MyClass")
     * @return the package name or "(default package)" if no package
     */
    public static String getPackageName(String absoluteName) {
        int lastDot = absoluteName.lastIndexOf('.');
        if (lastDot > 0) {
            return absoluteName.substring(0, lastDot);
        }
        return "(default package)";
    }

    /**
     * Extracts the simple name from an absolute class name.
     * 
     * @param absoluteName the absolute class name (e.g., "com.example.MyClass")
     * @return the simple name (e.g., "MyClass")
     */
    public static String getSimpleName(String absoluteName) {
        if (absoluteName.contains(".")) {
            return absoluteName.substring(absoluteName.lastIndexOf('.') + 1);
        }
        return absoluteName;
    }

    /**
     * Checks if a schema class is a descendant of a given ancestor class.
     * 
     * @param schemaClass       the class to check
     * @param ancestorClassName the name of the ancestor class
     * @param schema            the schema containing all classes
     * @return true if schemaClass is a descendant of ancestorClassName
     */
    public static boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName, DOSchema schema) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.source;
        if (currentClassName.equals(ancestorClassName)) {
            return true;
        }

        String parentClassName = schemaClass.parentClassName;
        if (parentClassName == null || parentClassName.isEmpty()) {
            return false;
        }

        if (parentClassName.equals(ancestorClassName)) {
            return true;
        }

        // Look up parent class and recurse
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass candidate : schema.getClasses()) {
                if (candidate.source.equals(parentClassName)) {
                    return isDescendantOf(candidate, ancestorClassName, schema);
                }
            }
        }

        return false;
    }

    /**
     * Checks if a tree node contains a ClassNode object.
     * 
     * @param node the tree node to check
     * @return true if the node's user object is a ClassNode
     */
    public static boolean isClassNode(DefaultMutableTreeNode node) {
        return node.getUserObject() instanceof MigrationStructurePanel.ClassNode;
    }

    /**
     * Finds a class node in a tree by class name.
     * 
     * @param root      the root node to start searching from
     * @param className the absolute class name to find
     * @return the tree node containing the class, or null if not found
     */
    public static DefaultMutableTreeNode findClassNodeInTree(DefaultMutableTreeNode root, String className) {
        if (root.getUserObject() instanceof MigrationStructurePanel.ClassNode) {
            MigrationStructurePanel.ClassNode classNode = (MigrationStructurePanel.ClassNode) root.getUserObject();
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
     * Recursively collects all class names from a module and its children.
     * 
     * @param moduleNode the module tree node to collect from
     * @param classNames the list to add class names to (modified in place)
     */
    public static void collectClassNamesFromModule(DefaultMutableTreeNode moduleNode, List<String> classNames) {
        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (child.getUserObject() instanceof MigrationStructurePanel.ClassNode) {
                MigrationStructurePanel.ClassNode classNode = (MigrationStructurePanel.ClassNode) child.getUserObject();
                classNames.add(classNode.getSchemaClass().source);
            } else if (child.getUserObject() instanceof MigrationStructurePanel.ModuleNode) {
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
        if (userObject instanceof MigrationStructurePanel.ClassNode) {
            MigrationStructurePanel.ClassNode classNode = (MigrationStructurePanel.ClassNode) userObject;
            // Find the class with updated counts from databaseSchema
            DOSchemaClass updatedClass = findClassByName(classNode.getSchemaClass().source, schema,
                    databaseSchema);
            if (updatedClass != null) {
                // Create new ClassNode with updated class data
                node.setUserObject(new MigrationStructurePanel.ClassNode(updatedClass));
            }
        }

        // Recursively update children
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            updateNodeCounts(child, schema, databaseSchema);
        }
    }
}
