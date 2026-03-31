package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.popups;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.layout.LayoutNode;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout.TableBlock;
import migration4o.util.DatabaseUtil;

/**
 * Popup editor for TABLE properties: collection ref, columns, titles, widths.
 */
public class TablePropertiesPopup {

    /**
     * Show a modal popup to edit table properties.
     * @return true if OK was pressed
     */
    public static boolean show(Component parent, TableBlock block, DOSchemaClass schemaClass, DOSchema refSchema) {
        LayoutNode node = block.getLayoutNode();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.setPreferredSize(new Dimension(500, 400));

        // Collection ref (read-only for now)
        JTextField refField = new JTextField(node.prop("ref", ""), 25);
        refField.setEditable(false);
        refField.setBackground(new Color(240, 240, 240));
        addRow(panel, "Collection:", refField);

        // Resolve child fields for column selection
        String ref = node.prop("ref", "");
        List<String> availableFieldNames = new ArrayList<>();
        Map<String, String> labelsByField = new HashMap<>();
        resolveChildFields(ref, schemaClass, refSchema, availableFieldNames, labelsByField);

        // Parse current columns
        String[] currentCols = node.prop("columns", "").isEmpty() ? new String[0] : node.prop("columns").split(",");
        String[] currentTitles = node.prop("columnTitles", "").isEmpty() ? new String[0] : node.prop("columnTitles").split(",", -1);
        String[] currentWidths = node.prop("widths", "").isEmpty() ? new String[0] : node.prop("widths").split(",", -1);
        Set<String> enabledCols = new LinkedHashSet<>(Arrays.asList(currentCols));

        // Build ordered list: enabled first, then remaining
        List<String> orderedNames = new ArrayList<>();
        for (String c : currentCols) {
            String name = c.trim();
            if (!name.isEmpty())
                orderedNames.add(name);
        }
        for (String f : availableFieldNames) {
            if (!orderedNames.contains(f))
                orderedNames.add(f);
        }

        // Column configuration with checkboxes
        JPanel colsPanel = new JPanel();
        colsPanel.setLayout(new BoxLayout(colsPanel, BoxLayout.Y_AXIS));
        colsPanel.setBorder(BorderFactory.createTitledBorder("Columns"));

        List<JCheckBox> checkboxes = new ArrayList<>();
        List<JTextField> titleFields = new ArrayList<>();
        List<JTextField> widthFields = new ArrayList<>();

        for (int i = 0; i < orderedNames.size(); i++) {
            String fname = orderedNames.get(i);
            boolean enabled = enabledCols.contains(fname);
            int colIdx = Arrays.asList(currentCols).indexOf(fname);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JCheckBox cb = new JCheckBox("", enabled);
            JLabel nameLbl = new JLabel(labelsByField.getOrDefault(fname, fname));
            nameLbl.setPreferredSize(new Dimension(120, 20));
            nameLbl.setToolTipText(fname);

            String titleVal = (colIdx >= 0 && colIdx < currentTitles.length) ? currentTitles[colIdx].trim() : "";
            JTextField titleTf = new JTextField(titleVal, 8);
            titleTf.setToolTipText("Column title override");

            String widthVal = (colIdx >= 0 && colIdx < currentWidths.length) ? currentWidths[colIdx].trim() : "";
            JTextField widthTf = new JTextField(widthVal, 3);
            widthTf.setToolTipText("Width %");

            row.add(cb);
            row.add(nameLbl);
            row.add(new JLabel("Title:"));
            row.add(titleTf);
            row.add(new JLabel("W:"));
            row.add(widthTf);

            checkboxes.add(cb);
            titleFields.add(titleTf);
            widthFields.add(widthTf);
            colsPanel.add(row);
        }

        JScrollPane scrollPane = new JScrollPane(colsPanel);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(scrollPane);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Table Properties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Serialize columns
            StringBuilder cols = new StringBuilder();
            StringBuilder titles = new StringBuilder();
            StringBuilder widths = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < orderedNames.size(); i++) {
                if (!checkboxes.get(i).isSelected())
                    continue;
                if (!first) {
                    cols.append(',');
                    titles.append(',');
                    widths.append(',');
                }
                first = false;
                cols.append(orderedNames.get(i));
                titles.append(titleFields.get(i).getText().trim());
                widths.append(widthFields.get(i).getText().trim());
            }
            node.setProp("columns", cols.toString());
            node.setProp("columnTitles", titles.toString());
            node.setProp("widths", widths.toString());
            block.refreshFromNode();
            return true;
        }
        return false;
    }

    private static void resolveChildFields(String ref, DOSchemaClass schemaClass, DOSchema refSchema, List<String> fieldNames, Map<String, String> labels) {
        if (ref.isEmpty())
            return;

        // Walk the ref path to find the collection field
        String[] parts = ref.split("\\.");
        DOSchemaClass current = schemaClass;
        DOSchemaField collField = null;
        for (int i = 0; i < parts.length; i++) {
            collField = DatabaseUtil.findSchemaFieldByDestinationNameIncludingAncestors(current, parts[i], refSchema);
            if (collField == null)
                return;
            if (i < parts.length - 1) {
                String nextType = collField.attributes.isCollection && collField.attributes.childrenType != null ? collField.attributes.childrenType : collField.attributes.type;
                current = findClassByType(nextType, refSchema);
                if (current == null)
                    return;
            }
        }

        if (collField == null || collField.attributes.childrenType == null)
            return;
        DOSchemaClass childClass = findClassByType(collField.attributes.childrenType, refSchema);
        if (childClass == null)
            return;

        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.destinationName == null)
                continue;
            if (sf.attributes.isCollection || (sf.attributes.embedContents && !isPrimitiveType(sf.attributes.type)))
                continue;
            fieldNames.add(sf.attributes.destinationName);
            String label = sf.attributes.title != null ? sf.attributes.title : sf.attributes.destinationName;
            labels.put(sf.attributes.destinationName, label);
        }
    }

    private static DOSchemaClass findClassByType(String typeName, DOSchema refSchema) {
        if (typeName == null || refSchema == null)
            return null;
        DOSchemaClass cls = refSchema.findClassByName(typeName);
        if (cls != null)
            return cls;
        String shortName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
        for (DOSchemaClass c : refSchema.getClasses()) {
            if (c.attributes.source != null && c.attributes.source.endsWith("." + shortName))
                return c;
        }
        return null;
    }

    private static boolean isPrimitiveType(String type) {
        if (type == null)
            return true;
        return type.equals("string") || type.equals("int") || type.equals("long") || type.equals("float") || type.equals("double") || type.equals("boolean") || type.equals("date") || type.equals("byte") || type.equals("short") || type.equals("char") || type.startsWith("java.lang.");
    }

    private static void addRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(90, 25));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createRigidArea(new Dimension(0, 6)));
    }
}
