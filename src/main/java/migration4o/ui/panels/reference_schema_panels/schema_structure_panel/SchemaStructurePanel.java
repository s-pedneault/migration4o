package migration4o.ui.panels.reference_schema_panels.schema_structure_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaFieldReference;
import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel that displays the schema structure as a tree, showing class
 * relationships
 * through field references to help understand object reachability.
 */
public class SchemaStructurePanel extends JPanel {
    private DOSchema schema;
    private JTree structureTree;
    private DefaultTreeModel treeModel;
    private Set<String> visitedClasses; // Track visited classes to avoid infinite recursion
    private Set<String> unreachedClasses; // Track classes not reached during tree building

    public SchemaStructurePanel(DOSchema schema) {
        this.schema = schema;
        this.visitedClasses = new HashSet<>();
        this.unreachedClasses = new HashSet<>();
        initializeUI();
        buildSchemaStructure();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // Create tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Schema Structure");
        treeModel = new DefaultTreeModel(root);
        structureTree = new JTree(treeModel);
        structureTree.setFont(new Font("Monospaced", Font.PLAIN, 12));

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

    private void buildSchemaStructure() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();

        // Initialize unreached classes with all schema classes
        unreachedClasses.clear();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            unreachedClasses.add(schemaClass.source);
        }

        // Count classes in each category
        int entitiesCount = countEntities();
        int paramsCount = countParams();

