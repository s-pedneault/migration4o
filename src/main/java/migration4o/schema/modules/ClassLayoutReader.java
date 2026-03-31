package migration4o.schema.modules;

import org.w3c.dom.*;

import migration4o.models.ui.layout.DetailLayout;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.models.ui.layout.LayoutNodeType;

import javax.xml.parsers.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads standalone class layouts from class-layouts.xml.
 */
class ClassLayoutReader {

    Map<String, DetailLayout> readClassLayouts(String filePath) throws Exception {
        Map<String, DetailLayout> layouts = new LinkedHashMap<>();

        File file = new File(filePath);
        if (!file.exists()) {
            return layouts;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "classLayout".equals(node.getNodeName())) {
                Element el = (Element) node;
                String sourceName = el.getAttribute("sourceName");
                if (sourceName == null || sourceName.isEmpty())
                    continue;

                // Find the <layout> child
                NodeList layoutChildren = el.getChildNodes();
                for (int j = 0; j < layoutChildren.getLength(); j++) {
                    Node lc = layoutChildren.item(j);
                    if (lc.getNodeType() == Node.ELEMENT_NODE && "layout".equals(lc.getNodeName())) {
                        DetailLayout layout = parseLayout((Element) lc);
                        if (layout != null) {
                            layouts.put(sourceName, layout);
                        }
                        break;
                    }
                }
            }
        }
        return layouts;
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
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            node.setProp(attr.getNodeName(), attr.getNodeValue());
        }

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
