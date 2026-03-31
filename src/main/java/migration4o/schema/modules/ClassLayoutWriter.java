package migration4o.schema.modules;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import migration4o.models.ui.layout.DetailLayout;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.util.FileUtil;

/**
 * Writes standalone class layouts to class-layouts.xml.
 * These layouts are reusable across any parent class that embeds the target class.
 */
class ClassLayoutWriter {

    void writeClassLayouts(Map<String, DetailLayout> classLayouts, String filePath) throws IOException {
        // Create backup if file exists
        File file = new File(filePath);
        if (file.exists()) {
            FileUtil.createBackup(filePath, DOModuleService.BACKUP_CLASS_LAYOUTS_PATH);
        }

        // Ensure parent directory exists
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<classLayouts>\n");

            for (Map.Entry<String, DetailLayout> entry : classLayouts.entrySet()) {
                writeClassLayout(writer, entry.getKey(), entry.getValue());
            }

            writer.write("</classLayouts>\n");
        }
    }

    private void writeClassLayout(FileWriter writer, String className, DetailLayout layout) throws IOException {
        writer.write("    <classLayout sourceName=\"" + escapeXml(className) + "\">\n");
        writer.write("        <layout>\n");
        for (LayoutNode node : layout.nodes) {
            writeLayoutNode(writer, node, 3);
        }
        writer.write("        </layout>\n");
        writer.write("    </classLayout>\n");
    }

    private void writeLayoutNode(FileWriter writer, LayoutNode node, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);
        writer.write(indent + "<" + node.type.xmlTag);
        for (Map.Entry<String, String> e : node.properties.entrySet()) {
            writer.write(" " + e.getKey() + "=\"" + escapeXml(e.getValue()) + "\"");
        }
        if (node.children.isEmpty()) {
            writer.write("/>\n");
        } else {
            writer.write(">\n");
            for (LayoutNode child : node.children) {
                writeLayoutNode(writer, child, indentLevel + 1);
            }
            writer.write(indent + "</" + node.type.xmlTag + ">\n");
        }
    }

    private String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
