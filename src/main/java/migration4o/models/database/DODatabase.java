package migration4o.models.database;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseEncoding;

public class DODatabase {
    private final ExtObjectContainer container;
    private final DODatabaseEncoding encoding;
    private final int totalClasses;
    private final int totalObjects;
    private final String databaseSize;
    private final DODatabaseClass[] classes;

    public DODatabase(ExtObjectContainer container, DODatabaseEncoding encoding, int totalClasses, int totalObjects,
            String databaseSize, DODatabaseClass[] classes) {
        this.container = container;
        this.encoding = encoding;
        this.totalClasses = totalClasses;
        this.totalObjects = totalObjects;
        this.databaseSize = databaseSize;
        this.classes = classes != null ? classes : new DODatabaseClass[0];
    }

    public ExtObjectContainer getContainer() {
        return container;
    }

    public DODatabaseEncoding getEncoding() {
        return encoding;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public int getTotalObjects() {
        return totalObjects;
    }

    public String getDatabaseSize() {
        return databaseSize;
    }

    public DODatabaseClass[] getClasses() {
        return classes;
    }
}
