package migration4o.database;

import com.db4o.ext.ExtObjectContainer;
import migration4o.models.schema.DOSchema;
import migration4o.ui.common.DatabaseProgressMonitor;
import java.io.IOException;

/**
 * Singleton service for managing the DB4O database connection.
 * Ensures only one in-memory database instance exists across the entire
 * application.
 * All components should use this service instead of opening databases directly.
 */
public class DODatabaseService {

    private static DODatabaseService instance;

    private ExtObjectContainer container;
    private String currentDatabasePath;
    private final DODatabaseOpener opener;
    private DOSchema databaseSchema;

    private DODatabaseService() {
        this.opener = new DODatabaseOpener();
    }

    /**
     * Get the singleton instance of the database service.
     */
    public static synchronized DODatabaseService getInstance() {
        if (instance == null) {
            instance = new DODatabaseService();
        }
        return instance;
    }

    /**
     * Open a database and load it into memory.
     * If a database is already open, it will be closed first.
     * 
     * @param databasePath Path to the database file
     * @return The opened database container
     * @throws IOException If the database cannot be opened
     */
    public synchronized ExtObjectContainer openDatabase(String databasePath) throws IOException {
        return openDatabase(databasePath, null);
    }

    /**
     * Open a database and load it into memory with progress monitoring.
     * If a database is already open, it will be closed first.
     * 
     * @param databasePath Path to the database file
     * @param monitor      Progress monitor for UI feedback (can be null)
     * @return The opened database container
     * @throws IOException If the database cannot be opened
     */
    public synchronized ExtObjectContainer openDatabase(String databasePath, DatabaseProgressMonitor monitor)
            throws IOException {
        // Close existing database if any
        closeDatabase();

        // Create opener with monitor if provided
        DODatabaseOpener openerWithMonitor = monitor != null ? new DODatabaseOpener(monitor) : opener;

        // Open new database in memory
        container = openerWithMonitor.openDatabase(databasePath, true);
        currentDatabasePath = databasePath;

        // Clear cached schema since we have a new database
        databaseSchema = null;

        System.out.println("Database opened and loaded into memory: " + databasePath);
        return container;
    }

    /**
     * Get the currently open database container.
     * 
     * @return The database container, or null if no database is open
     */
    public synchronized ExtObjectContainer getContainer() {
        return container;
    }

    /**
     * Check if a database is currently open.
     * 
     * @return true if a database is open, false otherwise
     */
    public synchronized boolean isDatabaseOpen() {
        return container != null && !container.ext().isClosed();
    }

    /**
     * Get the path of the currently open database.
     * 
     * @return The database path, or null if no database is open
     */
    public synchronized String getCurrentDatabasePath() {
        return currentDatabasePath;
    }

    /**
     * Get the database schema from the currently open database.
     * The schema is cached after the first read.
     * 
     * @return The database schema, or null if no database is open
     */
    public synchronized DOSchema getDatabaseSchema() {
        return getDatabaseSchema(null);
    }

    /**
     * Get the database schema from the currently open database with progress
     * monitoring.
     * The schema is cached after the first read.
     * 
     * @param monitor Progress monitor for UI feedback (can be null)
     * @return The database schema, or null if no database is open
     */
    public synchronized DOSchema getDatabaseSchema(DatabaseProgressMonitor monitor) {
        if (!isDatabaseOpen()) {
            return null;
        }

        // Return cached schema if available
        if (databaseSchema != null) {
            return databaseSchema;
        }

        // Read schema from database and cache it
        DODatabaseReader reader = monitor != null ? new DODatabaseReader(monitor) : new DODatabaseReader();
        databaseSchema = reader.readDatabaseAsSchema(container);
        return databaseSchema;
    }

    /**
     * Close the currently open database.
     * Does nothing if no database is open.
     */
    public synchronized void closeDatabase() {
        if (container != null && !container.ext().isClosed()) {
            try {
                container.close();
                System.out.println("Database closed: " + currentDatabasePath);
            } catch (Exception e) {
                System.err.println("Error closing database: " + e.getMessage());
            }
        }
        container = null;
        currentDatabasePath = null;
        databaseSchema = null;
    }

    /**
     * Get the database opener instance (for advanced use cases).
     */
    public DODatabaseOpener getOpener() {
        return opener;
    }
}
