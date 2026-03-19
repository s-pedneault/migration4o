package migration4o.database;

import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;

public class DODatabaseClass {

    public DODatabase database;
    public DOSchemaClass schemaClass;
    public DODatabaseClassAttributes attributes = new DODatabaseClassAttributes();
    public DODatabaseField[] fields;

    public long[] objectIds;
    public long[] uniqueObjectIds;

    public DODatabaseClass(DODatabase database) {
        this.database = database;
    }

    public String getSourcePackage() {
        return ClassUtil.getPackageName(attributes.source);
    }

    public String getSourceName() {
        return ClassUtil.getSimpleName(attributes.source);
    }

    public void setFields(DODatabaseField[] fields) {
        this.fields = fields;
        if (fields != null) {
            for (DODatabaseField field : fields) {
                if (field != null) {
                    field.parentClass = this;
                }
            }
        }
    }

}
