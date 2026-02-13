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

    /**
     * Absolute path of currently opened database.
     */
    public String currentDatabasePath;

    /**
     * Cached schema read from active database.
     */
    public DOSchema databaseSchema;

    /**
     * Shared opener instance used by DODatabaseService.
     */
    public DODatabaseOpener opener;

    /**
     * Optional monitor for database operations.
     */
    public DODatabaseMonitor monitor;

    /**
     * The DB4O database container
     */
    public ExtObjectContainer container;

    /**
     * Map of stored classes by their fully qualified name for quick lookup
     */
    public Map<String, StoredClass> storedClassMap;

    /**
     * Creates a new database context.
     * 
     * @param container      The DB4O database container
     * @param storedClassMap Map of stored classes by name
     */
    public DODatabaseContext(ExtObjectContainer container, Map<String, StoredClass> storedClassMap) {
        this.container = container;
        this.storedClassMap = storedClassMap;
    }
}
