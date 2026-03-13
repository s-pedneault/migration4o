package migration4o.api.database;

import com.db4o.ext.ExtObjectContainer;

import migration4o.api.database.meta.M4oDatabaseEncoding;

public interface M4oDatabase {

    public String getID();

    public String getPath();

    public ExtObjectContainer getContainer();

    public M4oDatabaseEncoding getEncoding();

    public void open(M4oDatabaseContext context);

    public void close(M4oDatabaseContext context);

    public boolean isOpen(M4oDatabaseContext context);

}
