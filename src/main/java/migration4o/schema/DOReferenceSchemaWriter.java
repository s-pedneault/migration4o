package migration4o.schema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;

/**
 * Writes DOSchema back to XML format matching reference-schema.xml structure.
 * Preserves element order and all attributes.
 */
public class DOReferenceSchemaWriter {

    public void writeSchema(DOSchema schema) throws IOException {
        writeSchema(schema, DOReferenceSchemaConstants.DEFAULT_SCHEMA_PATH);
    }

    private void writeSchema(DOSchema schema, String filePath) throws IOException {
        // Create backup first
        createBackup(filePath);

        // Write the schema
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<classes>\n");

            // Sort classes alphabetically by absolute name before writing
            if (schema.getClasses() != null) {
                DOSchemaClass[] sortedClasses = Arrays.copyOf(schema.getClasses(), schema.getClasses().length);
                Arrays.sort(sortedClasses, Comparator.comparing(c -> c.source));

                for (DOSchemaClass schemaClass : sortedClasses) {
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
        writeAttribute(writer, "source", schemaClass.source);
        writeAttribute(writer, "destinationName", schemaClass.destinationName);
        writeAttribute(writer, "isExported", String.valueOf(schemaClass.migrate));

        if (schemaClass.title != null && !schemaClass.title.isEmpty()) {
            writeAttribute(writer, "title", schemaClass.title);
        }

        if (schemaClass.description != null && !schemaClass.description.isEmpty()) {
            writeAttribute(writer, "description", schemaClass.description);
        }

        if (schemaClass.parentClassName != null && !schemaClass.parentClassName.isEmpty()
                && !"Undetermined".equals(schemaClass.parentClassName)) {
            writeAttribute(writer, "parentClass", schemaClass.parentClassName);
        }

        if (schemaClass.pointsTo != null && !schemaClass.pointsTo.isEmpty()) {
            writeAttribute(writer, "pointsTo", schemaClass.pointsTo);
        }

        // Check if we have fields, references or nested content
        boolean hasFields = schemaClass.fields != null && schemaClass.fields.length > 0;
        boolean hasReferences = schemaClass.schemaReferences != null
                && schemaClass.schemaReferences.length > 0;

        if (!hasFields && !hasReferences) {
            writer.write(">\n");
            writer.write(indent + "</class>\n");
        } else {
            writer.write(">\n");

            // Write references first (as in original format)
            if (hasReferences) {
                for (DOSchemaReference ref : schemaClass.schemaReferences) {
                    writeReference(writer, ref, indentLevel + 1);
                }
            }

            // Write fields after references
            if (hasFields) {
                for (DOSchemaField field : schemaClass.fields) {
                    writeField(writer, field, indentLevel + 1);
                }
            }

            writer.write(indent + "</class>\n");
        }
    }

    private void writeField(FileWriter writer, DOSchemaField field, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);

        writer.write(indent + "<field");

        // If field has source, use source and destinationName attributes
        // Otherwise use name attribute (for fields without source mapping)
        if (field.source != null && !field.source.isEmpty()) {
            writeAttribute(writer, "source", field.source);
            if (field.destinationName != null && !field.destinationName.isEmpty()) {
                writeAttribute(writer, "destinationName", field.destinationName);
            }
            writeAttribute(writer, "isExported", String.valueOf(field.isExported));
            writeAttribute(writer, "skipIfEmpty", String.valueOf(field.skipIfEmpty));
        } else {
            // No source - use name attribute
            if (field.destinationName != null && !field.destinationName.isEmpty()) {
                writeAttribute(writer, "name", field.destinationName);
            }
        }

        if (field.type != null && !field.type.isEmpty()) {
            writeAttribute(writer, "type", field.type);
        }

        if (field.isCollection) {
            writeAttribute(writer, "collection", "true");
        }

        if (field.embedContents) {
            writeAttribute(writer, "embedContents", "true");
        }

        if (field.childrenType != null && !field.childrenType.isEmpty()) {
            writeAttribute(writer, "childrenType", field.childrenType);
        }

        if (field.pointsTo != null && !field.pointsTo.isEmpty()) {
            writeAttribute(writer, "pointsTo", field.pointsTo);
        }

        if (field.title != null && !field.title.isEmpty()) {
            writeAttribute(writer, "title", field.title);
        }

        if (field.description != null && !field.description.isEmpty()) {
            writeAttribute(writer, "description", field.description);
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
