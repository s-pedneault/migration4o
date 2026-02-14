package migration4o.util.tools.structuredwriter;

import java.util.Map;
import java.util.Vector;

public class StructuredWriterBlock {

    public int indent;
    public String name;
    public Map<String, String> attributes;
    public StringBuilder content = new StringBuilder();
    public StringBuilder block = new StringBuilder();
    public Vector<StructuredWriterBlock> children = new Vector<StructuredWriterBlock>();
    public boolean openWritten = false;

    public StructuredWriterBlock(String name, Map<String, String> attributes) {
        this.name = name;
        this.attributes = attributes;
    }

    public boolean isEmpty() {
        boolean isEmpty = content.length() == 0;
        for (StructuredWriterBlock child : children) {
            if (!child.isEmpty()) {
                isEmpty = false;
                break;
            }
        }
        return isEmpty;
    }

}
