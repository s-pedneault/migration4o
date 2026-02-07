package migration4o.schema;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;
import migration4o.schema.processors.DOEmbeddingDetector;
import migration4o.schema.processors.DOReferenceDetector;

/**
 * Reader for the new reference-schema.xml format.
 * This format uses <classes> as root element with direct class children.
 * Attributes use 'source' and 'isExported' instead of 'sourceName' and
 * 'migrate'.
 */
public class DOReferenceSchemaReader {

    public DOSchema readSchema() {
        return readSchema(DOSchemaService.DEFAULT_SCHEMA_PATH);
    }

    private DOSchema readSchema(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"classes".equals(root.getNodeName())) {
                throw new RuntimeException("Invalid schema file: root element must be 'classes'");
            }

            // Parse shared field definitions first
            DOSchema schema = new DOSchema(new DOSchemaClass[0], new DOSchemaClass[0]);
            parseSharedFields(root, schema);

            // Parse all classes directly from root
            DOSchemaClass[] allClasses = parseClassesFromRoot(root, schema);

            // No foundation classes in new format
            DOSchemaClass[] foundationClasses = new DOSchemaClass[0];

            // Update schema with parsed classes
            schema = new DOSchema(allClasses, foundationClasses);

            // Restore shared fields (they were lost when creating new schema)
            DOSchema tempSchema = schema;
            parseSharedFields(root, tempSchema);

            // Post-process: detect and add missing references (e.g., IDEntite collections)
            DOReferenceDetector.detectAndAddReferences(schema);

            // Post-process: validate embedContents configuration
            DOEmbeddingDetector.detectEmbeddingAnomalies(schema);

            // Post-process: determine optimal embedding strategy based on reference
            // patterns
            // DISABLED: Using embedContents values from XML file instead
            // DOEmbeddingCoordinator coordinator = new DOEmbeddingCoordinator(schema);
            // coordinator.coordinateEmbedding();

