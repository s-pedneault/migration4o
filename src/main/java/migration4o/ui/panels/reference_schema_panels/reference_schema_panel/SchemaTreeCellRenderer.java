package migration4o.ui.panels.reference_schema_panels.reference_schema_panel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.SchemaTreeNode;

/**
 * Custom tree cell renderer for schema tree nodes.
 * Provides different icons and colors for different node types.
 */
public class SchemaTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Color MODULE_COLOR = new Color(0, 100, 200);
    private static final Color CLASS_COLOR = new Color(0, 150, 0);
    private static final Color FIELD_COLOR = new Color(100, 100, 100);
    private static final Color DISABLED_COLOR = new Color(150, 150, 150);
    private static final Color ERROR_COLOR = new Color(200, 0, 0);

    private SchemaEditorPanel editorPanel;

    public SchemaTreeCellRenderer(SchemaEditorPanel editorPanel) {
        this.editorPanel = editorPanel;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof SchemaTreeNode) {
            SchemaTreeNode node = (SchemaTreeNode) value;

            switch (node.getNodeType()) {
                case ROOT:
                    setIcon(UIManager.getIcon("FileView.hardDriveIcon"));
                    setFont(getFont().deriveFont(Font.BOLD));
                    break;

                case MODULE:
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    if (!selected) {
                        setForeground(MODULE_COLOR);
                    }
                    setFont(getFont().deriveFont(Font.BOLD));
                    break;

                case FOLDER:
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    if (!selected) {
                        setForeground(MODULE_COLOR);
                    }
                    setFont(getFont().deriveFont(Font.ITALIC));
                    break;

                case CLASS:
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                    String classText = node.toString();
                    boolean hasErrors = false;

                    // Check if this class node has errors
                    if (editorPanel != null && node.getSchemaElement() instanceof DOSchemaClass) {
                        hasErrors = editorPanel.hasErrors((DOSchemaClass) node.getSchemaElement());
                    }

                    if (hasErrors) {
                        if (!selected) {
                            setForeground(ERROR_COLOR);
                        }
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (classText.contains("(not exported)")) {
                        if (!selected) {
                            setForeground(DISABLED_COLOR);
                        }
                        setFont(getFont().deriveFont(Font.ITALIC));
                    } else {
                        if (!selected) {
                            setForeground(CLASS_COLOR);
                        }
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                    break;

                case FIELD:
                    setIcon(null);
                    String fieldText = node.toString();
                    if (fieldText.contains("(not exported)")) {
                        if (!selected) {
                            setForeground(DISABLED_COLOR);
                        }
                        setFont(getFont().deriveFont(Font.ITALIC));
                    } else {
                        if (!selected) {
                            setForeground(FIELD_COLOR);
                        }
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                    break;
            }
        }

        return this;
    }
}
