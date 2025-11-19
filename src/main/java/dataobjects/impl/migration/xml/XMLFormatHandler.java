package dataobjects.impl.migration.xml;

import dataobjects.api.migration.generic.ExportFormatHandler;
import dataobjects.api.migration.generic.ExportColumn;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * XML format handler for the generic export engine.
 * Handles XML-specific operations using StAX streaming for performance.
 */
public class XMLFormatHandler implements ExportFormatHandler {

    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";
    private static final String NAMESPACE_URI = "migration4o";

    private String outputDirectory;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // Context class for module (XML file per module)
    private static class ModuleContext {
        XMLStreamWriter writer;
        FileOutputStream outputStream;
        String fileName;
        int totalObjects = 0;
    }

    // Context class for class (element container)
    private static class ClassContext {
        ModuleContext moduleContext;
        String className;
        int objectCount = 0;
    }

    @Override
    public void initialize(String outputDirectory) throws IOException {
        this.outputDirectory = outputDirectory;

        // Create output directory
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @Override
    public Object beginModule(DOSchemaModule module) throws IOException {
        try {
            ModuleContext ctx = new ModuleContext();
            ctx.fileName = outputDirectory + "/" + sanitizeFileName(module.getName()) + ".xml";

            File file = new File(ctx.fileName);
            ctx.outputStream = new FileOutputStream(file);

            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            ctx.writer = factory.createXMLStreamWriter(ctx.outputStream, "UTF-8");

            // Start XML document
            ctx.writer.writeStartDocument("UTF-8", "1.0");
            ctx.writer.writeCharacters("\n");

            // Root element for module
            ctx.writer.writeStartElement("module");
            ctx.writer.writeAttribute("name", module.getName());
            ctx.writer.writeAttribute("xmlns", NAMESPACE_URI);
            ctx.writer.writeCharacters("\n");

            return ctx;
        } catch (Exception e) {
            throw new IOException("Error starting module XML: " + e.getMessage(), e);
        }
    }

    @Override
    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,
            List<ExportColumn> columns, int objectCount) throws IOException {
        try {
            ModuleContext modCtx = (ModuleContext) moduleContext;
            ClassContext clsCtx = new ClassContext();
            clsCtx.moduleContext = modCtx;

            // Use export name if available, otherwise fall back to short name
            String exportName = schemaClass.getExportName();
            if (exportName == null || exportName.isEmpty()) {
                exportName = schemaClass.getShortName();
            }
            clsCtx.className = exportName;

            // Start class container element
            modCtx.writer.writeCharacters("  ");
            modCtx.writer.writeStartElement("class");
            modCtx.writer.writeAttribute("name", exportName);
            modCtx.writer.writeAttribute("count", String.valueOf(objectCount));
            modCtx.writer.writeCharacters("\n");

            return clsCtx;
        } catch (Exception e) {
            throw new IOException("Error starting class XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,
            List<Object> cellValues) throws IOException {
        try {
            ClassContext ctx = (ClassContext) classContext;
            XMLStreamWriter writer = ctx.moduleContext.writer;

            // Start object element
            writer.writeCharacters("    ");
            writer.writeStartElement("object");
            writer.writeAttribute("id", String.valueOf(obj.getObjectId()));
            writer.writeCharacters("\n");

            // Write each field as a child element
            for (int i = 0; i < columns.size(); i++) {
                ExportColumn column = columns.get(i);
                Object value = cellValues.get(i);

                if (value != null) {
                    writer.writeCharacters("      ");
                    writer.writeStartElement("field");
                    writer.writeAttribute("name", column.columnName);

                    String valueStr = formatValueForXML(value, isIDTypeField(column));

                    if (valueStr != null && !valueStr.isEmpty()) {
                        writer.writeCharacters(valueStr);
                    }

                    writer.writeEndElement(); // field
                    writer.writeCharacters("\n");
                }
            }

            // End object element
            writer.writeCharacters("    ");
            writer.writeEndElement(); // object
            writer.writeCharacters("\n");

            ctx.objectCount++;
            ctx.moduleContext.totalObjects++;
        } catch (Exception e) {
            throw new IOException("Error writing row XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {
        try {
            ClassContext ctx = (ClassContext) classContext;

            // End class container element
            ctx.moduleContext.writer.writeCharacters("  ");
            ctx.moduleContext.writer.writeEndElement(); // class
            ctx.moduleContext.writer.writeCharacters("\n");
        } catch (Exception e) {
            throw new IOException("Error ending class XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {
        try {
            ModuleContext ctx = (ModuleContext) moduleContext;

            // End root element
            ctx.writer.writeEndElement(); // module
            ctx.writer.writeCharacters("\n");

            // End document
            ctx.writer.writeEndDocument();
            ctx.writer.flush();
            ctx.writer.close();

            ctx.outputStream.close();

            System.out.println("  Exported " + ctx.totalObjects + " total objects to " + ctx.fileName);
        } catch (Exception e) {
            throw new IOException("Error ending module XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void finalize() throws IOException {
        // Nothing to finalize for XML export
    }

    @Override
    public String getDefaultOutputDirectory() {
        return DEFAULT_OUTPUT_DIR;
    }

    private boolean isIDTypeField(ExportColumn column) {
        if (column.isFlattened) {
            return false; // Flattened fields are not ID fields
        }
        String typeName = column.field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    private String formatValueForXML(Object value, boolean isIDField) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            Number numValue = (Number) value;
            // Skip -1 values for ID fields (indicates no reference)
            if (isIDField && numValue.intValue() == -1) {
                return null;
            }
            return value.toString();
        } else if (value instanceof Date) {
            return dateFormat.format((Date) value);
        } else if (value instanceof String) {
            String strValue = (String) value;
            // Convert French boolean strings to English
            if ("VRAI".equalsIgnoreCase(strValue)) {
                return "true";
            } else if ("FAUX".equalsIgnoreCase(strValue)) {
                return "false";
            }
            // Escape XML special characters
            return escapeXML(strValue);
        } else {
            return escapeXML(value.toString());
        }
    }

    private String escapeXML(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown";
        }
        // Replace any non-alphanumeric chars except dots and hyphens
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
