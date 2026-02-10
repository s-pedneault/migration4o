package migration4o.migration;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

/**
 * Handles low-level XML writing operations including formatting and escaping.
 * Can write to any Writer (FileWriter, StringWriter, etc.) for flexibility.
 */
public class XMLWriter {
    private final Writer writer;
    private static final String INDENT = "  ";

    public XMLWriter(Writer writer) {
        this.writer = writer;
    }

    public void writeXMLHeader() throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    // public void writeXMLHeaderWithSchema(String schemaLocation) throws
    // IOException {
    // writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    // if (schemaLocation != null && !schemaLocation.isEmpty()) {
    // // Schema location is relative from the Data folder to Definitions folder
    // writer.write("<?xml-stylesheet type=\"text/xsl\" href=\"" + schemaLocation +
    // "\"?>\n");
    // }
    // }

    // public void writeExportHeader(String className) throws IOException {
    // writer.write("<export>\n");
    // writer.write(" <metadata>\n");
    // writer.write(" <exportClass>" + xmlEscape(className) + "</exportClass>\n");
    // writer.write(" <exportDate>" + new Date() + "</exportDate>\n");
    // writer.write(" </metadata>\n");
    // writer.write(" <objects>\n");
    // }

    public void writeExportHeaderWithSchema(String className, String schemaLocation) throws IOException {
        writer.write("<export");
        if (schemaLocation != null && !schemaLocation.isEmpty()) {
            writer.write(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            writer.write(" xsi:noNamespaceSchemaLocation=\"" + schemaLocation + "\"");
        }
        writer.write(">\n");
        writer.write(" <metadata>\n");
        writer.write(" <exportClass>" + xmlEscape(className) + "</exportClass>\n");
        writer.write(" <exportDate>" + new Date() + "</exportDate>\n");
        writer.write(" </metadata>\n");
        writer.write(" <objects>\n");
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

    public void writeEmptyElement(String elementName, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("<" + elementName + "/>\n");
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
     * Escapes special XML characters in text content and removes invalid XML
     * characters.
     * XML 1.0 only allows:
     * - #x9 (tab), #xA (line feed), #xD (carriage return)
     * - #x20-#xD7FF, #xE000-#xFFFD, #x10000-#x10FFFF
     */
    public static String xmlEscape(String text) {
        if (text == null) {
            return "";
        }

        // First, sanitize invalid XML characters
        text = sanitizeXMLCharacters(text);

        // Then, escape XML entities
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Removes characters that are invalid in XML 1.0.
     * Control characters (except tab, newline, carriage return) are replaced with
     * space.
     */
    private static String sanitizeXMLCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Valid XML 1.0 characters
            if (c == 0x9 || c == 0xA || c == 0xD ||
                    (c >= 0x20 && c <= 0xD7FF) ||
                    (c >= 0xE000 && c <= 0xFFFD)) {
                sb.append(c);
            } else {
                // Replace invalid characters with space
                sb.append(' ');
            }
        }
        return sb.toString();
    }

}
