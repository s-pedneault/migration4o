
package migration4o.models.schema;

import migration4o.models.DOClass;
import migration4o.models.DOField;
import migration4o.models.database.DODatabaseClass;

public class DOSchemaClass extends DOClass {
    private final String exportName;
    private DODatabaseClass databaseClass;

    public DOSchemaClass(String absoluteName, String shortName, String description, String title,
            String superClassAbsoluteName,
            DOField[] fields, String exportName) {
        super(absoluteName, shortName, description, title, superClassAbsoluteName, fields);
        this.exportName = exportName;
    }

    public String getExportName() {
        return exportName;
    }

    public DODatabaseClass getDatabaseClass() {
        return databaseClass;
    }

    public void setDatabaseClass(DODatabaseClass databaseClass) {
        this.databaseClass = databaseClass;
    }
}
