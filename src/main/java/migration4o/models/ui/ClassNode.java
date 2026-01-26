package migration4o.models.ui;

import migration4o.models.schema.DOSchemaClass;

/**
 * Model class representing a class node in the migration structure tree.
 * Handles display formatting with object counts.
 */
public class ClassNode {
    private DOSchemaClass schemaClass;

    public ClassNode(DOSchemaClass schemaClass) {
        this.schemaClass = schemaClass;
    }

    public DOSchemaClass getSchemaClass() {
        return schemaClass;
    }

    @Override
    public String toString() {
        String simpleName = schemaClass.source;
        if (simpleName.contains(".")) {
            simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
        }
        int objectCount = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0;
        if (objectCount > 0) {
            return simpleName + " (" + objectCount + " objects)";
        } else {
            return simpleName;
        }
    }
}
