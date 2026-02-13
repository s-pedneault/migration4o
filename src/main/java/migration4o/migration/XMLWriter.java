package migration4o.migration;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Map;

import migration4o.util.XMLUtil;

/**
 * Handles low-level XML writing operations including formatting and escaping.
 * Can write to any Writer (FileWriter, StringWriter, etc.) for flexibility.
 */

@FunctionalInterface
interface XMLContentsProvider {
    void write(Writer writer) throws IOException;
}

public class XMLWriter {
    private static final String INDENT = "  ";
    private final Writer writer;
    private static final String GENERATOR = "Migration4o";
    private static final String PROVIDER = "Gestion Technologies";

    public XMLWriter(Writer writer) {
        this.writer = writer;
    }

    private void writeLine(int indent, String line) throws IOException {
        writer.write(INDENT.repeat(indent) + line + "\n");
    }

    /**
     * Writes a self-closing tag with optional attributes and indentation, and then
     * adds a new line. Example: <tag attr="value" />. Does not write content.
     * 
     * @param indent
     * @param tag
     * @param attributes
     * @throws IOException
     */
    public void tagNoContent(int indent, String tag, Map<String, String> attributes) throws IOException {
        indent(indent);
        openTag(tag, attributes, true);
        newLine();
    }

    public void tagOpen(int indent, String tag, Map<String, String> attributes) throws IOException {
        indent(indent);
        openTag(tag, attributes, false);
        newLine();
    }

    /**
     * Writes a tag with optional attributes and content. If contents is null,
     * writes an empty tag. If contents is provided, it will be written inside the
     * tag. A new line is added after the tag. Example:
     * <tag attr="value">contents</tag>
     * 
     * @param indent
     * @param tag
     * @param attributes
     * @param contents
     * @throws IOException
     */
    public void tagInline(int indent, String tag, Map<String, String> attributes, XMLContentsProvider contents) throws IOException {
        indent(indent);
        openTag(tag, attributes, false);
        if (contents != null) {
            contents.write(writer);
            closeTag(0, tag);
        } else {
            newLine();
        }
    }

    public void tagInline(int indent, String tag, Map<String, String> attributes, String contents) throws IOException {
        tagInline(indent, tag, attributes, (writer) -> {
            if (contents != null)
                writer.write(contents);
        });
    }

    public void tagComplex(int indent, String tag, Map<String, String> attributes, XMLContentsProvider contents) throws IOException {
        indent(indent);
        openTag(tag, attributes, false);
        newLine();
        if (contents != null) {
            contents.write(writer);
        }
        closeTag(indent, tag);
    }

    // public void tag(int indent, String tag, Map<String, String> attributes,
    // boolean autoClose, boolean newLine) throws IOException {
    // tag(indent, tag, attributes, (XMLContentsProvider) null, autoClose, newLine);
    // }

    // public void tag(int indent, String tag, Map<String, String> attributes,
    // String contents, boolean autoClose, boolean newLine) throws IOException {
    // tag(indent, tag, attributes, (writer) -> {
    // if (contents != null)
    // writer.write(contents);
    // }, autoClose, newLine);
    // }

    // public void tag(int indent, String tag, Map<String, String> attributes,
    // XMLContentsProvider contents, boolean autoClose, boolean newLine) throws
    // IOException {
    // writer.write(INDENT.repeat(indent));
    // openTag(tag, attributes, autoClose);
    // if (contents != null) {
    // contents.write(writer);
    // closeTag(indent, tag);
    // }
    // newLine(newLine);
    // }

    public void indent(int indent) throws IOException {
        writer.write(INDENT.repeat(indent));
    }

    public void newLine() throws IOException {
        writer.write("\n");
    }

    public void newLine(boolean newLine) throws IOException {
        if (newLine)
            newLine();
    }

    /**
     * Writes a start tag with optional attributes. It does NOT write indentation or
     * new line.
     */
    private void openTag(String tag, Map<String, String> attributes, boolean selfClose) throws IOException {
        writer.write("<" + tag);
        if (attributes != null) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                writeAttribute(entry.getKey(), entry.getValue());
            }
        }
        writer.write(selfClose ? " />" : ">");
    }

    public void closeTag(int indent, String tag) throws IOException {
        writeLine(indent, "</" + tag + ">");
    }

    public void writeXMLHeader() throws IOException {
        writeLine(0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }

    public void writeExportHeader(String module, String type, Integer objects, String schemaLocation) throws IOException {

        tagOpen(0, "export", Map.of("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "schemaLocation"));

        tagComplex(1, "metadata", null, (Writer metadata) -> {
            tagInline(2, "generator", null, GENERATOR);
            tagInline(2, "provider", null, PROVIDER);
            tagInline(2, "module", null, module);
            tagInline(2, "type", null, type);
            tagInline(2, "objects", null, objects != null ? objects.toString() : "");
            tagInline(2, "exportDate", null, new Date().toString());
        });
        tagOpen(1, "objects", null);
    }

    public void writeExportFooter() throws IOException {
        writeLine(1, "</objects>");
        writeLine(0, "</export>");
    }

    public void writeIndent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write(INDENT);
        }
    }

    public void writeStartElement(String elementName, int indentLevel, Map<String, String> args) throws IOException {
        writeLine(indentLevel, elementName);
    }

    public void writeStartElementWithId(String elementName, long objectId, int indentLevel) throws IOException {
        writeIndent(indentLevel);
        writer.write("<" + elementName + " id=\"" + objectId + "\">\n");
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
        writer.write(XMLUtil.xmlEscape(content));
        writer.write("</" + elementName + ">\n");
    }

    public void writeAttribute(String name, String value) throws IOException {
        writer.write(" " + name + "=\"" + XMLUtil.xmlEscape(value) + "\"");
    }

    public void write(String text) throws IOException {
        writer.write(text);
    }

    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }

}
