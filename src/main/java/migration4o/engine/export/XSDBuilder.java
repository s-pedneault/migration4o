package migration4o.engine.export;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Builds XSD (XML Schema) definitions for exported XML files.
 * Tracks classes and fields discovered during export and generates a complete
 * schema.
 */
public class XSDBuilder {
    private final Map<String, DOSchemaClass> classMap = new LinkedHashMap<>();
    private final Map<String, Map<String, DOSchemaField>> fieldsByClass = new LinkedHashMap<>();
    private final Set<String> topLevelObjects = new LinkedHashSet<>();
    private final Set<String> referencedTypes = new LinkedHashSet<>(); // Types used in fields
    private final Map<String, DOSchemaField> fieldsWithValueMappings = new LinkedHashMap<>(); // Fields that need
                                                                                              // enumeration types
    private final migration4o.models.schema.DOSchema referenceSchema;
    private final migration4o.models.schema.DOSchema databaseSchema;

    public XSDBuilder(migration4o.models.schema.DOSchema referenceSchema,
            migration4o.models.schema.DOSchema databaseSchema) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
    }

    public void startExportRoot() {
        // Marks the beginning of schema recording
    }

    public void addTopLevelObject(String destName, DOSchemaClass schemaClass) {
        topLevelObjects.add(destName);
        if (schemaClass != null) {
            classMap.put(schemaClass.source, schemaClass);
        }
    }

    public void addClass(DOSchemaClass schemaClass) {
        if (schemaClass == null)
            return;
        String absName = schemaClass.source;
        if (!classMap.containsKey(absName)) {
            classMap.put(absName, schemaClass);
        }
    }

    public void addField(DOSchemaClass parentClass, DOSchemaField field) {
        if (field == null || parentClass == null)
            return;
        fieldsByClass.computeIfAbsent(parentClass.source, k -> new LinkedHashMap<>())
                .put(field.destinationName, field);

        // Track fields with value mappings for enumeration type generation
        if (field.valueMap != null && !field.valueMap.isEmpty()) {
            String uniqueKey = parentClass.destinationName + "_" + field.destinationName;
            fieldsWithValueMappings.put(uniqueKey, field);
        }
    }

    public void writeXSD(String xsdPath) throws IOException {
        try (FileWriter xsdWriter = new FileWriter(xsdPath)) {
            xsdWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xsdWriter.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n");

            // Root export element
            xsdWriter.write("  <xs:element name=\"export\">\n");
            xsdWriter.write("    <xs:complexType>\n");
            xsdWriter.write("      <xs:sequence>\n");
            xsdWriter.write("        <xs:element name=\"metadata\" type=\"MetadataType\"/>\n");
            xsdWriter.write("        <xs:element name=\"objects\">\n");
            xsdWriter.write("          <xs:complexType>\n");
            xsdWriter.write("            <xs:sequence>\n");
            for (String obj : topLevelObjects) {
                xsdWriter.write("              <xs:element ref=\"" + obj
                        + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            }
            xsdWriter.write("            </xs:sequence>\n");
            xsdWriter.write("          </xs:complexType>\n");
            xsdWriter.write("        </xs:element>\n");
            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");

            // Metadata type
            xsdWriter.write("  <xs:complexType name=\"MetadataType\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            xsdWriter.write("      <xs:element name=\"moduleName\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"classCount\" type=\"xs:int\"/>\n");
            xsdWriter.write("      <xs:element name=\"exportDate\" type=\"xs:string\"/>\n");
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");

            // Write type declarations based on usage:
            // - Element declaration if class appears as top-level object
            // - Type declaration if class is referenced as a field type
            for (DOSchemaClass schemaClass : classMap.values()) {
                String destName = schemaClass.destinationName;
                boolean isTopLevel = topLevelObjects.contains(destName);
                boolean isReferenced = referencedTypes.contains(getSimpleClassName(schemaClass.source) + "Type");
                writeClassTypeDefinition(xsdWriter, schemaClass, isTopLevel, isReferenced);
            }
            // Generate enumeration types for fields with value mappings
            if (!fieldsWithValueMappings.isEmpty()) {
                xsdWriter.write("\n  <!-- Enumeration types for fields with value mappings -->\n");
                for (Map.Entry<String, DOSchemaField> entry : fieldsWithValueMappings.entrySet()) {
                    writeEnumerationType(xsdWriter, entry.getKey(), entry.getValue());
                }
            }
            xsdWriter.write("</xs:schema>\n");
        }
    }

    private void writeClassTypeDefinition(FileWriter xsdWriter, DOSchemaClass schemaClass,
            boolean writeElement, boolean writeType) throws IOException {
        if (!writeElement && !writeType) {
            return; // Class not used anywhere
        }

        String className = schemaClass.source;
        String destClassName = schemaClass.destinationName;
        Map<String, DOSchemaField> fields = fieldsByClass.getOrDefault(className, new LinkedHashMap<>());

        xsdWriter.write("  <!-- Class: " + className + " (exported as " + destClassName + ") -->\n");

        // Write as element (used in top-level <objects>) if needed
        if (writeElement) {
            xsdWriter.write("  <xs:element name=\"" + destClassName + "\">\n");
            xsdWriter.write("    <xs:complexType>\n");
            xsdWriter.write("      <xs:sequence>\n");
            for (DOSchemaField field : fields.values()) {
                writeFieldElement(xsdWriter, field, "        ");
            }
            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");
        }

        // Write as type (reusable for field references) if needed
        if (writeType) {
            xsdWriter.write("  <xs:complexType name=\"" + getSimpleClassName(className) + "Type\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            for (DOSchemaField field : fields.values()) {
                writeFieldElement(xsdWriter, field, "      ");
            }
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");
        }
    }

    private void writeFieldElement(FileWriter xsdWriter, DOSchemaField field, String indent) throws IOException {
        String fieldName = field.destinationName;
        String fieldType = field.type;
        boolean isCollection = field.isCollection;

        if (fieldType == null || fieldType.isEmpty())
            return;

        if (isCollection) {
            String childrenType = field.childrenType;
            if (childrenType == null || childrenType.isEmpty()) {
                childrenType = fieldType;
            }
            // Collection fields have a complex type with size attribute and child elements
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\">\n");
            xsdWriter.write(indent + "  <xs:complexType>\n");
            xsdWriter.write(indent + "    <xs:sequence>\n");
            if (isPrimitiveType(childrenType)) {
                String xsdType = getXSDType(childrenType);
                xsdWriter.write(indent + "      <xs:element name=\"item\" type=\"" + xsdType
                        + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            } else {
                String refClassName = getSimpleClassName(childrenType) + "Type";
                referencedTypes.add(refClassName); // Track that this type is referenced
                xsdWriter.write(indent + "      <xs:element name=\"item\" type=\"" + refClassName
                        + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            }
            xsdWriter.write(indent + "    </xs:sequence>\n");
            xsdWriter.write(indent + "    <xs:attribute name=\"size\" type=\"xs:int\"/>\n");
            xsdWriter.write(indent + "  </xs:complexType>\n");
            xsdWriter.write(indent + "</xs:element>\n");
        } else if (isPrimitiveType(fieldType)) {
            // Check if this field has value mappings (enumeration)
            String xsdType;
            if (field.valueMap != null && !field.valueMap.isEmpty()) {
                xsdType = fieldName + "Type"; // Use custom enumeration type
            } else {
                xsdType = getXSDType(fieldType);
            }
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                    + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
        } else {
            // Check if this is a non-embedded IDEntite reference
            DOSchemaClass fieldClass = migration4o.util.SchemaUtil.findClassByName(fieldType, referenceSchema);
            if (fieldClass != null && fieldClass.isIDEntite(databaseSchema) && !field.embedContents) {
                // Non-embedded IDEntite references are exported as simple long values
                // But if they have value mappings, use enumeration type
                String xsdType;
                if (field.valueMap != null && !field.valueMap.isEmpty()) {
                    xsdType = fieldName + "Type"; // Use custom enumeration type
                } else {
                    xsdType = "xs:long";
                }
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" "
                        + "minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                String refClassName = getSimpleClassName(fieldType) + "Type";
                referencedTypes.add(refClassName); // Track that this type is referenced
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                        + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            }
        }
    }

    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("java.lang.String") ||
                typeName.equals("string") ||
                typeName.equals("java.lang.Integer") ||
                typeName.equals("int") ||
                typeName.equals("java.lang.Long") ||
                typeName.equals("long") ||
                typeName.equals("java.lang.Boolean") ||
                typeName.equals("boolean") ||
                typeName.equals("java.lang.Double") ||
                typeName.equals("double") ||
                typeName.equals("java.lang.Float") ||
                typeName.equals("float") ||
                typeName.equals("java.util.Date") ||
                typeName.equals("date");
    }

    private String getXSDType(String javaType) {
        if (javaType.equals("java.lang.String") || javaType.equals("string"))
            return "xs:string";
        if (javaType.equals("java.lang.Integer") || javaType.equals("int"))
            return "xs:int";
        if (javaType.equals("java.lang.Long") || javaType.equals("long"))
            return "xs:long";
        if (javaType.equals("java.lang.Boolean") || javaType.equals("boolean"))
            return "xs:boolean";
        if (javaType.equals("java.lang.Double") || javaType.equals("double"))
            return "xs:double";
        if (javaType.equals("java.lang.Float") || javaType.equals("float"))
            return "xs:float";
        if (javaType.equals("java.util.Date") || javaType.equals("date"))
            return "xs:dateTime";
        return "xs:string";
    }

    private String getSimpleClassName(String fullClassName) {
        if (fullClassName == null) {
            return "Unknown";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private void writeEnumerationType(FileWriter xsdWriter, String typeKey, DOSchemaField field) throws IOException {
        String typeName = field.destinationName + "Type";
        xsdWriter.write("\n  <xs:simpleType name=\"" + typeName + "\">\n");
        xsdWriter.write("    <xs:restriction base=\"xs:string\">\n");

        // Write enumeration values (the mapped "to" values)
        for (String mappedValue : field.valueMap.values()) {
            xsdWriter.write("      <xs:enumeration value=\"" + escapeXml(mappedValue) + "\"/>\n");
        }

        xsdWriter.write("    </xs:restriction>\n");
        xsdWriter.write("  </xs:simpleType>\n");
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
}
