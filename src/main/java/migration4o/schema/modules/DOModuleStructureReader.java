package migration4o.schema.modules;

import org.w3c.dom.*;

import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ExportCriteria;
import migration4o.models.ui.layout.DetailLayout;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.models.ui.layout.LayoutNodeType;

import javax.xml.parsers.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads migration structure from migration-format.xml file. Supports both old format (simple classRef) and new format (classRef with criteria).
 */
public class DOModuleStructureReader {

    public List<DOSchemaModule> readMigrationFormat(String filePath) throws Exception {
        List<DOSchemaModule> modules = new ArrayList<>();

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
                    modules.add(parseModule((Element) node, null));
                }
            }
        }

        return modules;
    }

    private DOSchemaModule parseModule(Element moduleElement, DOSchemaModule parentModule) {
        String name = moduleElement.getAttribute("name");
        String id = moduleElement.getAttribute("id");
        if (id == null || id.isEmpty()) {
            id = name; // Use name as ID if no ID specified
        }
        String icon = moduleElement.getAttribute("icon");
        if (icon != null && icon.isEmpty()) {
            icon = null;
        }
        String tileBg = moduleElement.getAttribute("tile-bg");
        if (tileBg != null && tileBg.isEmpty()) {
            tileBg = null;
        }
        String tileTextColor = moduleElement.getAttribute("tile-text");
        if (tileTextColor != null && tileTextColor.isEmpty()) {
            tileTextColor = null;
        }
        String tileIconColor = moduleElement.getAttribute("tile-icon");
        if (tileIconColor != null && tileIconColor.isEmpty()) {
            tileIconColor = null;
        }
        String tileFontSize = moduleElement.getAttribute("tile-font-size");
        if (tileFontSize != null && tileFontSize.isEmpty()) {
            tileFontSize = null;
        }

        // Create module first so it can be referenced as parent by its children
        DOSchemaModule module = new DOSchemaModule(parentModule);
        module.name = name;
        module.id = id;
        module.icon = icon;
        module.tileBg = tileBg;
        module.tileTextColor = tileTextColor;
        module.tileIconColor = tileIconColor;
        module.tileFontSize = tileFontSize;

        List<ClassExportConfig> classConfigs = new ArrayList<>();
        List<DOSchemaModule> childModules = new ArrayList<>();

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
                    // Recursive call for nested modules — pass current module as parent
                    childModules.add(parseModule(childElement, module));
                }
            }
        }

        module.classConfigs = classConfigs;
        module.children = childModules;
        return module;
    }

    /**
     * Parses a classRef element which can be: - Old format: <classRef sourceName="gest.config.ParamConfig"/> - New format: <classRef sourceName="..." destinationFile="..." description= "..."><criteria field="..." operator="..." value="..."/><unitCost priceList="..." cost="..."/></classRef>
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

        String description = classRefElement.getAttribute("description");
        if (description != null && description.isEmpty()) {
            description = null; // Treat empty string as null
        }

        // Parse criteria if any
        List<ExportCriteria> criteria = new ArrayList<>();
        NodeList criteriaNodes = classRefElement.getElementsByTagName("criteria");
        // System.out.println("DEBUG parseClassRef: sourceName=" + sourceName +
        // ",
        // criteriaNodes.length="
        // + criteriaNodes.getLength());
        for (int i = 0; i < criteriaNodes.getLength(); i++) {
            Element criteriaElement = (Element) criteriaNodes.item(i);
            ExportCriteria criterion = parseCriteria(criteriaElement);
            if (criterion != null) {
                // System.out.println("DEBUG parseClassRef: Added criterion: " +
                // criterion);
                criteria.add(criterion);
            }
        }

        // Parse unit costs if any
        java.util.Map<String, Float> unitCosts = new java.util.HashMap<>();
        NodeList unitCostNodes = classRefElement.getElementsByTagName("unitCost");
        for (int i = 0; i < unitCostNodes.getLength(); i++) {
            Element unitCostElement = (Element) unitCostNodes.item(i);
            String priceList = unitCostElement.getAttribute("priceList");
            String costStr = unitCostElement.getAttribute("cost");
            // Empty priceList is valid (represents "Default" price list)
            if (priceList != null && costStr != null && !costStr.isEmpty()) {
                try {
                    float cost = Float.parseFloat(costStr);
                    unitCosts.put(priceList, cost);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Invalid cost value for price list " + priceList + ": " + costStr);
                }
            }
        }

        ClassExportConfig config = new ClassExportConfig(sourceName, destinationFile, criteria, description, unitCosts);

        // Parse optional title override
        String title = classRefElement.getAttribute("title");
        if (title != null && !title.isEmpty()) {
            config.setTitle(title);
        }

        // Parse default columns (comma-separated list of field paths)
        String defaultColumnsAttr = classRefElement.getAttribute("defaultColumns");
        if (defaultColumnsAttr != null && !defaultColumnsAttr.isEmpty()) {
            List<String> cols = new ArrayList<>();
            for (String c : defaultColumnsAttr.split(",")) {
                String trimmed = c.trim();
                if (!trimmed.isEmpty())
                    cols.add(trimmed);
            }
            config.setDefaultColumns(cols);
        }

        // Parse layout if present
        NodeList layoutNodes = classRefElement.getChildNodes();
        for (int i = 0; i < layoutNodes.getLength(); i++) {
            Node node = layoutNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "layout".equals(node.getNodeName())) {
                config.setLayout(parseLayout((Element) node));
                break;
            }
        }

        if (!criteria.isEmpty()) {
            // System.out.println(
            // "DEBUG parseClassRef: Created config for " + sourceName + " with
            // " +
            // criteria.size() + " criteria");
        }
        return config;
    }

    /**
     * Parses a criteria element: <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
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

    private DetailLayout parseLayout(Element layoutElement) {
        DetailLayout layout = new DetailLayout();
        NodeList children = layoutElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                LayoutNode node = parseLayoutNode((Element) child);
                if (node != null)
                    layout.nodes.add(node);
            }
        }
        return layout.isEmpty() ? null : layout;
    }

    private LayoutNode parseLayoutNode(Element element) {
        LayoutNodeType type = LayoutNodeType.fromXmlTag(element.getNodeName());
        if (type == null)
            return null;

        LayoutNode node = new LayoutNode(type);

        // Copy all attributes as properties
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            node.setProp(attr.getNodeName(), attr.getNodeValue());
        }

        // Recurse children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                LayoutNode childNode = parseLayoutNode((Element) child);
                if (childNode != null)
                    node.children.add(childNode);
            }
        }
        return node;
    }
}
