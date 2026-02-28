package migration4o.database;

import java.io.IOException;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.DOSchemaService;

/**
 * Singleton service for managing the DB4O database connection.
 * Ensures only one in-memory database instance exists across the entire
 * application.
 * All components should use this service instead of opening databases directly.
 */
public class DODatabaseService {

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
     * Open a database and load it into memory with progress monitoring.
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

        // Create and store opener for this open operation
        DODatabaseOpener opener = new DODatabaseOpener(context.monitor);

        // Open new database in memory
        context.container = opener.openDatabase(context, true);

        // Infer schema from database
        DODatabaseReader reader = context.monitor != null ? new DODatabaseReader(context.monitor) : new DODatabaseReader();
        context.databaseSchema = reader.readDatabaseAsSchema(context.container);
        enrichPointsToFromReferenceSchema(context.databaseSchema);

        if (context.monitor != null) {
            context.monitor.onServiceDatabaseOpened(context.databaseFilePath);
        }
    }

    private void enrichPointsToFromReferenceSchema(DOSchema databaseSchema) {
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return;
        }

        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        if (referenceSchema == null || referenceSchema.getClasses() == null) {
            return;
        }

        for (DOSchemaClass dbClass : databaseSchema.getClasses()) {
            if (dbClass == null || dbClass.pointsTo != null) {
                continue;
            }

            DOSchemaClass referenceMatch = referenceSchema.findClassByName(dbClass.source);
            if (referenceMatch == null && dbClass.destinationName != null) {
                referenceMatch = referenceSchema.findClassByName(dbClass.destinationName);
            }

            if (referenceMatch != null && referenceMatch.isIDEntite(referenceSchema) && referenceMatch.pointsTo != null && !referenceMatch.pointsTo.isBlank()) {
                dbClass.pointsTo = referenceMatch.pointsTo;
            }
        }
    }

}
