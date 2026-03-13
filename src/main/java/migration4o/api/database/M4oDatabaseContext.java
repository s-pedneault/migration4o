package migration4o.api.database;

import migration4o.api.database.monitoring.M4oDatabaseMonitors;

public interface M4oDatabaseContext {

    public M4oDatabaseMonitors getMonitors();

}
