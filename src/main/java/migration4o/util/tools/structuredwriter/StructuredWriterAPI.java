package migration4o.util.tools.structuredwriter;

import java.io.IOException;

public interface StructuredWriterAPI {

    void data(String content, StructuredWriterBlock block) throws IOException;

    void compile(StructuredWriterBlock block) throws IOException;

}
