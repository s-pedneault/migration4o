package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Vector;

/*
elementWithoutContent();
<tag />: OPEN (<tag), CLOSE ( />\n)   CLOSE causes write of OPEN + CLOSE.

elementWithContent()
<tag>content</tag>: OPEN (<tag), data (> + content), CLOSE (</tag>\n)   CLOSE causes  of OPEN + CLOSE.

elementWithStructure()
<tag>
  <child>content</child>
</tag> : OPEN (<tag), OPEN-2 (>\n), ..., CLOSE-2, CLOSE (</tag>\n)   OPEN-2 causes write of OPEN + OPEN-2

*/

public class StructuredWriter {

    final StructuredWriterAPI api;
    public final Writer writer;
    public final Path outputPath;
    public Vector<StructuredWriterElement> branch = new Vector<StructuredWriterElement>();

    public StructuredWriter(StructuredWriterAPI api, Writer writer) throws IOException {
        this(api, writer, null);
    }

    public StructuredWriter(StructuredWriterAPI api, Writer writer, Path outputPath) throws IOException {
        this.api = api;
        this.writer = writer;
        this.outputPath = outputPath;
        api.initialize(this);
    }

    public StructuredWriter elementWithoutContent(String name) throws IOException {
        return elementWithoutContent(name, null);
    }

    public StructuredWriter elementWithoutContent(String name, Map<String, String> attributes) throws IOException {
        StructuredWriterElementWithoutContent element = new StructuredWriterElementWithoutContent(name, attributes);
        pushElement(element);
        api.add(element);
        writer.write(element.prefix.toString());
        popElement(element);
        return this;
    }

    public StructuredWriter elementWithContent(String name, String value, boolean skipIfEmpty) throws IOException {
        return elementWithContent(name, null, value, skipIfEmpty);
    }

    public StructuredWriter elementWithContent(String name, Map<String, String> attributes, String value, boolean skipIfEmpty) throws IOException {
        StructuredWriterElementWithContent element = new StructuredWriterElementWithContent(name, attributes);
        pushElement(element);
        try {
            if (value != null)
                api.addContent(element, value);

            if (skipIfEmpty && element.prefix.isEmpty() && element.content.isEmpty() && element.suffix.isEmpty())
                return this;

            writer.write(element.prefix.toString());
            writer.write(element.content.toString());
            writer.write(element.suffix.toString());
            return this;
        } finally {
            popElement(element);
        }
    }

    public StructuredWriter openRootStructure(String name, String schemaLocation) throws IOException {
        StructuredWriterUtil.openRoot(this, name, schemaLocation);
        return this;
    }

    public StructuredWriter openStructure(String name) throws IOException {
        return openStructure(name, null);
    }

    public StructuredWriter openStructure(String name, Map<String, String> attributes) throws IOException {
        return openElement(name, attributes, false);
    }

    public StructuredWriter openArray(String name) throws IOException {
        return openArray(name, null);
    }

    public StructuredWriter openArray(String name, Map<String, String> attributes) throws IOException {
        return openElement(name, attributes, true);
    }

    private StructuredWriter openElement(String name, Map<String, String> attributes, boolean isArray) throws IOException {
        StructuredWriterElementWithStructure element = new StructuredWriterElementWithStructure(name, attributes);
        element.isArray = isArray;
        pushElement(element);
        if (isArray) {
            api.openArray(element);
        } else {
            api.openStructure(element);
        }
        writer.write(element.prefix.toString());
        return this;
    }

    public StructuredWriter closeStructure(String name) throws IOException {
        return closeElement(name);
    }

    public StructuredWriter closeArray(String name) throws IOException {
        return closeElement(name);
    }

