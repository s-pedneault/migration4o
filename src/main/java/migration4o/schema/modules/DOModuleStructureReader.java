package migration4o.schema.modules;

import org.w3c.dom.*;

import migration4o.models.ui.MigrationModule;

import javax.xml.parsers.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads migration structure from migration-format.xml file.
 */
public class DOModuleStructureReader {

    public List<MigrationModule> readMigrationFormat(String filePath) throws Exception {
        List<MigrationModule> modules = new ArrayList<>();

        File file = new File(filePath);
        if (!file.exists()) {
            return modules; // Return empty list if file doesn't exist
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        // Get only top-level module nodes (direct children of <modules>)
        Element modulesElement = (Element) doc.getElementsByTagName("modules").item(0);
        if (modulesElement != null) {
            NodeList children = modulesElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && "module".equals(node.getNodeName())) {
                    modules.add(parseModule((Element) node));
                }
            }
        }

        return modules;
    }

    private MigrationModule parseModule(Element moduleElement) {
        String name = moduleElement.getAttribute("name");
        String id = moduleElement.getAttribute("id");
        if (id == null || id.isEmpty()) {
            id = name; // Use name as ID if no ID specified
        }

        List<String> classNames = new ArrayList<>();
        List<MigrationModule> childModules = new ArrayList<>();

        // Parse direct children only
        NodeList children = moduleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                if ("classRef".equals(childElement.getNodeName())) {
                    String sourceName = childElement.getAttribute("sourceName");
                    if (sourceName != null && !sourceName.isEmpty()) {
                        classNames.add(sourceName);
                    }
                } else if ("module".equals(childElement.getNodeName())) {
                    // Recursive call for nested modules
                    childModules.add(parseModule(childElement));
                }
            }
        }

        return new MigrationModule(name, id, classNames, childModules);
    }
}
