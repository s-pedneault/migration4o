package migration4o.database;

/**
 * Monitor interface for tracking database operations.
 * Implementations can provide user feedback during database opening and
 * reading.
 */
public abstract class DODatabaseMonitor {

    // ===== Database Service Lifecycle Methods =====

    /**
     * Called when DODatabaseService has successfully opened and registered
     * an active database container.
     */
    public void onServiceDatabaseOpened(String databasePath) {
        // Default: no-op
    }

    /**
     * Called when DODatabaseService has closed the active database container.
     */
    public void onServiceDatabaseClosed(String databasePath) {
        // Default: no-op
    }

    /**
     * Called when DODatabaseService fails while closing the active database.
     */
    public void onServiceDatabaseCloseFailed(String databasePath, String errorMessage) {
        // Default: no-op
    }

    // ===== Database Opening Methods =====

    /**
     * Called when starting to try a specific encoding configuration
     */
    public void onTryingEncoding(String encodingDescription) {
        // Default: no-op
    }

    /**
     * Called when an encoding attempt fails
     */
    public void onEncodingFailed(String encodingDescription, String errorType) {
        // Default: no-op
    }

    /**
     * Called when database is successfully opened with a specific encoding
     */
    public void onDatabaseOpened(String encodingDescription) {
        // Default: no-op
    }

    /**
     * Called when database opening fails completely (all encodings failed)
     */
    public void onDatabaseOpenFailed(String errorMessage) {
        // Default: no-op
    }

    // ===== Database Reading Methods =====

    /**
     * Called when starting to read database as schema
     */
    public void onStartingSchemaRead(int totalClasses) {
        // Default: no-op
    }

    /**
     * Called when creating database context
     */
    public void onCreatingDatabaseContext() {
        // Default: no-op
    }

    /**
     * Called when converting stored classes to schema classes
     */
    public void onConvertingClasses(int totalClasses) {
        // Default: no-op
    }

    /**
     * Called when starting to convert a single class
     */
    public void onConvertingClass(String className, int classIndex, int totalClasses) {
        // Default: no-op
    }

    /**
     * Called when starting to retrieve object IDs for a class (expensive operation)
     */
    public void onRetrievingObjectIds(String className, int objectCount) {
        // Default: no-op
    }

    /**
     * Called when object IDs have been retrieved
     */
    public void onObjectIdsRetrieved(String className, int idCount) {
        // Default: no-op
    }

    /**
     * Called when a class conversion is complete
     */
    public void onClassConverted(String className, int fieldCount) {
        // Default: no-op
    }

    /**
     * Called when a class conversion fails
     */
    public void onClassConversionWarning(String className, String errorMessage) {
        // Default: no-op
    }

    /**
     * Called when starting field conversion for a class
     */
    public void onConvertingFields(String className, int fieldCount) {
        // Default: no-op
    }

    /**
     * Called when a field conversion fails
     */
    public void onFieldConversionWarning(String className, String fieldName, String errorMessage) {
        // Default: no-op
    }

    /**
     * Called when field conversion encounters an error
     */
    public void onFieldConversionError(String className, String errorMessage) {
        // Default: no-op
    }

    /**
     * Called when creating schema modules
     */
    public void onCreatingModules(int moduleCount) {
        // Default: no-op
    }

    /**
     * Called when creating the final schema object
     */
    public void onCreatingSchema(int classCount) {
        // Default: no-op
    }

    /**
     * Called when starting object ID deduplication
     */
    public void onStartingDeduplication(int totalLeafClasses) {
        // Default: no-op
    }

    /**
     * Called when processing a leaf class for deduplication
     */
    public void onProcessingLeafClass(String className, int leafIndex, int totalLeafClasses) {
        // Default: no-op
    }

    /**
     * Called when deduplication is complete for a class
     */
    public void onClassDeduplicated(String className, int removedCount, int remainingCount) {
        // Default: no-op
    }

    /**
     * Called when deduplication is complete
     */
    public void onDeduplicationComplete(int leafClasses, int totalRemoved) {
        // Default: no-op
    }

    /**
     * Called when schema reading is complete
     */
    public void onSchemaReadComplete(int totalClasses) {
        // Default: no-op
    }

    /**
     * Called when schema reading fails
     */
    public void onSchemaReadError(String errorMessage) {
        // Default: no-op
    }
}
