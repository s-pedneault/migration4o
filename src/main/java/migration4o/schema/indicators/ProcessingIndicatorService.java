package migration4o.schema.indicators;

import migration4o.models.ui.ExportCriteria;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Singleton service that manages the ordered list of processing indicators used in the cost panel. Each indicator groups one or more classes (with optional criteria); its count is the sum of matching objects. Persists to {@code schema/cost-indicators.xml}.
 *
 * <p>
 * XML format:
 * 
 * <pre>{@code
 * <indicators>
 *   <indicator name="Optional name">
 *     <class name="gest.dossPrev.DossPrev">
 *       <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
 *     </class>
 *     <class name="gest.dossPrev.DossPrevSuppr"/>
 *   </indicator>
 * </indicators>
 * }</pre>
 */
public class ProcessingIndicatorService {

    public static final String DEFAULT_FILE = "schema/cost-indicators.xml";

    private static ProcessingIndicatorService instance;

    private final List<ProcessingIndicator> indicators = new ArrayList<>();

    private ProcessingIndicatorService() {
    }

    public static synchronized ProcessingIndicatorService getInstance() {
        if (instance == null) {
            instance = new ProcessingIndicatorService();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Nested model types
    // -------------------------------------------------------------------------

    /** A single class entry within an indicator, with optional filter criteria. */
    public record IndicatorClass(String className, List<ExportCriteria> criteria) {
        public IndicatorClass(String className) {
            this(className, Collections.emptyList());
        }
    }

    /** An indicator row: one or more classes whose object counts are summed. */
    public record ProcessingIndicator(String name, List<IndicatorClass> classes) {
        public ProcessingIndicator(String className) {
            this(null, List.of(new IndicatorClass(className)));
        }
    }

    // -------------------------------------------------------------------------
    // Read / write
    // -------------------------------------------------------------------------

    /** Loads indicators from disk. Silently returns if the file does not exist. */
    public synchronized void load() {
        load(DEFAULT_FILE);
    }

    public synchronized void load(String filePath) {
        indicators.clear();
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList indicatorNodes = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < indicatorNodes.getLength(); i++) {
                Node iNode = indicatorNodes.item(i);
                if (iNode.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                // Support legacy flat <class name="..."/> elements (upgrade on first load)
                if ("class".equals(iNode.getNodeName())) {
                    String n = ((Element) iNode).getAttribute("name");
                    if (n != null && !n.isBlank()) {
                        indicators.add(new ProcessingIndicator(n));
                    }
                    continue;
                }

                if (!"indicator".equals(iNode.getNodeName()))
                    continue;
                Element indicatorEl = (Element) iNode;
                String indicatorName = indicatorEl.getAttribute("name");
                if (indicatorName.isBlank())
                    indicatorName = null;

                List<IndicatorClass> classes = new ArrayList<>();
                NodeList classNodes = indicatorEl.getChildNodes();
                for (int j = 0; j < classNodes.getLength(); j++) {
                    Node cNode = classNodes.item(j);
                    if (cNode.getNodeType() != Node.ELEMENT_NODE || !"class".equals(cNode.getNodeName()))
                        continue;
                    Element classEl = (Element) cNode;
                    String className = classEl.getAttribute("name");
                    if (className == null || className.isBlank())
                        continue;

                    List<ExportCriteria> criteria = new ArrayList<>();
                    NodeList critNodes = classEl.getChildNodes();
                    for (int k = 0; k < critNodes.getLength(); k++) {
                        Node crNode = critNodes.item(k);
                        if (crNode.getNodeType() != Node.ELEMENT_NODE || !"criteria".equals(crNode.getNodeName()))
                            continue;
                        Element crEl = (Element) crNode;
                        String field = crEl.getAttribute("field");
                        String operator = crEl.getAttribute("operator");
                        String value = crEl.getAttribute("value");
                        if (!field.isBlank() && !operator.isBlank()) {
                            criteria.add(new ExportCriteria(field, ExportCriteria.Operator.fromSymbol(operator), value));
                        }
                    }
                    classes.add(new IndicatorClass(className, criteria));
                }
                if (!classes.isEmpty()) {
                    indicators.add(new ProcessingIndicator(indicatorName, classes));
                }
            }
        } catch (Exception e) {
            System.err.println("ProcessingIndicatorService: failed to load " + filePath + ": " + e.getMessage());
        }
    }

    /** Saves the current indicator list to disk. */
    public synchronized void save() {
        save(DEFAULT_FILE);
    }

    public synchronized void save(String filePath) {
        try (FileWriter w = new FileWriter(filePath)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<indicators>\n");
            for (ProcessingIndicator indicator : indicators) {
                if (indicator.name() != null) {
                    w.write("    <indicator name=\"" + escapeXml(indicator.name()) + "\">\n");
                } else {
                    w.write("    <indicator>\n");
                }
                for (IndicatorClass ic : indicator.classes()) {
                    if (ic.criteria().isEmpty()) {
                        w.write("        <class name=\"" + escapeXml(ic.className()) + "\"/>\n");
                    } else {
                        w.write("        <class name=\"" + escapeXml(ic.className()) + "\">\n");
                        for (ExportCriteria c : ic.criteria()) {
                            w.write("            <criteria field=\"" + escapeXml(c.getFieldName()) + "\" operator=\"" + escapeXml(c.getOperator().getSymbol()) + "\" value=\"" + escapeXml(c.getValue()) + "\"/>\n");
                        }
                        w.write("        </class>\n");
                    }
                }
                w.write("    </indicator>\n");
            }
            w.write("</indicators>\n");
        } catch (IOException e) {
            System.err.println("ProcessingIndicatorService: failed to save " + filePath + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable snapshot of the ordered indicators. */
    public synchronized List<ProcessingIndicator> getIndicators() {
        return List.copyOf(indicators);
    }

    /**
     * Returns the first class name of each indicator. Used by the ProcessingIndicatorsPanel which manages single-class indicators.
     */
    public synchronized List<String> getClassNames() {
        List<String> names = new ArrayList<>();
        for (ProcessingIndicator ind : indicators) {
            if (!ind.classes().isEmpty()) {
                names.add(ind.classes().get(0).className());
            }
        }
        return Collections.unmodifiableList(names);
    }

    /** Replaces the entire list with single-class indicators (no criteria) and saves. */
    public synchronized void setClassNames(List<String> names) {
        indicators.clear();
        for (String name : names) {
            indicators.add(new ProcessingIndicator(name));
        }
        save();
    }

    /** Adds a single-class indicator at the end (if no indicator with that first class already exists) and saves. */
    public synchronized void add(String className) {
        if (className == null || className.isBlank())
            return;
        boolean exists = indicators.stream().anyMatch(ind -> !ind.classes().isEmpty() && ind.classes().get(0).className().equals(className));
        if (!exists) {
            indicators.add(new ProcessingIndicator(className));
            save();
        }
    }

    /** Removes the first indicator whose first class name matches, and saves. */
    public synchronized void remove(String className) {
        boolean removed = indicators.removeIf(ind -> !ind.classes().isEmpty() && ind.classes().get(0).className().equals(className));
        if (removed)
            save();
    }

    /** Moves the indicator at {@code index} up by one position. */
    public synchronized void moveUp(int index) {
        if (index > 0 && index < indicators.size()) {
            ProcessingIndicator tmp = indicators.get(index - 1);
            indicators.set(index - 1, indicators.get(index));
            indicators.set(index, tmp);
            save();
        }
    }

    /** Moves the indicator at {@code index} down by one position. */
    public synchronized void moveDown(int index) {
        if (index >= 0 && index < indicators.size() - 1) {
            ProcessingIndicator tmp = indicators.get(index + 1);
            indicators.set(index + 1, indicators.get(index));
            indicators.set(index, tmp);
            save();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
