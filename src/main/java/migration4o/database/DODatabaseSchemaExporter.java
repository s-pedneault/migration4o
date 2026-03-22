package migration4o.database;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Exports the database-discovered class structure to a schema XML file. This produces a reference-schema-compatible XML with class names, parent classes, field names, field types, and collection metadata derived from the DB4O container.
 */
public class DODatabaseSchemaExporter {

    /**
     * Exports all gest.* classes from the given database to a schema XML file.
     *
     * @param database the database to export
     * @param filePath the output file path
     * @return the number of classes written
     */
    public int export(DODatabase database, String filePath) throws IOException {
        DODatabaseClass[] classes = database.getClasses();
        if (classes == null || classes.length == 0) {
            return 0;
        }

        // Sort by source name for readability
        DODatabaseClass[] sorted = Arrays.copyOf(classes, classes.length);
        Arrays.sort(sorted, Comparator.comparing(c -> c.attributes.source != null ? c.attributes.source : ""));

        int count = 0;
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<classes>\n");

            for (DODatabaseClass dbClass : sorted) {
                String source = dbClass.attributes.source;
                if (source == null || !source.startsWith("gest.")) {
                    continue;
                }
                writeClass(writer, dbClass);
                count++;
            }

            writer.write("</classes>\n");
        }
        return count;
    }

    private void writeClass(FileWriter writer, DODatabaseClass dbClass) throws IOException {
        String source = dbClass.attributes.source;
        String simpleName = getSimpleName(source);
        String parent = dbClass.attributes.parentClassName;

        writer.write("    <class");
        writeAttr(writer, "source", source);
        writeAttr(writer, "destinationName", simpleName);
        writeAttr(writer, "isExported", "false");

        if (parent != null && !parent.isEmpty() && parent.startsWith("gest.")) {
            writeAttr(writer, "parentClass", parent);
        }

        int instanceCount = dbClass.attributes.instanceCount;
        writeAttr(writer, "title", simpleName + " (" + instanceCount + " instances)");

        boolean hasFields = dbClass.fields != null && dbClass.fields.length > 0;
        if (!hasFields) {
            writer.write(">\n    </class>\n");
            return;
        }

        writer.write(">\n");

        for (DODatabaseField field : dbClass.fields) {
            writeField(writer, field);
        }

        writer.write("    </class>\n");
    }

    private void writeField(FileWriter writer, DODatabaseField field) throws IOException {
        String source = field.attributes.source;
        String type = field.attributes.type;

        writer.write("        <field");
        writeAttr(writer, "source", source);
        writeAttr(writer, "destinationName", source);
        writeAttr(writer, "isExported", "false");

        if (type != null && !type.isEmpty()) {
            writeAttr(writer, "type", mapType(type));
        }

        if (field.attributes.isCollection) {
            writeAttr(writer, "collection", "true");
            if (field.attributes.childrenType != null && !field.attributes.childrenType.isEmpty()) {
                writeAttr(writer, "childrenType", field.attributes.childrenType);
            }
        }

        if (field.attributes.isArray) {
            writeAttr(writer, "collection", "true");
            if (field.attributes.childrenType != null && !field.attributes.childrenType.isEmpty()) {
                writeAttr(writer, "childrenType", field.attributes.childrenType);
            }
        }

        writer.write(" />\n");
    }

    /**
     * Maps DB4O internal type names to schema type names.
     */
    private String mapType(String dbType) {
        return switch (dbType) {
        case "int", "java.lang.Integer" -> "int";
        case "long", "java.lang.Long" -> "long";
        case "boolean", "java.lang.Boolean" -> "boolean";
        case "float", "java.lang.Float" -> "float";
        case "double", "java.lang.Double" -> "double";
        case "java.lang.String" -> "String";
        case "java.util.Date" -> "Date";
        default -> dbType;
        };
    }

    private String getSimpleName(String fullName) {
        if (fullName == null)
            return "";
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }

    private void writeAttr(FileWriter writer, String name, String value) throws IOException {
        if (value != null && !value.isEmpty()) {
            writer.write(" " + name + "=\"" + escapeXml(value) + "\"");
        }
    }

    private String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
