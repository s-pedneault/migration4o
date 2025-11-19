package dataobjects.impl.migration.xml;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates XSD schema from DOSchema.
 * Creates complex types for each class, handles inheritance, and defines
 * primitive types.
 */
public class XMLSchemaGenerator {

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    private static final String XS_PREFIX = "xs";

    private final DOEngine engine;
    private final String namespace;
    private final Set<String> processedClasses = new HashSet<>();

    public XMLSchemaGenerator(DOEngine engine, String namespace) {
        this.engine = engine;
        this.namespace = namespace;
    }

    /**
     * Generate the XSD schema file.
     */
    public void generateSchema(String outputPath) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // Root schema element
            writer.writeStartElement(XS_PREFIX, "schema", XSD_NAMESPACE);
            writer.writeNamespace(XS_PREFIX, XSD_NAMESPACE);
            writer.writeAttribute("targetNamespace", namespace);
            writer.writeAttribute("elementFormDefault", "qualified");
            writer.writeCharacters("\n");

            DOSchema schema = engine.getSchema();

            // Generate complex types for all classes
            if (schema.getClasses() != null) {
                for (DOSchemaClass schemaClass : schema.getClasses()) {
                    generateComplexType(writer, schemaClass);
                }
            }

            // Generate root elements for each module
            if (schema.getModules() != null) {
                writer.writeCharacters("\n  ");
                writer.writeComment(" Root elements for module data files ");
                writer.writeCharacters("\n");

                for (DOSchemaModule module : schema.getModules()) {
                    generateModuleElement(writer, module);
                }

                // Add unreached objects root element
                writer.writeCharacters("\n  ");
                writer.writeStartElement(XS_PREFIX, "element", XSD_NAMESPACE);
                writer.writeAttribute("name", "unreached");
                writer.writeCharacters("\n    ");
                writer.writeStartElement(XS_PREFIX, "complexType", XSD_NAMESPACE);
                writer.writeCharacters("\n      ");
                writer.writeStartElement(XS_PREFIX, "sequence", XSD_NAMESPACE);
                writer.writeCharacters("\n        ");
                writer.writeStartElement(XS_PREFIX, "element", XSD_NAMESPACE);
                writer.writeAttribute("name", "object");
                writer.writeAttribute("type", "anyObjectType");
                writer.writeAttribute("minOccurs", "0");
                writer.writeAttribute("maxOccurs", "unbounded");
                writer.writeCharacters("\n        ");
                writer.writeEndElement(); // element
                writer.writeCharacters("\n      ");
                writer.writeEndElement(); // sequence
                writer.writeCharacters("\n    ");
                writer.writeEndElement(); // complexType
                writer.writeCharacters("\n  ");
                writer.writeEndElement(); // element
                writer.writeCharacters("\n");
            }

            // Generate a generic anyObjectType that can hold any class
            generateAnyObjectType(writer);

