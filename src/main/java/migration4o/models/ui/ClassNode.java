package migration4o.models.ui;

import migration4o.models.schema.DOSchemaClass;

/**
 * Model class representing a class node in the migration structure tree.
 * Handles display formatting with object counts and export configuration.
 */
public class ClassNode {
    private DOSchemaClass schemaClass;
    private ClassExportConfig exportConfig; // Optional export configuration

    public ClassNode(DOSchemaClass schemaClass) {
        this.schemaClass = schemaClass;
    }

    public DOSchemaClass getSchemaClass() {
        return schemaClass;
    }

    public ClassExportConfig getExportConfig() {
        return exportConfig;
    }

    public void setExportConfig(ClassExportConfig exportConfig) {
        this.exportConfig = exportConfig;
    }

    public boolean hasConfiguration() {
        return exportConfig != null &&
                (exportConfig.hasCustomDestination() || exportConfig.hasCriteria());
    }

    @Override
    public String toString() {
        int objectCount = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0;

        StringBuilder display = new StringBuilder();

        // Show custom destination if set, otherwise use schema destination name
        if (exportConfig != null && exportConfig.hasCustomDestination()) {
            display.append(exportConfig.getDestinationFileName());
        } else {
            display.append(schemaClass.destinationName);
        }

        // Show object count
        if (objectCount > 0) {
            display.append(" (").append(objectCount).append(" objects)");
        }

        // Show configuration indicator
        if (exportConfig != null && exportConfig.hasCriteria()) {
            display.append(" [").append(exportConfig.getCriteria().size()).append(" filter(s)]");
        }

        return display.toString();
    }
}
