package migration4o.ui.common.renderers;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.TypeUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Reusable table cell renderer for type columns that colors types based on
 * their category:
 * - Schema classes: black and underlined with hand cursor (clickable
 * appearance)
 * - Primitive types: green
 * - Unresolved types: red
 * 
 * This renderer is used for both Type and ChildrenType columns in schema
 * tables.
 */
public class SchemaTypeRenderer extends DefaultTableCellRenderer {

    private final DOSchema schema;

    /**
     * Creates a new SchemaTypeRenderer.
     * 
     * @param schema The schema to use for checking if types are schema classes
     */
    public SchemaTypeRenderer(DOSchema schema) {
        this.schema = schema;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus,
            int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value != null && !value.toString().isEmpty()) {
            String typeName = value.toString();

            // Check if it's a class in our schema
            boolean isSchemaClass = false;
            String debugInfo = "";
            if (schema != null && schema.getClasses() != null) {
                for (DOSchemaClass cls : schema.getClasses()) {
                    String shortName = cls.source.contains(".")
                            ? cls.source.substring(cls.source.lastIndexOf('.') + 1)
                            : cls.source;

                    // Check full name, short name, and destination name
                    if (cls.source.equals(typeName) ||
                            shortName.equals(typeName) ||
                            (cls.destinationName != null && cls.destinationName.equals(typeName))) {
                        isSchemaClass = true;
                        debugInfo = " (matched: " + cls.source + ")";
                        break;
                    }
                }
            }

            if (isSchemaClass) {
                // Class: black and underlined
                setText("<html><u>" + typeName + "</u></html>");
                setToolTipText("Schema class: " + typeName + debugInfo);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else if (TypeUtil.isPrimitiveType(typeName)) {
                // Primitive: green
                setText("<html><font color='green'>" + typeName + "</font></html>");
                setToolTipText("Primitive type: " + typeName);
                setCursor(Cursor.getDefaultCursor());
            } else {
                // Other: red (unresolved)
                setText("<html><font color='red'>" + typeName + "</font></html>");
                setToolTipText("Unresolved type: '" + typeName + "' is not a schema class or primitive type. Searched "
                        +
                        (schema != null && schema.getClasses() != null ? schema.getClasses().length : 0) + " classes.");
                setCursor(Cursor.getDefaultCursor());
            }
        } else {
            setToolTipText(null);
            setCursor(Cursor.getDefaultCursor());
        }

        return c;
    }
}
