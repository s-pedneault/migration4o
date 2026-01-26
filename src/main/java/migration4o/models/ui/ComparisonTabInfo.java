package migration4o.models.ui;

import migration4o.ui.panels.database_panels.conformity_analysis_panel.SchemaComparisonPanel;

/**
 * Model class holding information about a comparison tab in the main window.
 * Tracks the reference schema tab, compared schema tab, title, and comparison
 * panel.
 */
public class ComparisonTabInfo {
    public SchemaTabInfo referenceTab;
    public SchemaTabInfo comparedTab;
    public String title;
    public SchemaComparisonPanel panel;

    public ComparisonTabInfo(SchemaTabInfo referenceTab, SchemaTabInfo comparedTab, String title,
            SchemaComparisonPanel panel) {
        this.referenceTab = referenceTab;
        this.comparedTab = comparedTab;
        this.title = title;
        this.panel = panel;
    }

    public SchemaTabInfo getReferenceTab() {
        return referenceTab;
    }

    public void setReferenceTab(SchemaTabInfo referenceTab) {
        this.referenceTab = referenceTab;
    }

    public SchemaTabInfo getComparedTab() {
        return comparedTab;
    }

    public void setComparedTab(SchemaTabInfo comparedTab) {
        this.comparedTab = comparedTab;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SchemaComparisonPanel getPanel() {
        return panel;
    }

    public void setPanel(SchemaComparisonPanel panel) {
        this.panel = panel;
    }
}
