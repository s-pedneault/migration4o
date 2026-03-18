package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Writes a single class complexType and global element definition into the XSD
 * output.
 * <p>
 * Uses {@code xs:extension} for classes with exported parent classes, emitting
 * only the class's own fields. For classes without an exported parent, all
 * fields (including inherited) are flattened into a single {@code xs:sequence}.
 * <p>
 * The XML export engine sorts fields in inheritance-aware order (ancestor
 * fields first, then own fields) to match the {@code xs:extension} content
 * model.
 */
class XSDClassWriter {

    private final XSDContext context;
    private final XSDFieldWriter fieldWriter;

    XSDClassWriter(XSDContext context) {
        this.context = context;
        this.fieldWriter = new XSDFieldWriter(context);
    }

    /**
     * Writes the XSD declarations for a single exported class: a named
     * complexType and a global element referencing it.
     */
    void writeClassTypeDefinition(FileWriter writer, DOSchemaClass schemaClass) throws IOException {
        String destClassName = schemaClass.attributes.destinationName;
        DOSchemaClass exportedParent = context.getExportedParent(schemaClass);

        // Determine which fields to include in this complexType
        Map<String, DOSchemaField> fields;
        if (exportedParent != null) {
            // xs:extension: only own fields (inherited fields come from parent
            // type)
            fields = context.getOwnExportedFields(schemaClass);
        } else {
            // No exported parent: flatten all fields including inherited
            fields = context.getAllExportedFieldsIncludingAncestors(schemaClass);
        }

        String classTitle = schemaClass.attributes.title != null ? schemaClass.attributes.title.trim() : "";
        String classDescription = schemaClass.attributes.description != null ? schemaClass.attributes.description.trim() : "";

        // Write the complexType definition
        writer.write("  <xs:complexType name=\"" + destClassName + "\">\n");
        if (!classTitle.isEmpty() || !classDescription.isEmpty()) {
            writer.write("    <xs:annotation>\n");
            if (!classTitle.isEmpty()) {
                writer.write("      <xs:documentation xml:lang=\"fr\">" + XSDTypeMapper.escapeXml(classTitle) + "</xs:documentation>\n");
            }
            if (!classDescription.isEmpty()) {
                writer.write("      <xs:documentation xml:lang=\"fr\">" + XSDTypeMapper.escapeXml(classDescription) + "</xs:documentation>\n");
            }
            writer.write("    </xs:annotation>\n");
        }

        if (exportedParent != null) {
            // xs:extension from parent type
            writer.write("    <xs:complexContent>\n");
            writer.write("      <xs:extension base=\"" + exportedParent.attributes.destinationName + "\">\n");
            writeFieldsSequence(writer, fields, "        ");
            writer.write("      </xs:extension>\n");
            writer.write("    </xs:complexContent>\n");
        } else {
            writeFieldsSequence(writer, fields, "    ");
        }

        writer.write("  </xs:complexType>\n");

        // Write global element referencing the type
        writer.write("  <xs:element name=\"" + destClassName + "\" type=\"" + destClassName + "\"/>\n");
        writer.write("\n");
    }

    /**
     * Writes the field list inside an {@code <xs:sequence>}. All fields are
     * sorted alphabetically by destination name.
     */
    private void writeFieldsSequence(FileWriter writer, Map<String, DOSchemaField> fields, String indent) throws IOException {
        writer.write(indent + "<xs:sequence>\n");
        if (!fields.isEmpty()) {
            List<DOSchemaField> sortedFields = new ArrayList<>(fields.values());
            sortedFields.sort((a, b) -> a.attributes.destinationName.compareTo(b.attributes.destinationName));
            for (DOSchemaField field : sortedFields) {
                fieldWriter.writeFieldElement(writer, field, indent + "  ");
            }
        }
        writer.write(indent + "</xs:sequence>\n");
    }
}
