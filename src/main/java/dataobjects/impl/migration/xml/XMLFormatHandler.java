package dataobjects.impl.migration.xml;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.migration.generic.*;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.util.ObjectResolverUtil;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * XML format handler that generates files following the documented structure:
 * - One XML file per module
 * - <migration> root with namespace
 * - <types> section with type definitions
 * - <modules> section with module data
 */
public class XMLFormatHandler extends HierarchicalFormatHandler {

    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";
    private static final String NAMESPACE = "http://migration4o/schema";

    // Access to engine and schema for reference resolution
    private DOEngine engine;
    private Map<String, String> classToModuleMap; // Maps full class name to module name

    // Global type collection for XSD generation
    private List<TypeInfo> allTypes = new ArrayList<>();
    private String structureDirectory;

    /**
     * Module-level context for XML export.
     */
    private static class XMLModuleContext {
        XMLStreamWriter writer;
        FileOutputStream outputStream;
        String fileName;
        List<TypeInfo> moduleTypes = new ArrayList<>();
        DOEngine engine; // Store engine reference for object resolution
        Map<String, String> classToModuleMap; // Maps full class name to module name

        void close() throws IOException {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (XMLStreamException e) {
                throw new IOException("Error closing XML writer: " + e.getMessage(), e);
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        }
    }

    /**
     * Type information for the types section.
     */
    private static class TypeInfo {
        String simpleName;
        String fullClassName;
        String moduleName;
        DOSchemaClass schemaClass; // Keep reference to schema class for field information

        TypeInfo(String simpleName, String fullClassName, String moduleName, DOSchemaClass schemaClass) {
            this.simpleName = simpleName;
            this.fullClassName = fullClassName;
            this.moduleName = moduleName;
            this.schemaClass = schemaClass;
        }
    }

    /**
     * Class-level context for XML export.
     */
    private static class XMLClassContext {
        XMLModuleContext moduleContext;
    }

    @Override
    public String getDefaultOutputDirectory() {
        return DEFAULT_OUTPUT_DIR;
    }

    @Override
    protected void initializeFormat(String outputDirectory) throws IOException {
        // Create structure directory for XSD files
        File baseDir = new File(outputDirectory).getParentFile();
        this.structureDirectory = new File(baseDir, "structure").getAbsolutePath();
        File structureDir = new File(structureDirectory);
        if (!structureDir.exists()) {
            structureDir.mkdirs();
        }

        // Clear global types list for this export
        allTypes.clear();
    }

    @Override
    public Object beginModule(ModuleExportContext context) throws IOException {
        XMLModuleContext xmlContext = new XMLModuleContext();

        try {
            // Store engine reference and build class-to-module map
            xmlContext.engine = context.getEngine();
            xmlContext.classToModuleMap = buildClassToModuleMap(context.getEngine());

            // Create output file
            xmlContext.fileName = context.getSanitizedModuleName() + ".xml";
            File outputFile = new File(outputDirectory, xmlContext.fileName);
            xmlContext.outputStream = new FileOutputStream(outputFile);

            // Create XML writer
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            xmlContext.writer = factory.createXMLStreamWriter(xmlContext.outputStream, "UTF-8");

            // Write XML declaration
            xmlContext.writer.writeStartDocument("UTF-8", "1.0");
            xmlContext.writer.writeCharacters("\n");

            // Write migration root element with namespace
            xmlContext.writer.writeStartElement("migration");
            xmlContext.writer.writeAttribute("xmlns", NAMESPACE);
            xmlContext.writer.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xmlContext.writer.writeAttribute("xsi:schemaLocation", NAMESPACE + " migration-schema.xsd");
            xmlContext.writer.writeCharacters("\n\n");

            // Collect type information from module
            collectModuleTypes(context, xmlContext);

            // Write types section
            writeTypesSection(xmlContext);

            // Write modules section start
            xmlContext.writer.writeCharacters("    ");
            xmlContext.writer.writeStartElement("modules");
            xmlContext.writer.writeCharacters("\n        ");

            // Write this module start
            xmlContext.writer.writeStartElement("module");
            xmlContext.writer.writeAttribute("name", context.getModuleName());
            xmlContext.writer.writeCharacters("\n            ");

            // Write objects container
            xmlContext.writer.writeStartElement("objects");
            xmlContext.writer.writeCharacters("\n");

            return xmlContext;

        } catch (Exception e) {
            // Clean up on error
            if (xmlContext.outputStream != null) {
                try {
                    xmlContext.outputStream.close();
                } catch (IOException ignored) {
                }
            }
            throw new IOException("Failed to begin XML module: " + e.getMessage(), e);
        }
    }

