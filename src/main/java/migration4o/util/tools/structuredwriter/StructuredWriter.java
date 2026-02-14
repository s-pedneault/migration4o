package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Vector;

/*
<tag />: OPEN (<tag), CLOSE ( />\n)   CLOSE causes write of OPEN + CLOSE.

<tag>content</tag>: OPEN (<tag), data (> + content), CLOSE (</tag>\n)   CLOSE causes  of OPEN + CLOSE.

<tag>
  <child>content</child>
</tag> : OPEN (<tag), OPEN-2 (>\n), ..., CLOSE-2, CLOSE (</tag>\n)   OPEN-2 causes write of OPEN + OPEN-2

*/

public class StructuredWriter {

    final StructuredWriterAPI api;
    final Writer writer;
    public Vector<StructuredWriterBlock> branch = new Vector<StructuredWriterBlock>();

    public StructuredWriter(StructuredWriterAPI api, Writer writer) throws IOException {
        this.api = api;
        this.writer = writer;
        StructuredWriterUtil.initXML(this);
    }

    public StructuredWriter root(String name, String schemaLocation) throws IOException {
        StructuredWriterUtil.openRoot(this, name, schemaLocation);
        return this;
    }

    // public StructuredWriter add(String name, Map<String, String> attributes)
    // throws IOException {
    // return open(name, attributes, false);
    // }

    public StructuredWriter open(String name) throws IOException {
        return open(name, null, true);
    }

    public StructuredWriter open(String name, Map<String, String> attributes) throws IOException {
        return open(name, attributes, true);
    }

    public StructuredWriter open(String name, Map<String, String> attributes, boolean complex) throws IOException {
        StructuredWriterBlock block = new StructuredWriterBlock(name, attributes);
        open(block, complex);
        return this;
    }

    // Main OPEN
    public StructuredWriter open(StructuredWriterBlock block, boolean complex) throws IOException {
        StructuredWriterBlock parent = block();
        if (parent != null) {
            parent.children.add(block);
        }
        block.indent = branch.size();
        branch.add(block);
        api.open(block, complex);
        if (complex) {
            writer.write(block.block.toString());
            System.out.println("Opened tag: " + block.block.toString());
            block.block.setLength(0); // Clear the block content as it's already written
        }
        return this;
    }

    // Main DATA
    public StructuredWriter data(String content) throws IOException {
        if (content == null)
            return this;
        api.data(content, block());
        System.out.println("      - Added " + content.length() + " to block  " + block().name);
        return this;
    }

    // Main CLOSE
    public void close() throws IOException {
        StructuredWriterBlock block = branch.lastElement();
        boolean hasContent = block.content.length() > 0;
        boolean hasChildren = !block.children.isEmpty();

        api.compile(block);
        writer.write(block.block.toString());
        System.out.println("Closed tag: " + block.block.toString());
        branch.removeLast();
    }

    public int indent() {
        return branch.size() - 1;
    }

    public StructuredWriterBlock block() {
        return branch.lastElement();
    }

}
