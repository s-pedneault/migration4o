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
 * - Schema classes: blue and underlined with hand cursor (clickable appearance)
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
            if (schema != null && schema.getClasses() != null) {
                for (DOSchemaClass cls : schema.getClasses()) {
                    String shortName = cls.source.contains(".")
                            ? cls.source.substring(cls.source.lastIndexOf('.') + 1)
                            : cls.source;
                    if (cls.source.equals(typeName) || shortName.equals(typeName)) {
                        isSchemaClass = true;
                        break;
                    }
                }
            }

            if (isSchemaClass) {
                // Class: blue and underlined
                setText("<html><u><font color='blue'>" + typeName + "</font></u></html>");
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else if (TypeUtil.isPrimitiveType(typeName)) {
                // Primitive: green
                setText("<html><font color='green'>" + typeName + "</font></html>");
                setCursor(Cursor.getDefaultCursor());
            } else {
                // Other: red (unresolved)
                setText("<html><font color='red'>" + typeName + "</font></html>");
                setCursor(Cursor.getDefaultCursor());
            }
        } else {
            setCursor(Cursor.getDefaultCursor());
        }

        return c;
    }
}
