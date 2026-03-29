package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.util.Map;

public class StructuredWriterUtil {

    public static void initXML(StructuredWriter writer) throws IOException {
        writer.writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    public static void openRoot(StructuredWriter writer, String name, String schemaLocation) throws IOException {
        writer.openStructure(name, Map.of("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", schemaLocation));
    }

    public static void metadata(StructuredWriter writer, StructuredWriterMetadata metadata) throws IOException {
        if (metadata != null) {
            writer.openStructure("metadata");
            if (metadata.generator != null)
                writer.elementWithContent("generator", metadata.generator, true);
            if (metadata.schemaVersion != null)
                writer.elementWithContent("schemaVersion", metadata.schemaVersion, true);
            if (metadata.provider != null)
                writer.elementWithContent("provider", metadata.provider, true);
            if (metadata.module != null)
                writer.elementWithContent("module", metadata.module, true);
            if (metadata.type != null)
                writer.elementWithContent("type", metadata.type, true);
            if (metadata.objects != null)
                writer.elementWithContent("objects", metadata.objects, true);
            if (metadata.date != null)
                writer.elementWithContent("date", metadata.date, true);
            writer.closeStructure("metadata");
        }
    }

}
