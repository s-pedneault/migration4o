package migration4o.database;

import com.db4o.ext.ExtObjectContainer;
import migration4o.models.schema.DOSchema;
import java.io.IOException;

/**
 * Singleton service for managing the DB4O database connection.
 * Ensures only one in-memory database instance exists across the entire
 * application.
 * All components should use this service instead of opening databases directly.
 */
public class DODatabaseService {

    private static DODatabaseService instance;

    private final DODatabaseContext context;

    private DODatabaseService() {
        this.context = new DODatabaseContext(null, null);
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
    public synchronized ExtObjectContainer openDatabase(String databasePath, DODatabaseMonitor monitor)
            throws IOException {
        // Close existing database if any
        closeDatabase();

        context.monitor = monitor;

        // Create and store opener for this open operation
        context.opener = new DODatabaseOpener(monitor);

        // Open new database in memory
        context.container = context.opener.openDatabase(databasePath, true);
        context.currentDatabasePath = databasePath;

        // Clear cached schema since we have a new database
        context.databaseSchema = null;

        if (context.monitor != null) {
            context.monitor.onServiceDatabaseOpened(databasePath);
        }
        return context.container;
    }

    /**
     * Get the currently open database container.
     * 
     * @return The database container, or null if no database is open
     */
    public synchronized ExtObjectContainer getContainer() {
        return context.container;
    }

    /**
     * Check if a database is currently open.
     * 
     * @return true if a database is open, false otherwise
     */
    public synchronized boolean isDatabaseOpen() {
        return context.container != null && !context.container.ext().isClosed();
    }

    /**
     * Get the path of the currently open database.
     * 
     * @return The database path, or null if no database is open
     */
    public synchronized String getCurrentDatabasePath() {
        return context.currentDatabasePath;
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
    public synchronized DOSchema getDatabaseSchema(DODatabaseMonitor monitor) {
        if (!isDatabaseOpen()) {
            return null;
        }

        // Return cached schema if available
        if (context.databaseSchema != null) {
            return context.databaseSchema;
        }

        // Read schema from database and cache it
        DODatabaseReader reader = monitor != null ? new DODatabaseReader(monitor) : new DODatabaseReader();
        context.databaseSchema = reader.readDatabaseAsSchema(context.container);
        return context.databaseSchema;
    }

    /**
     * Close the currently open database.
     * Does nothing if no database is open.
     */
    public synchronized void closeDatabase() {
        if (context.container != null && !context.container.ext().isClosed()) {
            try {
                context.container.close();
                if (context.monitor != null) {
                    context.monitor.onServiceDatabaseClosed(context.currentDatabasePath);
                }
            } catch (Exception e) {
                if (context.monitor != null) {
                    context.monitor.onServiceDatabaseCloseFailed(context.currentDatabasePath, e.getMessage());
                }
            }
        }
        context.container = null;
        context.currentDatabasePath = null;
        context.databaseSchema = null;
    }

    /**
     * Get the database opener instance (for advanced use cases).
     */
    public DODatabaseOpener getOpener() {
        return context.opener;
    }
}
