package migration4o.util.tools.structuredwriter.formats;

import java.io.IOException;
import java.util.Map;

import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterElement;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithContent;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithStructure;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithoutContent;

/**
 * Compact JavaScript writer producing JS-native data.
 *
 * <p>
 * Output format (single-line, minified): {@code window.__m4o={...};}
 *
 * <p>
 * Data contract:
 * <ul>
 * <li>Scalars are direct values: {@code "nom":"value"}
 * <li>Objects use {@code {}} with reserved {@code _class}, {@code _id}, {@code _summary}, {@code _preview}, {@code _label} properties
 * <li>Collections use {@code []} via explicit {@code openArray/closeArray}
 * <li>No {@code @attributes}, {@code #text}, or forced single-element arrays
 * </ul>
 */
public class StructuredWriterJS implements StructuredWriterAPI {

    /** Reserved attribute keys emitted in fixed order before other attributes. */
    private static final String[] RESERVED_KEYS = { "_id", "_summary", "_preview" };

    @Override
    public String getName() {
        return "JS";
    }

    @Override
    public void initialize(StructuredWriter writer) throws IOException {
        writer.writer.write("window.__m4o={");
    }

    @Override
    public boolean includeCollectionSizeMetadata() {
        return false;
    }

    @Override
    public void onDocumentComplete(StructuredWriter writer) throws IOException {
        writer.writer.write(";\n");
    }

    // ── Element without content (e.g. empty field / empty reference) ──

    @Override
    public void add(StructuredWriterElementWithoutContent element) throws IOException {
        StringBuilder out = element.prefix;
        writeComma(element, out);
        if (hasAttributes(element)) {
            writeKey(element, out);
            out.append('{');
            writeInlineAttributes(element.attributes, out);
            out.append('}');
        } else {
            writeKey(element, out);
            out.append("null");
        }
    }

    // ── Element with content (scalar value, possibly with attributes) ──

    @Override
    public void addContent(StructuredWriterElementWithContent element, String content) throws IOException {
        StringBuilder out = element.prefix;
        writeComma(element, out);
        if (hasAttributes(element)) {
            writeKey(element, out);
            out.append('{');
            if (isParentArray(element)) {
                // Inside an array: emit _class discriminator (mirrors openStructure)
                appendQuoted("_class", out);
                out.append(':');
                appendQuoted(element.name, out);
                out.append(',');
            }
            writeInlineAttributes(element.attributes, out);
            out.append(',');
            appendQuoted("_label", out);
            out.append(':');
            appendQuoted(content, out);
            out.append('}');
        } else {
            writeKey(element, out);
            appendQuoted(content, out);
        }
    }

    // ── Open/close structure (object: {...}) ──

    @Override
    public void openStructure(StructuredWriterElementWithStructure element) throws IOException {
        StringBuilder out = element.prefix;
        if (element.parent == null) {
            // Root-level key (e.g. top module name inside window.__m4o)
            appendQuoted(element.name, out);
            out.append(":{");
        } else {
            writeComma(element, out);
            writeKey(element, out);
            out.append('{');
            if (isParentArray(element)) {
                // Inside an array: emit _class discriminator
                appendQuoted("_class", out);
                out.append(':');
                appendQuoted(element.name, out);
                if (hasAttributes(element)) {
                    out.append(',');
                }
            }
        }
        if (hasAttributes(element) && element.parent != null) {
            writeInlineAttributes(element.attributes, out);
            element.hasWrittenChild = true;
        } else if (isParentArray(element)) {
            // _class was written; mark so first child gets a comma
            element.hasWrittenChild = true;
        }
    }

    @Override
    public void closeStructure(StructuredWriterElementWithStructure element) throws IOException {
        element.suffix.append('}');
        if (element.indent == 0) {
            element.suffix.append('}');
        }
    }

    // ── Open/close array ([...]) ──

    @Override
    public void openArray(StructuredWriterElementWithStructure element) throws IOException {
        StringBuilder out = element.prefix;
        if (element.parent == null) {
            appendQuoted(element.name, out);
            out.append(":[");
        } else {
            writeComma(element, out);
            writeKey(element, out);
            out.append('[');
        }
    }

    @Override
    public void closeArray(StructuredWriterElementWithStructure element) throws IOException {
        element.suffix.append(']');
        if (element.indent == 0) {
            element.suffix.append('}');
        }
    }

    // ── Private helpers ──

    private boolean hasAttributes(StructuredWriterElement element) {
        return element.attributes != null && !element.attributes.isEmpty();
    }

    private boolean isParentArray(StructuredWriterElement element) {
        return element.parent != null && element.parent.isArray;
    }

    /**
     * Writes the element key ({@code "name":}) when inside an object context. Inside an array context, no key is written.
     */
    private void writeKey(StructuredWriterElement element, StringBuilder out) {
        if (!isParentArray(element)) {
            appendQuoted(element.name, out);
            out.append(':');
        }
    }

    /**
     * Writes a comma separator before a child element when the parent already has written children.
     */
    private void writeComma(StructuredWriterElement element, StringBuilder out) {
        StructuredWriterElement parent = element.parent;
        if (parent != null) {
            if (parent.hasWrittenChild) {
                out.append(',');
            }
            parent.hasWrittenChild = true;
        }
    }

    /**
     * Writes reserved properties in a fixed order ({@code _id}, {@code _summary}, {@code _preview}), then any remaining attributes.
     */
    private void writeInlineAttributes(Map<String, String> attributes, StringBuilder out) {
        boolean first = true;
        // Emit reserved keys in fixed order
        for (String key : RESERVED_KEYS) {
            String value = attributes.get(key);
            if (value != null) {
                if (!first)
                    out.append(',');
                appendQuoted(key, out);
                out.append(':');
                appendQuoted(value, out);
                first = false;
            }
        }
        // Emit remaining keys (skip reserved keys already emitted)
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || entry.getValue() == null)
                continue;
            if (isReservedKey(key))
                continue;
            if (!first)
                out.append(',');
            appendQuoted(key, out);
            out.append(':');
            appendQuoted(entry.getValue(), out);
            first = false;
        }
    }

    private boolean isReservedKey(String key) {
        for (String reserved : RESERVED_KEYS) {
            if (reserved.equals(key))
                return true;
        }
        return false;
    }

    private void appendQuoted(String value, StringBuilder destination) {
        destination.append('"').append(escapeJson(value)).append('"');
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