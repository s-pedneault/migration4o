package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Writes a single class complexType and/or global element definition into the
 * XSD output.
 * <p>
 * Uses the full schema-derived field set (own + inherited) as the authoritative
 * source rather than the live-observed {@code fieldsByClass}, which may miss
 * inherited fields and fields that are always at their default value
 * ({@code skipWhen=DEFAULT}).
 */
class XSDClassWriter {

    private final XSDContext context;
    private final XSDFieldWriter fieldWriter;

    XSDClassWriter(XSDContext context) {
        this.context = context;
        this.fieldWriter = new XSDFieldWriter(context);
    }

    /**
     * Writes the XSD declarations for a single class.
     *
     * @param writeElement true to emit a global {@code <xs:element>}
     * @param writeType true to emit a {@code <xs:complexType>}
     */
    void writeClassTypeDefinition(FileWriter writer, DOSchemaClass schemaClass, boolean writeElement, boolean writeType) throws IOException {
        if (!writeElement && !writeType) {
            return;
        }

        String destClassName = schemaClass.destinationName;
        Map<String, DOSchemaField> fields = context.getAllExportedFieldsIncludingAncestors(schemaClass);

        // Title comment
        if (schemaClass.title != null && schemaClass.title.length() > 0) {
            writer.write("  <!-- " + schemaClass.title + " -->\n");
        }
        String classDescription = schemaClass.description != null ? schemaClass.description.trim() : "";

        // Write the complexType definition
        if (writeType || (writeElement && writeType)) {
            writer.write("  <xs:complexType name=\"" + destClassName + "\">\n");
            if (!classDescription.isEmpty()) {
                writer.write("    <xs:annotation>\n");
                writer.write("      <xs:documentation xml:lang=\"fr\">" + XSDTypeMapper.escapeXml(classDescription) + "</xs:documentation>\n");
                writer.write("    </xs:annotation>\n");
            }
            writeFieldsBody(writer, fields, "    ");
            writer.write("  </xs:complexType>\n");
        }

        // Write element definition
        if (writeElement) {
            if (writeType) {
                // Type was already written above — just reference it
                writer.write("  <xs:element name=\"" + destClassName + "\" type=\"" + destClassName + "\"/>\n");
            } else {
                // Write element with inline anonymous complexType
                writer.write("  <xs:element name=\"" + destClassName + "\">\n");
                if (!classDescription.isEmpty()) {
                    writer.write("    <xs:annotation>\n");
                    writer.write("      <xs:documentation xml:lang=\"fr\">" + XSDTypeMapper.escapeXml(classDescription) + "</xs:documentation>\n");
                    writer.write("    </xs:annotation>\n");
                }
                writer.write("    <xs:complexType>\n");
                if (!classDescription.isEmpty()) {
                    writer.write("    <xs:annotation>\n");
                    writer.write("      <xs:documentation xml:lang=\"fr\">" + XSDTypeMapper.escapeXml(classDescription) + "</xs:documentation>\n");
                    writer.write("    </xs:annotation>\n");
                }
                writeFieldsBody(writer, fields, "      ");
                writer.write("    </xs:complexType>\n");
                writer.write("  </xs:element>\n");
            }
        }

        writer.write("\n");
    }

    /**
     * Writes the inner body of a complexType — either a sorted field list
     * inside {@code <xs:all>} or a permissive {@code <xs:any>} when no fields
     * are exported.
     */
    private void writeFieldsBody(FileWriter writer, Map<String, DOSchemaField> fields, String indent) throws IOException {
        if (fields.isEmpty()) {
            writer.write(indent + "<xs:sequence>\n");
            writer.write(indent + "  <xs:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"skip\"/>\n");
            writer.write(indent + "</xs:sequence>\n");
            writer.write(indent + "<!-- no exported fields -->\n");
        } else {
            writer.write(indent + "<xs:all>\n");
            List<DOSchemaField> sortedFields = new ArrayList<>(fields.values());
            sortedFields.sort((a, b) -> a.destinationName.compareTo(b.destinationName));
            for (DOSchemaField field : sortedFields) {
                fieldWriter.writeFieldElement(writer, field, indent + "  ");
            }
            // Allow unmapped database fields exported as <unknown>
            writer.write(indent + "  <xs:element name=\"unknown\" type=\"xs:anyType\" minOccurs=\"0\"/>\n");
            writer.write(indent + "</xs:all>\n");
        }
    }
}
