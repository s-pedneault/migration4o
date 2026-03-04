package migration4o.schema.modules;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.models.ui.MigrationModule;
import migration4o.models.ui.layout.DetailLayout;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.util.FileUtil;

/**
 * Writes migration structure to migration-format.xml file.
 * Uses the same backup strategy as DODatabaseSchemaWriter.
 */
public class DOModuleStructureWriter {

    public void writeMigrationFormat(List<MigrationModule> modules, String filePath) throws IOException {
        // Create backup first
        FileUtil.createBackup(filePath, DOModuleService.BACKUP_MODULES_PATH);

        // Write the migration format
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<database>\n");
            writer.write("    <modules>\n");

            for (MigrationModule module : modules) {
                writeModule(writer, module);
            }

            writer.write("    </modules>\n");
            writer.write("</database>\n");
        }
    }

    private void writeModule(FileWriter writer, MigrationModule module) throws IOException {
        writeModule(writer, module, 2);
    }

    private void writeModule(FileWriter writer, MigrationModule module, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);

        writer.write(indent + "<module name=\"" + escapeXml(module.getName()) + "\"");
        if (module.getId() != null && !module.getId().isEmpty()) {
            writer.write(" id=\"" + escapeXml(module.getId()) + "\"");
        }
        writer.write(">\n");

        // Write class configurations
        for (ClassExportConfig config : module.getClassConfigs()) {
            writeClassRef(writer, config, indentLevel + 1);
        }

        // Write child modules recursively
        for (MigrationModule childModule : module.getChildModules()) {
            writeModule(writer, childModule, indentLevel + 1);
        }

        writer.write(indent + "</module>\n");
    }

    private void writeClassRef(FileWriter writer, ClassExportConfig config, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);

        writer.write(indent + "<classRef sourceName=\"" + escapeXml(config.getClassName()) + "\"");

        // Add destination file if custom
        if (config.hasCustomDestination()) {
            writer.write(" destinationFile=\"" + escapeXml(config.getRawDestinationFileName()) + "\"");
        }

        // Add description if set
        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            writer.write(" description=\"" + escapeXml(config.getDescription()) + "\"");
        }

        // If there are criteria, unit costs, or layout, use child elements; otherwise self-close
        if (config.hasCriteria() || !config.getUnitCosts().isEmpty() || config.hasLayout()) {
            writer.write(">\n");

            // Write criteria
            for (ExportCriteria criterion : config.getCriteria()) {
                writeCriteria(writer, criterion, indentLevel + 1);
            }

            // Write unit costs
            for (java.util.Map.Entry<String, Float> entry : config.getUnitCosts().entrySet()) {
                writeUnitCost(writer, entry.getKey(), entry.getValue(), indentLevel + 1);
            }

            // Write layout if present
            if (config.hasLayout()) {
                writeLayout(writer, config.getLayout(), indentLevel + 1);
            }

            writer.write(indent + "</classRef>\n");
        } else {
            writer.write("/>\n");
        }
    }

    private void writeCriteria(FileWriter writer, ExportCriteria criterion, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);

        writer.write(indent + "<criteria field=\"" + escapeXml(criterion.getFieldName()) + "\"");
        writer.write(" operator=\"" + escapeXml(criterion.getOperator().getSymbol()) + "\"");

        // Add value if not null (IS_NULL and IS_NOT_NULL don't need value)
        if (criterion.getValue() != null) {
            writer.write(" value=\"" + escapeXml(criterion.getValue()) + "\"");
        }

        writer.write("/>\n");
    }

    private void writeUnitCost(FileWriter writer, String priceList, Float cost, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);

        writer.write(indent + "<unitCost priceList=\"" + escapeXml(priceList) + "\"");
        writer.write(" cost=\"" + cost + "\"");
        writer.write("/>\n");
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void writeLayout(FileWriter writer, DetailLayout layout, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);
        writer.write(indent + "<layout>\n");
        for (LayoutNode node : layout.nodes) {
            writeLayoutNode(writer, node, indentLevel + 1);
        }
        writer.write(indent + "</layout>\n");
    }

    private void writeLayoutNode(FileWriter writer, LayoutNode node, int indentLevel) throws IOException {
        String indent = "    ".repeat(indentLevel);
        writer.write(indent + "<" + node.type.xmlTag);
        for (java.util.Map.Entry<String, String> e : node.properties.entrySet()) {
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
}
