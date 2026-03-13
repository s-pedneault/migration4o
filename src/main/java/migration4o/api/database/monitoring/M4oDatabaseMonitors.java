package migration4o.api.database.monitoring;

public interface M4oDatabaseMonitors extends M4oDatabaseMonitor {

    public M4oDatabaseMonitor[] getMonitors();

    public void subscribe(M4oDatabaseMonitor monitor);

    public void unsubscribe(M4oDatabaseMonitor monitor);

}
