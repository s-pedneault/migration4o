package migration4o.database;

import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;

/**
 * Context object that holds common objects needed throughout the database
 * reading and conversion process. Simplifies parameter passing between
 * processors.
 */
public class DODatabaseContext {

    /**
     * The DB4O database container
     */
    public final ExtObjectContainer container;

    /**
     * Map of stored classes by their fully qualified name for quick lookup
     */
    public final Map<String, StoredClass> storedClassMap;

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
