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
 * Builds XSD (XML Schema) definitions for exported XML files. Tracks classes
 * and fields discovered during export and generates a complete schema.
 */
public class XSDBuilder {
    private final Map<String, DOSchemaClass> classMap = new LinkedHashMap<>();
    private final Map<String, Map<String, DOSchemaField>> fieldsByClass = new LinkedHashMap<>();
    private final Set<String> topLevelObjects = new LinkedHashSet<>();
    private final Set<String> referencedTypes = new LinkedHashSet<>(); // Types
                                                                       // used
                                                                       // in
                                                                       // fields
    // Value mappings are written inline with fields, not as separate types

    private migration4o.database.DODatabaseContext dbContext;

    public XSDBuilder(migration4o.database.DODatabaseContext dbContext) {
        this.dbContext = dbContext;
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
                topLevelObjects.add(refClass.destinationName); // Use reference
                                                               // schema's
                                                               // destination
                                                               // name
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

        // Look up the field in reference schema to get correct export
        // properties
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
            xsdWriter.write("        <xs:element name=\"metadata\" type=\"Metadata\" minOccurs=\"0\"/>\n");
            xsdWriter.write("        <xs:element name=\"objects\">\n");
            xsdWriter.write("          <xs:complexType>\n");
            xsdWriter.write("            <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\n");
            // Sort top-level objects alphabetically (use a copy to avoid
            // modifying the original)
            List<String> sortedTopLevelObjects = new ArrayList<>(topLevelObjects);
            Collections.sort(sortedTopLevelObjects);
            for (String obj : sortedTopLevelObjects) {
                xsdWriter.write("              <xs:element ref=\"" + obj + "\"/>\n");
            }
            xsdWriter.write("            </xs:choice>\n");
            xsdWriter.write("          </xs:complexType>\n");
            xsdWriter.write("        </xs:element>\n");
            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");

            // Metadata type
            xsdWriter.write("  <xs:complexType name=\"Metadata\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            // Order and names must match StructuredWriterUtil.metadata()
            // exactly:
            // generator → provider → module → type → objects → date (optional)
            xsdWriter.write("      <xs:element name=\"generator\" type=\"xs:string\" minOccurs=\"0\"/>\n");
            xsdWriter.write("      <xs:element name=\"provider\" type=\"xs:string\" minOccurs=\"0\"/>\n");
            xsdWriter.write("      <xs:element name=\"module\" type=\"xs:string\" minOccurs=\"0\"/>\n");
            xsdWriter.write("      <xs:element name=\"type\" type=\"xs:string\" minOccurs=\"0\"/>\n");
            xsdWriter.write("      <xs:element name=\"objects\" type=\"xs:string\" minOccurs=\"0\"/>\n");
            xsdWriter.write("      <xs:element name=\"date\" type=\"xs:string\" minOccurs=\"0\"/>\n");
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
                // Always write type definition for classes that might be
                // referenced
                // Write element definition only for top-level objects
                boolean shouldWrite = isTopLevel || isReferenced;
                writeClassTypeDefinition(xsdWriter, schemaClass, isTopLevel, shouldWrite);
                // Only mark as written if we actually wrote something
                if (shouldWrite) {
                    writtenTypes.add(destName);
                }
            }

            // Also write type definitions for referenced classes not in
            // classMap
            // Keep processing newly discovered referenced types until all are
            // written
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

                    // Find the class in the schema and add its fields to
                    // fieldsByClass
                    boolean found = false;
                    int classesSearched = 0;
                    for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
                        classesSearched++;
                        if (schemaClass.destinationName.equals(referencedTypeName)) {
                            // Only export classes that have migrate=true
                            // (isExported in XML)
                            if (!schemaClass.migrate) {
                                System.err.println("WARNING: Referenced type '" + referencedTypeName + "' (source: " + schemaClass.source + ") has isExported=false");
                                writtenTypes.add(referencedTypeName); // Mark as
                                                                      // handled
                                                                      // to
                                                                      // avoid
                                                                      // infinite
                                                                      // loop
                                found = true;
                                break;
                            }

                            // Add the class to classMap and populate its fields
                            classMap.put(schemaClass.source, schemaClass);

                            // Populate fields from schema definition (including
                            // inherited)
                            fieldsByClass.put(schemaClass.source, getAllExportedFieldsIncludingAncestors(schemaClass, referenceSchema));

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
        String destClassName = schemaClass.destinationName; // Already from
                                                            // reference schema
        // Use full schema-derived field set (own + inherited) as authoritative
        // source.
        // fieldsByClass only tracks what was observed live and misses inherited
        // fields
        // and fields that are always at their default value (skipWhen=DEFAULT).
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        Map<String, DOSchemaField> fields = getAllExportedFieldsIncludingAncestors(schemaClass, referenceSchema);

        // xsdWriter.write(" <!-- " + destClassName + " -->\n");
        if (schemaClass.title != null && schemaClass.title.length() > 0) {
            xsdWriter.write("  <!-- " + schemaClass.title + " -->\n");
        }
        String classDescription = schemaClass.description != null ? schemaClass.description.trim() : "";

        // Write the complexType definition if needed (or if we need both
        // element and
        // type)
        if (writeType || (writeElement && writeType)) {
            xsdWriter.write("  <xs:complexType name=\"" + destClassName + "\">\n");
            if (!classDescription.isEmpty()) {
                xsdWriter.write("    <xs:annotation>\n");
                xsdWriter.write("      <xs:documentation xml:lang=\"fr\">" + escapeXml(classDescription) + "</xs:documentation>\n");
                xsdWriter.write("    </xs:annotation>\n");
            }
            if (fields.isEmpty()) {
                // No exported fields: use mixed content so any text/element
                // children are accepted
                // (e.g. java.util.Hashtable which may contain whitespace or
                // dynamic content).
                xsdWriter.write("    <xs:sequence>\n");
                xsdWriter.write("      <xs:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"skip\"/>\n");
                xsdWriter.write("    </xs:sequence>\n");
                xsdWriter.write("    <!-- no exported fields -->\n");
            } else {
                xsdWriter.write("    <xs:all>\n");
                List<DOSchemaField> sortedFields = new ArrayList<>(fields.values());
                sortedFields.sort((a, b) -> a.destinationName.compareTo(b.destinationName));
                for (DOSchemaField field : sortedFields) {
                    writeFieldElement(xsdWriter, field, "      ");
                }
                xsdWriter.write("    </xs:all>\n");
            }
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
                if (!classDescription.isEmpty()) {
                    xsdWriter.write("    <xs:annotation>\n");
                    xsdWriter.write("      <xs:documentation xml:lang=\"fr\">" + escapeXml(classDescription) + "</xs:documentation>\n");
                    xsdWriter.write("    </xs:annotation>\n");
                }
                xsdWriter.write("    <xs:complexType>\n");
                if (!classDescription.isEmpty()) {
                    xsdWriter.write("    <xs:annotation>\n");
                    xsdWriter.write("      <xs:documentation xml:lang=\"fr\">" + escapeXml(classDescription) + "</xs:documentation>\n");
                    xsdWriter.write("    </xs:annotation>\n");
                }
                if (fields.isEmpty()) {
                    xsdWriter.write("      <xs:sequence>\n");
                    xsdWriter.write("        <xs:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"skip\"/>\n");
                    xsdWriter.write("      </xs:sequence>\n");
                } else {
                    xsdWriter.write("      <xs:all>\n");
                    List<DOSchemaField> sortedFields = new ArrayList<>(fields.values());
                    sortedFields.sort((a, b) -> a.destinationName.compareTo(b.destinationName));
                    for (DOSchemaField field : sortedFields) {
                        writeFieldElement(xsdWriter, field, "        ");
                    }
                    xsdWriter.write("      </xs:all>\n");
                }
                xsdWriter.write("    </xs:complexType>\n");
                xsdWriter.write("  </xs:element>\n");
            }
        }

        xsdWriter.write("\n");
    }

