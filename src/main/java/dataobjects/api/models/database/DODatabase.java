package dataobjects.api.models.database;

import dataobjects.api.database.DODatabaseEncoding;
import dataobjects.api.models.database.DODatabaseClass;
import com.db4o.ext.ExtObjectContainer;

public interface DODatabase {

    public ExtObjectContainer getContainer();

    public DODatabaseEncoding getEncoding();

    public int getTotalClasses();

    public int getTotalObjects();

    public String getDatabaseSize();

    public DODatabaseClass[] getClasses();

}
