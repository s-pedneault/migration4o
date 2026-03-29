package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Orchestrates the generation of the complete XSD document.
 * <p>
 * Iterates ALL {@code isExported=true} classes from the reference schema to
 * produce a comprehensive XSD. No observation-based discovery — the full
 * reference schema is the single source of truth.
 */
class XSDSchemaWriter {

    private final XSDContext context;
    private final XSDClassWriter classWriter;

    XSDSchemaWriter(XSDContext context) {
        this.context = context;
        this.classWriter = new XSDClassWriter(context);
    }

    /**
     * Writes the complete XSD schema file at the given path.
     */
    void write(String xsdPath) throws IOException {
        try (FileWriter writer = new FileWriter(xsdPath)) {
            writeHeader(writer);
            writeRootElement(writer);
            writeMetadataType(writer);
            writePrimitiveElements(writer);
            writeAllExportedClasses(writer);
            writeCollectionItemsGroup(writer);
            writeFooter(writer);
        }
    }

    // ── Document structure ─────────────────────────────────────────────────

    private void writeHeader(FileWriter writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n");

        // Global annotations
        writer.write("  <xs:annotation>\n");
        writer.write("    <xs:appinfo>Migration4o - par Gestion Technologies</xs:appinfo>\n");
        writer.write("  </xs:annotation>\n\n");
    }

    private void writeRootElement(FileWriter writer) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        // Collect all exported class destination names for root xs:choice
        List<String> topLevelNames = new ArrayList<>();
        for (DOSchemaClass sc : referenceSchema.getClasses()) {
            if (sc.attributes.migrate) {
                topLevelNames.add(sc.attributes.destinationName);
            }
        }
        topLevelNames.sort(String::compareTo);

        writer.write("  <xs:element name=\"export\">\n");
        writer.write("    <xs:complexType>\n");
        writer.write("      <xs:sequence>\n");
        writer.write("        <xs:element name=\"metadata\" type=\"Metadata\" minOccurs=\"0\"/>\n");
        writer.write("        <xs:element name=\"objects\">\n");
        writer.write("          <xs:complexType>\n");
        writer.write("            <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\n");

        for (String name : topLevelNames) {
            writer.write("              <xs:element ref=\"" + name + "\"/>\n");
        }

        writer.write("            </xs:choice>\n");
        writer.write("          </xs:complexType>\n");
        writer.write("        </xs:element>\n");
        writer.write("      </xs:sequence>\n");
        writer.write("    </xs:complexType>\n");
        writer.write("  </xs:element>\n\n");
    }

    private void writeMetadataType(FileWriter writer) throws IOException {
        writer.write("  <xs:complexType name=\"Metadata\">\n");
        writer.write("    <xs:sequence>\n");
        // Order and names must match StructuredWriterUtil.metadata() exactly:
        // generator → schemaVersion → provider → module → type → objects → date (optional)
        writer.write("      <xs:element name=\"generator\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"schemaVersion\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"provider\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"module\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"type\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"objects\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"date\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("    </xs:sequence>\n");
        writer.write("  </xs:complexType>\n\n");
    }

    /**
     * Writes global element declarations for XSD primitive types used as
     * collection item elements (e.g. {@code <int>8</int>}).
     */
    private void writePrimitiveElements(FileWriter writer) throws IOException {
        writer.write("  <!-- Primitive type elements for collection items -->\n");
        writer.write("  <xs:element name=\"int\" type=\"xs:int\"/>\n");
        writer.write("  <xs:element name=\"long\" type=\"xs:long\"/>\n");
        writer.write("  <xs:element name=\"double\" type=\"xs:double\"/>\n");
        writer.write("  <xs:element name=\"float\" type=\"xs:float\"/>\n");
        writer.write("  <xs:element name=\"boolean\" type=\"xs:boolean\"/>\n");
        writer.write("  <xs:element name=\"short\" type=\"xs:short\"/>\n");
        writer.write("  <xs:element name=\"byte\" type=\"xs:byte\"/>\n");
        writer.write("  <xs:element name=\"string\" type=\"xs:string\"/>\n");
        writer.write("  <xs:element name=\"dateTime\" type=\"xs:dateTime\"/>\n");
        writer.write("  <xs:element name=\"base64Binary\" type=\"xs:base64Binary\"/>\n\n");
    }

    /**
     * Writes an {@code xs:group} named "collectionItems" that enumerates every
     * valid element that can appear inside a collection or map type: the
     * primitive-type elements plus every exported class element.
     */
    private void writeCollectionItemsGroup(FileWriter writer) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        List<String> classNames = new ArrayList<>();
        for (DOSchemaClass sc : referenceSchema.getClasses()) {
            if (sc.attributes.migrate) {
                classNames.add(sc.attributes.destinationName);
            }
        }
        classNames.sort(String::compareTo);

        writer.write("  <xs:group name=\"collectionItems\">\n");
        writer.write("    <xs:choice>\n");
        // Primitive types
        for (String p : new String[] { "int", "long", "double", "float", "boolean", "short", "byte", "string", "dateTime", "base64Binary" }) {
            writer.write("      <xs:element ref=\"" + p + "\"/>\n");
        }
        // All exported classes
        for (String name : classNames) {
            writer.write("      <xs:element ref=\"" + name + "\"/>\n");
        }
        writer.write("    </xs:choice>\n");
        writer.write("  </xs:group>\n\n");
    }

    private void writeFooter(FileWriter writer) throws IOException {
        writer.write("</xs:schema>\n");
    }

    // ── Class definitions ──────────────────────────────────────────────────

    /**
     * Writes type/element declarations for ALL exported classes from the
     * reference schema. Every class with {@code migrate=true} gets a
     * complexType definition and a global element declaration.
     */
    private void writeAllExportedClasses(FileWriter writer) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        List<DOSchemaClass> exportedClasses = new ArrayList<>();
        for (DOSchemaClass sc : referenceSchema.getClasses()) {
            if (sc.attributes.migrate) {
                exportedClasses.add(sc);
            }
        }
        exportedClasses.sort((c1, c2) -> c1.attributes.destinationName.compareTo(c2.attributes.destinationName));

        for (DOSchemaClass schemaClass : exportedClasses) {
            classWriter.writeClassTypeDefinition(writer, schemaClass);
        }
    }
}