    private StructuredWriter closeElement(String name) throws IOException {
        StructuredWriterElement element = element();
        if (element == null) {
            System.err.println("[WARN] closeElement(\"" + name + "\"): branch stack is empty — ignored");
            return this;
        }

        if (element instanceof StructuredWriterElementWithStructure && element.name.equals(name)) {
            // Normal case: top matches the expected name
            closeStructureElement((StructuredWriterElementWithStructure) element);
        } else {
            // Mismatch: search the stack for the target name and close all
            // intervening elements to recover a consistent state.
            System.err.println("[WARN] Mismatched closing tag: expected </" + element.name + "> but got </" + name + "> — recovering");
            boolean found = false;
            for (int i = branch.size() - 1; i >= 0; i--) {
                if (branch.get(i).name.equals(name)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                // Pop and close elements until we reach (and include) the target
                while (!branch.isEmpty()) {
                    StructuredWriterElement top = branch.lastElement();
                    if (top instanceof StructuredWriterElementWithStructure) {
                        closeStructureElement((StructuredWriterElementWithStructure) top);
                    }
                    popElement(top);
                    if (top.name.equals(name)) {
                        break;
                    }
                }
            } else {
                // Target not found in stack — ignore the close to avoid further corruption
                System.err.println("[WARN] closeElement(\"" + name + "\"): not found in branch stack — ignored");
            }
        }

        if (branch.isEmpty()) {
            api.onDocumentComplete(this);
        }
        return this;
    }

    private void closeStructureElement(StructuredWriterElementWithStructure element) throws IOException {
        if (element.isArray) {
            api.closeArray(element);
        } else {
            api.closeStructure(element);
        }
        writer.write(element.suffix.toString());
        popElement(element);
    }

    private void pushElement(StructuredWriterElement element) {
        StructuredWriterElement parent = element();
        if (parent != null) {
            parent.children.add(element);
            element.parent = parent;
        }
        element.indent = branch.size();
        branch.add(element);
    }

    private void popElement(StructuredWriterElement element) {
        branch.removeLast();
    }
    //

    // public StructuredWriter open(String name) throws IOException {
    // return open(name, null, true);
    // }

    // public StructuredWriter open(String name, Map<String, String> attributes)
    // throws IOException {
    // return open(name, attributes, true);
    // }

    // public StructuredWriter open(String name, Map<String, String> attributes,
    // boolean complex) throws IOException {
    // StructuredWriterElement block = new StructuredWriterElement(name,
    // attributes);
    // open(block, complex);
    // return this;
    // }

    // Main OPEN
    // public StructuredWriter open(StructuredWriterElement block, boolean
    // complex) throws IOException {
    // StructuredWriterElement parent = block();
    // if (parent != null) {
    // parent.children.add(block);
    // }
    // block.indent = branch.size();
    // branch.add(block);
    // api.open(block, complex);
    // if (complex) {
    // writer.write(block.block.toString());
    // System.out.println("Opened tag: " + block.block.toString());
    // block.block.setLength(0); // Clear the block content as it's already
    // // written
    // }
    // return this;
    // }

    // // Main DATA
    // public StructuredWriter data(String content) throws IOException {
    // if (content == null)
    // return this;
    // api.data(content, block());
    // System.out.println(" - Added " + content.length() + " to block " +
    // block().name);
    // return this;
    // }

    // // Main CLOSE
    // public void close() throws IOException {
    // StructuredWriterElement block = branch.lastElement();
    // boolean hasContent = block.content.length() > 0;
    // boolean hasChildren = !block.children.isEmpty();

    // api.compile(block);
    // writer.write(block.block.toString());
    // System.out.println("Closed tag: " + block.block.toString());
    // branch.removeLast();
    // }

    public int indent() {
        return branch.size() - 1;
    }

    public StructuredWriterElement element() {
        return branch.size() > 0 ? branch.lastElement() : null;
    }

    public void metadata(StructuredWriterMetadata metadata) throws IOException {
        StructuredWriterUtil.metadata(this, metadata);
    }

    public boolean includeCollectionSizeMetadata() {
        return api.includeCollectionSizeMetadata();
    }

}
