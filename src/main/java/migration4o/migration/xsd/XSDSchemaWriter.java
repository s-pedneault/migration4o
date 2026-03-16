package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Orchestrates the generation of the complete XSD document.
 * <p>
 * Writes the XML header, root export element, metadata type, all registered
 * class definitions, and iteratively discovers and writes referenced types
 * until the type graph is fully resolved.
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
            writeRegisteredClasses(writer);
            writeDiscoveredReferencedTypes(writer);
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
        writer.write("  <xs:element name=\"export\">\n");
        writer.write("    <xs:complexType>\n");
        writer.write("      <xs:sequence>\n");
        writer.write("        <xs:element name=\"metadata\" type=\"Metadata\" minOccurs=\"0\"/>\n");
        writer.write("        <xs:element name=\"objects\">\n");
        writer.write("          <xs:complexType>\n");
        writer.write("            <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\n");

        List<String> sortedTopLevelObjects = new ArrayList<>(context.topLevelObjects);
        Collections.sort(sortedTopLevelObjects);
        for (String obj : sortedTopLevelObjects) {
            writer.write("              <xs:element ref=\"" + obj + "\"/>\n");
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
        // generator → provider → module → type → objects → date (optional)
        writer.write("      <xs:element name=\"generator\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"provider\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"module\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"type\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"objects\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("      <xs:element name=\"date\" type=\"xs:string\" minOccurs=\"0\"/>\n");
        writer.write("    </xs:sequence>\n");
        writer.write("  </xs:complexType>\n\n");
    }

    private void writeFooter(FileWriter writer) throws IOException {
        writer.write("</xs:schema>\n");
    }

    // ── Class definitions ──────────────────────────────────────────────────

    /**
     * Writes type/element declarations for all classes registered during
     * export.
     */
    private void writeRegisteredClasses(FileWriter writer) throws IOException {
        List<DOSchemaClass> classesToWrite = new ArrayList<>(context.classMap.values());
        classesToWrite.sort((c1, c2) -> c1.destinationName.compareTo(c2.destinationName));

        for (DOSchemaClass schemaClass : classesToWrite) {
            String destName = schemaClass.destinationName;
            boolean isTopLevel = context.topLevelObjects.contains(destName);
            boolean isReferenced = context.referencedTypes.contains(destName);
            boolean shouldWrite = isTopLevel || isReferenced;
            classWriter.writeClassTypeDefinition(writer, schemaClass, isTopLevel, shouldWrite);
            if (shouldWrite) {
                writtenTypes.add(destName);
            }
        }
    }

    /** Tracks which type definitions have been written, to avoid duplicates. */
    private final Set<String> writtenTypes = new LinkedHashSet<>();

    /**
     * Iteratively discovers and writes type definitions for classes that were
     * referenced by fields but not originally registered. Keeps processing
     * until no new types are found.
     */
    private void writeDiscoveredReferencedTypes(FileWriter writer) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        boolean foundNewTypes = true;

        while (foundNewTypes) {
            foundNewTypes = false;
            // Snapshot to avoid ConcurrentModificationException
            Set<String> currentReferencedTypes = new LinkedHashSet<>(context.referencedTypes);

            for (String referencedTypeName : currentReferencedTypes) {
                if (writtenTypes.contains(referencedTypeName)) {
                    continue;
                }

                boolean found = false;
                for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                    if (schemaClass.destinationName.equals(referencedTypeName)) {
                        if (!schemaClass.migrate) {
                            System.err.println("WARNING: Referenced type '" + referencedTypeName + "' (source: " + schemaClass.source + ") has isExported=false");
                            writtenTypes.add(referencedTypeName);
                            found = true;
                            break;
                        }

                        // Add the class and populate its fields
                        context.classMap.put(schemaClass.source, schemaClass);
                        context.fieldsByClass.put(schemaClass.source, context.getAllExportedFieldsIncludingAncestors(schemaClass));

                        // Write the type definition (may discover more
                        // referenced types)
                        classWriter.writeClassTypeDefinition(writer, schemaClass, false, true);
                        writtenTypes.add(referencedTypeName);
                        foundNewTypes = true;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.err.println("ERROR: Referenced type '" + referencedTypeName + "' not found in reference schema. XSD validation will fail.");
                }
            }
        }

        // Report any referenced types that were never resolved
        Set<String> missingTypes = new LinkedHashSet<>(context.referencedTypes);
        missingTypes.removeAll(writtenTypes);
        if (!missingTypes.isEmpty()) {
            System.err.println("\nWARNING: The following types are referenced but not defined in XSD:");
            for (String missingType : missingTypes) {
                System.err.println("  - " + missingType);
            }
            System.err.println("These types should be added to reference-schema.xml with isExported=\"true\"");
            System.err.println("or the fields referencing them should have embedContents=\"false\" if they are ID references.\n");
        }
    }
}
