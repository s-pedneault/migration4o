package migration4o.util.tools.structuredwriter;

import java.io.IOException;

public interface StructuredWriterAPI {

    public String getName();

    default void initialize(StructuredWriter writer) throws IOException {
    }

    default boolean includeCollectionSizeMetadata() {
        return true;
    }

    default void onDocumentComplete(StructuredWriter writer) throws IOException {
    }

    public void add(StructuredWriterElementWithoutContent element) throws IOException;

    public void addContent(StructuredWriterElementWithContent element, String content) throws IOException;

    public void openStructure(StructuredWriterElementWithStructure element) throws IOException;

    public void closeStructure(StructuredWriterElementWithStructure element) throws IOException;
}
