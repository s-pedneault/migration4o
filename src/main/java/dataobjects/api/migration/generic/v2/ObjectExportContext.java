package dataobjects.api.migration.generic.v2;

import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.migration.generic.ExportColumn;
import java.util.List;

/**
 * Context for object-level export operations.
 * Contains all information needed for exporting a single database object.
 */
public class ObjectExportContext extends ExportContext {
    private final ClassExportContext classContext;
    private final DODatabaseObject databaseObject;
    private final int rowIndex;

    public ObjectExportContext(ClassExportContext classContext, DODatabaseObject databaseObject, int rowIndex) {
        super(classContext.getOutputDirectory());
        this.classContext = classContext;
        this.databaseObject = databaseObject;
        this.rowIndex = rowIndex;
    }

    public ClassExportContext getClassContext() {
        return classContext;
    }

    public ModuleExportContext getModuleContext() {
        return classContext.getModuleContext();
    }

    public DODatabaseObject getDatabaseObject() {
        return databaseObject;
    }

    public long getObjectId() {
        return databaseObject.getObjectId();
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public List<ExportColumn> getColumns() {
        return classContext.getColumns();
    }
}