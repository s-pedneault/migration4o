package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

/**
 * Handles low-level XML writing operations including formatting and escaping.
 */
public class XMLWriter {
    private final FileWriter writer;
    private static final String INDENT = "  ";

    public XMLWriter(FileWriter writer) {
        this.writer = writer;
    }

    public void writeXMLHeader() throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    public void writeXMLHeaderWithSchema(String schemaLocation) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (schemaLocation != null && !schemaLocation.isEmpty()) {
            // Schema location is relative from the Data folder to Definitions folder
            writer.write("<?xml-stylesheet type=\"text/xsl\" href=\"" + schemaLocation + "\"?>\n");
        }
    }

    public void writeExportHeader(String className) throws IOException {
        writer.write("<export>\n");
        writer.write("  <metadata>\n");
        writer.write("    <exportClass>" + xmlEscape(className) + "</exportClass>\n");
        writer.write("    <exportDate>" + new Date() + "</exportDate>\n");
        writer.write("  </metadata>\n");
        writer.write("  <objects>\n");
    }

    public void writeExportHeaderWithSchema(String className, String schemaLocation) throws IOException {
        String schemaRef = schemaLocation != null
                ? " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\""
                        + schemaLocation + "\""
                : "";
        writer.write("<export" + schemaRef + ">\n");
        writer.write("  <metadata>\n");
        writer.write("    <exportClass>" + xmlEscape(className) + "</exportClass>\n");
        writer.write("    <exportDate>" + new Date() + "</exportDate>\n");
        writer.write("  </metadata>\n");
        writer.write("  <objects>\n");
    }

    public void writeModuleHeader(String moduleName, int classCount) throws IOException {
        writer.write("<export>\n");
        writer.write("  <metadata>\n");
        writer.write("    <moduleName>" + xmlEscape(moduleName) + "</moduleName>\n");
        writer.write("    <classCount>" + classCount + "</classCount>\n");
        writer.write("    <exportDate>" + new Date() + "</exportDate>\n");
        writer.write("  </metadata>\n");
        writer.write("  <objects>\n");
    }

    public void writeExportFooter() throws IOException {
        writer.write("  </objects>\n");
        writer.write("</export>\n");
    }

    public void writeIndent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write(INDENT);
        }
    }

    public void writeStartElement(String elementName, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("<" + elementName + ">\n");
    }

    public void writeStartElementWithSize(String elementName, int size, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("<" + elementName + " size=\"" + size + "\">\n");
    }

    public void writeEndElement(String elementName, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("</" + elementName + ">\n");
    }

    public void writeElement(String elementName, String content, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("<" + elementName + ">");
        writer.write(xmlEscape(content));
        writer.write("</" + elementName + ">\n");
    }

    public void writeAttribute(String name, String value) throws IOException {
        writer.write(" " + name + "=\"" + xmlEscape(value) + "\"");
    }

    public void write(String text) throws IOException {
        writer.write(text);
    }

    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Escapes special XML characters in text content.
     */
    public static String xmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}
