package migration4o.database;

import migration4o.models.schema.DOSchemaField;

public class DODatabaseField {

    public DODatabase database;
    public DODatabaseClass parentClass;
    public DOSchemaField schemaField;
    public DODatabaseFieldAttributes attributes = new DODatabaseFieldAttributes();

    public DODatabaseField(DODatabase database, DODatabaseClass parentClass) {
        this.database = database;
        this.parentClass = parentClass;
    }

}
