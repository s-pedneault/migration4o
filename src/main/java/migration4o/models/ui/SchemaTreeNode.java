package migration4o.models.ui;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Tree node for schema elements (modules, classes, fields).
 */
public class SchemaTreeNode extends DefaultMutableTreeNode {

    public enum NodeType {
        ROOT,
        MODULE,
        CLASS,
        FIELD,
        FOLDER
    }

    private final NodeType nodeType;
    private Object schemaElement; // DOSchemaModule, DOSchemaClass, or DOSchemaField

    public SchemaTreeNode(String label, NodeType nodeType) {
        super(label);
        this.nodeType = nodeType;
    }

    public SchemaTreeNode(String label, NodeType nodeType, Object schemaElement) {
        super(label);
        this.nodeType = nodeType;
        this.schemaElement = schemaElement;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Object getSchemaElement() {
        return schemaElement;
    }

    public void setSchemaElement(Object schemaElement) {
        this.schemaElement = schemaElement;
    }

    @Override
    public String toString() {
        return (String) getUserObject();
    }
}
