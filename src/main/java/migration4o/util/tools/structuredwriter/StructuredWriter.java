package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Vector;

public class StructuredWriter {

    final StructuredWriterAPI api;
    final Writer writer;
    public Vector<StructuredWriterBlock> branch = new Vector<StructuredWriterBlock>();

    public StructuredWriter(StructuredWriterAPI api, Writer writer) throws IOException {
        this.api = api;
        this.writer = writer;
        init();
    }

    private void init() throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    public void metadata(StructuredWriterMetadata metadata) throws IOException {
        if (metadata != null) {
            open("metadata");
            if (metadata.generator != null)
                open("generator").data(metadata.generator).close();
            if (metadata.provider != null)
                open("provider").data(metadata.provider).close();
            if (metadata.module != null)
                open("module").data(metadata.module).close();
            if (metadata.type != null)
                open("type").data(metadata.type).close();
            if (metadata.objects != null)
                open("objects").data(metadata.objects).close();
            if (metadata.date != null)
                open("date").data(metadata.date).close();
            close();
        }
    }

    public StructuredWriter root(String name, String schemaLocation) throws IOException {
        open(name, Map.of("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", schemaLocation));
        return this;
    }

    public StructuredWriter open(String name) throws IOException {
        return open(name, null);
    }

    public StructuredWriter open(String name, Map<String, String> attributes) throws IOException {
        StructuredWriterBlock block = new StructuredWriterBlock(name, attributes);
        open(block);
        return this;
    }

    public StructuredWriter open(StructuredWriterBlock block) throws IOException {
        block.indent = branch.size();
        if (branch.size() > 0) {
            StructuredWriterBlock parent = branch.lastElement();
            parent.children.add(block);
        }
        branch.add(block);
        return this;
    }

    public StructuredWriter data(String content) throws IOException {
        if (content == null)
            return this;
        api.data(content, block());
        return this;
    }

    public void close() throws IOException {
        StructuredWriterBlock block = branch.lastElement();
        api.compile(block);
        writer.write(block.block.toString());
        branch.removeLast();
    }

    public int indent() {
        return branch.size() - 1;
    }

    public StructuredWriterBlock block() {
        return branch.lastElement();
    }

}
