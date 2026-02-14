package migration4o.util.tools.structuredwriter;

import java.io.IOException;

public interface StructuredWriterAPI {

    void open(StructuredWriterBlock block, boolean complex) throws IOException;

    void data(String content, StructuredWriterBlock block) throws IOException;

    void compile(StructuredWriterBlock block) throws IOException;

}
