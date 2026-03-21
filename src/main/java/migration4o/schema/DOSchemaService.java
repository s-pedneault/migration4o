package migration4o.schema;

import migration4o.models.schema.DOSchema;

/**
 * Singleton service for managing the reference schema.
 * Ensures the schema is loaded once and shared across the entire application.
 * All components should use this service instead of loading schemas directly.
 */
public class DOSchemaService {
    static final String DEFAULT_SCHEMA_PATH = "schema/reference-schema.xml";
    static final String BACKUP_SCHEMA_PATH = "local/schema/reference-schema.xml";

    private static DOSchemaService instance;

    private DOSchema referenceSchema;
    // private String currentSchemaPath;

    private DOSchemaService() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance of the schema service.
     */
    public static synchronized DOSchemaService getInstance() {
        if (instance == null) {
            instance = new DOSchemaService();
        }
        return instance;
    }

    /**
     * Load the reference schema from the specified path.
     * If a schema is already loaded, it will be replaced.
     * 
     * @param schemaPath Path to the schema XML file
     * @return The loaded schema
     * @throws Exception If the schema cannot be loaded
     */
    public synchronized DOSchema loadReferenceSchema() throws Exception {

        DOReferenceSchemaReader reader = new DOReferenceSchemaReader();
        referenceSchema = reader.readSchema();

        return referenceSchema;
    }

    /**
     * Get the currently loaded reference schema.
     * 
     * @return The reference schema, or null if no schema is loaded
     */
    public synchronized DOSchema getReferenceSchema() {
        return referenceSchema;
    }

    /**
     * Set the reference schema directly (used when schema is already loaded).
     * 
     * @param schema The schema to set
     */
    public synchronized void setReferenceSchema(DOSchema schema) {
        this.referenceSchema = schema;
        // this.currentSchemaPath = null; // Path not known when setting directly
    }

    /**
     * Check if a reference schema is currently loaded.
     * 
     * @return true if a schema is loaded, false otherwise
     */
    public synchronized boolean isSchemaLoaded() {
        return referenceSchema != null;
    }

    // /**
    // * Get the path of the currently loaded schema.
    // *
    // * @return The schema path, or null if no schema is loaded
    // */
    // public synchronized String getCurrentSchemaPath() {
    // return currentSchemaPath;
    // }

    /**
     * Clear the currently loaded schema.
     */
    public synchronized void clearSchema() {
        referenceSchema = null;
        // currentSchemaPath = null;
    }
}
