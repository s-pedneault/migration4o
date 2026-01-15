package migration4o.schema;

import migration4o.ui.schema.MigrationStructurePanel.MigrationModule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Writes migration structure to migration-format.xml file.
 * Uses the same backup strategy as DODatabaseSchemaWriter.
 */
public class MigrationFormatWriter {

    public void writeMigrationFormat(List<MigrationModule> modules, String filePath) throws IOException {
        // Create backup first
        createBackup(filePath);

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

    private void createBackup(String filePath) throws IOException {
        File originalFile = new File(filePath);
        if (!originalFile.exists()) {
            return; // No need to backup if file doesn't exist yet
        }

        // Find next available backup number
        int backupNumber = 1;
        File backupFile;
        do {
            String backupPath = filePath + "." + String.format("%04d", backupNumber) + ".bak";
            backupFile = new File(backupPath);
            backupNumber++;
        } while (backupFile.exists());

        // Create the backup
        Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        System.out.println("Created backup: " + backupFile.getName());
    }

    private void writeModule(FileWriter writer, MigrationModule module) throws IOException {
        writer.write("        <module name=\"" + escapeXml(module.getName()) + "\"");
        if (module.getId() != null && !module.getId().isEmpty()) {
            writer.write(" id=\"" + escapeXml(module.getId()) + "\"");
        }
        writer.write(">\n");

        for (String className : module.getClassNames()) {
            writer.write("            <classRef sourceName=\"" + escapeXml(className) + "\"/>\n");
        }

        writer.write("        </module>\n");
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
