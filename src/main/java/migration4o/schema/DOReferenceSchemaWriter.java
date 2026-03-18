package migration4o.schema;

import java.io.FileWriter;
import java.io.IOException;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;
import migration4o.models.schema.DOSchemaValueMap;
import migration4o.util.FileUtil;

/**
 * Writes DOSchema back to XML format matching reference-schema.xml structure.
 * Preserves element order and all attributes.
 */
public class DOReferenceSchemaWriter {

    public void writeSchema(DOSchema schema) throws IOException {
        writeSchema(schema, DOSchemaService.DEFAULT_SCHEMA_PATH);
    }

    private void writeSchema(DOSchema schema, String filePath) throws IOException {
        // Create backup first
        FileUtil.createBackup(filePath, DOSchemaService.BACKUP_SCHEMA_PATH);

        // Write the schema
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<classes>\n");

            // Write shared field definitions first if any exist
            if (schema.sharedFields != null && !schema.sharedFields.isEmpty()) {
                writer.write("    <fields>\n");
                for (java.util.Map.Entry<String, DOSchemaField> entry : schema.sharedFields.entrySet()) {
                    writeSharedField(writer, entry.getKey(), entry.getValue(), 2);
                }
                writer.write("    </fields>\n");
            }

            // Write classes in their original order (do NOT sort)
            if (schema.getClasses() != null) {
                for (DOSchemaClass schemaClass : schema.getClasses()) {
                    writeClass(writer, schemaClass, 1);
                }
            }

            writer.write("</classes>\n");
        }
    }

    /**
     * Write a shared field definition (source attribute is the key).
     */
    private void writeSharedField(FileWriter writer, String definitionId, DOSchemaField field, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);

        writer.write(indent + "<field");

        // Write all field attributes (source is used as the definition key)
        if (field.attributes.source != null && !field.attributes.source.isEmpty()) {
            writeAttribute(writer, "source", field.attributes.source);
        }
        if (field.attributes.destinationName != null && !field.attributes.destinationName.isEmpty()) {
            writeAttribute(writer, "destinationName", field.attributes.destinationName);
        }
        writeAttribute(writer, "isExported", String.valueOf(field.attributes.isExported));

        if (field.attributes.skipWhen != null && !field.attributes.skipWhen.trim().isEmpty()) {
            writeAttribute(writer, "skipWhen", field.attributes.skipWhen);
        }

        if (field.attributes.skipUserOption != null && !field.attributes.skipUserOption.trim().isEmpty()) {
            writeAttribute(writer, "skipUserOption", field.attributes.skipUserOption);
        }

        if (field.attributes.type != null && !field.attributes.type.isEmpty()) {
            writeAttribute(writer, "type", field.attributes.type);
        }

        if (field.attributes.format != null && !field.attributes.format.isEmpty()) {
            writeAttribute(writer, "format", field.attributes.format);
        }

        if (field.attributes.isCollection) {
            writeAttribute(writer, "collection", "true");
        }

        if (field.attributes.embedContents) {
            writeAttribute(writer, "embedContents", "true");
        }

        if (field.attributes.childrenType != null && !field.attributes.childrenType.isEmpty()) {
            writeAttribute(writer, "childrenType", field.attributes.childrenType);
        }

        if (field.attributes.pointsTo != null && !field.attributes.pointsTo.isEmpty()) {
            writeAttribute(writer, "pointsTo", field.attributes.pointsTo);
        }

        if (field.attributes.title != null && !field.attributes.title.isEmpty()) {
            writeAttribute(writer, "title", field.attributes.title);
        }

        if (field.attributes.description != null && !field.attributes.description.isEmpty()) {
            writeAttribute(writer, "description", field.attributes.description);
        }

        // Check if we have child elements (valueMap, criterias)
        boolean hasChildren = (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) || (field.attributes.criterias != null && !field.attributes.criterias.isEmpty());

        if (hasChildren) {
            writer.write(">\n");
            if (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) {
                writeValueMap(writer, field.attributes.valueMap, indentLevel + 1);
            }
            if (field.attributes.criterias != null && !field.attributes.criterias.isEmpty()) {
                writeCriterias(writer, field, indentLevel + 1);
            }
            writer.write(indent + "</field>\n");
        } else {
            writer.write(" />\n");
        }
    }

    private void writeClass(FileWriter writer, DOSchemaClass schemaClass, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);

        writer.write(indent + "<class");
        writeAttribute(writer, "source", schemaClass.attributes.source);
        writeAttribute(writer, "destinationName", schemaClass.attributes.destinationName);
        writeAttribute(writer, "isExported", String.valueOf(schemaClass.attributes.migrate));

        if (schemaClass.attributes.title != null && !schemaClass.attributes.title.isEmpty()) {
            writeAttribute(writer, "title", schemaClass.attributes.title);
        }

        if (schemaClass.attributes.description != null && !schemaClass.attributes.description.isEmpty()) {
            writeAttribute(writer, "description", schemaClass.attributes.description);
        }

        if (schemaClass.attributes.schemaNotes != null && !schemaClass.attributes.schemaNotes.isEmpty()) {
            writeAttribute(writer, "schemaNotes", schemaClass.attributes.schemaNotes);
        }

        if (schemaClass.attributes.summary != null && !schemaClass.attributes.summary.isEmpty()) {
            writeAttribute(writer, "summary", schemaClass.attributes.summary);
        }

        if (schemaClass.attributes.parentClassName != null && !schemaClass.attributes.parentClassName.isEmpty() && !"Undetermined".equals(schemaClass.attributes.parentClassName)) {
            writeAttribute(writer, "parentClass", schemaClass.attributes.parentClassName);
        }

        if (schemaClass.attributes.pointsTo != null && !schemaClass.attributes.pointsTo.isEmpty()) {
            writeAttribute(writer, "pointsTo", schemaClass.attributes.pointsTo);
        }

        // Check if we have fields, references or nested content
        boolean hasFields = schemaClass.fields != null && schemaClass.fields.length > 0;
        boolean hasReferences = schemaClass.schemaReferences != null && schemaClass.schemaReferences.length > 0;

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

        // If this is a reference to a shared field, write source and definition
        if (field.isSharedField()) {
            writer.write(indent + "<field");
            writeAttribute(writer, "source", field.attributes.source);
            writeAttribute(writer, "definition", field.attributes.definitionId);
            if (field.attributes.format != null && !field.attributes.format.isEmpty()) {
                writeAttribute(writer, "format", field.attributes.format);
            }
            writer.write(" />\n");
            return;
        }

        // Otherwise write the full field definition
        writer.write(indent + "<field");

        // If field has source, use source and destinationName attributes
        // Otherwise use name attribute (for fields without source mapping)
        if (field.attributes.source != null && !field.attributes.source.isEmpty()) {
            writeAttribute(writer, "source", field.attributes.source);
            if (field.attributes.destinationName != null && !field.attributes.destinationName.isEmpty()) {
                writeAttribute(writer, "destinationName", field.attributes.destinationName);
            }
            writeAttribute(writer, "isExported", String.valueOf(field.attributes.isExported));
            if (field.attributes.skipWhen != null && !field.attributes.skipWhen.trim().isEmpty()) {
                writeAttribute(writer, "skipWhen", field.attributes.skipWhen);
            }
        } else {
            // No source - use name attribute
            if (field.attributes.destinationName != null && !field.attributes.destinationName.isEmpty()) {
                writeAttribute(writer, "name", field.attributes.destinationName);
            }
        }

        if (field.attributes.type != null && !field.attributes.type.isEmpty()) {
            writeAttribute(writer, "type", field.attributes.type);
        }

        if (field.attributes.format != null && !field.attributes.format.isEmpty()) {
            writeAttribute(writer, "format", field.attributes.format);
        }

        if (field.attributes.isCollection) {
            writeAttribute(writer, "collection", "true");
        }

        if (field.attributes.skipUserOption != null && !field.attributes.skipUserOption.isEmpty()) {
            writeAttribute(writer, "skipUserOption", field.attributes.skipUserOption);
        }

        if (field.attributes.embedContents) {
            writeAttribute(writer, "embedContents", "true");
        }

        if (field.attributes.childrenType != null && !field.attributes.childrenType.isEmpty()) {
            writeAttribute(writer, "childrenType", field.attributes.childrenType);
        }

        if (field.attributes.pointsTo != null && !field.attributes.pointsTo.isEmpty()) {
            writeAttribute(writer, "pointsTo", field.attributes.pointsTo);
        }

        if (field.attributes.title != null && !field.attributes.title.isEmpty()) {
            writeAttribute(writer, "title", field.attributes.title);
        }

        if (field.attributes.description != null && !field.attributes.description.isEmpty()) {
            writeAttribute(writer, "description", field.attributes.description);
        }

        // Check if we have child elements (valueMap, criterias)
        boolean hasChildren = (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) || (field.attributes.criterias != null && !field.attributes.criterias.isEmpty());

        if (hasChildren) {
            writer.write(">\n");

            // Write valueMap
            if (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) {
                writeValueMap(writer, field.attributes.valueMap, indentLevel + 1);
            }

            // Write criterias for virtual fields
            if (field.attributes.criterias != null && !field.attributes.criterias.isEmpty()) {
                writeCriterias(writer, field, indentLevel + 1);
            }

            writer.write(indent + "</field>\n");
        } else {
            writer.write(" />\n");
        }
    }

    private void writeCriterias(FileWriter writer, DOSchemaField field, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);
        writer.write(indent + "<criterias");

        String operator = (field.attributes.criteriasOperator != null && !field.attributes.criteriasOperator.trim().isEmpty()) ? field.attributes.criteriasOperator : "AND";
        writeAttribute(writer, "operator", operator);
        writer.write(">\n");

        for (migration4o.models.schema.DOFieldCriteria criteria : field.attributes.criterias) {
            if (criteria == null) {
                continue;
            }

            writer.write(indent + "    <criteria");
            writeAttribute(writer, "match", criteria.match);
            writeAttribute(writer, "with", criteria.with);

            String criteriaOperator = (criteria.operator != null && !criteria.operator.trim().isEmpty()) ? criteria.operator : "equals";
            writeAttribute(writer, "operator", criteriaOperator);
            writer.write(" />\n");
        }

        writer.write(indent + "</criterias>\n");
    }

    private void writeValueMap(FileWriter writer, DOSchemaValueMap valueMap, int indentLevel) throws IOException {
        String indent = getIndent(indentLevel);
        if (valueMap.bitmask) {
            writer.write(indent + "<valueMap bitmask=\"true\">\n");
        } else {
            writer.write(indent + "<valueMap>\n");
        }

        for (java.util.Map.Entry<String, String> entry : valueMap.entrySet()) {
            writer.write(indent + "    <mapping");
            writeAttribute(writer, "from", entry.getKey());
            writeAttribute(writer, "to", entry.getValue());
            writer.write(" />\n");
        }

        writer.write(indent + "</valueMap>\n");
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
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("    "); // 4 spaces per level
        }
        return sb.toString();
    }
}
