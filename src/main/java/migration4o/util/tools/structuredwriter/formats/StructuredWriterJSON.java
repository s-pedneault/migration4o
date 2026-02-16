package migration4o.util.tools.structuredwriter.formats;

import java.io.IOException;
import java.util.Map;

import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterElement;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithContent;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithStructure;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithoutContent;

public class StructuredWriterJSON implements StructuredWriterAPI {

    private static final String INDENT = "  ";

    @Override
    public String getName() {
        return "JSON";
    }

    @Override
    public void initialize(StructuredWriter writer) throws IOException {
        writer.writer.write("{");
    }

    @Override
    public boolean includeCollectionSizeMetadata() {
        return false;
    }

    @Override
    public void add(StructuredWriterElementWithoutContent element) throws IOException {
        beginValue(element, element.prefix);
        if (hasAttributes(element)) {
            element.prefix.append("{\"@attributes\": ");
            appendAttributesObject(element.attributes, element.prefix);
            element.prefix.append("}");
            return;
        }
        element.prefix.append("null");
    }

    @Override
    public void addContent(StructuredWriterElementWithContent element, String content) throws IOException {
        beginValue(element, element.prefix);
        if (hasAttributes(element)) {
            element.prefix.append("{\"@attributes\": ");
            appendAttributesObject(element.attributes, element.prefix);
            element.prefix.append(", \"#text\": ");
            appendQuoted(content, element.prefix);
            element.prefix.append("}");
            return;
        }
        appendQuoted(content, element.prefix);
    }

    @Override
    public void openStructure(StructuredWriterElementWithStructure element) throws IOException {
        if (element.parent == null) {
            element.prefix.append("\n");
            appendIndent(element.indent + 1, element.prefix);
            appendQuoted(element.name, element.prefix);
            element.prefix.append(": {");
        } else {
            beginValue(element, element.prefix);
            element.prefix.append("{");
        }

        if (!hasAttributes(element)) {
            return;
        }

        element.prefix.append("\"@attributes\": ");
        appendAttributesObject(element.attributes, element.prefix);
    }

    @Override
    public void closeStructure(StructuredWriterElementWithStructure element) throws IOException {
        closeOpenChildArray(element, element.suffix);

        if (!element.hasWrittenChild) {
            element.suffix.append("}");
        } else {
            element.suffix.append("}");
        }

        if (element.indent == 0) {
            element.suffix.append("\n}\n");
        }
    }

    private boolean hasAttributes(StructuredWriterElement element) {
        return element.attributes != null && !element.attributes.isEmpty();
    }

    private void beginValue(StructuredWriterElement element, StringBuilder destination) {
        StructuredWriterElement parent = element.parent;
        if (parent == null) {
            destination.append("\n");
            appendIndent(element.indent + 1, destination);
            appendQuoted(element.name, destination);
            destination.append(": ");
            return;
        }

        String childName = element.name;
        if (parent.openChildArrayName == null) {
            if (hasAttributes(parent) || parent.hasWrittenChild) {
                destination.append(",\n");
            } else {
                destination.append("\n");
            }
            openChildArray(parent, childName, destination);
        } else if (!parent.openChildArrayName.equals(childName)) {
            closeOpenChildArray(parent, destination);
            destination.append(",\n");
            openChildArray(parent, childName, destination);
        }

        if (parent.openChildArrayHasElements) {
            destination.append(", ");
        } else {
            parent.openChildArrayHasElements = true;
        }
    }

    private void openChildArray(StructuredWriterElement parent, String childName, StringBuilder destination) {
        appendIndent(parent.indent + 2, destination);
        appendQuoted(childName, destination);
        destination.append(": [");

        parent.openChildArrayName = childName;
        parent.openChildArrayHasElements = false;
        parent.hasWrittenChild = true;
    }

    private void closeOpenChildArray(StructuredWriterElement parent, StringBuilder destination) {
        if (parent.openChildArrayName == null) {
            return;
        }

        destination.append("]");

        parent.openChildArrayName = null;
        parent.openChildArrayHasElements = false;
    }

    private void appendIndent(int level, StringBuilder destination) {
        destination.append(INDENT.repeat(level));
    }

    private void appendAttributesObject(Map<String, String> attributes, StringBuilder destination) {
        destination.append("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                destination.append(", ");
            }
            appendQuoted(entry.getKey(), destination);
            destination.append(": ");
            appendQuoted(entry.getValue(), destination);
            first = false;
        }
        destination.append("}");
    }

    private void appendQuoted(String value, StringBuilder destination) {
        destination.append("\"").append(escapeJson(value)).append("\"");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
            case '\\':
                escaped.append("\\\\");
                break;
            case '"':
                escaped.append("\\\"");
                break;
            case '\b':
                escaped.append("\\b");
                break;
            case '\f':
                escaped.append("\\f");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            case '\r':
                escaped.append("\\r");
                break;
            case '\t':
                escaped.append("\\t");
                break;
            default:
                if (current <= 0x1F) {
                    escaped.append(String.format("\\u%04x", (int) current));
                } else {
                    escaped.append(current);
                }
                break;
            }
        }
        return escaped.toString();
    }
}