            return schema;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read schema from " + filePath, e);
        }
    }

    private void parseSharedFields(Element root, DOSchema schema) {
        // Look for <fields> element at root level
        NodeList fieldsNodes = root.getElementsByTagName("fields");
        if (fieldsNodes.getLength() == 0) {
            return; // No shared fields defined
        }

        Element fieldsElement = (Element) fieldsNodes.item(0);
        // Only process if it's a direct child of root
        if (fieldsElement.getParentNode() != root) {
            return;
        }

        // Parse all field definitions
        NodeList fieldNodes = fieldsElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            // Only parse direct child fields
            if (fieldElement.getParentNode() == fieldsElement) {
                DOSchemaField field = parseField(fieldElement);
                // Use source attribute as the key for shared field definitions
                if (field.source != null && !field.source.trim().isEmpty()) {
                    field.definitionId = field.source; // Mark as shared field definition
                    schema.addSharedField(field.source, field);
                }
            }
        }
    }

    private DOSchemaClass[] parseClassesFromRoot(Element root, DOSchema schema) {
        List<DOSchemaClass> classList = new ArrayList<>();

        NodeList classNodes = root.getElementsByTagName("class");
        for (int i = 0; i < classNodes.getLength(); i++) {
            Element classElement = (Element) classNodes.item(i);
            // Only parse direct child classes, not nested ones
            if (classElement.getParentNode() == root) {
                classList.add(parseClass(classElement, schema));
            }
        }

        return classList.toArray(new DOSchemaClass[0]);
    }

    private DOSchemaClass parseClass(Element classElement, DOSchema schema) {
        // New format uses 'source' instead of 'name'
        String absoluteName = classElement.getAttribute("source");
        String destinationName = classElement.getAttribute("destinationName");
        String parentClassName = classElement.getAttribute("parentClass");
        String description = classElement.getAttribute("description");
        String title = classElement.getAttribute("title");
        String isExportedAttr = classElement.getAttribute("isExported");
        String pointsTo = classElement.getAttribute("pointsTo");

        // Use destinationName as simpleName if available, otherwise derive from source
        String simpleName = !destinationName.isEmpty() ? destinationName : getSimpleClassName(absoluteName);

        // Parse isExported attribute to migrate flag (default to true if not specified)
        boolean migrate = isExportedAttr.isEmpty() || "true".equalsIgnoreCase(isExportedAttr);

        // Use null if pointsTo is empty
        String pointsToValue = pointsTo.isEmpty() ? null : pointsTo;

        // Parse fields
        List<DOSchemaField> fieldList = new ArrayList<>();
        NodeList fieldNodes = classElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            // Only parse direct child fields, not nested ones
            if (fieldElement.getParentNode() == classElement) {
                DOSchemaField field = parseFieldOrReference(fieldElement, schema);
                if (field != null) {
                    fieldList.add(field);
                }
            }
        }

        // Parse reference elements
        List<DOSchemaReference> referenceList = new ArrayList<>();
        NodeList referenceNodes = classElement.getElementsByTagName("reference");
        for (int i = 0; i < referenceNodes.getLength(); i++) {
            Element referenceElement = (Element) referenceNodes.item(i);
            // Only parse direct child references
            if (referenceElement.getParentNode() == classElement) {
                String refClass = referenceElement.getAttribute("class");
                String refField = referenceElement.getAttribute("field");
                referenceList.add(new DOSchemaReference(refClass, refField));
            }
        }

        DOSchemaField[] fields = fieldList.toArray(new DOSchemaField[0]);
        DOSchemaReference[] references = referenceList.toArray(new DOSchemaReference[0]);

        // Create new constructor that accepts references and pointsTo
        DOSchemaClass newClass = new DOSchemaClass();
        newClass.source = absoluteName;
        newClass.destinationName = simpleName;
        newClass.description = description;
        newClass.title = title;
        newClass.parentClassName = parentClassName;
        newClass.setFields(fields);
        newClass.schemaReferences = references;
        newClass.migrate = migrate;
        newClass.pointsTo = pointsToValue;
        return newClass;
    }

    /**
     * Parse a field element which could be either a full field definition or a
     * reference to a shared field.
     * If it's a reference (has 'definition' attribute), resolve it from the
     * schema's shared fields.
     */
    private DOSchemaField parseFieldOrReference(Element fieldElement, DOSchema schema) {
        String definitionRef = fieldElement.getAttribute("definition");

        // Check if this is a reference to a shared field
        if (definitionRef != null && !definitionRef.trim().isEmpty()) {
            // This is a field reference - resolve from shared fields
            DOSchemaField sharedField = schema.getSharedField(definitionRef);
            if (sharedField == null) {
                System.err.println("Warning: Shared field definition not found: " + definitionRef);
                return null;
            }

            // Create a copy of the shared field
            DOSchemaField field = sharedField.copy();
            field.definitionId = definitionRef; // Keep the reference ID

            // CRITICAL: Use the source from THIS element, not from the shared definition
            // This allows the actual field name to vary per class (e.g., mID vs mIDEntite)
            String classSpecificSource = fieldElement.getAttribute("source");
            if (classSpecificSource != null && !classSpecificSource.trim().isEmpty()) {
                field.source = classSpecificSource;
            }

            return field;
        }

        // Not a reference - parse as regular field
        return parseField(fieldElement);
    }

    private DOSchemaField parseField(Element fieldElement) {
        String source = fieldElement.getAttribute("source");
        String destinationName = fieldElement.getAttribute("destinationName");
        String type = fieldElement.getAttribute("type");
        String isExportedAttr = fieldElement.getAttribute("isExported");
        String skipIfEmpty = fieldElement.getAttribute("skipIfEmpty");
        String skipWhen = fieldElement.getAttribute("skipWhen");
        String collection = fieldElement.getAttribute("collection");
        String embedContents = fieldElement.getAttribute("embedContents");
        String childrenType = fieldElement.getAttribute("childrenType");
        String title = fieldElement.getAttribute("title");
        String description = fieldElement.getAttribute("description");
        String pointsTo = fieldElement.getAttribute("pointsTo");

        // Parse boolean attributes
        boolean isExported = isExportedAttr.isEmpty() ? true : "true".equalsIgnoreCase(isExportedAttr);
        boolean isCollection = "true".equalsIgnoreCase(collection);
        boolean isEmbedContents = "true".equalsIgnoreCase(embedContents);

        // Convert legacy skipIfEmpty to skipWhen if skipWhen is not set
        String effectiveSkipWhen = skipWhen;
        if ((effectiveSkipWhen == null || effectiveSkipWhen.trim().isEmpty()) && !skipIfEmpty.isEmpty()) {
            boolean isSkipIfEmpty = "true".equalsIgnoreCase(skipIfEmpty);
            if (isSkipIfEmpty) {
                // Convert legacy skipIfEmpty=true to appropriate skipWhen keywords
                effectiveSkipWhen = "NULL,MINUS_ONE";
            }
        }

        // Children class name
        String childrenClassName = !childrenType.isEmpty() ? childrenType : null;

        DOSchemaField field = new DOSchemaField();
        field.source = source;
        field.destinationName = destinationName;
        field.type = type;
        field.isExported = isExported;
        field.skipWhen = effectiveSkipWhen != null && !effectiveSkipWhen.trim().isEmpty() ? effectiveSkipWhen : null;
        field.isCollection = isCollection;
        field.embedContents = isEmbedContents;
        field.childrenType = childrenClassName;
        field.title = title.isEmpty() ? null : title;
        field.description = description.isEmpty() ? null : description;
        field.pointsTo = pointsTo.isEmpty() ? null : pointsTo;
        field.childrenSchemaClass = null;

        // Parse value mappings from child elements
        NodeList valueMapNodes = fieldElement.getElementsByTagName("valueMap");
        if (valueMapNodes.getLength() > 0) {
            Element valueMapElement = (Element) valueMapNodes.item(0);
            NodeList mappingNodes = valueMapElement.getElementsByTagName("mapping");
            for (int i = 0; i < mappingNodes.getLength(); i++) {
                Element mappingElement = (Element) mappingNodes.item(i);
                String fromValue = mappingElement.getAttribute("from");
                String toValue = mappingElement.getAttribute("to");
                if (!fromValue.isEmpty() && !toValue.isEmpty()) {
                    field.addValueMapping(fromValue, toValue);
                }
            }
        }

        return field;
    }

    private String getSimpleClassName(String absoluteName) {
        if (absoluteName == null || absoluteName.isEmpty()) {
            return "";
        }
        int lastDotIndex = absoluteName.lastIndexOf('.');
        return lastDotIndex >= 0 ? absoluteName.substring(lastDotIndex + 1) : absoluteName;
    }
}
