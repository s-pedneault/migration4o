package migration4o.models.ui;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseService;
import migration4o.models.schema.DOSchemaClass;

/**
 * Model class representing a class node in the migration structure tree.
 * Handles display formatting with object counts and export configuration.
 */
public class ClassNode {
    private DOSchemaClass schemaClass;
    private DODatabaseClass dbClass; // Optional database class for object counts
    private ClassExportConfig exportConfig; // Optional export configuration
    private Integer filteredObjectCount; // Cached filtered count

    public ClassNode(DOSchemaClass schemaClass) {
        this.schemaClass = schemaClass;
    }

    public ClassNode(DOSchemaClass schemaClass, DODatabaseClass dbClass) {
        this.schemaClass = schemaClass;
        this.dbClass = dbClass;
    }

    public DOSchemaClass getSchemaClass() {
        return schemaClass;
    }

    public DODatabaseClass getDbClass() {
        return dbClass;
    }

    public void setDbClass(DODatabaseClass dbClass) {
        this.dbClass = dbClass;
        this.filteredObjectCount = null;
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
        // Prefer DODatabaseClass for object counts
        long[] uniqueIds = null;
        if (dbClass != null && dbClass.objects.uniqueObjectIds != null) {
            uniqueIds = dbClass.objects.uniqueObjectIds;
        } else if (schemaClass.uniqueObjectIds != null) {
            uniqueIds = schemaClass.uniqueObjectIds;
        }
        int totalCount = uniqueIds != null ? uniqueIds.length : 0;

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
        if (container != null && uniqueIds != null) {
            filteredObjectCount = exportConfig.countMatchingObjects(container, uniqueIds);
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
            display.append(schemaClass.attributes.destinationName);
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
