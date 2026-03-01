package migration4o.util.tools.structuredwriter;

import java.util.List;

import migration4o.util.tools.structuredwriter.formats.StructuredWriterExcel;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterJS;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterJSON;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterXML;

public class StructuredWriterProvider {

    private static List<StructuredWriterAPI> formats = List.of(new StructuredWriterXML(), new StructuredWriterJSON(), new StructuredWriterJS(), new StructuredWriterExcel());

    public static List<StructuredWriterAPI> listFormats() {
        return formats;
    }

    public static StructuredWriterAPI getFormat(String name) {
        for (StructuredWriterAPI api : formats) {
            if (api.getName().equalsIgnoreCase(name)) {
                return api;
            }
        }
        return null;
    }

}
