package migration4o.schema;

import migration4o.ui.schema.MigrationStructurePanel.MigrationModule;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads migration structure from migration-format.xml file.
 */
public class MigrationFormatReader {

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

        NodeList moduleNodes = doc.getElementsByTagName("module");

        for (int i = 0; i < moduleNodes.getLength(); i++) {
            Node moduleNode = moduleNodes.item(i);
            if (moduleNode.getNodeType() == Node.ELEMENT_NODE) {
                Element moduleElement = (Element) moduleNode;

                String name = moduleElement.getAttribute("name");
                String id = moduleElement.getAttribute("id");
                if (id == null || id.isEmpty()) {
                    id = name; // Use name as ID if no ID specified
                }

                List<String> classNames = new ArrayList<>();
                NodeList classRefs = moduleElement.getElementsByTagName("classRef");

                for (int j = 0; j < classRefs.getLength(); j++) {
                    Node classRefNode = classRefs.item(j);
                    if (classRefNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element classRefElement = (Element) classRefNode;
                        String sourceName = classRefElement.getAttribute("sourceName");
                        if (sourceName != null && !sourceName.isEmpty()) {
                            classNames.add(sourceName);
                        }
                    }
                }

                modules.add(new MigrationModule(name, id, classNames));
            }
        }

        return modules;
    }
}
