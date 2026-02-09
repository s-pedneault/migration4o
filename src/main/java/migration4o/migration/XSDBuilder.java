package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.database.DODatabaseService;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.DOSchemaService;

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

    public XSDBuilder() {
    }

    public void startExportRoot() {
        // Marks the beginning of schema recording
    }

    public void addTopLevelObject(String destName, DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            // Always use reference schema for export definitions
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            DOSchemaClass refClass = referenceSchema.findClassByName(schemaClass.source);
            if (refClass != null) {
                topLevelObjects.add(refClass.destinationName); // Use reference schema's destination name
                classMap.put(refClass.source, refClass);
            }
        }
    }

    public void addClass(DOSchemaClass schemaClass) {
        if (schemaClass == null)
            return;
        String absName = schemaClass.source;
        if (!classMap.containsKey(absName)) {
            // Always use reference schema for export definitions
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            DOSchemaClass refClass = referenceSchema.findClassByName(absName);
            if (refClass != null) {
                classMap.put(absName, refClass);
            }
        }
    }

    public void addField(DOSchemaClass parentClass, DOSchemaField field) {
        if (field == null || parentClass == null)
            return;

        // Always use reference schema for export definitions
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DOSchemaClass refClass = referenceSchema.findClassByName(parentClass.source);
        if (refClass == null) {
            return; // Skip if not in reference schema
        }

        // Look up the field in reference schema to get correct export properties
        DOSchemaField refField = null;
        if (refClass.fields != null) {
            for (DOSchemaField f : refClass.fields) {
                if (f.source.equals(field.source)) {
                    refField = f;
                    break;
                }
            }
        }

        if (refField != null && refField.isExported) {
            fieldsByClass.computeIfAbsent(refClass.source, k -> new LinkedHashMap<>())
                    .put(refField.destinationName, refField);

            // Track fields with value mappings for enumeration type generation
            if (refField.valueMap != null && !refField.valueMap.isEmpty()) {
                String uniqueKey = refClass.destinationName + "_" + refField.destinationName;
                fieldsWithValueMappings.put(uniqueKey, refField);
            }
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
            // Create a snapshot to avoid ConcurrentModificationException
            List<DOSchemaClass> classesToWrite = new java.util.ArrayList<>(classMap.values());
            Set<String> writtenTypes = new LinkedHashSet<>();

            for (DOSchemaClass schemaClass : classesToWrite) {
                String destName = schemaClass.destinationName;
                boolean isTopLevel = topLevelObjects.contains(destName);
                boolean isReferenced = referencedTypes.contains(destName);
                // Always write type definition for classes that might be referenced
                // Write element definition only for top-level objects
                writeClassTypeDefinition(xsdWriter, schemaClass, isTopLevel, isTopLevel || isReferenced);
                writtenTypes.add(destName);
            }

            // Also write type definitions for referenced classes not in classMap
            // Keep processing newly discovered referenced types until all are written
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            boolean foundNewTypes = true;
            while (foundNewTypes) {
                foundNewTypes = false;
                // Take a snapshot of current referenced types
                Set<String> currentReferencedTypes = new LinkedHashSet<>(referencedTypes);

                for (String referencedTypeName : currentReferencedTypes) {
                    // Skip if already written
                    if (writtenTypes.contains(referencedTypeName)) {
                        continue;
                    }

                    // Find the class in the schema and add its fields to fieldsByClass
                    for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                        if (schemaClass.destinationName.equals(referencedTypeName)) {
                            // Add the class to classMap and populate its fields
                            classMap.put(schemaClass.source, schemaClass);

                            // Populate fields from schema definition
                            if (schemaClass.fields != null) {
                                Map<String, DOSchemaField> fields = new LinkedHashMap<>();
                                for (DOSchemaField field : schemaClass.fields) {
                                    if (field.isExported) {
                                        fields.put(field.destinationName, field);
                                    }
                                }
                                fieldsByClass.put(schemaClass.source, fields);
                            }

                            // Now write the type definition with proper fields
                            // This may discover more referenced types
                            writeClassTypeDefinition(xsdWriter, schemaClass, false, true);
                            writtenTypes.add(referencedTypeName);
                            foundNewTypes = true;
                            break;
                        }
                    }
                }
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
        String destClassName = schemaClass.destinationName; // Already from reference schema
        Map<String, DOSchemaField> fields = fieldsByClass.getOrDefault(className, new LinkedHashMap<>());

        xsdWriter.write("  <!-- " + destClassName + " -->\n");

        // Write the complexType definition if needed (or if we need both element and
        // type)
        if (writeType || (writeElement && writeType)) {
            xsdWriter.write("  <xs:complexType name=\"" + destClassName + "\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            for (DOSchemaField field : fields.values()) {
                writeFieldElement(xsdWriter, field, "      ");
            }
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n");
        }

        // Write element definition
        if (writeElement) {
            if (writeType) {
                // If we also wrote the type, just reference it
                xsdWriter.write("  <xs:element name=\"" + destClassName + "\" type=\"" + destClassName + "\"/>\n");
            } else {
                // Write element with inline anonymous complexType
                xsdWriter.write("  <xs:element name=\"" + destClassName + "\">\n");
                xsdWriter.write("    <xs:complexType>\n");
                xsdWriter.write("      <xs:sequence>\n");
                for (DOSchemaField field : fields.values()) {
                    writeFieldElement(xsdWriter, field, "        ");
                }
                xsdWriter.write("      </xs:sequence>\n");
                xsdWriter.write("    </xs:complexType>\n");
                xsdWriter.write("  </xs:element>\n");
            }
        }

        xsdWriter.write("\n");
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

            // Determine the XSD type for collection items
            String itemType;
            if (isPrimitiveType(childrenType)) {
                itemType = getXSDType(childrenType);
            } else {
                // Check if childrenType is an IDEntite with embedContents=false
                DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
                DOSchema databaseSchema = DODatabaseService.getInstance().getDatabaseSchema();
                DOSchemaClass childClass = referenceSchema.findClassByName(childrenType);

                if (childClass != null && childClass.isIDEntite(databaseSchema) && !field.embedContents) {
                    // Non-embedded IDEntite collections use xs:long items
                    itemType = "xs:long";
                } else {
                    // Complex type - need to reference it
                    String refClassName = getDestinationName(childrenType);
                    referencedTypes.add(refClassName);
                    itemType = refClassName;
                }
            }

            // Collection fields have a complex type with size attribute and child elements
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\">\n");
            xsdWriter.write(indent + "  <xs:complexType>\n");
            xsdWriter.write(indent + "    <xs:sequence>\n");
            xsdWriter.write(indent + "      <xs:element name=\"item\" type=\"" + itemType
                    + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
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
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            DOSchema databaseSchema = DODatabaseService.getInstance().getDatabaseSchema();
            DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);
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
                String refClassName = getDestinationName(fieldType);
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
                typeName.equals("java.lang.Byte") ||
                typeName.equals("byte") ||
                typeName.equals("java.lang.Short") ||
                typeName.equals("short") ||
                typeName.equals("java.util.Date") ||
                typeName.equals("date") ||
                typeName.equals("java.lang.Object") ||
                typeName.equals("Object") ||
                typeName.equals("object") ||
                typeName.equals("byte[]");
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
        if (javaType.equals("java.lang.Byte") || javaType.equals("byte"))
            return "xs:byte";
        if (javaType.equals("java.lang.Short") || javaType.equals("short"))
            return "xs:short";
        if (javaType.equals("java.util.Date") || javaType.equals("date"))
            return "xs:dateTime";
        if (javaType.equals("java.lang.Object") || javaType.equals("Object") || javaType.equals("object"))
            return "xs:anyType";
        if (javaType.equals("byte[]"))
            return "xs:base64Binary";
        return "xs:string";
    }

    private String getSimpleClassName(String fullClassName) {
        if (fullClassName == null) {
            return "Unknown";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * Gets the XSD type name for a class, using its destinationName from the
     * schema.
     */
    private String getDestinationName(String sourceClassName) {
        if (sourceClassName == null) {
            return "Unknown";
        }

        // Look up in the reference schema - it has all class definitions
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DOSchemaClass schemaClass = referenceSchema.findClassByName(sourceClassName);
        if (schemaClass != null) {
            return schemaClass.destinationName;
        }

        // Fallback to simple class name if not found
        return getSimpleClassName(sourceClassName);
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
