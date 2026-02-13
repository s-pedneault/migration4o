package migration4o.util.tools.structuredwriter;

import java.io.IOException;
import java.util.Map;

import migration4o.util.XMLUtil;

public class StructuredWriterXML implements StructuredWriterAPI {

    private static final String INDENT = "  ";

    @Override
    public void data(String content, StructuredWriterBlock block) throws IOException {
        String sanitizedContent = XMLUtil.sanitizeXMLCharacters(content);
        block.content.append(sanitizedContent);
    }

    // BUG: Auto-detection of empty content is not working correctly, children
    // blocks write to the writer before we can write their parent's opening tag.

    @Override
    public void compile(StructuredWriterBlock block) throws IOException {
        boolean isEmpty = block.isEmpty();

        // Open tag
        block.block.append(INDENT.repeat(block.indent));
        block.block.append("<" + block.name);
        if (block.attributes != null) {
            for (Map.Entry<String, String> entry : block.attributes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                block.block.append(" " + entry.getKey() + "=\"" + XMLUtil.xmlEscape(entry.getValue()) + "\"");
            }
        }
        System.out.println("Compiling block: " + block.name + ", isEmpty: " + isEmpty + ", content: " + block.content.length() + ", children: " + block.children.size());
        if (isEmpty) {
            block.block.append(" />\n");
        } else {
            block.block.append(">");

            // Content
            block.block.append(block.content);

            // Close tag
            if (block.content.indexOf("\n") >= 0) {
                // If content has multiple lines, newline at the end if not already present
                if (block.content.lastIndexOf("\n") == block.content.length() - 1) {
                    block.content.append('\n');
                }
                // If content has multiple lines, add indentation before closing tag
                block.block.append(INDENT.repeat(block.indent));
            }
            block.block.append("</" + block.name + ">\n");
        }

    }

}
