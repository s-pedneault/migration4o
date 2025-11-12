package dataobjects.api.models.schema;

import dataobjects.api.models.*;
import dataobjects.api.models.database.DODatabaseClass;

public interface DOSchemaClass extends DOClass {

    // Returns the name of the class to use for exporting data
    public String getExportName();

    public DODatabaseClass getDatabaseClass();

    public void setDatabaseClass(DODatabaseClass databaseClass);

}
