package dataobjects.impl.models.database;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.database.DODatabaseEncoding;
import com.db4o.ext.ExtObjectContainer;
import dataobjects.api.models.database.DODatabaseClass;

public class DODatabaseImpl implements DODatabase {
    private final ExtObjectContainer container;
    private final DODatabaseEncoding encoding;
    private final int totalClasses;
    private final int totalObjects;
    private final String databaseSize;
    private final DODatabaseClass[] classes;

    public DODatabaseImpl(ExtObjectContainer container, DODatabaseEncoding encoding, int totalClasses, int totalObjects,
            String databaseSize, DODatabaseClass[] classes) {
        this.container = container;
        this.encoding = encoding;
        this.totalClasses = totalClasses;
        this.totalObjects = totalObjects;
        this.databaseSize = databaseSize;
        this.classes = classes != null ? classes : new DODatabaseClass[0];
    }

    @Override
    public ExtObjectContainer getContainer() {
        return container;
    }

    @Override
    public DODatabaseEncoding getEncoding() {
        return encoding;
    }

    @Override
    public int getTotalClasses() {
        return totalClasses;
    }

    @Override
    public int getTotalObjects() {
        return totalObjects;
    }

    @Override
    public String getDatabaseSize() {
        return databaseSize;
    }

    @Override
    public DODatabaseClass[] getClasses() {
        return classes;
    }
}
