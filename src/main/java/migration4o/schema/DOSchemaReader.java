package migration4o.schema;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;

public class DOSchemaReader {

    public DOSchema readSchema(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"database".equals(root.getNodeName())) {
                throw new RuntimeException("Invalid schema file: root element must be 'database'");
            }

            DOSchemaModule[] modules = parseModules(root);
            DOSchemaClass[] foundationClasses = parseFoundationClasses(root);
            DOSchemaClass[] allClasses = extractAllClasses(modules, foundationClasses);

            return new DOSchema(allClasses, modules, foundationClasses);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read schema from " + filePath, e);
        }
    }

    private DOSchemaModule[] parseModules(Element root) {
        List<DOSchemaModule> moduleList = new ArrayList<>();

        Element modulesElement = getChildElement(root, "modules");
        if (modulesElement != null) {
            NodeList moduleNodes = modulesElement.getElementsByTagName("module");
            for (int i = 0; i < moduleNodes.getLength(); i++) {
                Element moduleElement = (Element) moduleNodes.item(i);
                moduleList.add(parseModule(moduleElement));
            }
        }

        return moduleList.toArray(new DOSchemaModule[0]);
    }

    private DOSchemaClass[] parseFoundationClasses(Element root) {
        List<DOSchemaClass> classList = new ArrayList<>();

        Element foundationElement = getChildElement(root, "foundation");
        if (foundationElement != null) {
            NodeList classNodes = foundationElement.getElementsByTagName("class");
            for (int i = 0; i < classNodes.getLength(); i++) {
                Element classElement = (Element) classNodes.item(i);
                // Only parse direct child classes, not nested ones
                if (classElement.getParentNode() == foundationElement) {
                    classList.add(parseClass(classElement));
                }
            }
        }

        return classList.toArray(new DOSchemaClass[0]);
    }

    private DOSchemaModule parseModule(Element moduleElement) {
        String name = moduleElement.getAttribute("name");
        List<DOSchemaClass> classList = new ArrayList<>();

        NodeList classNodes = moduleElement.getElementsByTagName("class");
        for (int i = 0; i < classNodes.getLength(); i++) {
            Element classElement = (Element) classNodes.item(i);
            // Only parse direct child classes, not nested ones
            if (classElement.getParentNode() == moduleElement) {
                classList.add(parseClass(classElement));
            }
        }

        DOSchemaClass[] classes = classList.toArray(new DOSchemaClass[0]);
        return new DOSchemaModule(name, classes);
    }

    private DOSchemaClass parseClass(Element classElement) {
        String absoluteName = classElement.getAttribute("name");
        String simpleName = classElement.getAttribute("simpleName");
        String parentClassName = classElement.getAttribute("parentClass");
        String description = classElement.getAttribute("description");
        String title = classElement.getAttribute("title");
        String exportName = simpleName.isEmpty() ? getSimpleClassName(absoluteName) : simpleName;

        List<DOSchemaField> fieldList = new ArrayList<>();
        NodeList fieldNodes = classElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            // Only parse direct child fields, not nested ones
            if (fieldElement.getParentNode() == classElement) {
                fieldList.add(parseField(fieldElement));
            }
        }

        DOSchemaField[] fields = fieldList.toArray(new DOSchemaField[0]);

        DOSchemaClass newClass = new DOSchemaClass();
        newClass.source = absoluteName;
        newClass.destinationName = simpleName;
        newClass.description = description;
        newClass.title = title;
        newClass.parentClassName = parentClassName;
        newClass.fields = fields;
        // newClass.exportName = exportName; // No exportName field in DOSchemaClass
        return newClass;
    }

    private DOSchemaField parseField(Element fieldElement) {
        String source = fieldElement.getAttribute("sourceName");
        String destinationName = fieldElement.getAttribute("destinationName");
        String type = fieldElement.getAttribute("type");
        String migrate = fieldElement.getAttribute("migrate");
        String skipIfEmpty = fieldElement.getAttribute("skipIfEmpty");
        String collection = fieldElement.getAttribute("collection");
        String embedContents = fieldElement.getAttribute("embedContents");

        String name = fieldElement.getAttribute("name");
        String childrenClass = fieldElement.getAttribute("childrenClass");
        String children = fieldElement.getAttribute("children");

        if (source.isEmpty())
            source = name;
        if (destinationName.isEmpty())
            destinationName = name;

        boolean isExported = "true".equalsIgnoreCase(migrate);
        boolean isSkipIfEmpty = "true".equalsIgnoreCase(skipIfEmpty);
        boolean isCollection = "true".equalsIgnoreCase(collection);
        boolean isEmbedContents = "true".equalsIgnoreCase(embedContents);

        String childrenClassName = !childrenClass.isEmpty() ? childrenClass : (!children.isEmpty() ? children : null);

        DOSchemaField field = new DOSchemaField();
        field.source = source;
        field.destinationName = destinationName;
        field.type = type;
        field.isExported = isExported;
        field.skipIfEmpty = isSkipIfEmpty;
        field.isCollection = isCollection;
        field.embedContents = isEmbedContents;
        field.childrenType = childrenClassName;
        field.title = null;
        field.description = null;
        field.pointsTo = null;
        field.databaseClass = null;
        field.childrenSchemaClass = null;
        return field;
    }

    private DOSchemaClass[] extractAllClasses(DOSchemaModule[] modules, DOSchemaClass[] foundationClasses) {
        List<DOSchemaClass> allClasses = new ArrayList<>();

        // Add module classes
        for (DOSchemaModule module : modules) {
            allClasses.addAll(Arrays.asList(module.getClasses()));
        }

        // Add foundation classes
        allClasses.addAll(Arrays.asList(foundationClasses));

        return allClasses.toArray(new DOSchemaClass[0]);
    }

    private Element getChildElement(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        return nodeList.getLength() > 0 ? (Element) nodeList.item(0) : null;
    }

    private String getSimpleClassName(String absoluteName) {
        if (absoluteName == null || absoluteName.isEmpty()) {
            return "";
        }
        int lastDotIndex = absoluteName.lastIndexOf('.');
        return lastDotIndex >= 0 ? absoluteName.substring(lastDotIndex + 1) : absoluteName;
    }
}
