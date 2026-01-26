package migration4o.ui.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.SchemaUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;
import java.util.List;

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
            DOSchemaClass updatedClass = SchemaUtil.findClassByName(classNode.getSchemaClass().source,
                    new DOSchema[] { schema,
                            databaseSchema });
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
