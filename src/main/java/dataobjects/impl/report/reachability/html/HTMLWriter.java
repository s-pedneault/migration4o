package dataobjects.impl.report.reachability.html;

import java.io.IOException;
import java.io.Writer;

/**
 * Base class for HTML generation with proper tag management
 */
public class HTMLWriter {
    protected Writer writer;

    public HTMLWriter(Writer writer) {
        this.writer = writer;
    }

    public void openTag(String tag) throws IOException {
        writer.write("<" + tag + ">");
    }

    public void openTag(String tag, String attributes) throws IOException {
        writer.write("<" + tag + " " + attributes + ">");
    }

    public void closeTag(String tag) throws IOException {
        writer.write("</" + tag + ">");
    }

    public void writeTag(String tag, String content) throws IOException {
        writeTag(tag, content, "");
    }

    public void writeTag(String tag, String content, String attributes) throws IOException {
        if (attributes.isEmpty()) {
            writer.write("<" + tag + ">" + content + "</" + tag + ">");
        } else {
            writer.write("<" + tag + " " + attributes + ">" + content + "</" + tag + ">");
        }
    }

    public void writeDocumentStart() throws IOException {
        writer.write("<!DOCTYPE html>\n");
        openTag("html", "lang='en'");
    }

    public void writeDocumentEnd() throws IOException {
        closeTag("html");
    }

    public void writeHead(String title) throws IOException {
        openTag("head");
        writeTag("meta", "", "charset='UTF-8'");
        writeTag("meta", "", "name='viewport' content='width=device-width, initial-scale=1.0'");
        writeTag("title", title);
        closeTag("head");
    }

    public void writeBodyStart() throws IOException {
        openTag("body");
    }

    public void writeBodyEnd() throws IOException {
        closeTag("body");
    }

    protected void writeLine(String content) throws IOException {
        writer.write(content + "\n");
    }

    protected void write(String content) throws IOException {
        writer.write(content);
    }

    protected String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    protected String escapeJs(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}