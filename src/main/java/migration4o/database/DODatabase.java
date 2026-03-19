package migration4o.database;

import migration4o.models.schema.DOSchema;
import migration4o.util.ClassUtil;

public class DODatabase {

    public DOSchema schema;
    public DODatabaseAttributes attributes = new DODatabaseAttributes();
    public DODatabaseClass[] classes;

    public DODatabase() {
        this.classes = new DODatabaseClass[0];
    }

    public DODatabaseClass[] getClasses() {
        return classes;
    }

    public DODatabaseClass findClassByName(String className) {
        if (className == null || classes == null) {
            return null;
        }
        for (DODatabaseClass dbClass : classes) {
            if (dbClass.attributes.source.equals(className)) {
                return dbClass;
            }
        }
        String searchSimpleName = ClassUtil.getSimpleName(className);
        for (DODatabaseClass dbClass : classes) {
            String classSimpleName = ClassUtil.getSimpleName(dbClass.attributes.source);
            if (classSimpleName.equals(searchSimpleName)) {
                return dbClass;
            }
        }
        return null;
    }

}
