package migration4o.migration.xsd;

import java.io.FileWriter;
import java.io.IOException;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Writes individual field XSD element definitions inside a class complexType.
 * <p>
 * Handles the complex branching logic for collections, arrays, primitive types,
 * IDEntite references, and embedded complex fields. Discovers new referenced
 * types and registers them in the shared {@link XSDContext}.
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
        String fieldName = field.destinationName;
        String fieldType = field.type;
        boolean isCollection = field.isCollection;

        if (fieldType == null || fieldType.isEmpty()) {
            // No type info: write a permissive anyType element
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:anyType\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            return;
        }

        // byte[] is exported as a single base64/string value, not as an array
        if ("byte[]".equals(fieldType)) {
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            return;
        }

        // Array types (e.g. int[]) are exported just like collections
        boolean isArrayType = !isCollection && fieldType.endsWith("[]");
        if (isCollection || isArrayType) {
            writeCollectionField(writer, field, fieldName, fieldType, isCollection, isArrayType, indent);
        } else if (XSDTypeMapper.isPrimitiveType(fieldType)) {
            writePrimitiveField(writer, field, fieldName, fieldType, indent);
        } else {
            writeComplexField(writer, field, fieldName, fieldType, indent);
        }
    }

    // ── Collection / array fields ──────────────────────────────────────────

    private void writeCollectionField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, boolean isCollection, boolean isArrayType, String indent) throws IOException {
        String childrenType = field.childrenType;
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
                System.err.println("WARNING: Collection field '" + fieldName + "' references childrenType='" + childrenType + "' which is not found in schema");
                itemType = "xs:anyType";
                itemElemName = fieldName;
            } else if (field.embedContents && childClass.pointsTo != null) {
                // IDEntite with embedContents=true: XML writes the pointed-to
                // entity class
                DOSchemaClass pointsToClass = referenceSchema.findClassByName(childClass.pointsTo);
                if (pointsToClass != null) {
                    itemElemName = pointsToClass.destinationName;
                    itemType = pointsToClass.destinationName;
                    context.referencedTypes.add(itemType);
                } else {
                    itemElemName = childClass.destinationName;
                    itemType = childClass.destinationName;
                    context.referencedTypes.add(itemType);
                }
            } else {
                // Non-embedded IDEntite collection OR non-IDEntite complex
                String refClassName = childClass.destinationName;
                context.referencedTypes.add(refClassName);
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
                // Polymorphic: runtime item elements may use subclass names
                writer.write(indent + "    <xs:sequence>\n");
                writer.write(indent + "      <xs:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"lax\"/>\n");
                writer.write(indent + "    </xs:sequence>\n");
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

    // ── Primitive fields ───────────────────────────────────────────────────

    private void writePrimitiveField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, String indent) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);
        boolean isClassType = fieldType.equals("java.lang.Class") || fieldType.equals("Class");

        if (field.embedContents && isClassType) {
            // java.lang.Class exported as text by FieldExporter
            writeWrappedTextElement(writer, fieldName, indent);
        } else if (field.embedContents && fieldClass != null) {
            // Other primitive-like type in schema with embedContents
            // (e.g. UUID) — exporter writes it as a wrapped complex element
            writeWrappedAnyElement(writer, fieldName, indent);
        } else if (field.valueMap != null && !field.valueMap.isEmpty()) {
            // ValueMap maps raw values to display strings — use xs:string
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        } else {
            String xsdType = XSDTypeMapper.getXSDType(fieldType);
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        }
    }

    // ── Complex (non-primitive, non-collection) fields ─────────────────────

    private void writeComplexField(FileWriter writer, DOSchemaField field, String fieldName, String fieldType, String indent) throws IOException {
        DOSchema referenceSchema = context.getReferenceSchema();
        DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);

        if (fieldClass != null && !field.embedContents && fieldClass.pointsTo != null) {
            // Non-embedded IDEntite reference: exported as a xs:long mID value
            writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:long\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        } else if (fieldClass != null && field.embedContents && fieldClass.pointsTo != null) {
            // Embedded IDEntite reference: XML may write either the pointed-to
            // entity OR the IDEntite class itself (when resolution fails)
            context.referencedTypes.add(fieldClass.destinationName);
            DOSchemaClass pointsToClass = referenceSchema.findClassByName(fieldClass.pointsTo);
            if (pointsToClass != null) {
                context.referencedTypes.add(pointsToClass.destinationName);
            }
            writeWrappedAnyElement(writer, fieldName, indent);
        } else if (fieldClass != null && fieldClass.migrate) {
            // Non-IDEntite complex field
            String refClassName = fieldClass.destinationName;
            context.referencedTypes.add(refClassName);
            if (context.hasAnySubclass(fieldClass)) {
                writeWrappedAnyElement(writer, fieldName, indent);
            } else {
                writeWrappedTypedElement(writer, fieldName, refClassName, indent);
            }
        } else if (field.embedContents) {
            // Embedded type that is not exported (e.g. java.util.UUID)
            writeWrappedAnyElement(writer, fieldName, indent);
        } else {
            // Field type not in schema or non-exported
            if (XSDTypeMapper.isPrimitiveType(fieldType)) {
                String xsdType = XSDTypeMapper.getXSDType(fieldType);
                writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                // Unknown complex type: use xs:anyType
                writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:anyType\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            }
        }
    }

    // ── Wrapper element helpers ────────────────────────────────────────────

    /**
     * Writes a wrapper element containing a specific named child element.
     * Matches: {@code <fieldName><childName>...</childName></fieldName>}
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
     * Writes a wrapper element that accepts any single child element. Used when
     * the field type has subclasses (polymorphism).
     */
    private void writeWrappedAnyElement(FileWriter writer, String fieldName, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
        writer.write(indent + "  <xs:complexType>\n");
        writer.write(indent + "    <xs:sequence>\n");
        writer.write(indent + "      <xs:any minOccurs=\"0\" processContents=\"lax\"/>\n");
        writer.write(indent + "    </xs:sequence>\n");
        writer.write(indent + "  </xs:complexType>\n");
        writer.write(indent + "</xs:element>\n");
    }

    /**
     * Writes a text-content element. Used for fields like java.lang.Class where
     * the export writes a plain string.
     */
    private void writeWrappedTextElement(FileWriter writer, String fieldName, String indent) throws IOException {
        writer.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
    }
}
