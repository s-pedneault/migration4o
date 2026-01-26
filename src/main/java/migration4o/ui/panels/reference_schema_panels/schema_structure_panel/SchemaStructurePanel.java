package migration4o.ui.panels.reference_schema_panels.schema_structure_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

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
        int entitiesCount = countDescendants("gest.gen.EntiteContientID");
        int paramsCount = countDescendants("gest.gen.EntiteParam");

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
            if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                entiteContientIDClasses.add(schemaClass);
            }
        }

        // Group by package
        Map<String, List<DOSchemaClass>> packageMap = new TreeMap<>();
        for (DOSchemaClass schemaClass : entiteContientIDClasses) {
            String packageName = getPackageName(schemaClass.source);
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
            packageClasses.sort(Comparator.comparing(c -> getSimpleName(c.source)));

            // Add each class to the package node
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = getSimpleName(schemaClass.source);
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
            if (isDescendantOf(schemaClass, "gest.gen.EntiteParam")) {
                entiteParamClasses.add(schemaClass);
            }
        }

        // Group by package
        Map<String, List<DOSchemaClass>> packageMap = new TreeMap<>();
        for (DOSchemaClass schemaClass : entiteParamClasses) {
            String packageName = getPackageName(schemaClass.source);
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
            packageClasses.sort(Comparator.comparing(c -> getSimpleName(c.source)));

            // Add each class to the package node
            for (DOSchemaClass schemaClass : packageClasses) {
                String simpleName = getSimpleName(schemaClass.source);
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
                    DOSchemaClass childClass = findClassInSchemaByName(childrenType);
                    if (childClass != null) {
                        // Check if children type is an IDEntite descendant
                        if (isDescendantOf(childClass, "gest.gen.IDEntite")) {
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
                DOSchemaClass referencedClass = findClassInSchemaByName(fieldType);
                if (referencedClass != null) {
                    // Check if this is an IDEntite descendant
                    if (isDescendantOf(referencedClass, "gest.gen.IDEntite")) {
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
            // Find the target class by absolute name first, then by simple name
            DOSchemaClass targetClass = findClassInSchemaByName(targetClassName);
            if (targetClass == null) {
                targetClass = findClassBySimpleName(targetClassName);
            }

            if (targetClass != null &&
                    (isDescendantOf(targetClass, "gest.gen.EntiteContientID") ||
                            isDescendantOf(targetClass, "gest.gen.EntiteParam"))) {
                // Mark the target class as reached
                unreachedClasses.remove(targetClass.source);

                String fieldLabel = "Field: " + field.source + " → "
                        + getSimpleName(targetClass.source);
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

        String referencedSimpleName = getSimpleName(referencedClass.source);
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
            DOSchemaClass schemaClass = findClassInSchemaByName(className);
            if (schemaClass != null) {
                unreachedClassList.add(schemaClass);
            }
        }

        // Update parent node label with count
        parentNode.setUserObject("Unreached (" + unreachedClassList.size() + ")");

        // Sort by simple name for easier navigation
        unreachedClassList.sort(Comparator.comparing(c -> getSimpleName(c.source)));

        // Add each unreached class to the tree
        for (DOSchemaClass schemaClass : unreachedClassList) {
            String simpleName = getSimpleName(schemaClass.source);
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
        List<FieldReference> references = new ArrayList<>();

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
                        DOSchemaClass childTypeClass = findClassInSchemaByName(childrenType);
                        if (childTypeClass != null && isDescendantOf(childTypeClass, "gest.gen.IDEntite")) {
                            // Use pointsTo if available, otherwise fall back to name extraction
                            String pointsTo = childTypeClass.pointsTo;
                            if (pointsTo != null && pointsTo.equals(targetClassName)) {
                                isMatch = true;
                            } else if (pointsTo == null) {
                                // Fallback to name extraction
                                String expectedType = extractExpectedTypeFromFieldName(field.source, childrenType);
                                if (expectedType != null) {
                                    String targetSimpleName = getSimpleName(targetClassName);
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
                    DOSchemaClass fieldTypeClass = findClassInSchemaByName(fieldType);
                    if (fieldTypeClass != null && isDescendantOf(fieldTypeClass, "gest.gen.IDEntite")) {
                        // Use pointsTo if available, otherwise fall back to name extraction
                        String pointsTo = fieldTypeClass.pointsTo;
                        if (pointsTo != null && pointsTo.equals(targetClassName)) {
                            isMatch = true;
                        } else if (pointsTo == null) {
                            // Fallback to name extraction
                            String expectedType = extractExpectedTypeFromFieldName(field.source, fieldType);
                            if (expectedType != null) {
                                String targetSimpleName = getSimpleName(targetClassName);
                                if (targetSimpleName.equals(expectedType)) {
                                    isMatch = true;
                                }
                            }
                        }
                    }
                }

                if (isMatch) {
                    references.add(new FieldReference(schemaClass, field));
                }
            }
        }

        // Add found references to the tree
        for (FieldReference ref : references) {
            String refClassName = ref.schemaClass.source;
            String refSimpleName = getSimpleName(refClassName);
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
    private static class FieldReference {
        DOSchemaClass schemaClass;
        DOSchemaField field;

        FieldReference(DOSchemaClass schemaClass, DOSchemaField field) {
            this.schemaClass = schemaClass;
            this.field = field;
        }
    }

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
        String simpleClassName = getSimpleName(idClassName);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2); // Remove "ID" prefix
        }
        return null;
    }

    /**
     * Counts how many classes in the schema descend from a given parent class.
     */
    private int countDescendants(String parentClassName) {
        int count = 0;
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (isDescendantOf(schemaClass, parentClassName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if a class is a descendant of a given parent class name.
     */
    private boolean isDescendantOf(DOSchemaClass schemaClass, String parentClassName) {
        if (schemaClass == null) {
            return false;
        }

        // Check if this is the parent class itself
        if (schemaClass.source.equals(parentClassName)) {
            return true;
        }

        // Check parent class recursively
        String parentName = schemaClass.parentClassName;
        if (parentName != null) {
            DOSchemaClass parentClass = findClassInSchemaByName(parentName);
            return isDescendantOf(parentClass, parentClassName);
        }

        return false;
    }

    /**
     * Finds a class in the schema by its absolute name.
     */
    private DOSchemaClass findClassInSchemaByName(String className) {
        if (className == null) {
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
     * Finds a class in the schema by its simple name (without package).
     */
    private DOSchemaClass findClassBySimpleName(String simpleName) {
        if (simpleName == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String schemaSimpleName = getSimpleName(schemaClass.source);
            if (schemaSimpleName.equals(simpleName)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Gets the simple name from a fully qualified class name.
     */
    private String getSimpleName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return "Unknown";
        }
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullyQualifiedName.length() - 1) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * Gets the package name from a fully qualified class name.
     */
    private String getPackageName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return "(default package)";
        }
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(0, lastDot);
        }
        return "(default package)";
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
