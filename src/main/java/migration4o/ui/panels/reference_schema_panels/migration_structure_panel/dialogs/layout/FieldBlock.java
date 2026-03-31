package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs.layout;

import java.awt.*;
import javax.swing.*;

import migration4o.models.ui.layout.LayoutNode;

/**
 * Visual block for FIELD nodes. Single-line chip with type-color coding.
 * Shows: [type-color bar] label · ref
 */
public class FieldBlock extends LayoutBlockPanel {

    private JLabel labelText;
    private JLabel refText;
    private JPanel colorBar;
    private String resolvedTitle; // title from the reference schema

    // Type-based colors
    private static final Color COLOR_DATE = new Color(59, 130, 246); // blue
    private static final Color COLOR_BOOL = new Color(34, 197, 94); // green
    private static final Color COLOR_STRING = new Color(148, 163, 184); // gray
    private static final Color COLOR_INT = new Color(168, 85, 247); // purple
    private static final Color COLOR_IDENTITY = new Color(249, 115, 22); // orange
    private static final Color COLOR_DEFAULT = new Color(148, 163, 184); // gray

    public FieldBlock(LayoutNode node) {
        super(node);
        setLayout(new BorderLayout(6, 0));
        setBackground(new Color(250, 251, 252));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1), BorderFactory.createEmptyBorder(4, 0, 4, 8)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        setPreferredSize(new Dimension(300, 28));

        // Left color bar
        colorBar = new JPanel();
        colorBar.setPreferredSize(new Dimension(4, 28));
        colorBar.setBackground(COLOR_DEFAULT);
        add(colorBar, BorderLayout.WEST);

        // Center: label and ref
        JPanel center = new JPanel(new BorderLayout(4, 0));
        center.setOpaque(false);

        labelText = new JLabel(getDisplayLabel());
        labelText.setFont(labelText.getFont().deriveFont(Font.BOLD, 12f));
        labelText.setForeground(new Color(30, 41, 59));
        center.add(labelText, BorderLayout.WEST);

        refText = new JLabel(" · " + node.prop("ref", "?"));
        refText.setFont(refText.getFont().deriveFont(Font.PLAIN, 11f));
        refText.setForeground(new Color(100, 116, 139));
        center.add(refText, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);
    }

    private String getDisplayLabel() {
        String label = layoutNode.prop("label", "");
        if (!label.isEmpty())
            return label;
        if (resolvedTitle != null && !resolvedTitle.isEmpty())
            return resolvedTitle;
        String ref = layoutNode.prop("ref", "?");
        // Humanize the last segment of the dot path
        int dot = ref.lastIndexOf('.');
        String name = dot >= 0 ? ref.substring(dot + 1) : ref;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c))
                sb.append(' ');
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    /** Set the resolved display title from the reference schema. */
    public void setResolvedTitle(String title) {
        this.resolvedTitle = title;
        labelText.setText(getDisplayLabel());
    }

    /** Set the color bar based on field type. Called by the canvas when type info is available. */
    public void setFieldTypeColor(String typeName) {
        if (typeName == null) {
            colorBar.setBackground(COLOR_DEFAULT);
            return;
        }
        switch (typeName) {
        case "date":
        case "java.util.Date":
        case "java.sql.Timestamp":
            colorBar.setBackground(COLOR_DATE);
            break;
        case "boolean":
        case "java.lang.Boolean":
            colorBar.setBackground(COLOR_BOOL);
            break;
        case "string":
        case "java.lang.String":
            colorBar.setBackground(COLOR_STRING);
            break;
        case "int":
        case "long":
        case "float":
        case "double":
        case "java.lang.Integer":
        case "java.lang.Long":
        case "java.lang.Float":
        case "java.lang.Double":
            colorBar.setBackground(COLOR_INT);
            break;
        default:
            if (typeName.contains("ID") && typeName.contains("Entite"))
                colorBar.setBackground(COLOR_IDENTITY);
            else
                colorBar.setBackground(COLOR_DEFAULT);
            break;
        }
    }

    @Override
    public void refreshFromNode() {
        labelText.setText(getDisplayLabel());
        refText.setText(" · " + layoutNode.prop("ref", "?"));
    }

    @Override
    protected void onDoubleClick() {
        if (getCanvas() != null)
            getCanvas().showFieldProperties(this);
    }
}
