package migration4o.util.tools.structuredwriter.formats;

import java.io.IOException;
import java.util.Map;

import migration4o.util.XMLUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterElement;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithContent;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithStructure;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithoutContent;
import migration4o.util.tools.structuredwriter.StructuredWriterUtil;

public class StructuredWriterXML implements StructuredWriterAPI {

    private static final String INDENT = "  ";

    @Override
    public String getName() {
        return "XML";
    }

    @Override
    public void initialize(StructuredWriter writer) throws IOException {
        StructuredWriterUtil.initXML(writer);
    }

    @Override
    public void add(StructuredWriterElementWithoutContent element) throws IOException {
        appendIndent(element, element.prefix);
        appendOpenTag((StructuredWriterElement) element, true, element.prefix);
        appendNewLine(element, element.prefix);
    }

    @Override
    public void addContent(StructuredWriterElementWithContent element, String content) throws IOException {
        appendIndent(element, element.prefix);
        appendOpenTag(element, false, element.prefix);
        appendContent(content, element, element.content);
        appendCloseTag(element, element.suffix);
        appendNewLine(element, element.suffix);
    }

    @Override
    public void openStructure(StructuredWriterElementWithStructure element) throws IOException {
        appendIndent(element, element.prefix);
        appendOpenTag(element, false, element.prefix);
        appendNewLine(element, element.prefix);
    }

    @Override
    public void closeStructure(StructuredWriterElementWithStructure element) throws IOException {
        appendIndent(element, element.suffix);
        appendCloseTag(element, element.suffix);
        appendNewLine(element, element.suffix);
    }

    private void appendOpenTag(StructuredWriterElement element, boolean autoClose, StringBuilder destination) throws IOException {
        destination.append("<" + element.name);
        if (element.attributes != null) {
            for (Map.Entry<String, String> entry : element.attributes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                destination.append(" " + entry.getKey() + "=\"" + XMLUtil.xmlEscape(entry.getValue()) + "\"");
            }
        }
        if (autoClose) {
            destination.append(" />");
        } else {
            destination.append(">");
        }
    }

    private void appendCloseTag(StructuredWriterElement element, StringBuilder destination) throws IOException {
        destination.append("</" + element.name + ">");
    }

    private void appendIndent(StructuredWriterElement element, StringBuilder destination) {
        destination.append(INDENT.repeat(element.indent));
    }

    private void appendNewLine(StructuredWriterElement element, StringBuilder destination) {
        destination.append("\n");
    }

    public void appendContent(String content, StructuredWriterElement element, StringBuilder destination) throws IOException {
        String sanitizedContent = XMLUtil.xmlEscape(content);
        destination.append(sanitizedContent);
    }

}