    /**
     * Returns all exported fields for a class including fields inherited from
     * ancestor classes. Ancestors are processed root-first so a child class
     * field overrides an ancestor field with the same destinationName.
     */
    private Map<String, DOSchemaField> getAllExportedFieldsIncludingAncestors(DOSchemaClass schemaClass, DOSchema schema) {
        // Build ancestry chain from root down to this class
        List<DOSchemaClass> chain = new ArrayList<>();
        DOSchemaClass current = schemaClass;
        while (current != null) {
            chain.add(0, current); // prepend so root is first
            String parentName = current.parentClassName;
            if (parentName == null || parentName.isEmpty())
                break;
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            current = parent;
        }
        // Merge fields root-first; child fields override ancestor fields with
        // same name
        Map<String, DOSchemaField> result = new LinkedHashMap<>();
        for (DOSchemaClass cls : chain) {
            if (cls.fields != null) {
                for (DOSchemaField f : cls.fields) {
                    if (f.isExported) {
                        result.put(f.destinationName, f);
                    }
                }
            }
        }
        return result;
    }

    private void writeFieldElement(FileWriter xsdWriter, DOSchemaField field, String indent) throws IOException {
        String fieldName = field.destinationName;
        String fieldType = field.type;
        boolean isCollection = field.isCollection;

        if (fieldType == null || fieldType.isEmpty()) {
            // No type info: write a permissive anyType element so the xs:choice
            // stays non-empty.
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:anyType\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            return;
        }

        // byte[] is exported as a single base64/string value, not as an array
        // of child elements.
        if ("byte[]".equals(fieldType)) {
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:string\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            return;
        }

        // Array types (e.g. int[]) are exported just like collections:
        // a wrapper element with a size attribute and item elements using the
        // field destination name.
        boolean isArrayType = !isCollection && fieldType != null && fieldType.endsWith("[]");
        if (isCollection || isArrayType) {
            String childrenType = field.childrenType;
            if (childrenType == null || childrenType.isEmpty()) {
                // For array types, strip [] to get the component type
                childrenType = isArrayType ? fieldType.substring(0, fieldType.length() - 2) : fieldType;
            }
            String itemType;
            String itemElemName;
            if (isPrimitiveType(childrenType) || isArrayType) {
                // Primitive collections and arrays: item element uses the field
                // destination name.
                // e.g. <tableDroits
                // size="7"><tableDroits>value</tableDroits>...</tableDroits>
                itemType = getXSDType(isArrayType ? fieldType.substring(0, fieldType.length() - 2) : childrenType);
                itemElemName = fieldName;
            } else {
                DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
                DOSchemaClass childClass = referenceSchema.findClassByName(childrenType);
                if (childClass == null) {
                    System.err.println("WARNING: Collection field '" + fieldName + "' references childrenType='" + childrenType + "' which is not found in schema");
                    itemType = "xs:anyType";
                    itemElemName = fieldName;
                } else if (field.embedContents && childClass.pointsTo != null) {
                    // IDEntite with embedContents=true: XML writes the
                    // pointed-to entity class.
                    // Detected by pointsTo being set (IDEntite classes always
                    // have a pointsTo).
                    // e.g. listeIDModeleHoraire (embedContents=true,
                    // pointsTo=ModeleHoraire) → <ModeleHoraire>
                    DOSchemaClass pointsToClass = referenceSchema.findClassByName(childClass.pointsTo);
                    if (pointsToClass != null) {
                        itemElemName = pointsToClass.destinationName;
                        itemType = pointsToClass.destinationName;
                        referencedTypes.add(itemType);
                    } else {
                        itemElemName = childClass.destinationName;
                        itemType = childClass.destinationName;
                        referencedTypes.add(itemType);
                    }
                } else {
                    // Non-embedded IDEntite collection OR non-IDEntite complex:
                    // reference the declared type directly.
                    String refClassName = childClass.destinationName;
                    referencedTypes.add(refClassName);
                    itemType = refClassName;
                    itemElemName = refClassName;
                }
            }
            xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" minOccurs=\"0\">\n");
            xsdWriter.write(indent + "  <xs:complexType>\n");
            xsdWriter.write(indent + "    <xs:sequence>\n");
            xsdWriter.write(indent + "      <xs:element name=\"" + itemElemName + "\" type=\"" + itemType + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            xsdWriter.write(indent + "    </xs:sequence>\n");
            xsdWriter.write(indent + "    <xs:attribute name=\"size\" type=\"xs:int\"/>\n");
            xsdWriter.write(indent + "  </xs:complexType>\n");
            xsdWriter.write(indent + "</xs:element>\n");
        } else if (isPrimitiveType(fieldType) && !field.embedContents) {
            // Primitive field (only when NOT embedded): if it has a valueMap,
            // the exported
            // Note: embedContents=true on a "primitive" type (e.g.
            // java.util.UUID) means
            // the export serialises it as a nested complex object, not a simple
            // string value.
            if (field.valueMap != null && !field.valueMap.isEmpty()) {
                // Restrict to the enumerated mapped output values.
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
            // Check if this is an IDEntite reference (detected by pointsTo
            // being set)
            DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
            DOSchemaClass fieldClass = referenceSchema.findClassByName(fieldType);
            if (fieldClass != null && !field.embedContents && fieldClass.pointsTo != null) {
                // Non-embedded IDEntite reference: exported as a xs:long mID
                // value.
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:long\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else if (fieldClass != null && field.embedContents && fieldClass.pointsTo != null) {
                // Embedded IDEntite reference: typed by the pointed-to entity
                // class,
                // same principle as any other complex field reference.
                DOSchemaClass pointsToClass = referenceSchema.findClassByName(fieldClass.pointsTo);
                String targetClassName = pointsToClass != null ? pointsToClass.destinationName : fieldClass.destinationName;
                referencedTypes.add(targetClassName);
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + targetClassName + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else if (fieldClass != null) {
                // Any other complex field: reference the declared type
                // directly.
                String refClassName = fieldClass.destinationName;
                referencedTypes.add(refClassName);
                xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + refClassName + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                // Field type is not in the schema - check if it's a known
                // primitive/Java type
                if (isPrimitiveType(fieldType)) {
                    String xsdType = getXSDType(fieldType);
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"" + xsdType + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                } else {
                    // Unknown complex type (e.g. java.util.Hashtable not in
                    // schema):
                    // use xs:anyType to accept any content structure.
                    xsdWriter.write(indent + "<xs:element name=\"" + fieldName + "\" type=\"xs:anyType\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
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

        // Keep byte[] as base64, but map other primitive arrays to their
        // component type
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
            return "xs:string"; // DB4O data may contain non-standard boolean
                                // representations (e.g. 'INT')
        if (normalizedType.equals("java.lang.Double") || normalizedType.equals("double"))
            return "xs:double";
        if (normalizedType.equals("java.lang.Float") || normalizedType.equals("float"))
            return "xs:float";
        if (normalizedType.equals("java.lang.Byte") || normalizedType.equals("byte"))
            return "xs:byte";
        if (normalizedType.equals("java.lang.Short") || normalizedType.equals("short"))
            return "xs:short";
        if (normalizedType.equals("java.util.Date") || normalizedType.equals("date"))
            return "xs:string"; // Java Date.toString() is not ISO, keep as
                                // string
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
