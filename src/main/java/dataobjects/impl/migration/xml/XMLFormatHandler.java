package dataobjects.impl.migration.xml;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.migration.generic.*;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.impl.migration.generic.ExportUtils;
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
        String moduleName; // Name of the module being exported
        List<TypeInfo> moduleTypes = new ArrayList<>();
        DOEngine engine; // Store engine reference for object resolution
        Map<String, String> classToModuleMap; // Maps full class name to module name
        Set<Long> exportedObjectIds = new HashSet<>(); // IDs of top-level exported objects (not embedded)
        int indentLevel = 0; // Current indentation level for proper XML formatting

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

        /**
         * Get indentation string for current level.
         */
        String indent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < indentLevel; i++) {
                sb.append("    "); // 4 spaces per level
            }
            return sb.toString();
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
            xmlContext.moduleName = context.getModuleName(); // Store module name for reference tracking

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

            // Collect type information from module (needed for XSD generation)
            collectModuleTypes(context, xmlContext);

            // V2: No types section - go directly to modules
            // Write modules section start
            xmlContext.writer.writeCharacters("    ");
            xmlContext.writer.writeStartElement("modules");
            xmlContext.writer.writeCharacters("\n        ");

            // Write this module start
            xmlContext.writer.writeStartElement("module");
            xmlContext.writer.writeAttribute("name", context.getModuleName());
            xmlContext.writer.writeCharacters("\n");

            // No <objects> wrapper - collections will be written per class type

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
        // Skip type collection for synthetic modules (e.g., General) that have no DOSchemaModule
        if (context.getModule() == null) {
            return;
        }
        
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

    @Override
    public Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;
        XMLClassContext classCtx = new XMLClassContext();
        classCtx.moduleContext = moduleCtx;

        try {
            // Write collection element with type attribute
            moduleCtx.writer.writeCharacters("            ");
            moduleCtx.writer.writeStartElement("collection");
            moduleCtx.writer.writeAttribute("type", context.getExportName());
            moduleCtx.writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new IOException("Failed to begin class collection: " + e.getMessage(), e);
        }

        return classCtx;
    }

    @Override
    public void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException {

        XMLClassContext classCtx = (XMLClassContext) classHandle;
        XMLStreamWriter writer = classCtx.moduleContext.writer;

        try {
            // Track this object ID as a top-level exported object (not embedded)
            long objectId = context.getObjectId();
            classCtx.moduleContext.exportedObjectIds.add(objectId);

            // Record this export in the tracker using strongly-typed DODatabaseClass
            if (tracker != null) {
                String moduleName = context.getClassContext().getModuleContext().getModule() != null
                        ? context.getClassContext().getModuleContext().getModule().getName()
                        : "General";
                dataobjects.api.models.database.DODatabaseClass databaseClass = context.getClassContext().getDatabaseClass();
                tracker.recordExportedObject(objectId, moduleName, databaseClass);
            }

            // Find the mID field value to use as the business ID in the XML
            String businessId = String.valueOf(objectId); // Default to db4o ID
            for (FormattedValue value : values) {
                if (value.isIDField() && value.getRawValue() != null) {
                    // Found the mID field - extract its numeric value
                    Object idValue = value.getRawValue();
                    if (idValue instanceof Number) {
                        businessId = String.valueOf(((Number) idValue).longValue());
                    } else {
                        businessId = String.valueOf(idValue);
                    }
                    break;
                }
            }

            // Set indentation level for top-level objects
            classCtx.moduleContext.indentLevel = 4; // Starting at 4 levels deep (inside <module><objects>)

            // Write object-specific element (v2 format: <PersonneRess> instead of <object
            // type="PersonneRess">)
            writer.writeCharacters(classCtx.moduleContext.indent());
            writer.writeStartElement(context.getClassContext().getExportName());
            writer.writeAttribute("id", businessId);
            writer.writeCharacters("\n");

            // Write all non-empty field values
            classCtx.moduleContext.indentLevel++; // Fields are one level deeper
            List<FormattedValue> nonEmptyValues = getNonEmptyValues(values);
            for (FormattedValue value : nonEmptyValues) {
                writeFieldElement(writer, value, classCtx.moduleContext);
            }
            classCtx.moduleContext.indentLevel--; // Back to object level

            // Write object end element
            writer.writeCharacters(classCtx.moduleContext.indent());
            writer.writeEndElement(); // object-specific element (e.g., </PersonneRess>)
            writer.writeCharacters("\n");

        } catch (XMLStreamException e) {
            throw new IOException("Failed to export XML object: " + e.getMessage(), e);
        }
    }

    @Override
    public void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException {
        XMLClassContext classCtx = (XMLClassContext) classHandle;

        try {
            // Close collection element
            classCtx.moduleContext.writer.writeCharacters("            ");
            classCtx.moduleContext.writer.writeEndElement(); // collection
            classCtx.moduleContext.writer.writeCharacters("\n");
        } catch (XMLStreamException e) {
            throw new IOException("Failed to end class collection: " + e.getMessage(), e);
        }
    }

    @Override
    public void endModule(Object moduleHandle, ModuleExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;

        try {
            // Close module (no <objects> wrapper anymore - collections closed per class)
            moduleCtx.writer.writeCharacters("        ");
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
    /**
     * Generate XSD schema for v2 format with object-specific complex types,
     * field-specific elements in camelCase, and reference elements with id
     * attributes.
     */
    private void generateXSDSchema() throws IOException {
        File xsdFile = new File(structureDirectory, "migration-schema.xsd");

        try (FileOutputStream fos = new FileOutputStream(xsdFile);
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8")) {

            // Write XSD header
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
            writer.write("           targetNamespace=\"" + NAMESPACE + "\"\n");
            writer.write("           xmlns:m4o=\"" + NAMESPACE + "\"\n");
            writer.write("           elementFormDefault=\"qualified\">\n\n");

            // Root element - v2 format (no types section)
            writer.write("    <!-- Root element: migration (v2 format) -->\n");
            writer.write("    <xs:element name=\"migration\">\n");
            writer.write("        <xs:complexType>\n");
            writer.write("            <xs:sequence>\n");
            writer.write("                <xs:element name=\"modules\" type=\"m4o:ModulesType\"/>\n");
            writer.write("            </xs:sequence>\n");
            writer.write("        </xs:complexType>\n");
            writer.write("    </xs:element>\n\n");

            // Modules container
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

            // Objects container - v2 uses xs:choice to allow any object type
            writer.write("    <!-- Objects container - allows any object type -->\n");
            writer.write("    <xs:complexType name=\"ObjectsType\">\n");
            writer.write("        <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\n");

            // Add reference to each object type
            Set<String> sortedTypes = new TreeSet<>();
            for (TypeInfo type : allTypes) {
                sortedTypes.add(type.simpleName);
            }
            for (String typeName : sortedTypes) {
                writer.write("            <xs:element ref=\"m4o:" + typeName + "\"/>\n");
            }

            writer.write("        </xs:choice>\n");
            writer.write("    </xs:complexType>\n\n");

            // Generate object-specific complex type definitions
            writer.write("    <!-- ========================================== -->\n");
            writer.write("    <!-- Object Type Definitions (PascalCase)      -->\n");
            writer.write("    <!-- ========================================== -->\n\n");

            for (TypeInfo type : allTypes) {
                generateObjectTypeDefinitionV2(writer, type);
            }

            // Generate reference element types (e.g., SpecialiteRef, VilleGeoRef)
            writer.write("    <!-- ========================================== -->\n");
            writer.write("    <!-- Reference Element Types (PascalCase+Ref)  -->\n");
            writer.write("    <!-- ========================================== -->\n\n");

            for (String typeName : sortedTypes) {
                writer.write("    <!-- Reference to " + typeName + " object -->\n");
                writer.write("    <xs:element name=\"" + typeName + "Ref\">\n");
                writer.write("        <xs:complexType>\n");
                writer.write("            <xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>\n");
                writer.write("        </xs:complexType>\n");
                writer.write("    </xs:element>\n\n");
            }

            writer.write("</xs:schema>\n");

            System.out.println("Generated v2 XSD schema: " + xsdFile.getAbsolutePath());
            System.out.println("  Total object types defined: " + sortedTypes.size());
            System.out.println("  Total reference types defined: " + sortedTypes.size());

        } catch (IOException e) {
            throw new IOException("Failed to generate XSD schema: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a specific XSD complex type definition for an object type with its
     * actual fields in v2 format (field-specific elements in camelCase).
     */
    private void generateObjectTypeDefinitionV2(java.io.OutputStreamWriter writer, TypeInfo type) throws IOException {
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

        // Generate field-specific elements with proper XSD types
        for (dataobjects.api.models.DOField field : sortedFields) {
            String xmlFieldName = toXmlFieldName(field.getName());
            String fieldTypeName = field.getTypeName();

            if (field.isArray()) {
                // Collection field: wrapper element containing sequence of items
                String contentTypeName = field.getContentTypeName();
                boolean isReferenceCollection = isReferenceType(contentTypeName);

                writer.write("                <xs:element name=\"" + xmlFieldName + "\" minOccurs=\"0\">\n");
                writer.write("                    <xs:complexType>\n");
                writer.write("                        <xs:sequence>\n");

                if (isReferenceCollection) {
                    // Collection of references: <TypeRef id="..."/> elements
                    String refElementName = getSimpleClassNameFromTypeName(contentTypeName) + "Ref";
                    writer.write("                            <xs:element ref=\"" + refElementName
                            + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                } else if (isPrimitiveTypeName(contentTypeName)) {
                    // Collection of primitives: <item> elements with primitive type
                    String xsdType = mapJavaTypeToXSD(contentTypeName);
                    writer.write("                            <xs:element name=\"item\" type=\"" + xsdType
                            + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                } else {
                    // Collection of embedded objects: reference to object element
                    String embeddedTypeName = getSimpleClassNameFromTypeName(contentTypeName);
                    writer.write("                            <xs:element ref=\"" + embeddedTypeName
                            + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                }

                writer.write("                        </xs:sequence>\n");
                writer.write("                    </xs:complexType>\n");
                writer.write("                </xs:element>\n");

            } else if (isReferenceType(fieldTypeName)) {
                // Reference field: string containing ID
                writer.write("                <xs:element name=\"" + xmlFieldName
                        + "\" type=\"xs:string\" minOccurs=\"0\"/>\n");

            } else if (field.isPrimitive() || isPrimitiveTypeName(fieldTypeName)) {
                // Primitive field: map to appropriate XSD type
                String xsdType = mapJavaTypeToXSD(fieldTypeName);
                writer.write("                <xs:element name=\"" + xmlFieldName + "\" type=\"" + xsdType
                        + "\" minOccurs=\"0\"/>\n");

            } else {
                // Embedded object field: reference to embedded object element
                String embeddedTypeName = getSimpleClassNameFromTypeName(fieldTypeName);
                writer.write("                <xs:element ref=\"" + embeddedTypeName + "\" minOccurs=\"0\"/>\n");
            }
        }

        writer.write("            </xs:sequence>\n");
        writer.write("            <xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>\n");
        writer.write("        </xs:complexType>\n");
        writer.write("    </xs:element>\n\n");
    }

    /**
     * Check if a type name represents a reference (ID) type.
     */
    private boolean isReferenceType(String typeName) {
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Check if a type name represents a primitive type.
     */
    private boolean isPrimitiveTypeName(String typeName) {
        if (typeName == null) {
            return false;
        }
        return typeName.equals("java.lang.String") ||
                typeName.equals("java.lang.Integer") || typeName.equals("int") ||
                typeName.equals("java.lang.Long") || typeName.equals("long") ||
                typeName.equals("java.lang.Double") || typeName.equals("double") ||
                typeName.equals("java.lang.Float") || typeName.equals("float") ||
                typeName.equals("java.lang.Boolean") || typeName.equals("boolean") ||
                typeName.equals("java.util.Date");
    }

    /**
     * Map Java type name to XSD type.
     */
    private String mapJavaTypeToXSD(String javaType) {
        if (javaType == null) {
            return "xs:string";
        }

        switch (javaType) {
            case "java.lang.String":
                return "xs:string";
            case "java.lang.Integer":
            case "int":
                return "xs:int";
            case "java.lang.Long":
            case "long":
                return "xs:long";
            case "java.lang.Double":
            case "double":
                return "xs:double";
            case "java.lang.Float":
            case "float":
                return "xs:float";
            case "java.lang.Boolean":
            case "boolean":
                return "xs:boolean";
            case "java.util.Date":
                return "xs:dateTime";
            default:
                // For unknown types, default to string
                return "xs:string";
        }
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
     * Write a field element to XML (v2 format: field-specific elements).
     */
    private void writeFieldElement(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        writer.writeCharacters(moduleContext.indent());

        // V2: Write field-specific element (e.g., <nom> instead of <field name="nom">)
        // Convert Java field name to XML field name (camelCase)
        String fieldName = toXmlFieldName(value.getColumnName());
        writer.writeStartElement(fieldName);

        // Handle different value types - check rawValue to properly detect complex
        // objects
        Object rawValue = value.getRawValue();

        if (value.isCollection()) {
            // Collection: write wrapper with children
            writeCollectionField(writer, value, moduleContext);
        } else if (rawValue != null && !isPrimitiveType(rawValue)) {
            // Complex object: reference ID or embedded object (check rawValue, not type)
            writeComplexObjectField(writer, value, moduleContext);
        } else {
            // Simple field: write text content directly
            String textContent = formatTextContent(value);
            if (!textContent.isEmpty()) {
                writer.writeCharacters(textContent);
            }
        }

        writer.writeEndElement(); // field-specific element (e.g., </nom>)
        writer.writeCharacters("\n");
    }

    /**
     * Write collection children (v2 format: wrapper element already written by
     * caller).
     * Called when we're already inside the collection wrapper element (e.g.,
     * <vectSpecialite>).
     */
    private void writeCollectionField(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        Collection<?> collection = value.getCollection();
        if (collection == null || collection.isEmpty()) {
            // Empty collection - wrapper element is empty
            return;
        }

        // Determine element type from first element
        Object firstElement = collection.iterator().next();
        if (firstElement == null) {
            return; // Skip null elements
        }

        writer.writeCharacters("\n");
        moduleContext.indentLevel++; // Collection items are one level deeper

        if (isPrimitiveType(firstElement)) {
            // Collection of primitives - write with generic element name
            String elementName = getPrimitiveCollectionElementName(value.getColumnName());
            for (Object element : collection) {
                if (element == null)
                    continue;
                writer.writeCharacters(moduleContext.indent());
                writer.writeStartElement(elementName);
                writer.writeCharacters(formatPrimitiveValue(element));
                writer.writeEndElement();
                writer.writeCharacters("\n");
            }
        } else {
            // Complex objects - check if references or embedded
            String fullClassName = getFullClassName(firstElement, moduleContext.engine);

            if (fullClassName.startsWith("gest.")) {
                // Collection of references - use {type}Ref pattern with id attribute
                String elementClass = getSimpleClassNameFromFull(fullClassName);
                String refElementName = elementClass + "Ref";

                // Look up the database class once for this collection (all elements have same type)
                dataobjects.api.models.database.DODatabaseClass dbClass = findDatabaseClass(fullClassName, moduleContext.engine);

                for (Object element : collection) {
                    if (element == null)
                        continue;
                    writer.writeCharacters(moduleContext.indent());
                    writer.writeStartElement(refElementName);
                    long objectId = getObjectId(element, moduleContext.engine);
                    writer.writeAttribute("id", String.valueOf(objectId));

                    // Track this reference using strongly-typed DODatabaseClass
                    if (tracker != null && dbClass != null) {
                        tracker.recordReference(objectId, moduleContext.moduleName, dbClass, element);

                        // Add module attribute for cross-module references
                        String targetModule = tracker.getTargetModule(objectId, moduleContext.moduleName);
                        if (targetModule != null) {
                            writer.writeAttribute("module", targetModule);
                        }
                    }

                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                }
            } else {
                // Collection of embedded objects - write full objects
                for (Object element : collection) {
                    if (element == null)
                        continue;
                    writeEmbeddedObject(writer, element, moduleContext);
                }
            }
        }

        moduleContext.indentLevel--; // Back to field level
        writer.writeCharacters(moduleContext.indent());
    }

    /**
     * Get element name for primitive collection items.
     * Tries to singularize the collection name or use a generic name.
     */
    private String getPrimitiveCollectionElementName(String collectionName) {
        // Simple singularization: remove common suffixes
        if (collectionName.startsWith("vect")) {
            // vectIDSousSSI -> sousSSI
            String rest = collectionName.substring(4);
            if (rest.startsWith("ID")) {
                rest = "id" + rest.substring(2, 3).toLowerCase() + rest.substring(3);
            }
            return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
        }
        // Default: use "item"
        return "item";
    }

    /**
     * Write a complex object field (reference or embedded) - v2 format.
     * For top-level exported objects (tracked in exportedObjectIds), writes an ID
     * reference.
     * For embedded objects (composition relationships), writes the full nested
     * object structure.
     */
    private void writeComplexObjectField(XMLStreamWriter writer, FormattedValue value, XMLModuleContext moduleContext)
            throws XMLStreamException {
        Object obj = value.getRawValue();
        if (obj == null) {
            // Null reference - write nothing (element is empty)
            return;
        }

        // Check if this object is a top-level exported object
        long objectId = getObjectId(obj, moduleContext.engine);
        if (objectId > 0 && moduleContext.exportedObjectIds.contains(objectId)) {
            // Top-level object: write TypeRef element with id attribute (same as collection
            // references)
            String fullClassName = getFullClassName(obj, moduleContext.engine);
            String elementClass = getSimpleClassNameFromFull(fullClassName);
            String refElementName = elementClass + "Ref";

            writer.writeCharacters("\n");
            moduleContext.indentLevel++; // References are one level deeper
            writer.writeCharacters(moduleContext.indent());
            writer.writeStartElement(refElementName);
            writer.writeAttribute("id", String.valueOf(objectId));

            // Track this reference using strongly-typed DODatabaseClass
            dataobjects.api.models.database.DODatabaseClass dbClass = findDatabaseClass(fullClassName, moduleContext.engine);
            if (tracker != null && dbClass != null) {
                tracker.recordReference(objectId, moduleContext.moduleName, dbClass, obj);

                // Add module attribute for cross-module references
                String targetModule = tracker.getTargetModule(objectId, moduleContext.moduleName);
                if (targetModule != null) {
                    writer.writeAttribute("module", targetModule);
                }
            }

            writer.writeEndElement();
            writer.writeCharacters("\n");
            moduleContext.indentLevel--; // Back to field level
            writer.writeCharacters(moduleContext.indent());
        } else {
            // Embedded object (composition): write full nested structure
            writer.writeCharacters("\n");
            moduleContext.indentLevel++; // Embedded object is one level deeper
            writeEmbeddedObject(writer, obj, moduleContext);
            moduleContext.indentLevel--; // Back to field level
            writer.writeCharacters(moduleContext.indent());
        }
    }

    /**
     * Write all fields of an object (shared by both embedded objects and collection
     * elements).
     * Uses DOEngine's schema and ObjectResolverUtil for proper field traversal.
     */
    private void writeObjectFields(XMLStreamWriter writer, Object obj, XMLModuleContext moduleContext,
            String fullClassName)
            throws XMLStreamException {
        long objectId = getObjectId(obj, moduleContext.engine);

        // Get the class definition from schema or database
        DOClass doClass = ObjectResolverUtil.findClassDefinition(fullClassName,
                moduleContext.engine.getSchema(),
                moduleContext.engine.getDatabase());

        if (doClass == null) {
            // Class not found in schema, skip field export
            return;
        }

        // Get all DOFields from the class definition
        DOField[] doFields = doClass.getFields();
        if (doFields == null || doFields.length == 0) {
            return;
        }

        // Sort fields using ExportUtils for consistent ordering and exclusions
        List<DOField> sortedFields = ExportUtils.sortFieldsForExport(Arrays.asList(doFields));

        // Get container for field value extraction
        com.db4o.ext.ExtObjectContainer container = moduleContext.engine.getDatabase().getContainer();
        ObjectResolverUtil.activateObject(container, obj, objectId);

        // Export each field using ObjectResolverUtil
        for (DOField doField : sortedFields) {
            try {
                // Use ObjectResolverUtil to get field value properly from db4o object
                Object fieldValue = ObjectResolverUtil.getFieldValue(container, obj, doField);

                // Skip empty values (null, empty strings, -1 for ID fields)
                if (isEmptyFieldValue(fieldValue, doField)) {
                    continue;
                }

                // Create an ExportColumn and FormattedValue to use the same export logic as
                // top-level objects
                ExportColumn column = new ExportColumn(doField, doField.getName());
                FormattedValue formattedValue = new FormattedValue(fieldValue, column);

                // Use the same writeFieldElement method as top-level objects for consistency
                writeFieldElement(writer, formattedValue, moduleContext);

            } catch (Exception e) {
                // Skip fields we can't access
                System.err.println("Warning: Could not export field " + doField.getName() +
                        " for embedded object: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a field value should be considered empty and not exported.
     */
    private boolean isEmptyFieldValue(Object value, DOField field) {
        if (value == null) {
            return true;
        }

        // Empty strings
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return true;
        }

        // For ID fields, -1 typically means "no reference"
        if (value instanceof Number) {
            String typeName = field != null ? field.getTypeName() : null;
            if (typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"))) {
                if (((Number) value).intValue() == -1) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Write an embedded object with all its fields (v2 format: object-specific
     * element).
     */
    private void writeEmbeddedObject(XMLStreamWriter writer, Object obj, XMLModuleContext moduleContext)
            throws XMLStreamException {
        String fullClassName = getFullClassName(obj, moduleContext.engine);
        String className = getSimpleClassNameFromFull(fullClassName);

        // Find the mID field value to use as the business ID (optional for embedded
        // objects)
        String businessId = null;
        DOClass doClass = ObjectResolverUtil.findClassDefinition(fullClassName,
                moduleContext.engine.getSchema(),
                moduleContext.engine.getDatabase());

        if (doClass != null) {
            DOField[] doFields = doClass.getFields();
            if (doFields != null) {
                com.db4o.ext.ExtObjectContainer container = moduleContext.engine.getDatabase().getContainer();
                for (DOField field : doFields) {
                    // Look for the mID field (ID type field)
                    String typeName = field.getTypeName();
                    if (typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"))) {
                        try {
                            Object idValue = ObjectResolverUtil.getFieldValue(container, obj, field);
                            if (idValue instanceof Number) {
                                businessId = String.valueOf(((Number) idValue).longValue());
                            } else if (idValue != null) {
                                // ID-type object - extract the numeric mValeur field
                                Long numericId = extractNumericIdFromIdObject(idValue, container);
                                if (numericId != null) {
                                    businessId = String.valueOf(numericId);
                                }
                                // If extraction fails, leave businessId as null (no id attribute)
                            }
                            break;
                        } catch (Exception e) {
                            // Continue without ID if we can't get the mID field
                        }
                    }
                }
            }
        }

        // V2: Write object-specific element (e.g., <Adresse> instead of <object
        // type="Adresse">)
        writer.writeCharacters(moduleContext.indent());
        writer.writeStartElement(className);

        // Only write id attribute if we found a valid business ID
        if (businessId != null) {
            writer.writeAttribute("id", businessId);
        }

        writer.writeCharacters("\n");

        // Write all fields (increment indent level for fields inside embedded object)
        moduleContext.indentLevel++;
        writeObjectFields(writer, obj, moduleContext, fullClassName);
        moduleContext.indentLevel--;

        writer.writeCharacters(moduleContext.indent());
        writer.writeEndElement(); // object-specific element (e.g., </Adresse>)
        writer.writeCharacters("\n");
    }

    /**
     * Extract the numeric value from an ID-type object (e.g., IDTypeChauffage).
     * ID objects have a mValeur field containing the actual numeric ID.
     */
    private Long extractNumericIdFromIdObject(Object idObject, com.db4o.ext.ExtObjectContainer container) {
        if (idObject == null) {
            return null;
        }

        try {
            // ID objects have a mValeur field containing the numeric value
            String fullClassName = idObject.getClass().getName();
            DOClass doClass = ObjectResolverUtil.findClassDefinition(fullClassName,
                    null, // schema not needed for this lookup
                    null); // database not needed for this lookup

            if (doClass == null) {
                // Try to find the field directly using reflection
                try {
                    java.lang.reflect.Field valeurField = idObject.getClass().getDeclaredField("mValeur");
                    valeurField.setAccessible(true);
                    Object valeur = valeurField.get(idObject);
                    if (valeur instanceof Number) {
                        return ((Number) valeur).longValue();
                    }
                } catch (Exception e) {
                    // Field not found or not accessible
                }
                return null;
            }

            // Find the mValeur field in the class definition
            DOField[] fields = doClass.getFields();
            if (fields != null) {
                for (DOField field : fields) {
                    if ("mValeur".equals(field.getName())) {
                        Object valeur = ObjectResolverUtil.getFieldValue(container, idObject, field);
                        if (valeur instanceof Number) {
                            return ((Number) valeur).longValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to extract numeric ID
        }

        return null;
    }

    /**
     * Write a field for an embedded object (v2 format: field-specific elements).
     */
    private void writeEmbeddedObjectField(XMLStreamWriter writer, String fieldName, Object fieldValue,
            XMLModuleContext moduleContext) throws XMLStreamException {
        writer.writeCharacters("                            ");

        // V2: Write field-specific element (e.g., <nom> instead of <field name="nom">)
        String xmlFieldName = toXmlFieldName(fieldName);
        writer.writeStartElement(xmlFieldName);

        // Determine field type and write value
        if (fieldValue instanceof String) {
            writer.writeCharacters(escapeText((String) fieldValue));
        } else if (fieldValue instanceof Integer || fieldValue instanceof Long) {
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Double || fieldValue instanceof Float) {
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Boolean) {
            writer.writeCharacters(String.valueOf(fieldValue));
        } else if (fieldValue instanceof Date) {
            writer.writeCharacters(formatPrimitiveValue(fieldValue));
        } else {
            // Complex type - just write string representation
            writer.writeCharacters(escapeText(String.valueOf(fieldValue)));
        }

        writer.writeEndElement(); // field-specific element
        writer.writeCharacters("\n");
    }

    /**
     * Convert database field name to XML field name.
     * Handles all field name transformations:
     * - m prefix: mAfficherSuite -> afficherSuite, mID -> id
     * - Uppercase first char: Adresse -> adresse (when m was already stripped)
     * - ID prefix: ID -> id, IDSSI -> idSSI, IDDBConso -> idDBConso
     * - iD prefix: iD -> id, idSSI -> idssi, idDossPrev -> idDossPrev
     * - iXXX patterns: iNDEX_toString -> index_toString
     */
    private String toXmlFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        // Remove 'm' prefix if present and lowercase the next character
        if (fieldName.startsWith("m") && fieldName.length() > 1 && Character.isUpperCase(fieldName.charAt(1))) {
            fieldName = Character.toLowerCase(fieldName.charAt(1)) + fieldName.substring(2);
        }

        // Handle iD -> id or ID -> id
        if (fieldName.equals("iD") || fieldName.equals("ID") || fieldName.equals("id")) {
            return "id";
        }

        // Handle uppercase ID prefix: IDSSI -> idSSI, IDDBConso -> idDBConso
        if (fieldName.startsWith("ID") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2))) {
            // Change ID to id, keep rest as-is
            return "id" + fieldName.substring(2);
        }

        // Handle idXxx -> idXxx or idxxx pattern
        if (fieldName.startsWith("id") && fieldName.length() > 2 && Character.isUpperCase(fieldName.charAt(2))) {
            String afterId = fieldName.substring(2);

            // Check if everything after "id" is uppercase (acronym)
            boolean isAllCaps = true;
            for (char c : afterId.toCharArray()) {
                if (Character.isLowerCase(c)) {
                    isAllCaps = false;
                    break;
                }
            }

            if (isAllCaps) {
                // idSSI -> idssi, idJPA -> idjpa
                return "id" + afterId.toLowerCase();
            } else {
                // idDossPrev stays idDossPrev
                return fieldName;
            }
        }

        // Handle iXXX patterns (like iNDEX_toString -> index_toString)

        // If first character is uppercase (m was already stripped by ColumnBuilder),
        // lowercase it
        // This handles: Adresse -> adresse, Energie -> energie
        if (Character.isUpperCase(fieldName.charAt(0))) {
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }

        // Already properly formatted - return as-is
        return fieldName;
    }

    /**
     * Override parent's sanitizeElementName to preserve PascalCase for v2 format.
     * Object type names should be in PascalCase (PersonneRess, VilleGeo).
     * This differs from the parent implementation which converts to lowercase.
     */
    @Override
    protected String sanitizeElementName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed";
        }

        // Replace spaces and special characters with underscores, but preserve case
        String sanitized = name.trim()
                .replaceAll("[\\s\\-\\.]", "_")
                .replaceAll("[^a-zA-Z0-9_$]", ""); // Allow $ for inner classes

        // Ensure it starts with a letter or underscore
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "Item_" + sanitized;
        }

        // V2: Preserve PascalCase (don't convert to lowercase)
        return sanitized;
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
    private String getSimpleClassNameFromObject(Object obj) {
        String fullName = obj.getClass().getName();
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * Get the simple class name from a fully qualified type name string.
     * For example: "gest.employe.Employe" -> "Employe"
     */
    private String getSimpleClassNameFromTypeName(String fullTypeName) {
        if (fullTypeName == null) {
            return "UnknownType";
        }
        int lastDot = fullTypeName.lastIndexOf('.');
        return lastDot >= 0 ? fullTypeName.substring(lastDot + 1) : fullTypeName;
    }

    /**
     * Get the actual full class name using ObjectResolverUtil.
     */
    private String getFullClassName(Object obj, DOEngine engine) {
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        return ObjectResolverUtil.getObjectClassName(container, obj);
    }
    
    /**
     * Find the DODatabaseClass for a given full class name.
     * This is used when recording references to get strongly-typed class definitions.
     */
    private dataobjects.api.models.database.DODatabaseClass findDatabaseClass(String fullClassName, DOEngine engine) {
        // Look up the class definition in schema or database
        dataobjects.api.models.DOClass doClass = ObjectResolverUtil.findClassDefinition(
            fullClassName, 
            engine.getSchema(), 
            engine.getDatabase()
        );
        
        // Return as DODatabaseClass if it's a database class
        if (doClass instanceof dataobjects.api.models.database.DODatabaseClass) {
            return (dataobjects.api.models.database.DODatabaseClass) doClass;
        }
        
        return null;
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