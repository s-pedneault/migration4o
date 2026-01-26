package migration4o.models.ui;

import migration4o.models.schema.DOSchema;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.SchemaEditorPanel;

/**
 * Model class holding information about a schema tab in the main window.
 * Tracks the tab label, schema data, editor panel, and whether it's the
 * reference schema.
 */
public class SchemaTabInfo {
    public String label;
    public DOSchema schema;
    public SchemaEditorPanel editorPanel;
    public boolean isReference; // true if this is the XML reference schema

    public SchemaTabInfo(String label, DOSchema schema, SchemaEditorPanel editorPanel, boolean isReference) {
        this.label = label;
        this.schema = schema;
        this.editorPanel = editorPanel;
        this.isReference = isReference;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public DOSchema getSchema() {
        return schema;
    }

    public void setSchema(DOSchema schema) {
        this.schema = schema;
    }

    public SchemaEditorPanel getEditorPanel() {
        return editorPanel;
    }

    public void setEditorPanel(SchemaEditorPanel editorPanel) {
        this.editorPanel = editorPanel;
    }

    public boolean isReference() {
        return isReference;
    }

    public void setReference(boolean isReference) {
        this.isReference = isReference;
    }

    @Override
    public String toString() {
        return label + (isReference ? " (Reference)" : "");
    }
}
