package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;

/**
 * Writes individual field XSD element definitions inside a class complexType.
 * <p>
 * Handles the complex branching logic for collections, arrays, primitive types, IDEntite references, and embedded complex fields. Uses {@code xs:choice} with explicit descendant listing instead of {@code xs:any} for polymorphic fields.
 */
class XSDFieldWriter {

    private final XSDContext context;

    XSDFieldWriter(XSDContext context) {
        this.context = context;
    }

    /**
     * Writes a single field element declaration at the given indent level.
     */
    void writeFieldElement(FileWriter writer, DOSchemaField field, String indent) throws IOException {
        String fieldName = field.attributes.destinationName;
        String fieldType = field.attributes.type;
        boolean isCollection = field.attributes.isCollection;

        if (fieldType == null || fieldType.isEmpty()) {
            // Field has no type information in the schema — skip it from XSD.
            // This can happen for inherited DB4O internal fields (e.g.
            // com.db4o.config.TCollection) where the type was never recorded.
            System.err.println("WARNING: XSD skipping field '" + fieldName + "' (source='" + field.attributes.source + "') — null or empty type. Check if a class is missing from the reference schema.");
            return;
        }

        // If the field type maps to a non-exported class (migrate=false), skip.
        // Per spec 3.8: all fields whose type resolves to a class with
        // migrate=false are skipped — no exception for primitive-like types.
        {
            DOSchemaClass typeClass = context.getReferenceSchema().findClassByName(fieldType);
            if (typeClass != null && !typeClass.attributes.migrate) {
                System.err.println("WARNING: XSD skipping field '" + fieldName + "' — type '" + fieldType + "' is not exported (migrate=false).");
                return;
            }
        }

        // byte[] is exported as a base64-encoded value — let XSDTypeMapper
        // handle it
        if ("byte[]".equals(fieldType)) {
            String xsdType = XSDTypeMapper.getXSDType(fieldType);
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            return;
        }

        // Array types (e.g. int[]) are exported just like collections
        boolean isArrayType = !isCollection && fieldType.endsWith("[]");
        if (isCollection || isArrayType) {
            writeCollectionField(writer, field, fieldName, fieldType, isCollection, isArrayType, indent);
        } else if (CollectionTypeUtil.isMapType(fieldType)) {
            writeMapField(writer, field, fieldName, indent);
        } else if (XSDTypeMapper.isPrimitiveType(fieldType)) {
            writePrimitiveField(writer, field, fieldName, fieldType, indent);
        } else {
            writeComplexField(writer, field, fieldName, fieldType, indent);
        }
    }

    // ── Collection / array fields ──────────────────────────────────────────

