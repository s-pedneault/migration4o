package migration4o.database;

import java.io.IOException;

/**
 * Singleton service for managing the DB4O database connection.
 * Ensures only one in-memory database instance exists across the entire
 * application.
 * All components should use this service instead of opening databases directly.
 */
public class DODatabaseService {

    private static DODatabaseService instance;

    private DODatabaseContext context;

    private DODatabaseService() {
        this.context = new DODatabaseContext(null, null);
    }

    public DODatabaseContext context() {
        return context;
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
     * Open a database and load it into memory with progress monitoring.
     * If a database is already open, it will be closed first.
     * 
        * @param context Database open context (file path + monitor + runtime state)
     * @throws IOException If the database cannot be opened
     */
    public synchronized void openDatabase(DODatabaseContext context) throws IOException {
        if (context == null || context.databaseFilePath == null || context.databaseFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database context and file path are required");
        }

        // Keep the requested path in case we are reusing the same context instance
        // and closing an already-open container clears the path.
        String requestedDatabasePath = context.databaseFilePath;

        // Close existing database if any
        if (this.context != null && this.context.isDatabaseOpen()) {
            this.context.closeDatabase();
        }

        // Store the new context in the singleton service so all callers share it.
        this.context = context;
        this.context.databaseFilePath = requestedDatabasePath;

        // Create and store opener for this open operation
        DODatabaseOpener opener = new DODatabaseOpener(this.context.monitor);

        // Open new database in memory
        this.context.container = opener.openDatabase(this.context, true);

        // Infer schema from database
        DODatabaseReader reader = this.context.monitor != null ? new DODatabaseReader(this.context.monitor) : new DODatabaseReader();
        this.context.databaseSchema = reader.readDatabaseAsSchema(this.context.container);

        if (this.context.monitor != null) {
            this.context.monitor.onServiceDatabaseOpened(this.context.databaseFilePath);
        }
    }

}