    /**
     * Collect type definitions from the module's classes.
     */
    private void collectModuleTypes(ModuleExportContext context, XMLModuleContext xmlContext) {
        if (context.getModule().getClasses() != null) {
            for (DOSchemaClass schemaClass : context.getModule().getClasses()) {
                String simpleName = schemaClass.getExportName();
                String fullClassName = schemaClass.getAbsoluteName();
                String moduleName = context.getModuleName();

                TypeInfo typeInfo = new TypeInfo(simpleName, fullClassName, moduleName, schemaClass);
                xmlContext.moduleTypes.add(typeInfo);

                // Add to global types list for XSD generation
                allTypes.add(typeInfo);
            }
        }
    }

    /**
     * Write the types section.
     */
    private void writeTypesSection(XMLModuleContext xmlContext) throws XMLStreamException {
        xmlContext.writer.writeCharacters("    ");
        xmlContext.writer.writeStartElement("types");
        xmlContext.writer.writeCharacters("\n");

        for (TypeInfo typeInfo : xmlContext.moduleTypes) {
            xmlContext.writer.writeCharacters("        ");
            xmlContext.writer.writeStartElement("type");
            xmlContext.writer.writeAttribute("name", typeInfo.simpleName);
            xmlContext.writer.writeAttribute("class", typeInfo.fullClassName);
            xmlContext.writer.writeAttribute("module", typeInfo.moduleName);
            xmlContext.writer.writeEndElement(); // type
            xmlContext.writer.writeCharacters("\n");
        }

        xmlContext.writer.writeCharacters("    ");
        xmlContext.writer.writeEndElement(); // types
        xmlContext.writer.writeCharacters("\n\n");
    }

    @Override
    public Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;
        XMLClassContext classCtx = new XMLClassContext();
        classCtx.moduleContext = moduleCtx;

        // We don't write a class element - objects go directly into the <objects>
        // container
        // with type attributes on each object

        return classCtx;
    }

    @Override
    public void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException {

        XMLClassContext classCtx = (XMLClassContext) classHandle;
        XMLStreamWriter writer = classCtx.moduleContext.writer;

        try {
            // Write object start element
            writer.writeCharacters("                ");
            writer.writeStartElement("object");
            writer.writeAttribute("type", context.getClassContext().getExportName());
            writer.writeAttribute("id", String.valueOf(context.getObjectId()));
            writer.writeCharacters("\n");

            // Write all non-empty field values
            List<FormattedValue> nonEmptyValues = getNonEmptyValues(values);
            for (FormattedValue value : nonEmptyValues) {
                writeFieldElement(writer, value, classCtx.moduleContext);
            }

            // Write object end element
            writer.writeCharacters("                ");
            writer.writeEndElement(); // object
            writer.writeCharacters("\n");

        } catch (XMLStreamException e) {
            throw new IOException("Failed to export XML object: " + e.getMessage(), e);
        }
    }

    @Override
    public void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException {
        // Nothing to do - we don't write class elements
    }

