package migration4o.models.ui;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseService;
import migration4o.models.schema.DOSchemaClass;

/**
 * Model class representing a class node in the migration structure tree.
 * Handles display formatting with object counts and export configuration.
 */
public class ClassNode {
    private DOSchemaClass schemaClass;
    private ClassExportConfig exportConfig; // Optional export configuration
    private Integer filteredObjectCount; // Cached filtered count

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
        this.filteredObjectCount = null; // Reset cached count
    }

    public boolean hasConfiguration() {
        return exportConfig != null && (exportConfig.hasCustomDestination() || exportConfig.hasCriteria());
    }

    /**
     * Gets the object count, applying criteria filtering if configured.
     * 
     * @return The count of objects that match the export criteria, or total count
     *         if no criteria
     */
    public int getObjectCount() {
        int totalCount = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0;

        // If no criteria or no objects, return total count
        if (exportConfig == null || !exportConfig.hasCriteria() || totalCount == 0) {
            return totalCount;
        }

        // Use cached filtered count if available
        if (filteredObjectCount != null) {
            return filteredObjectCount;
        }

        // Calculate filtered count
        migration4o.database.DODatabaseContext dbContext = migration4o.ui.main.MainWindow.getInstance().getCurrentContext();
        ExtObjectContainer container = dbContext != null ? dbContext.container : null;
        if (container != null && schemaClass.uniqueObjectIds != null) {
            filteredObjectCount = exportConfig.countMatchingObjects(container, schemaClass.uniqueObjectIds);
            return filteredObjectCount;
        }

        return totalCount;
    }

    @Override
    public String toString() {
        int objectCount = getObjectCount();

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
