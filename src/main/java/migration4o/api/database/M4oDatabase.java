package migration4o.api.database;

import migration4o.api.database.meta.M4oDatabaseEncoding;
import migration4o.database.DODatabaseDelegate;

public interface M4oDatabase {

    public String getID();

    public String getPath();

    public DODatabaseDelegate getDelegate();

    public M4oDatabaseEncoding getEncoding();

    public void open(M4oDatabaseContext context);

    public void close(M4oDatabaseContext context);

    public boolean isOpen(M4oDatabaseContext context);

}
