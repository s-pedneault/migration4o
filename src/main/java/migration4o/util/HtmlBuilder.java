package migration4o.util;

import java.util.Stack;

/**
 * A safe HTML builder that ensures all tags are properly balanced.
 * This prevents the common issue of missing closing tags in generated HTML.
 */
public class HtmlBuilder {
    private final StringBuilder html;
    private final Stack<String> openTags;
    private int indentLevel;

    public HtmlBuilder() {
        this.html = new StringBuilder();
        this.openTags = new Stack<>();
        this.indentLevel = 0;
    }

    public HtmlBuilder(StringBuilder existingBuilder) {
        this.html = existingBuilder;
        this.openTags = new Stack<>();
        this.indentLevel = 0;
    }

    /**
     * Open a tag with optional attributes
     */
    public HtmlBuilder openTag(String tagName, String... attributes) {
        appendIndent();
        html.append("<").append(tagName);

        // Add attributes in pairs (name, value, name, value, ...)
        for (int i = 0; i < attributes.length; i += 2) {
            if (i + 1 < attributes.length) {
                html.append(" ").append(attributes[i]).append("=\"")
                        .append(escapeHtml(attributes[i + 1])).append("\"");
            }
        }

        html.append(">\n");
        openTags.push(tagName);
        indentLevel++;
        return this;
    }

    /**
     * Open a self-closing tag
     */
    public HtmlBuilder selfClosingTag(String tagName, String... attributes) {
        appendIndent();
        html.append("<").append(tagName);

        // Add attributes in pairs
        for (int i = 0; i < attributes.length; i += 2) {
            if (i + 1 < attributes.length) {
                html.append(" ").append(attributes[i]).append("=\"")
                        .append(escapeHtml(attributes[i + 1])).append("\"");
            }
        }

        html.append(" />\n");
        return this;
    }

    /**
     * Close the most recent open tag
     */
    public HtmlBuilder closeTag() {
        if (openTags.isEmpty()) {
            throw new IllegalStateException("No open tags to close");
        }

        indentLevel--;
        String tagName = openTags.pop();
        appendIndent();
        html.append("</").append(tagName).append(">\n");
        return this;
    }

    /**
     * Close a specific tag (validates it matches the most recent open tag)
     */
    public HtmlBuilder closeTag(String expectedTagName) {
        if (openTags.isEmpty()) {
            throw new IllegalStateException("No open tags to close, expected: " + expectedTagName);
        }

        String actualTagName = openTags.peek();
        if (!actualTagName.equals(expectedTagName)) {
            throw new IllegalStateException("Tag mismatch: expected to close '" + expectedTagName
                    + "' but most recent open tag is '" + actualTagName + "'");
        }

        return closeTag();
    }

    /**
     * Add text content (will be HTML escaped)
     */
    public HtmlBuilder text(String content) {
        if (content != null) {
            appendIndent();
            html.append(escapeHtml(content)).append("\n");
        }
        return this;
    }

    /**
     * Add raw HTML content (not escaped - use carefully)
     */
    public HtmlBuilder rawHtml(String content) {
        if (content != null) {
            appendIndent();
            html.append(content).append("\n");
        }
        return this;
    }

    /**
     * Add inline text without indentation or newline
     */
    public HtmlBuilder inlineText(String content) {
        if (content != null) {
            html.append(escapeHtml(content));
        }
        return this;
    }

    /**
     * Add inline raw HTML without indentation or newline
     */
    public HtmlBuilder inlineRawHtml(String content) {
        if (content != null) {
            html.append(content);
        }
        return this;
    }

    /**
     * Close all remaining open tags in reverse order
     */
    public HtmlBuilder closeAllTags() {
        while (!openTags.isEmpty()) {
            closeTag();
        }
        return this;
    }

    /**
     * Get the number of currently open tags
     */
    public int getOpenTagCount() {
        return openTags.size();
    }

    /**
     * Get the current HTML string
     */
    public String toString() {
        return html.toString();
    }

    /**
     * Get the underlying StringBuilder
     */
    public StringBuilder getStringBuilder() {
        return html;
    }

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) {
            html.append("    ");
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Convenience method to create a complete element with content in one call
     */
    public HtmlBuilder element(String tagName, String content, String... attributes) {
        openTag(tagName, attributes);
        if (content != null) {
            inlineText(content);
        }
        closeTag(tagName);
        return this;
    }

    /**
     * Convenience method to create a span with classes
     */
    public HtmlBuilder span(String content, String cssClass) {
        return element("span", content, "class", cssClass);
    }

    /**
     * Convenience method to create a div with classes
     */
    public HtmlBuilder div(String cssClass) {
        return openTag("div", "class", cssClass);
    }

    /**
     * Add a newline without indentation
     */
    public HtmlBuilder newline() {
        html.append("\n");
        return this;
    }
}
