
package dataobjects.impl.models.schema;

import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.impl.models.DOClassImpl;
import dataobjects.api.models.DOField;
import dataobjects.api.models.DOReference;
import dataobjects.api.models.database.DODatabaseClass;

public class DOSchemaClassImpl extends DOClassImpl implements DOSchemaClass {
    private final String exportName;
    private DODatabaseClass databaseClass;

    public DOSchemaClassImpl(String absoluteName, String shortName, String description, String title,
            String superClassAbsoluteName,
            DOField[] fields, String exportName) {
        super(absoluteName, shortName, description, title, superClassAbsoluteName, fields);
        this.exportName = exportName;
    }

    @Override
    public String getExportName() {
        return exportName;
    }

    @Override
    public DODatabaseClass getDatabaseClass() {
        return databaseClass;
    }

    @Override
    public void setDatabaseClass(DODatabaseClass databaseClass) {
        this.databaseClass = databaseClass;
    }
}
