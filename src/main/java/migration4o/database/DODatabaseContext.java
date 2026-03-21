package migration4o.database;

import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;

import migration4o.models.schema.DOSchema;

/**
 * Context object that holds common objects needed throughout the database
 * reading and conversion process. Simplifies parameter passing between
 * processors.
 */
public class DODatabaseContext {

    public ExtObjectContainer container;
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
        return container != null && !container.ext().isClosed();
    }

    public synchronized void closeDatabase() {
        if (container != null && !container.ext().isClosed()) {
            try {
                container.close();
                if (monitor != null) {
                    monitor.onServiceDatabaseClosed(databaseFilePath);
                }
            } catch (Exception e) {
                if (monitor != null) {
                    monitor.onServiceDatabaseCloseFailed(databaseFilePath, e.getMessage());
                }
            }
        }
        container = null;
        databaseFilePath = null;
        database = null;
        databaseSchema = null;
    }
}