    private void writeCollectionField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, boolean isCollection, boolean isArrayType, String indent) throws IOException {
        String childrenType = field.attributes.childrenType;
        if (childrenType == null || childrenType.isEmpty()) {
            childrenType = isArrayType ? fieldType.substring(0, fieldType.length() - 2) : fieldType;
        }

        String itemType;
        String itemElemName;

        if (XSDTypeMapper.isPrimitiveType(childrenType) || isArrayType) {
            itemType = XSDTypeMapper.getXSDType(isArrayType ? fieldType.substring(0, fieldType.length() - 2) : childrenType);
            itemElemName = fieldName;
        } else {
            DOSchema referenceSchema = context.getReferenceSchema();
            DOSchemaClass childClass = referenceSchema.findClassByName(childrenType);
            if (childClass == null) {
                throw new IllegalStateException("XSD generation error: collection field '" + fieldName + "' references childrenType='" + childrenType + "' which is not found in schema");
            }
            if (!childClass.attributes.migrate) {
                System.err.println("WARNING: XSD skipping collection field '" + fieldName + "' — child type '" + childrenType + "' is not exported (migrate=false).");
                return;
            }
            if (field.attributes.embedContents && childClass.attributes.pointsTo != null) {
                // IDEntite with embedContents=true: XML writes the pointed-to
                // entity class
                DOSchemaClass pointsToClass = referenceSchema.findClassByName(childClass.attributes.pointsTo);
                if (pointsToClass != null && !pointsToClass.attributes.migrate) {
                    System.err.println("WARNING: XSD skipping collection field '" + fieldName + "' — target type '" + childClass.attributes.pointsTo + "' is not exported (migrate=false).");
                    return;
                }
                if (pointsToClass != null) {
                    itemElemName = pointsToClass.attributes.destinationName;
                    itemType = pointsToClass.attributes.destinationName;
                } else {
                    itemElemName = childClass.attributes.destinationName;
                    itemType = childClass.attributes.destinationName;
                }
            } else {
                // Non-embedded IDEntite collection OR non-IDEntite complex
                String refClassName = childClass.attributes.destinationName;
                itemType = refClassName;
                itemElemName = refClassName;
            }
        }

        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\">\n");
        writer.write(indent + "  <xs:complexType>\n");

        if (XSDTypeMapper.isPrimitiveType(childrenType) || isArrayType) {
            writer.write(indent + "    <xs:sequence>\n");
            writer.write(indent + "      <xs:element name=\"" + itemElemName + "\" type=\"" + itemType + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            writer.write(indent + "    </xs:sequence>\n");
        } else {
            // Complex items: check if the children type has subclasses
            DOSchema refSchema = context.getReferenceSchema();
            DOSchemaClass itemClass = refSchema.findClassByName(childrenType);
            if (itemClass != null && context.hasAnySubclass(itemClass)) {
                // Polymorphic: use xs:choice listing base + all exported
                // descendants
                writePolymorphicChoice(writer, itemClass, indent + "    ", true);
            } else {
                writer.write(indent + "    <xs:sequence>\n");
                writer.write(indent + "      <xs:element name=\"" + itemElemName + "\" type=\"" + itemType + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                writer.write(indent + "    </xs:sequence>\n");
            }
        }

        writer.write(indent + "    <xs:attribute name=\"size\" type=\"xs:int\"/>\n");
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    // ── Map fields ─────────────────────────────────────────────────────────

    /**
     * Writes a map field (Hashtable, HashMap, etc.) as a wrapper element with entry children. Each entry has a key and a value, both as xs:anyType since the concrete types are not known at schema time. Matches export structure: {@code <field size=
     * "N"><entry><key>...</key><value>...</value></entry>...</field>}
     */
    private void writeMapField(FileWriter writer, DOSchemaField field, String fieldName, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
        writer.write(indent + "  <xs:complexType>\n");
        writer.write(indent + "    <xs:sequence>\n");
        writer.write(indent + "      <xs:element name=\"entry\" minOccurs=\"0\" maxOccurs=\"unbounded\">\n");
        writer.write(indent + "        <xs:complexType>\n");
        writer.write(indent + "          <xs:sequence>\n");
        writer.write(indent + "            <xs:any minOccurs=\"0\" maxOccurs=\"2\" processContents=\"lax\"/>\n");
        writer.write(indent + "          </xs:sequence>\n");
        writer.write(indent + "        </xs:complexType>\n");
        writer.write(indent + "      </xs:element>\n");
        writer.write(indent + "    </xs:sequence>\n");
        writer.write(indent + "    <xs:attribute name=\"size\" type=\"xs:int\"/>\n");
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    // ── Primitive fields ───────────────────────────────────────────────────

    private void writePrimitiveField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, String indent) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);
        boolean isClassType = fieldType.equals("java.lang.Class") || fieldType.equals("Class");

        if (field.attributes.embedContents && isClassType) {
            // java.lang.Class exported as text by FieldExporter
            writeWrappedTextElement(writer, fieldName, indent);
        } else if (field.attributes.embedContents && fieldClass != null) {
            // Other primitive-like type in schema with embedContents
            // (e.g. UUID) — exporter writes it as a wrapped complex element
            writeWrappedChoiceElement(writer, fieldName, fieldClass, indent);
        } else if (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) {
            // ValueMap: generate inline simpleType with xs:enumeration facets.
            // However, if the field type is "object", the value can be anything
            // (dates, numbers, strings) — the valueMap is a best-effort
            // transformation, not an exhaustive constraint. Use xs:string.
            // Bitmask value maps produce comma-separated combinations, so they
            // cannot be constrained to a single enumeration value.
            if ("object".equalsIgnoreCase(fieldType) || field.attributes.valueMap.bitmask) {
                writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                Collection<String> mappedValues = field.attributes.valueMap.values();
                // Deduplicate and sort for deterministic output
                TreeSet<String> sortedValues = new TreeSet<>(mappedValues);
                writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
                writer.write(indent + "  <xs:simpleType>\n");
                writer.write(indent + "    <xs:restriction base=\"xs:string\">\n");
                for (String value : sortedValues) {
                    writer.write(indent + "      <xs:enumeration value=\"" + XSDTypeMapper.escapeXml(value) + "\"/>\n");
                }
                writer.write(indent + "    </xs:restriction>\n");
                writer.write(indent + "  </xs:simpleType>\n");
                writer.write(indent + "</xs:element>\n");
            }
        } else {
            String xsdType = XSDTypeMapper.getXSDType(fieldType);
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        }
    }

    // ── Complex (non-primitive, non-collection) fields ─────────────────────

    private void writeComplexField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, String indent) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);

        if (fieldClass != null && !field.attributes.embedContents && fieldClass.attributes.pointsTo != null) {
            // Non-embedded IDEntite reference: exported as a xs:long mID value
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:long\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        } else if (fieldClass != null && field.attributes.embedContents && fieldClass.attributes.pointsTo != null) {
            // Embedded IDEntite reference: the export resolves the IDEntite to
            // the target entity, but resolution can fail and fall back to
            // exporting the IDEntite object itself. Use an xs:choice that
            // accepts both the IDEntite class and the pointsTo target class
            // (plus their descendants).
            DOSchemaClass pointsToClass = referenceSchema.findClassByName(fieldClass.attributes.pointsTo);
            writeWrappedIDEntiteChoice(writer, fieldName, fieldClass, pointsToClass, indent);
        } else if (fieldClass != null && fieldClass.attributes.migrate) {
            // Non-IDEntite complex field
            if (context.hasAnySubclass(fieldClass)) {
                writeWrappedChoiceElement(writer, fieldName, fieldClass, indent);
            } else {
                writeWrappedTypedElement(writer, fieldName, fieldClass.attributes.destinationName, indent);
            }
        } else if (field.attributes.embedContents) {
            // Embedded type not in schema — write typed element if we know the
            // type
            if (fieldClass != null) {
                writeWrappedChoiceElement(writer, fieldName, fieldClass, indent);
            } else {
                System.err.println("WARNING: XSD skipping embedded field '" + fieldName + "' — references unknown type '" + fieldType + "'. Check if a class is missing from the reference schema.");
                return;
            }
        } else {
            // Field type not in schema or non-exported
            if (XSDTypeMapper.isPrimitiveType(fieldType)) {
                String xsdType = XSDTypeMapper.getXSDType(fieldType);
                writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                // Unknown complex type — skip with warning
                System.err.println("WARNING: XSD skipping field '" + fieldName + "' — references unknown type '" + fieldType + "'. Check if a class is missing from the reference schema.");
                return;
            }
        }
    }

    // ── Wrapper element helpers ────────────────────────────────────────────

    /**
     * Writes a wrapper element containing a specific named child element. Matches: {@code <fieldName><childName>...</childName></fieldName>}
     */
    private void writeWrappedTypedElement(FileWriter writer, String fieldName, String childTypeName, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
        writer.write(indent + "  <xs:complexType>\n");
        writer.write(indent + "    <xs:sequence>\n");
        writer.write(indent + "      <xs:element name=\"" + childTypeName + "\" type=\"" + childTypeName + "\" minOccurs=\"0\"/>\n");
        writer.write(indent + "    </xs:sequence>\n");
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    /**
     * Writes a wrapper element with an xs:choice listing the base class and all its exported descendants. If no descendants, uses a single element ref.
     */
    private void writeWrappedChoiceElement(FileWriter writer, String fieldName, DOSchemaClass baseClass, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
        writer.write(indent + "  <xs:complexType>\n");
        writePolymorphicChoice(writer, baseClass, indent + "    ", false);
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    /**
     * Writes an xs:choice (or xs:sequence for single element) listing the base class and all its exported descendants.
     *
     * @param unbounded true for collection contexts (maxOccurs="unbounded"), false for single fields
     */
    private void writePolymorphicChoice(FileWriter writer, DOSchemaClass baseClass, String indent, boolean unbounded) throws IOException {
        List<DOSchemaClass> descendants = context.getAllExportedDescendants(baseClass);

        if (descendants.isEmpty()) {
            // No descendants: single element
            String maxOcc = unbounded ? " maxOccurs=\"unbounded\"" : "";
            writer.write(indent + "<xs:sequence>\n");
            writer.write(indent + "  <xs:element name=\"" + baseClass.attributes.destinationName + "\" type=\"" + baseClass.attributes.destinationName + "\" minOccurs=\"0\"" + maxOcc + "/>\n");
            writer.write(indent + "</xs:sequence>\n");
        } else {
            // xs:choice with base + all descendants
            String maxOcc = unbounded ? " maxOccurs=\"unbounded\"" : "";
            writer.write(indent + "<xs:choice minOccurs=\"0\"" + maxOcc + ">\n");

            // Collect all names (base + descendants), sort alphabetically
            List<String> allNames = new ArrayList<>();
            if (baseClass.attributes.migrate) {
                allNames.add(baseClass.attributes.destinationName);
            }
            for (DOSchemaClass desc : descendants) {
                allNames.add(desc.attributes.destinationName);
            }
            allNames.sort(String::compareTo);

            for (String name : allNames) {
                writer.write(indent + "  <xs:element ref=\"" + name + "\"/>\n");
            }
            writer.write(indent + "</xs:choice>\n");
        }
    }

    /**
     * Writes a wrapper element for an embedded IDEntite field. The runtime can produce either the IDEntite class itself (when resolution fails) or the pointsTo target class (when resolution succeeds). Generates an xs:choice that accepts both, plus all their exported descendants.
     */
    private void writeWrappedIDEntiteChoice(FileWriter writer, String fieldName, DOSchemaClass idEntiteClass, DOSchemaClass pointsToClass, String indent) throws IOException {
        // Collect all valid element names
        TreeSet<String> allNames = new TreeSet<>();

        // Always include the IDEntite class itself (fallback when resolution
        // fails)
        if (idEntiteClass.attributes.migrate) {
            allNames.add(idEntiteClass.attributes.destinationName);
            for (DOSchemaClass desc : context.getAllExportedDescendants(idEntiteClass)) {
                allNames.add(desc.attributes.destinationName);
            }
        }

        // Include the pointsTo target class and its descendants
        if (pointsToClass != null && pointsToClass.attributes.migrate) {
            allNames.add(pointsToClass.attributes.destinationName);
            for (DOSchemaClass desc : context.getAllExportedDescendants(pointsToClass)) {
                allNames.add(desc.attributes.destinationName);
            }
        }

        if (allNames.isEmpty()) {
            System.err.println("WARNING: XSD skipping embedded IDEntite field '" + fieldName + "' — neither IDEntite class '" + idEntiteClass.attributes.source + "' nor pointsTo target are exported.");
            return;
        }

        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
        writer.write(indent + "  <xs:complexType>\n");
        if (allNames.size() == 1) {
            String name = allNames.first();
            writer.write(indent + "    <xs:sequence>\n");
            writer.write(indent + "      <xs:element name=\"" + name + "\" type=\"" + name + "\" minOccurs=\"0\"/>\n");
            writer.write(indent + "    </xs:sequence>\n");
        } else {
            writer.write(indent + "    <xs:choice minOccurs=\"0\">\n");
            for (String name : allNames) {
                writer.write(indent + "      <xs:element ref=\"" + name + "\"/>\n");
            }
            writer.write(indent + "    </xs:choice>\n");
        }
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    /**
     * Writes a text-content element. Used for fields like java.lang.Class where the export writes a plain string.
     */
    private void writeWrappedTextElement(FileWriter writer, String fieldName, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
    }
}
