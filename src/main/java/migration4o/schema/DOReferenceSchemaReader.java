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
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.schema.DOSchemaReference;
import migration4o.models.ui.MigrationModule;
import migration4o.schema.modules.DOModuleStructureReader;
import migration4o.schema.processors.DOReferenceDetector;

/**
 * Reader for the new reference-schema.xml format.
 * This format uses <classes> as root element with direct class children.
 * Attributes use 'source' and 'isExported' instead of 'sourceName' and
 * 'migrate'.
 */
public class DOReferenceSchemaReader {

    public DOSchema readSchema() {
        return readSchema(DOReferenceSchemaConstants.DEFAULT_SCHEMA_PATH);
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

            // Parse all classes directly from root
            DOSchemaClass[] allClasses = parseClassesFromRoot(root);

            // Create a single module containing all classes
            DOSchemaModule[] modules = new DOSchemaModule[] {
                    new DOSchemaModule("All Classes", allClasses)
            };

            // No foundation classes in new format
            DOSchemaClass[] foundationClasses = new DOSchemaClass[0];

            DOSchema schema = new DOSchema(allClasses, modules, foundationClasses);

            // Load migration modules from migration-format.xml
            try {
                DOModuleStructureReader moduleReader = new DOModuleStructureReader();
                List<MigrationModule> migrationModules = moduleReader
                        .readMigrationFormat("schema/migration-format.xml");
                schema.setMigrationModules(migrationModules);
                System.out.println("Loaded " + migrationModules.size() + " modules from schema/migration-format.xml");
            } catch (Exception e) {
                System.err.println("Warning: Could not load migration modules: " + e.getMessage());
                schema.setMigrationModules(new ArrayList<>());
            }

            // Post-process: detect and add missing references (e.g., IDEntite collections)
            DOReferenceDetector.detectAndAddReferences(schema);

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

    private DOSchemaClass[] parseClassesFromRoot(Element root) {
        List<DOSchemaClass> classList = new ArrayList<>();

        NodeList classNodes = root.getElementsByTagName("class");
        for (int i = 0; i < classNodes.getLength(); i++) {
            Element classElement = (Element) classNodes.item(i);
            // Only parse direct child classes, not nested ones
            if (classElement.getParentNode() == root) {
                classList.add(parseClass(classElement));
            }
        }

        return classList.toArray(new DOSchemaClass[0]);
    }

    private DOSchemaClass parseClass(Element classElement) {
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
                fieldList.add(parseField(fieldElement));
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
        newClass.fields = fields;
        newClass.schemaReferences = references;
        newClass.migrate = migrate;
        newClass.pointsTo = pointsToValue;
        return newClass;
    }

    private DOSchemaField parseField(Element fieldElement) {
        String source = fieldElement.getAttribute("source");
        String destinationName = fieldElement.getAttribute("destinationName");
        String type = fieldElement.getAttribute("type");
        String isExportedAttr = fieldElement.getAttribute("isExported");
        String skipIfEmpty = fieldElement.getAttribute("skipIfEmpty");
        String collection = fieldElement.getAttribute("collection");
        String embedContents = fieldElement.getAttribute("embedContents");
        String childrenType = fieldElement.getAttribute("childrenType");
        String title = fieldElement.getAttribute("title");
        String description = fieldElement.getAttribute("description");
        String pointsTo = fieldElement.getAttribute("pointsTo");

        // Parse boolean attributes
        boolean isExported = isExportedAttr.isEmpty() || "true".equalsIgnoreCase(isExportedAttr);
        boolean isSkipIfEmpty = skipIfEmpty.isEmpty() || "true".equalsIgnoreCase(skipIfEmpty);
        boolean isCollection = "true".equalsIgnoreCase(collection);
        boolean isEmbedContents = "true".equalsIgnoreCase(embedContents);

        // Children class name
        String childrenClassName = !childrenType.isEmpty() ? childrenType : null;

        DOSchemaField field = new DOSchemaField();
        field.source = source;
        field.destinationName = destinationName;
        field.type = type;
        field.isExported = isExported;
        field.skipIfEmpty = isSkipIfEmpty;
        field.isCollection = isCollection;
        field.embedContents = isEmbedContents;
        field.childrenType = childrenClassName;
        field.title = title.isEmpty() ? null : title;
        field.description = description.isEmpty() ? null : description;
        field.pointsTo = pointsTo.isEmpty() ? null : pointsTo;
        field.childrenSchemaClass = null;
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