        // Create three main branches with counts
        DefaultMutableTreeNode entitiesNode = new DefaultMutableTreeNode("Entities (" + entitiesCount + ")");
        DefaultMutableTreeNode paramsNode = new DefaultMutableTreeNode("Params (" + paramsCount + ")");
        DefaultMutableTreeNode unreachedNode = new DefaultMutableTreeNode("Unreached");

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
     * EntiteContientID
     * and recursively showing their field references.
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
            String packageName = ClassUtil.getPackageName(schemaClass.source);
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
            packageClasses.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.source)));

            // Add each class to the package node
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = ClassUtil.getSimpleName(schemaClass.source);
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(simpleName);
                packageNode.add(classNode);

                // Mark this class as reached
                unreachedClasses.remove(schemaClass.source);

                // Clear visited set for each top-level class to allow full exploration
                visitedClasses.clear();
                visitedClasses.add(schemaClass.source);

                // Expand this class's fields
                expandClassFields(classNode, schemaClass);
            }
        }
    }

    /**
     * Builds the Params branch by finding all classes that descend from EntiteParam
     * and recursively showing their field references.
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
            String packageName = ClassUtil.getPackageName(schemaClass.source);
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
            packageClasses.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.source)));

            // Add each class to the package node
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = ClassUtil.getSimpleName(schemaClass.source);
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(simpleName);
                packageNode.add(classNode);

                // Mark this class as reached
                unreachedClasses.remove(schemaClass.source);

                // Clear visited set for each top-level class to allow full exploration
                visitedClasses.clear();
                visitedClasses.add(schemaClass.source);

                // Expand this class's fields
                expandClassFields(classNode, schemaClass);
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
            String fieldType = field.type;
            if (fieldType == null) {
                continue;
            }

            // Check if this is a collection - look at childrenType
            if (field.isCollection) {
                String childrenType = field.childrenType;
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
     * Handles an IDEntite field by showing what EntiteContientID class it refers
     * to.
     */
    private void handleIDEntiteField(DefaultMutableTreeNode classNode, DOSchemaField field,
            DOSchemaClass idEntiteClass) {
        // Mark the IDEntite class as reached
        unreachedClasses.remove(idEntiteClass.source);

        // Get the target class from the pointsTo attribute (preferred)
        // or fall back to name extraction for backwards compatibility
        String targetClassName = idEntiteClass.pointsTo;
        if (targetClassName == null) {
            // Fallback: extract from field name or class name
            targetClassName = extractExpectedTypeFromFieldName(field.source, idEntiteClass.source);
        }

        if (targetClassName != null) {
            // Find the target class by absolute name (with simple name fallback in
            // SchemaUtil)
            DOSchemaClass targetClass = SchemaUtil.findClassByName(targetClassName, schema);

            if (targetClass != null &&
                    (targetClass.isEntite(schema) ||
                            targetClass.isParam(schema))) {
                // Mark the target class as reached
                unreachedClasses.remove(targetClass.source);

                String fieldLabel = "Field: " + field.source + " → "
                        + ClassUtil.getSimpleName(targetClass.source);
                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(fieldLabel);
                classNode.add(fieldNode);

                // Continue drilling into the target class if not already visited
                if (!visitedClasses.contains(targetClass.source)) {
                    visitedClasses.add(targetClass.source);
                    expandClassFields(fieldNode, targetClass);
                }
            }
        }
    }

    /**
     * Handles a regular (non-IDEntite) field reference.
     */
    private void handleRegularField(DefaultMutableTreeNode classNode, DOSchemaField field,
            DOSchemaClass referencedClass) {
        // Mark the referenced class as reached
        unreachedClasses.remove(referencedClass.source);

        String referencedSimpleName = ClassUtil.getSimpleName(referencedClass.source);
        String fieldLabel = "Field: " + field.source + " → " + referencedSimpleName;
        DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(fieldLabel);
        classNode.add(fieldNode);

        // Continue drilling into the referenced class if not already visited
        if (!visitedClasses.contains(referencedClass.source)) {
            visitedClasses.add(referencedClass.source);
            expandClassFields(fieldNode, referencedClass);
        }
    }

    /**
     * Builds the Unreached branch showing all classes not encountered during tree
     * building.
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
        unreachedClassList.sort(Comparator.comparing(c -> ClassUtil.getSimpleName(c.source)));

        // Add each unreached class to the tree
        for (DOSchemaClass schemaClass : unreachedClassList) {
            String simpleName = ClassUtil.getSimpleName(schemaClass.source);
            String fullName = schemaClass.source;
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(simpleName + " (" + fullName + ")");
            parentNode.add(classNode);

            // Drill backwards to find what references this class
            Set<String> visitedBackward = new HashSet<>();
            visitedBackward.add(schemaClass.source);
            drillBackwards(classNode, schemaClass, visitedBackward);
        }
    }

    /**
     * Drills backwards from an unreached class to find all fields that reference
     * it.
     * Continues recursively to understand the chain of unreachability.
     */
    private void drillBackwards(DefaultMutableTreeNode classNode, DOSchemaClass targetClass,
            Set<String> visitedBackward) {
        String targetClassName = targetClass.source;

        // Find all classes and fields that reference this target class
        List<DOSchemaFieldReference> references = new ArrayList<>();

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            DOSchemaField[] fields = schemaClass.fields;
            if (fields == null) {
                continue;
            }

            for (DOSchemaField field : fields) {
                String fieldType = field.type;
                if (fieldType == null) {
                    continue;
                }

                boolean isMatch = false;

                // Check if this field's direct type references our target class
                if (fieldType.equals(targetClassName)) {
                    isMatch = true;
                }
                // Check if it's a collection with childrenType matching our target
                else if (field.isCollection) {
                    String childrenType = field.childrenType;
                    if (childrenType != null && childrenType.equals(targetClassName)) {
                        isMatch = true;
                    }
                    // Also check if children type is IDEntite pointing to our target
                    else if (childrenType != null) {
                        DOSchemaClass childTypeClass = SchemaUtil.findClassByName(childrenType, schema);
                        if (childTypeClass != null && childTypeClass.isIDEntite(schema)) {
                            // Use pointsTo if available, otherwise fall back to name extraction
                            String pointsTo = childTypeClass.pointsTo;
                            if (pointsTo != null && pointsTo.equals(targetClassName)) {
                                isMatch = true;
                            } else if (pointsTo == null) {
                                // Fallback to name extraction
                                String expectedType = extractExpectedTypeFromFieldName(field.source, childrenType);
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
                        // Use pointsTo if available, otherwise fall back to name extraction
                        String pointsTo = fieldTypeClass.pointsTo;
                        if (pointsTo != null && pointsTo.equals(targetClassName)) {
                            isMatch = true;
                        } else if (pointsTo == null) {
                            // Fallback to name extraction
                            String expectedType = extractExpectedTypeFromFieldName(field.source, fieldType);
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
            String refClassName = ref.schemaClass.source;
            String refSimpleName = ClassUtil.getSimpleName(refClassName);
            String fieldLabel = "← " + refSimpleName + "." + ref.field.source;
            DefaultMutableTreeNode refNode = new DefaultMutableTreeNode(fieldLabel);
            classNode.add(refNode);

            // Continue drilling backwards if this referencing class hasn't been visited
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
     * Extracts expected EntiteContientID type from field name.
     * Example: "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
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
