package migration4o.schema.modules;

import org.w3c.dom.*;

import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.models.ui.MigrationModule;

import javax.xml.parsers.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads migration structure from migration-format.xml file.
 * Supports both old format (simple classRef) and new format (classRef with
 * criteria).
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

        List<ClassExportConfig> classConfigs = new ArrayList<>();
        List<MigrationModule> childModules = new ArrayList<>();

        // Parse direct children only
        NodeList children = moduleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                if ("classRef".equals(childElement.getNodeName())) {
                    ClassExportConfig config = parseClassRef(childElement);
                    if (config != null) {
                        classConfigs.add(config);
                    }
                } else if ("module".equals(childElement.getNodeName())) {
                    // Recursive call for nested modules
                    childModules.add(parseModule(childElement));
                }
            }
        }

        return new MigrationModule(name, id, classConfigs, childModules);
    }

    /**
     * Parses a classRef element which can be:
     * - Old format: <classRef sourceName="gest.config.ParamConfig"/>
     * - New format:
     * <classRef sourceName="..." destinationFile="..."><criteria field="..."
     * operator="..." value="..."/></classRef>
     */
    private ClassExportConfig parseClassRef(Element classRefElement) {
        String sourceName = classRefElement.getAttribute("sourceName");
        if (sourceName == null || sourceName.isEmpty()) {
            return null;
        }

        String destinationFile = classRefElement.getAttribute("destinationFile");
        if (destinationFile != null && destinationFile.isEmpty()) {
            destinationFile = null; // Treat empty string as null
        }

        // Parse criteria if any
        List<ExportCriteria> criteria = new ArrayList<>();
        NodeList criteriaNodes = classRefElement.getElementsByTagName("criteria");
        for (int i = 0; i < criteriaNodes.getLength(); i++) {
            Element criteriaElement = (Element) criteriaNodes.item(i);
            ExportCriteria criterion = parseCriteria(criteriaElement);
            if (criterion != null) {
                criteria.add(criterion);
            }
        }

        return new ClassExportConfig(sourceName, destinationFile, criteria);
    }

    /**
     * Parses a criteria element:
     * <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
     */
    private ExportCriteria parseCriteria(Element criteriaElement) {
        String field = criteriaElement.getAttribute("field");
        String operatorStr = criteriaElement.getAttribute("operator");
        String value = criteriaElement.getAttribute("value");

        if (field == null || field.isEmpty() || operatorStr == null || operatorStr.isEmpty()) {
            return null;
        }

        ExportCriteria.Operator operator = ExportCriteria.Operator.fromSymbol(operatorStr);

        // For IS_NULL and IS_NOT_NULL, value is not needed
        if (operator == ExportCriteria.Operator.IS_NULL || operator == ExportCriteria.Operator.IS_NOT_NULL) {
            value = null;
        }

        return new ExportCriteria(field, operator, value);
    }
}
