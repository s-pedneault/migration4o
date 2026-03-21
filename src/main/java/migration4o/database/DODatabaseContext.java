package migration4o.database;

import java.util.Map;

import com.db4o.ext.StoredClass;

import migration4o.models.schema.DOSchema;

/**
 * Context object that holds common objects needed throughout the database
 * reading and conversion process. Simplifies parameter passing between
 * processors.
 */
public class DODatabaseContext {

    public DODatabaseEncoding encoding;
    public String databaseFilePath;
    public Map<String, StoredClass> storedClassMap;
    public DODatabase database;
    /** @deprecated Use {@link #database} instead. Kept temporarily for coexistence. */
    @Deprecated
    public DOSchema databaseSchema;
    public DODatabaseMonitor monitor;

    public DODatabaseContext(String databasePath, DODatabaseMonitor monitor) {
        this.databaseFilePath = databasePath;
        this.monitor = monitor;
    }

    public synchronized boolean isDatabaseOpen() {
        if (database == null) {
            return false;
        }
        for (DODatabaseDelegate delegate : database.getDelegates()) {
            if (!delegate.isClosed()) {
                return true;
            }
        }
        return false;
    }

    public synchronized void closeDatabase() {
        if (database != null) {
            for (DODatabaseDelegate delegate : database.getDelegates()) {
                try {
                    if (!delegate.isClosed()) {
                        delegate.close();
                    }
                } catch (Exception e) {
                    if (monitor != null) {
                        monitor.onServiceDatabaseCloseFailed(delegate.getFilePath(), e.getMessage());
                    }
                }
            }
            if (monitor != null) {
                monitor.onServiceDatabaseClosed(databaseFilePath);
            }
        }
        databaseFilePath = null;
        database = null;
        databaseSchema = null;
    }
}