    @Override
    public void endModule(Object moduleHandle, ModuleExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;

        try {
            // Close objects
            moduleCtx.writer.writeCharacters("            ");
            moduleCtx.writer.writeEndElement(); // objects
            moduleCtx.writer.writeCharacters("\n        ");

            // Close module
            moduleCtx.writer.writeEndElement(); // module
            moduleCtx.writer.writeCharacters("\n    ");

            // Close modules
            moduleCtx.writer.writeEndElement(); // modules
            moduleCtx.writer.writeCharacters("\n");

            // Close migration root
            moduleCtx.writer.writeEndElement(); // migration
            moduleCtx.writer.writeCharacters("\n");

            moduleCtx.writer.writeEndDocument();
            moduleCtx.writer.flush();

            System.out.println("  Exported module " + context.getModuleName() + " to " + moduleCtx.fileName);

        } catch (XMLStreamException e) {
            throw new IOException("Failed to end XML module: " + e.getMessage(), e);
        } finally {
            moduleCtx.close();
        }
    }

    @Override
    public void cleanup() throws IOException {
        // Generate XSD schema file based on all collected types
        generateXSDSchema();
    }

    /**
     * Generate XSD schema file for the exported XML data.
     */
    private void generateXSDSchema() throws IOException {
        File xsdFile = new File(structureDirectory, "migration-schema.xsd");

        try (FileOutputStream fos = new FileOutputStream(xsdFile);
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8")) {

            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
            writer.write("           targetNamespace=\"" + NAMESPACE + "\"\n");
            writer.write("           xmlns:m4o=\"" + NAMESPACE + "\"\n");
            writer.write("           elementFormDefault=\"qualified\">\n\n");

            // Root element
            writer.write("    <!-- Root element: migration -->\n");
            writer.write("    <xs:element name=\"migration\">\n");
            writer.write("        <xs:complexType>\n");
            writer.write("            <xs:sequence>\n");
            writer.write("                <xs:element name=\"types\" type=\"m4o:TypesType\"/>\n");
            writer.write("                <xs:element name=\"modules\" type=\"m4o:ModulesType\"/>\n");
            writer.write("            </xs:sequence>\n");
            writer.write("        </xs:complexType>\n");
            writer.write("    </xs:element>\n\n");

            // Types section
            writer.write("    <!-- Types section -->\n");
            writer.write("    <xs:complexType name=\"TypesType\">\n");
            writer.write("        <xs:sequence>\n");
            writer.write(
                    "            <xs:element name=\"type\" type=\"m4o:TypeDefinitionType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            writer.write("        </xs:sequence>\n");
            writer.write("    </xs:complexType>\n\n");

            // Type definition
            writer.write("    <!-- Type definition -->\n");
            writer.write("    <xs:complexType name=\"TypeDefinitionType\">\n");
            writer.write("        <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("        <xs:attribute name=\"class\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("        <xs:attribute name=\"module\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("    </xs:complexType>\n\n");

            // Modules section
            writer.write("    <!-- Modules section -->\n");
            writer.write("    <xs:complexType name=\"ModulesType\">\n");
            writer.write("        <xs:sequence>\n");
            writer.write(
                    "            <xs:element name=\"module\" type=\"m4o:ModuleType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            writer.write("        </xs:sequence>\n");
            writer.write("    </xs:complexType>\n\n");

            // Module
            writer.write("    <!-- Module -->\n");
            writer.write("    <xs:complexType name=\"ModuleType\">\n");
            writer.write("        <xs:sequence>\n");
            writer.write("            <xs:element name=\"objects\" type=\"m4o:ObjectsType\"/>\n");
            writer.write("        </xs:sequence>\n");
            writer.write("        <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("    </xs:complexType>\n\n");

            // Objects container
            writer.write("    <!-- Objects container -->\n");
            writer.write("    <xs:complexType name=\"ObjectsType\">\n");
            writer.write("        <xs:sequence>\n");
            writer.write(
                    "            <xs:element name=\"object\" type=\"m4o:ObjectType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            writer.write("        </xs:sequence>\n");
            writer.write("    </xs:complexType>\n\n");

            // Object
            writer.write("    <!-- Object -->\n");
            writer.write("    <xs:complexType name=\"ObjectType\">\n");
            writer.write("        <xs:sequence>\n");
            writer.write(
                    "            <xs:element name=\"field\" type=\"m4o:FieldType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            writer.write("        </xs:sequence>\n");
            writer.write("        <xs:attribute name=\"type\" type=\"m4o:ObjectTypeEnum\" use=\"required\"/>\n");
            writer.write("        <xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("    </xs:complexType>\n\n");

            // Field type
            writer.write("    <!-- Field - can contain simple value, collection, reference, or embedded object -->\n");
            writer.write("    <xs:complexType name=\"FieldType\" mixed=\"true\">\n");
            writer.write("        <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\n");
            writer.write("            <!-- For collections: value elements or embedded objects -->\n");
            writer.write("            <xs:element name=\"value\" type=\"xs:string\"/>\n");
            writer.write("            <xs:element name=\"object\" type=\"m4o:ObjectType\"/>\n");
            writer.write("        </xs:choice>\n");
            writer.write("        <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>\n");
            writer.write("        <xs:attribute name=\"type\" type=\"m4o:FieldTypeEnum\" use=\"required\"/>\n");
            writer.write("        \n");
            writer.write("        <!-- For reference fields -->\n");
            writer.write("        <xs:attribute name=\"targetType\" type=\"m4o:ObjectTypeEnum\" use=\"optional\"/>\n");
            writer.write("        <xs:attribute name=\"targetModule\" type=\"xs:string\" use=\"optional\"/>\n");
            writer.write("        \n");
            writer.write("        <!-- For collection fields -->\n");
            writer.write("        <xs:attribute name=\"elementType\" type=\"xs:string\" use=\"optional\"/>\n");
            writer.write(
                    "        <xs:attribute name=\"elementClass\" type=\"m4o:ObjectTypeEnum\" use=\"optional\"/>\n");
            writer.write("        <xs:attribute name=\"elementModule\" type=\"xs:string\" use=\"optional\"/>\n");
            writer.write("    </xs:complexType>\n\n");

            // Field type enumeration
            writer.write("    <!-- Field type enumeration -->\n");
            writer.write("    <xs:simpleType name=\"FieldTypeEnum\">\n");
            writer.write("        <xs:restriction base=\"xs:string\">\n");
            writer.write("            <xs:enumeration value=\"string\"/>\n");
            writer.write("            <xs:enumeration value=\"integer\"/>\n");
            writer.write("            <xs:enumeration value=\"double\"/>\n");
            writer.write("            <xs:enumeration value=\"boolean\"/>\n");
            writer.write("            <xs:enumeration value=\"date\"/>\n");
            writer.write("            <xs:enumeration value=\"reference\"/>\n");
            writer.write("            <xs:enumeration value=\"embedded\"/>\n");
            writer.write("            <xs:enumeration value=\"collection\"/>\n");
            writer.write("            <xs:enumeration value=\"empty\"/>\n");
            writer.write("        </xs:restriction>\n");
            writer.write("    </xs:simpleType>\n\n");

            // Object type enumeration (all types found during export)
            writer.write("    <!-- Object type enumeration (all types in database) -->\n");
            writer.write("    <xs:simpleType name=\"ObjectTypeEnum\">\n");
            writer.write("        <xs:restriction base=\"xs:string\">\n");

            // Add all collected types
            Set<String> uniqueTypes = new TreeSet<>(); // Use TreeSet for sorted output
            for (TypeInfo type : allTypes) {
                uniqueTypes.add(type.simpleName);
            }
            for (String typeName : uniqueTypes) {
                writer.write("            <xs:enumeration value=\"" + escapeXml(typeName) + "\"/>\n");
            }

            writer.write("        </xs:restriction>\n");
            writer.write("    </xs:simpleType>\n\n");

            // Generate specific complex types for each object type with their actual fields
            writer.write("    <!-- ========================================== -->\n");
            writer.write("    <!-- Specific object type definitions with actual fields -->\n");
            writer.write("    <!-- ========================================== -->\n\n");

            for (TypeInfo type : allTypes) {
                generateObjectTypeDefinition(writer, type);
            }

            writer.write("</xs:schema>\n");

            System.out.println("Generated XSD schema: " + xsdFile.getAbsolutePath());
            System.out.println("  Total types defined: " + uniqueTypes.size());

        } catch (IOException e) {
            throw new IOException("Failed to generate XSD schema: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a specific XSD complex type definition for an object type with its
     * actual fields.
     */
    private void generateObjectTypeDefinition(java.io.OutputStreamWriter writer, TypeInfo type) throws IOException {
        if (type.schemaClass == null || type.schemaClass.getDatabaseClass() == null) {
            return;
        }

        writer.write("    <!-- " + type.simpleName + " (" + type.moduleName + ") -->\n");
        writer.write("    <xs:element name=\"" + sanitizeElementName(type.simpleName) + "\">\n");
        writer.write("        <xs:complexType>\n");
        writer.write("            <xs:sequence>\n");

        // Get all fields from the database class using the same logic as export
        List<dataobjects.api.models.DOField> allFields = getAllFieldsFromDatabaseClass(
                type.schemaClass.getDatabaseClass());
        List<dataobjects.api.models.DOField> sortedFields = dataobjects.impl.migration.generic.ExportUtils
                .sortFieldsForExport(allFields);

        // Generate field elements
        for (dataobjects.api.models.DOField field : sortedFields) {
            String cleanedFieldName = cleanFieldName(field.getName());
            String fieldTypeName = field.getTypeName();
            String xsdType = mapFieldTypeToXSDType(fieldTypeName);

            writer.write("                <xs:element name=\"" + cleanedFieldName + "\" type=\"" + xsdType
                    + "\" minOccurs=\"0\"/>\n");
        }

        writer.write("            </xs:sequence>\n");
        writer.write("            <xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>\n");
        writer.write("        </xs:complexType>\n");
        writer.write("    </xs:element>\n\n");
    }

    /**
     * Get all fields from a database class including inherited fields.
     */
    private List<dataobjects.api.models.DOField> getAllFieldsFromDatabaseClass(
            dataobjects.api.models.database.DODatabaseClass dbClass) {
        List<dataobjects.api.models.DOField> allFields = new ArrayList<>();

        if (dbClass.getFields() != null) {
            for (dataobjects.api.models.DOField field : dbClass.getFields()) {
                allFields.add(field);
            }
        }

        // Add inherited fields
        if (dbClass.getParentClass() != null) {
            allFields.addAll(getAllFieldsFromDatabaseClass(dbClass.getParentClass()));
        }

        return allFields;
    }

    /**
     * Map a Java type name to an XSD type.
     */
    private String mapFieldTypeToXSDType(String javaType) {
        if (javaType == null) {
            return "xs:string";
        }

        // Primitive types
        if (javaType.equals("java.lang.String") || javaType.equals("String")) {
            return "xs:string";
        } else if (javaType.equals("int") || javaType.equals("java.lang.Integer") || javaType.equals("Integer")) {
            return "xs:int";
        } else if (javaType.equals("long") || javaType.equals("java.lang.Long") || javaType.equals("Long")) {
            return "xs:long";
        } else if (javaType.equals("double") || javaType.equals("java.lang.Double") || javaType.equals("Double")) {
            return "xs:double";
        } else if (javaType.equals("float") || javaType.equals("java.lang.Float") || javaType.equals("Float")) {
            return "xs:float";
        } else if (javaType.equals("boolean") || javaType.equals("java.lang.Boolean") || javaType.equals("Boolean")) {
            return "xs:boolean";
        } else if (javaType.equals("java.util.Date") || javaType.equals("Date")) {
            return "xs:dateTime";
        }

        // Collections
        if (javaType.contains("Vector") || javaType.contains("ArrayList") || javaType.contains("List")
                || javaType.contains("Collection")) {
            return "xs:string"; // Collections will be comma-separated strings or have complex structure
        }

        // Default for complex objects
        return "xs:string";
    }

    /**
     * Escape XML special characters for XSD.
     */
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

    /**
     * Write a field element to XML.
     */
    private void writeFieldElement(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        writer.writeCharacters("                    ");
        writer.writeStartElement("field");

        // Write name attribute - use cleaned field name
        writer.writeAttribute("name", value.getColumnName());

        // Handle different value types
        if (value.isCollection()) {
            writeCollectionField(writer, value, moduleContext);
        } else if (value.isComplexObject()) {
            writeComplexObjectField(writer, value, moduleContext);
        } else {
            // Simple field - write type and value
            writer.writeAttribute("type", value.getType().toString().toLowerCase());
            String textContent = formatTextContent(value);
            if (!textContent.isEmpty()) {
                writer.writeCharacters(textContent);
            }
        }

        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }

    /**
     * Write a collection field with proper structure.
     */
    private void writeCollectionField(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        Collection<?> collection = value.getCollection();
        if (collection == null || collection.isEmpty()) {
            // Empty collection
            writer.writeAttribute("type", "collection");
            writer.writeAttribute("elementType", "unknown");
            return;
        }

        // Determine element type from first element
        Object firstElement = collection.iterator().next();
        String elementType;
        String elementClass = null;
        String elementModule = null;

        if (firstElement == null) {
            elementType = "unknown";
        } else if (isPrimitiveType(firstElement)) {
            elementType = getPrimitiveTypeName(firstElement);
        } else {
            // Complex object - determine if it's a reference or embedded
            String fullClassName = getFullClassName(firstElement, moduleContext.engine);
            elementClass = getSimpleClassNameFromFull(fullClassName);

            if (fullClassName.startsWith("gest.")) {
                // Reference to database object
                elementType = "reference";
                elementModule = getModuleForClass(fullClassName, moduleContext.classToModuleMap);
            } else {
                // Embedded object
                elementType = "embedded";
            }
        }

        writer.writeAttribute("type", "collection");
        writer.writeAttribute("elementType", elementType);
        if (elementClass != null) {
            writer.writeAttribute("elementClass", elementClass);
        }
        if (elementModule != null) {
            writer.writeAttribute("elementModule", elementModule);
        }
        writer.writeCharacters("\n");

        // Write collection elements
        for (Object element : collection) {
            if (element == null) {
                continue;
            }

            if (isPrimitiveType(element)) {
                // Primitive value
                writer.writeCharacters("                        ");
                writer.writeStartElement("value");
                writer.writeCharacters(formatPrimitiveValue(element));
                writer.writeEndElement();
                writer.writeCharacters("\n");
            } else {
                // Complex object - write full object with all fields (both references and
                // embedded)
                String fullClassName = getFullClassName(element, moduleContext.engine);
                String simpleClassName = getSimpleClassNameFromFull(fullClassName);
                long objectId = getObjectId(element, moduleContext.engine);

                // Write object start
                writer.writeCharacters("                        ");
                writer.writeStartElement("object");
                writer.writeAttribute("type", simpleClassName);
                writer.writeAttribute("id", String.valueOf(objectId));
                writer.writeCharacters("\n");

                // Write all fields of the object
                writeObjectFields(writer, element, moduleContext, fullClassName);

                // Write object end
                writer.writeCharacters("                        ");
                writer.writeEndElement(); // object
                writer.writeCharacters("\n");
            }
        }

        writer.writeCharacters("                    ");
    }

    /**
     * Write a complex object field (reference or embedded).
     */
    private void writeComplexObjectField(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        Object obj = value.getRawValue();
        if (obj == null) {
            writer.writeAttribute("type", "reference");
            return;
        }

        String fullClassName = getFullClassName(obj, moduleContext.engine);
        String className = getSimpleClassNameFromFull(fullClassName);

        // Check if this is a database object (starts with "gest.")
        if (fullClassName.startsWith("gest.")) {
            // This is a reference to another database object
            writer.writeAttribute("type", "reference");
            writer.writeAttribute("targetType", className);

            // Get the target module from our map
            String targetModule = getModuleForClass(fullClassName, moduleContext.classToModuleMap);
            writer.writeAttribute("targetModule", targetModule);

            // Get the object ID
            long objectId = getObjectId(obj, moduleContext.engine);
            if (objectId > 0) {
                writer.writeCharacters(String.valueOf(objectId));
            }
        } else {
            // Not a gest. object - treat as embedded
            writer.writeAttribute("type", "embedded");
            writer.writeCharacters("\n");
            writer.writeCharacters("                        ");
            writeEmbeddedObject(writer, obj, moduleContext);
            writer.writeCharacters("                    ");
        }
    }

    /**
     * Write all fields of an object (shared by both embedded objects and collection
     * elements).
     */
    private void writeObjectFields(XMLStreamWriter writer, Object obj, XMLModuleContext moduleContext,
            String fullClassName)
            throws XMLStreamException {
        long objectId = getObjectId(obj, moduleContext.engine);

        // Export the object's fields using reflection
        try {
            com.db4o.ext.ExtObjectContainer container = moduleContext.engine.getDatabase().getContainer();
            ObjectResolverUtil.activateObject(container, obj, objectId);

            // Get all fields from the object
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);

                    // Skip null, empty, or static fields
                    if (fieldValue == null || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    // Write the field
                    writeEmbeddedObjectField(writer, field.getName(), fieldValue, moduleContext);

                } catch (IllegalAccessException e) {
                    // Skip fields we can't access
                }
            }
        } catch (Exception e) {
            // If we can't export fields, write placeholder
            writer.writeCharacters("                            ");
            writer.writeStartElement("field");
            writer.writeAttribute("name", "error");
            writer.writeAttribute("type", "string");
            writer.writeCharacters("Could not export object fields");
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
    }

    /**
     * Write an embedded object with all its fields.
     */
    private void writeEmbeddedObject(XMLStreamWriter writer, Object obj, XMLModuleContext moduleContext)
            throws XMLStreamException {
        String fullClassName = getFullClassName(obj, moduleContext.engine);
        String className = getSimpleClassNameFromFull(fullClassName);
        long objectId = getObjectId(obj, moduleContext.engine);

        writer.writeStartElement("object");
        writer.writeAttribute("type", className);
        writer.writeAttribute("id", String.valueOf(objectId));
        writer.writeCharacters("\n");

        // Write all fields
        writeObjectFields(writer, obj, moduleContext, fullClassName);

        writer.writeCharacters("                        ");
        writer.writeEndElement(); // object
        writer.writeCharacters("\n");
    }

    /**
     * Write a field for an embedded object.
     */
    private void writeEmbeddedObjectField(XMLStreamWriter writer, String fieldName, Object fieldValue,
            XMLModuleContext moduleContext) throws XMLStreamException {
        writer.writeCharacters("                            ");
        writer.writeStartElement("field");

        // Clean the field name (remove 'm' prefix, etc.)
        String cleanedName = cleanFieldName(fieldName);
        writer.writeAttribute("name", cleanedName);

        // Determine field type and write value
        if (fieldValue instanceof String) {
            writer.writeAttribute("type", "string");
            writer.writeCharacters(escapeText((String) fieldValue));
        } else if (fieldValue instanceof Integer || fieldValue instanceof Long) {
            writer.writeAttribute("type", "integer");
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Double || fieldValue instanceof Float) {
            writer.writeAttribute("type", "double");
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Boolean) {
            writer.writeAttribute("type", "boolean");
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Date) {
            writer.writeAttribute("type", "date");
            writer.writeCharacters(formatPrimitiveValue(fieldValue));
        } else {
            // Complex type - just write string representation for now
            writer.writeAttribute("type", "string");
            writer.writeCharacters(escapeText(String.valueOf(fieldValue)));
        }

        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }

    /**
     * Clean field name (remove 'm' prefix, handle ID fields).
     */
    private String cleanFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        // Handle mID and mIDXxx pattern - lowercase entire ID part
        if (fieldName.startsWith("mID")) {
            if (fieldName.length() == 3) {
                return "id"; // mID -> id
            }
            // mIDSSI -> idssi, mIDDossPrev -> idDossPrev
            String rest = fieldName.substring(3);
            if (rest.length() > 0 && Character.isUpperCase(rest.charAt(0))) {
                return "id" + rest.toLowerCase();
            }
        }

        // Handle IDXxx at start (not preceded by m)
        if (fieldName.startsWith("ID") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2))) {
            String rest = fieldName.substring(2);
            return "id" + Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
        }

        // Handle mXxx pattern - remove m and lowercase first letter
        if (fieldName.startsWith("m") && fieldName.length() > 1 && Character.isUpperCase(fieldName.charAt(1))) {
            return Character.toLowerCase(fieldName.charAt(1)) + fieldName.substring(2);
        }

        // No m prefix - just lowercase first letter
        if (Character.isUpperCase(fieldName.charAt(0))) {
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }

        return fieldName;
    }

    /**
     * Check if an object is a primitive type.
     */
    private boolean isPrimitiveType(Object obj) {
        return obj instanceof String ||
                obj instanceof Number ||
                obj instanceof Boolean ||
                obj instanceof Date ||
                obj instanceof Character;
    }

    /**
     * Get the type name for a primitive object.
     */
    private String getPrimitiveTypeName(Object obj) {
        if (obj instanceof String)
            return "string";
        if (obj instanceof Integer || obj instanceof Long)
            return "integer";
        if (obj instanceof Double || obj instanceof Float)
            return "double";
        if (obj instanceof Boolean)
            return "boolean";
        if (obj instanceof Date)
            return "date";
        return "string";
    }

    /**
     * Format a primitive value to string.
     */
    private String formatPrimitiveValue(Object obj) {
        if (obj instanceof Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) obj);
        }
        return String.valueOf(obj);
    }

    /**
     * Get the simple class name from a full class name.
     * Uses db4o's StoredClass to get the actual database class name, not the proxy
     * class.
     */
    private String getSimpleClassName(Object obj) {
        String fullName = obj.getClass().getName();
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * Get the actual full class name using ObjectResolverUtil.
     */
    private String getFullClassName(Object obj, DOEngine engine) {
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        return ObjectResolverUtil.getObjectClassName(container, obj);
    }

    /**
     * Get the simple class name from the full stored class name.
     */
    private String getSimpleClassNameFromFull(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    /**
     * Build a map from full class names to module names.
     */
    private Map<String, String> buildClassToModuleMap(DOEngine engine) {
        Map<String, String> map = new HashMap<>();

        for (DOSchemaModule module : engine.getSchema().getModules()) {
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    map.put(schemaClass.getAbsoluteName(), module.getName());
                }
            }
        }

        return map;
    }

    /**
     * Get the module name for a given class name.
     */
    private String getModuleForClass(String fullClassName, Map<String, String> classToModuleMap) {
        return classToModuleMap.getOrDefault(fullClassName, "Unknown");
    }

    /**
     * Get the object ID from a database object.
     */
    private long getObjectId(Object obj, DOEngine engine) {
        if (obj == null) {
            return -1;
        }

        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        return container.getID(obj);
    }
}