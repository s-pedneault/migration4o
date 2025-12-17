package migration4o.engine.migration.generic;

import migration4o.models.schema.DOSchemaClass;
import migration4o.engine.migration.generic.ExportColumn;
import migration4o.models.database.DODatabaseClass;

import java.util.List;

/**
 * Context for class-level export operations.
 * Contains all information needed for exporting objects of a specific class.
 */
public class ClassExportContext extends ExportContext {
    private final ModuleExportContext moduleContext;
    private final DOSchemaClass schemaClass;
    private final DODatabaseClass databaseClass;
    private final List<ExportColumn> columns;
    private final String exportName;
    private final int totalObjectCount;

    public ClassExportContext(ModuleExportContext moduleContext, DOSchemaClass schemaClass,
            DODatabaseClass databaseClass, List<ExportColumn> columns,
            String exportName, int totalObjectCount) {
        super(moduleContext.getOutputDirectory());
        this.moduleContext = moduleContext;
        this.schemaClass = schemaClass;
        this.databaseClass = databaseClass;
        this.columns = columns;
        this.exportName = exportName;
        this.totalObjectCount = totalObjectCount;
    }

    public ModuleExportContext getModuleContext() {
        return moduleContext;
    }

    public DOSchemaClass getSchemaClass() {
        return schemaClass;
    }

    public DODatabaseClass getDatabaseClass() {
        return databaseClass;
    }

    public List<ExportColumn> getColumns() {
        return columns;
    }

    public String getExportName() {
        return exportName;
    }

    public String getShortName() {
        return schemaClass.getShortName();
    }

    public int getTotalObjectCount() {
        return totalObjectCount;
    }
}