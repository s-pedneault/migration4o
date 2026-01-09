package migration4o.schema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;

/**
 * Writes DOSchema back to XML format matching database-schema.xml structure.
 * Preserves element order and all attributes.
 */
public class DODatabaseSchemaWriter {

    public void writeSchema(DOSchema schema, String filePath) throws IOException {
        // Create backup first
        createBackup(filePath);

        // Write the schema
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<classes>\n");

            // Write all classes in their current order
            if (schema.getClasses() != null) {
                for (DOSchemaClass schemaClass : schema.getClasses()) {
                    writeClass(writer, schemaClass, 1);
                }
            }

            writer.write("</classes>\n");
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

    private void writeClass(FileWriter writer, DOSchemaClass schemaClass, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);

        writer.write(indent + "<class");
        writeAttribute(writer, "source", schemaClass.getAbsoluteName());
        writeAttribute(writer, "destinationName", schemaClass.getShortName());

        if (schemaClass.getParentClass() != null && !schemaClass.getParentClass().isEmpty()
                && !"Undetermined".equals(schemaClass.getParentClass())) {
            writeAttribute(writer, "parentClass", schemaClass.getParentClass());
        }

        writeAttribute(writer, "isExported", String.valueOf(schemaClass.isMigrate()));

        // Check if we have fields, references or nested content
        boolean hasFields = schemaClass.getFields() != null && schemaClass.getFields().length > 0;
        boolean hasReferences = schemaClass.getSchemaReferences() != null
                && schemaClass.getSchemaReferences().length > 0;

        if (!hasFields && !hasReferences) {
            writer.write(" />\n");
        } else {
            writer.write(">\n");

            // Write fields
            if (hasFields) {
                for (DOSchemaField field : schemaClass.getFields()) {
                    writeField(writer, field, indentLevel + 1);
                }
            }

            // Write references
            if (hasReferences) {
                for (DOSchemaReference ref : schemaClass.getSchemaReferences()) {
                    writeReference(writer, ref, indentLevel + 1);
                }
            }

            writer.write(indent + "</class>\n");
        }
    }

    private void writeField(FileWriter writer, DOSchemaField field, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);

        writer.write(indent + "<field");

        if (field.getSource() != null && !field.getSource().isEmpty()) {
            writeAttribute(writer, "source", field.getSource());
        }

        if (field.getDestinationName() != null && !field.getDestinationName().isEmpty()) {
            writeAttribute(writer, "destinationName", field.getDestinationName());
        }

        if (field.getType() != null && !field.getType().isEmpty()) {
            writeAttribute(writer, "type", field.getType());
        }

        writeAttribute(writer, "isExported", String.valueOf(field.isExported()));
        writeAttribute(writer, "skipIfEmpty", String.valueOf(field.isSkipIfEmpty()));

        if (field.isCollection()) {
            writeAttribute(writer, "collection", "true");
        }

        if (field.isEmbedContents()) {
            writeAttribute(writer, "embedContents", "true");
        }

        if (field.getChildrenType() != null && !field.getChildrenType().isEmpty()) {
            writeAttribute(writer, "children", field.getChildrenType());
        }

        writer.write(" />\n");
    }

    private void writeReference(FileWriter writer, DOSchemaReference ref, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);
        writer.write(indent + "<reference");
        writeAttribute(writer, "class", ref.getClassName());
        writeAttribute(writer, "field", ref.getFieldName());
        writer.write(" />\n");
    }

    private void writeAttribute(FileWriter writer, String name, String value) throws IOException {
        if (value != null && !value.isEmpty()) {
            writer.write(" " + name + "=\"" + escapeXml(value) + "\"");
        }
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

    private String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("    "); // 4 spaces per level
        }
        return sb.toString();
    }
}
