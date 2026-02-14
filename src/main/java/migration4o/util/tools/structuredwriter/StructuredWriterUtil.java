package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.util.Map;

public class StructuredWriterUtil {

    public static void initXML(StructuredWriter writer) throws IOException {
        writer.writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    public static void openRoot(StructuredWriter writer, String name, String schemaLocation) throws IOException {
        writer.open(name, Map.of("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", schemaLocation), true);
    }

    public static void metadata(StructuredWriter writer, StructuredWriterMetadata metadata) throws IOException {
        if (metadata != null) {
            writer.open("metadata");
            if (metadata.generator != null)
                writer.open("generator").data(metadata.generator).close();
            if (metadata.provider != null)
                writer.open("provider").data(metadata.provider).close();
            if (metadata.module != null)
                writer.open("module").data(metadata.module).close();
            if (metadata.type != null)
                writer.open("type").data(metadata.type).close();
            if (metadata.objects != null)
                writer.open("objects").data(metadata.objects).close();
            if (metadata.date != null)
                writer.open("date").data(metadata.date).close();
            writer.close();
        }
    }

}
