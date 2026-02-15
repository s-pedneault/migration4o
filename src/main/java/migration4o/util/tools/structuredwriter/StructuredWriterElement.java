package migration4o.util.tools.structuredwriter;

import java.util.Map;
import java.util.Vector;

public abstract class StructuredWriterElement {

    public int indent;
    public String name;
    public Map<String, String> attributes;
    public StructuredWriterElement parent;

    public StringBuilder prefix = new StringBuilder();
    public StringBuilder content = new StringBuilder();
    public StringBuilder suffix = new StringBuilder();
    public boolean hasWrittenChild;
    public String openChildArrayName;
    public boolean openChildArrayHasElements;

    public Vector<StructuredWriterElement> children = new Vector<StructuredWriterElement>();

    public StructuredWriterElement(String name, Map<String, String> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    public boolean isEmpty() {
        boolean isEmpty = content.length() == 0;
        for (StructuredWriterElement child : children) {
            if (!child.isEmpty()) {
                isEmpty = false;
                break;
            }
        }
        return isEmpty;
    }

}
