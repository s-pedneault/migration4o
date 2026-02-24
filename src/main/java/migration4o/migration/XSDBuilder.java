package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import migration4o.util.TypeUtil;

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
    // Value mappings are written inline with fields, not as separate types

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
            fieldsByClass.computeIfAbsent(refClass.source, k -> new LinkedHashMap<>()).put(refField.destinationName, refField);

            // Value mappings will be written inline with fields
        }
    }

    public void writeXSD(String xsdPath) throws IOException {
        try (FileWriter xsdWriter = new FileWriter(xsdPath)) {
            xsdWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xsdWriter.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n");

            // Global annotations
            xsdWriter.write("  <xs:annotation>\n");
            xsdWriter.write("    <xs:appinfo>Migration4o - par Gestion Technologies</xs:appinfo>\n");
            xsdWriter.write("  </xs:annotation>\n\n");

            // Root export element
            xsdWriter.write("  <xs:element name=\"export\">\n");
            xsdWriter.write("    <xs:complexType>\n");
            xsdWriter.write("      <xs:sequence>\n");
            xsdWriter.write("        <xs:element name=\"metadata\" type=\"Metadata\"/>\n");
            xsdWriter.write("        <xs:element name=\"objects\">\n");
            xsdWriter.write("          <xs:complexType>\n");
            xsdWriter.write("            <xs:sequence>\n");
            // Sort top-level objects alphabetically (use a copy to avoid modifying the
            // original)
            List<String> sortedTopLevelObjects = new ArrayList<>(topLevelObjects);
            Collections.sort(sortedTopLevelObjects);
            for (String obj : sortedTopLevelObjects) {
                xsdWriter.write("              <xs:element ref=\"" + obj + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            }
            xsdWriter.write("            </xs:sequence>\n");
            xsdWriter.write("          </xs:complexType>\n");
            xsdWriter.write("        </xs:element>\n");
            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");

            // Metadata type
            xsdWriter.write("  <xs:complexType name=\"Metadata\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            xsdWriter.write("      <xs:element name=\"generator\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"provider\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"exportDate\" type=\"xs:date\"/>\n");
            xsdWriter.write("      <xs:element name=\"module\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"type\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"objects\" type=\"xs:int\"/>\n");
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");

            // Write type declarations based on usage:
            // - Element declaration if class appears as top-level object
            // - Type declaration if class is referenced as a field type
            // Create a snapshot to avoid ConcurrentModificationException
            List<DOSchemaClass> classesToWrite = new ArrayList<>(classMap.values());
            // Sort classes alphabetically by destination name
            classesToWrite.sort((c1, c2) -> c1.destinationName.compareTo(c2.destinationName));
            Set<String> writtenTypes = new LinkedHashSet<>();

            for (DOSchemaClass schemaClass : classesToWrite) {
                String destName = schemaClass.destinationName;
                boolean isTopLevel = topLevelObjects.contains(destName);
                boolean isReferenced = referencedTypes.contains(destName);
                // Always write type definition for classes that might be referenced
                // Write element definition only for top-level objects
                boolean shouldWrite = isTopLevel || isReferenced;
                writeClassTypeDefinition(xsdWriter, schemaClass, isTopLevel, shouldWrite);
                // Only mark as written if we actually wrote something
                if (shouldWrite) {
                    writtenTypes.add(destName);
                }
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
                    boolean found = false;
                    int classesSearched = 0;
                    for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                        classesSearched++;
                        if (schemaClass.destinationName.equals(referencedTypeName)) {
                            // Only export classes that have migrate=true (isExported in XML)
                            if (!schemaClass.migrate) {
                                System.err.println("WARNING: Referenced type '" + referencedTypeName + "' (source: " + schemaClass.source + ") has isExported=false");
                                writtenTypes.add(referencedTypeName); // Mark as handled to avoid infinite loop
                                found = true;
                                break;
                            }

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
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.err.println("ERROR: Referenced type '" + referencedTypeName + "' not found in reference schema. XSD validation will fail.");
                    }
                }
            }

            // Check for any referenced types that were never found and written
            Set<String> missingTypes = new LinkedHashSet<>(referencedTypes);
            missingTypes.removeAll(writtenTypes);
            if (!missingTypes.isEmpty()) {
                System.err.println("\nWARNING: The following types are referenced but not defined in XSD:");
                for (String missingType : missingTypes) {
                    System.err.println("  - " + missingType);
                }
                System.err.println("These types should be added to reference-schema.xml with isExported=\"true\"");
                System.err.println("or the fields referencing them should have embedContents=\"false\" if they are ID references.\n");
            }

            xsdWriter.write("</xs:schema>\n");
        }
    }

    private void writeClassTypeDefinition(FileWriter xsdWriter, DOSchemaClass schemaClass, boolean writeElement, boolean writeType) throws IOException {
        if (!writeElement && !writeType) {
            return; // Class not used anywhere
        }

        String className = schemaClass.source;
        String destClassName = schemaClass.destinationName; // Already from reference schema
        Map<String, DOSchemaField> fields = fieldsByClass.getOrDefault(className, new LinkedHashMap<>());

        xsdWriter.write("  <!-- " + destClassName + " -->\n");
        if (schemaClass.title != null && schemaClass.title.length() > 0) {
            xsdWriter.write("  <!-- " + schemaClass.title + " -->\n");
        }
        if (schemaClass.description != null && schemaClass.description.length() > 0) {
            xsdWriter.write("  <xs:documentation xml:lang=\"fr\">" + schemaClass.description + "</xs:documentation>\n");
        }

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

                if (childClass == null) {
                    System.err.println("WARNING: Collection field '" + fieldName + "' references childrenType='" + childrenType + "' which is not found in schema");
                    itemType = "xs:anyType"; // Fallback
                } else if (childClass.isIDEntite(databaseSchema) && !field.embedContents) {
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
            xsdWriter.write(indent + "      <xs:element name=\"item\" type=\"" + itemType + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            xsdWriter.write(indent + "    </xs:sequence>\n");
            xsdWriter.write(indent + "    <xs:attribute name=\"size\" type=\"xs:int\"/>\n");
            xsdWriter.write(indent + "  </xs:complexType>\n");
            xsdWriter.write(indent + "</xs:element>\n");
        } else if (isPrimitiveType(fieldType)) {
            // If field has a valueMap, write inline restriction
            if (field.valueMap != null && !field.valueMap.isEmpty()) {
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
                xsdWriter.write(indent + "  <xs:simpleType>\n");
                xsdWriter.write(indent + "    <xs:restriction base=\"xs:string\">\n");
                for (String mappedValue : field.valueMap.values()) {
                    xsdWriter.write(indent + "      <xs:enumeration value=\"" + escapeXml(mappedValue) + "\"/>\n");
                }
                xsdWriter.write(indent + "    </xs:restriction>\n");
                xsdWriter.write(indent + "  </xs:simpleType>\n");
                xsdWriter.write(indent + "</xs:element>\n");
            } else {
                String xsdType = getXSDType(fieldType);
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            }
        } else {
            // Check if this is a non-embedded IDEntite reference
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            DOSchema databaseSchema = DODatabaseService.getInstance().getDatabaseSchema();
            DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);
            if (fieldClass != null && fieldClass.isIDEntite(databaseSchema) && !field.embedContents) {
                // Non-embedded IDEntite references are exported as simple long values
                // If field has a valueMap, write inline restriction
                if (field.valueMap != null && !field.valueMap.isEmpty()) {
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\" maxOccurs=\"1\">\n");
                    xsdWriter.write(indent + "  <xs:simpleType>\n");
                    xsdWriter.write(indent + "    <xs:restriction base=\"xs:string\">\n");
                    for (String mappedValue : field.valueMap.values()) {
                        xsdWriter.write(indent + "      <xs:enumeration value=\"" + escapeXml(mappedValue) + "\"/>\n");
                    }
                    xsdWriter.write(indent + "    </xs:restriction>\n");
                    xsdWriter.write(indent + "  </xs:simpleType>\n");
                    xsdWriter.write(indent + "</xs:element>\n");
                } else {
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:long\" " + "minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                }
            } else if (fieldClass != null) {
                // Field is a schema class - use its destinationName
                String refClassName = fieldClass.destinationName;
                referencedTypes.add(refClassName); // Track that this type is referenced
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + refClassName + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                // Field type is not in the schema - check if it's a known primitive/Java type
                if (isPrimitiveType(fieldType)) {
                    String xsdType = getXSDType(fieldType);
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                } else {
                    // Unknown type - use simple class name as fallback
                    String refClassName = getSimpleClassName(fieldType);
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + refClassName + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                }
            }
        }
    }

    private boolean isPrimitiveType(String typeName) {
        return TypeUtil.isPrimitiveType(typeName) || typeName.equals("java.lang.Class") || typeName.equals("Class");
    }

    private String getXSDType(String javaType) {
        if (javaType == null || javaType.isEmpty()) {
            return "xs:string";
        }

        String normalizedType = javaType;
        boolean isArrayType = normalizedType.endsWith("[]");

        // Keep byte[] as base64, but map other primitive arrays to their component type
        if (isArrayType && !normalizedType.equals("byte[]")) {
            normalizedType = normalizedType.replaceAll("\\[\\]", "");
        }

        if (normalizedType.equals("java.lang.String") || normalizedType.equals("string"))
            return "xs:string";
        if (normalizedType.equals("java.lang.Integer") || normalizedType.equals("int"))
            return "xs:int";
        if (normalizedType.equals("java.lang.Long") || normalizedType.equals("long"))
            return "xs:long";
        if (normalizedType.equals("java.lang.Boolean") || normalizedType.equals("boolean"))
            return "xs:boolean";
        if (normalizedType.equals("java.lang.Double") || normalizedType.equals("double"))
            return "xs:double";
        if (normalizedType.equals("java.lang.Float") || normalizedType.equals("float"))
            return "xs:float";
        if (normalizedType.equals("java.lang.Byte") || normalizedType.equals("byte"))
            return "xs:byte";
        if (normalizedType.equals("java.lang.Short") || normalizedType.equals("short"))
            return "xs:short";
        if (normalizedType.equals("java.util.Date") || normalizedType.equals("date"))
            return "xs:dateTime";
        if (normalizedType.equals("java.lang.Object") || normalizedType.equals("Object") || normalizedType.equals("object"))
            return "xs:anyType";
        if (normalizedType.equals("java.lang.Class") || normalizedType.equals("Class"))
            return "xs:string";
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

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
