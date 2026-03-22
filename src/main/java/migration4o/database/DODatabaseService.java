package migration4o.database;

import java.io.File;
import java.io.IOException;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.processors.DOObjectDeduplicator;
import migration4o.models.schema.DOSchema;
import migration4o.schema.DOSchemaService;

/**
 * Singleton service for managing the DB4O database connection. Ensures only one in-memory database instance exists across the entire application. All components should use this service instead of opening databases directly.
 */
public class DODatabaseService {

    private static final String STATIC_DB_PATH = "local/Static.dat";

    private static DODatabaseService instance;

    private DODatabaseService() {
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
     * Open a database and load it into memory with progress monitoring. Also opens the static database (local/Static.dat) as a second delegate if available.
     * 
     * @param context Database open context (file path + monitor + runtime state)
     * @throws IOException If the database cannot be opened
     */
    public void openDatabase(DODatabaseContext context) throws IOException {
        if (context == null || context.databaseFilePath == null || context.databaseFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database context and file path are required");
        }

        // Close existing database if any inside the passed context
        if (context.isDatabaseOpen()) {
            context.closeDatabase();
        }

        DODatabaseOpener opener = new DODatabaseOpener(context.monitor);
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        DODatabaseLoader loader = new DODatabaseLoader();

        // Create the aggregate database
        DODatabase database = new DODatabase();
        database.schema = referenceSchema;

        // ── User database (primary delegate) ────────────────────────────
        ExtObjectContainer userContainer = opener.openDatabase(context, true);
        DODatabaseDelegate userDelegate = new DODatabaseDelegate(userContainer, context.databaseFilePath);

        loader.load(userDelegate, database, referenceSchema);
        DOObjectDeduplicator.deduplicateObjectIds(userDelegate, context.monitor);
        database.addDelegate(userDelegate);

        // ── Static database (secondary delegate, optional) ──────────────
        File staticFile = new File(STATIC_DB_PATH);
        if (staticFile.exists()) {
            try {
                DODatabaseContext staticCtx = new DODatabaseContext(STATIC_DB_PATH, context.monitor);
                ExtObjectContainer staticContainer = opener.openDatabase(staticCtx, true);
                DODatabaseDelegate staticDelegate = new DODatabaseDelegate(staticContainer, STATIC_DB_PATH);

                loader.load(staticDelegate, database, referenceSchema);
                DOObjectDeduplicator.deduplicateObjectIds(staticDelegate, context.monitor);
                database.addDelegate(staticDelegate);

                System.out.println("[INFO] Static database loaded: " + STATIC_DB_PATH + " (" + staticDelegate.classes.length + " classes)");
            } catch (Exception e) {
                System.out.println("[WARN] Could not open static database '" + STATIC_DB_PATH + "': " + e.getMessage());
            }
        } else {
            System.out.println("[INFO] No static database found at " + STATIC_DB_PATH + " — continuing with user database only.");
        }

        context.database = database;

        // Also populate old databaseSchema for coexistence during migration
        DODatabaseReader reader = context.monitor != null ? new DODatabaseReader(context.monitor) : new DODatabaseReader();
        context.databaseSchema = reader.readDatabaseAsSchema(userDelegate);

        if (context.monitor != null) {
            context.monitor.onServiceDatabaseOpened(context.databaseFilePath);
        }
    }

}