            writer.writeCharacters("\n");
            writer.writeEndElement(); // schema
            writer.writeEndDocument();
            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException("Error generating XML schema", e);
        }
    }

    /**
     * Generate a module root element.
     */
    private void generateModuleElement(XMLStreamWriter writer, DOSchemaModule module) throws XMLStreamException {
        writer.writeCharacters("\n  ");
        writer.writeStartElement(XS_PREFIX, "element", XSD_NAMESPACE);
        writer.writeAttribute("name", sanitizeName(module.getName()));

        writer.writeCharacters("\n    ");
        writer.writeStartElement(XS_PREFIX, "complexType", XSD_NAMESPACE);
        writer.writeCharacters("\n      ");
        writer.writeStartElement(XS_PREFIX, "sequence", XSD_NAMESPACE);

        // Each module contains objects from its classes
        if (module.getClasses() != null) {
            for (DOSchemaClass schemaClass : module.getClasses()) {
                String className = schemaClass.getShortName();
                writer.writeCharacters("\n        ");
                writer.writeStartElement(XS_PREFIX, "element", XSD_NAMESPACE);
                writer.writeAttribute("name", sanitizeName(className));
                writer.writeAttribute("type", getTypeNameForClass(schemaClass));
                writer.writeAttribute("minOccurs", "0");
                writer.writeAttribute("maxOccurs", "unbounded");
                writer.writeCharacters("\n        ");
                writer.writeEndElement(); // element
            }
        }

        writer.writeCharacters("\n      ");
        writer.writeEndElement(); // sequence
        writer.writeCharacters("\n    ");
        writer.writeEndElement(); // complexType
        writer.writeCharacters("\n  ");
        writer.writeEndElement(); // element
        writer.writeCharacters("\n");
    }

    /**
     * Generate a complex type for a class.
     */
    private void generateComplexType(XMLStreamWriter writer, DOSchemaClass schemaClass) throws XMLStreamException {
        String className = schemaClass.getShortName();

        if (processedClasses.contains(className)) {
            return; // Already processed
        }
        processedClasses.add(className);

        writer.writeCharacters("\n  ");
        writer.writeComment(" Type for class: " + schemaClass.getAbsoluteName() + " ");
        writer.writeCharacters("\n  ");
        writer.writeStartElement(XS_PREFIX, "complexType", XSD_NAMESPACE);
        writer.writeAttribute("name", getTypeNameForClass(schemaClass));

        // Handle inheritance with extension
        String superClassName = schemaClass.getSuperClassAbsoluteName();
        if (superClassName != null && !superClassName.equals("java.lang.Object")) {
            writer.writeCharacters("\n    ");
            writer.writeStartElement(XS_PREFIX, "complexContent", XSD_NAMESPACE);
            writer.writeCharacters("\n      ");
            writer.writeStartElement(XS_PREFIX, "extension", XSD_NAMESPACE);
            writer.writeAttribute("base", getTypeNameForClassName(superClassName));
            writeFieldSequence(writer, schemaClass);
            writer.writeCharacters("\n      ");
            writer.writeEndElement(); // extension
            writer.writeCharacters("\n    ");
            writer.writeEndElement(); // complexContent
        } else {
            // No inheritance - direct sequence
            writeFieldSequence(writer, schemaClass);
        }

        // Add id attribute for object identification
        writer.writeCharacters("\n    ");
        writer.writeStartElement(XS_PREFIX, "attribute", XSD_NAMESPACE);
        writer.writeAttribute("name", "id");
        writer.writeAttribute("type", XS_PREFIX + ":long");
        writer.writeAttribute("use", "required");
        writer.writeCharacters("\n    ");
        writer.writeEndElement(); // attribute

        writer.writeCharacters("\n  ");
        writer.writeEndElement(); // complexType
        writer.writeCharacters("\n");
    }

    /**
     * Write the field sequence for a class.
     */
    private void writeFieldSequence(XMLStreamWriter writer, DOSchemaClass schemaClass) throws XMLStreamException {
        DOField[] fields = schemaClass.getFields();
        if (fields == null || fields.length == 0) {
            return;
        }

        writer.writeCharacters("\n        ");
        writer.writeStartElement(XS_PREFIX, "sequence", XSD_NAMESPACE);

        for (DOField field : fields) {
            generateFieldElement(writer, field);
        }

        writer.writeCharacters("\n        ");
        writer.writeEndElement(); // sequence
    }

    /**
     * Generate an element for a field.
     */
    private void generateFieldElement(XMLStreamWriter writer, DOField field) throws XMLStreamException {
        writer.writeCharacters("\n          ");
        writer.writeStartElement(XS_PREFIX, "element", XSD_NAMESPACE);
        writer.writeAttribute("name", sanitizeName(field.getName()));

        String xsdType = mapToXsdType(field);
        writer.writeAttribute("type", xsdType);

        // Optional field by default
        writer.writeAttribute("minOccurs", "0");

        // If it's an array, allow multiple occurrences
        if (field.isArray()) {
            writer.writeAttribute("maxOccurs", "unbounded");
        }

        writer.writeCharacters("\n          ");
        writer.writeEndElement(); // element
    }

    /**
     * Generate anyObjectType that can hold any class instance.
     */
    private void generateAnyObjectType(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("\n  ");
        writer.writeComment(" Generic object type for unreached objects ");
        writer.writeCharacters("\n  ");
        writer.writeStartElement(XS_PREFIX, "complexType", XSD_NAMESPACE);
        writer.writeAttribute("name", "anyObjectType");

        writer.writeCharacters("\n    ");
        writer.writeStartElement(XS_PREFIX, "sequence", XSD_NAMESPACE);
        writer.writeCharacters("\n      ");
        writer.writeStartElement(XS_PREFIX, "any", XSD_NAMESPACE);
        writer.writeAttribute("minOccurs", "0");
        writer.writeAttribute("maxOccurs", "unbounded");
        writer.writeAttribute("processContents", "skip");
        writer.writeCharacters("\n      ");
        writer.writeEndElement(); // any
        writer.writeCharacters("\n    ");
        writer.writeEndElement(); // sequence

        writer.writeCharacters("\n    ");
        writer.writeStartElement(XS_PREFIX, "attribute", XSD_NAMESPACE);
        writer.writeAttribute("name", "type");
        writer.writeAttribute("type", XS_PREFIX + ":string");
        writer.writeAttribute("use", "required");
        writer.writeCharacters("\n    ");
        writer.writeEndElement(); // attribute

        writer.writeCharacters("\n    ");
        writer.writeStartElement(XS_PREFIX, "attribute", XSD_NAMESPACE);
        writer.writeAttribute("name", "id");
        writer.writeAttribute("type", XS_PREFIX + ":long");
        writer.writeAttribute("use", "required");
        writer.writeCharacters("\n    ");
        writer.writeEndElement(); // attribute

        writer.writeCharacters("\n  ");
        writer.writeEndElement(); // complexType
        writer.writeCharacters("\n");
    }

    /**
     * Map a DOField to an XSD type.
     */
    private String mapToXsdType(DOField field) {
        if (field.isPrimitive()) {
            return mapPrimitiveToXsd(field.getTypeName());
        }

        // For object references, use string (will contain object ID)
        if (field.isArray()) {
            // Array of objects - use object reference type
            String contentType = field.getContentTypeName();
            if (contentType != null) {
                DOClass contentClass = field.getContentTypeClass();
                if (contentClass != null) {
                    return getTypeNameForClass(contentClass);
                }
                return mapPrimitiveToXsd(contentType);
            }
            return XS_PREFIX + ":string";
        }

        // Single object reference
        DOClass typeClass = field.getTypeClass();
        if (typeClass != null) {
            return getTypeNameForClass(typeClass);
        }

        return XS_PREFIX + ":string";
    }

    /**
     * Map Java primitive types to XSD types.
     */
    private String mapPrimitiveToXsd(String javaType) {
        if (javaType == null) {
            return XS_PREFIX + ":string";
        }

        switch (javaType) {
            case "java.lang.String":
            case "String":
                return XS_PREFIX + ":string";
            case "int":
            case "java.lang.Integer":
            case "Integer":
                return XS_PREFIX + ":int";
            case "long":
            case "java.lang.Long":
            case "Long":
                return XS_PREFIX + ":long";
            case "double":
            case "java.lang.Double":
            case "Double":
                return XS_PREFIX + ":double";
            case "float":
            case "java.lang.Float":
            case "Float":
                return XS_PREFIX + ":float";
            case "boolean":
            case "java.lang.Boolean":
            case "Boolean":
                return XS_PREFIX + ":boolean";
            case "java.util.Date":
            case "Date":
                return XS_PREFIX + ":dateTime";
            case "byte":
            case "java.lang.Byte":
            case "Byte":
                return XS_PREFIX + ":byte";
            case "short":
            case "java.lang.Short":
            case "Short":
                return XS_PREFIX + ":short";
            default:
                return XS_PREFIX + ":string";
        }
    }

    /**
     * Get the XSD type name for a class.
     */
    private String getTypeNameForClass(DOClass clazz) {
        return sanitizeName(clazz.getShortName()) + "Type";
    }

    /**
     * Get the XSD type name from a class name.
     */
    private String getTypeNameForClassName(String absoluteName) {
        String[] parts = absoluteName.split("\\.");
        String shortName = parts[parts.length - 1];
        return sanitizeName(shortName) + "Type";
    }

    /**
     * Sanitize a name for XML (remove special characters).
     */
    private String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        // Replace non-XML-safe characters
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
