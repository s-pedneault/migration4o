package migration4o.engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import migration4o.database.DODatabaseBuilder;
import migration4o.engine.resolvers.DOFieldResolver;
import migration4o.engine.resolvers.DOObjectReachabilityTracker;
import migration4o.engine.resolvers.DOObjectResolver;
import migration4o.engine.resolvers.DOReferenceResolver;
import migration4o.engine.resolvers.DOSchemaToDatabaseClassResolver;
import migration4o.models.DOField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.DOSchemaReader;

public class DOEngine {

    private final DOSchema schema;
    private final DODatabase database;
    private final DOEngineMonitoring monitoring;
    private final DOObjectReachabilityTracker reachabilityTracker;
    private boolean closed = false;

    public DOEngine(String schemaFilePath, String databaseFilePath) throws IOException {
        // Validate file paths
        if (schemaFilePath == null || schemaFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema file path cannot be null or empty");
        }
        if (databaseFilePath == null || databaseFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }

        // Check file existence
        File schemaFile = new File(schemaFilePath);
        if (!schemaFile.exists()) {
            throw new FileNotFoundException("Schema file does not exist: " + schemaFilePath);
        }
        if (!schemaFile.isFile()) {
            throw new IOException("Schema path is not a file: " + schemaFilePath);
        }
        if (!schemaFile.canRead()) {
            throw new IOException("Schema file is not readable: " + schemaFilePath);
        }

        File databaseFile = new File(databaseFilePath);
        if (!databaseFile.exists()) {
            throw new FileNotFoundException("Database file does not exist: " + databaseFilePath);
        }
        if (!databaseFile.isFile()) {
            throw new IOException("Database path is not a file: " + databaseFilePath);
        }
        if (!databaseFile.canRead()) {
            throw new IOException("Database file is not readable: " + databaseFilePath);
        }

        // Initialize components
        try {
            // Load schema
            DOSchemaReader schemaReader = new DOSchemaReader();
            this.schema = schemaReader.readSchema(schemaFilePath);

            // Load database with schema information for enhanced field resolution
            DODatabaseBuilder databaseBuilder = new DODatabaseBuilder();
            this.database = databaseBuilder.buildDatabase(databaseFilePath, this.schema);

            // Initialize monitoring
            this.monitoring = new DOEngineMonitoring();

            // Initialize reachability tracker (will be populated during object resolution)
            this.reachabilityTracker = new DOObjectReachabilityTracker();

            // Perform post-loading resolution
            performResolution();

            // Perform object resolution with reachability tracking
            performObjectResolution();

            // Analyze ID-type field references to support export flattening
            analyzeIDTypeReferences();

        } catch (Exception e) {
            // Wrap exceptions as IO exceptions for consistent error handling
            throw new IOException("Failed to initialize DOEngine with schema '" + schemaFilePath +
                    "' and database '" + databaseFilePath + "'", e);
        }
    }

    /**
     * Performs all necessary resolution steps after loading schema and database.
     * This includes field-to-class resolution, schema-to-database class mapping,
     * and reference discovery.
     */
    private void performResolution() {
        // Step 1: Resolve field types to actual class objects
        DOFieldResolver fieldResolver = new DOFieldResolver();
        fieldResolver.resolveFieldTypes(this);

        // Step 2: Map schema classes to their corresponding database classes
        DOSchemaToDatabaseClassResolver schemaToDbResolver = new DOSchemaToDatabaseClassResolver();
        if (schema != null && schema.getClasses() != null && database != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                schemaToDbResolver.resolveReferences(schemaClass, this);
            }
        }

        // Step 3: Resolve references between classes
        DOReferenceResolver referenceResolver = new DOReferenceResolver();

        // Resolve references for all schema classes
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                referenceResolver.resolveReferences(schemaClass, this);
            }
        }

        // Resolve references for all database classes
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass databaseClass : database.getClasses()) {
                referenceResolver.resolveReferences(databaseClass, this);
            }
        }
    }

    /**
     * Performs object resolution with reachability tracking.
     * This happens after the database and schema are loaded.
     */
    private void performObjectResolution() {
        System.out.println("Starting object resolution with reachability tracking...");

        DOObjectResolver objectResolver = new DOObjectResolver();

        objectResolver.resolveAllObjects(
                database.getContainer(),
                database,
                schema,
                this);

        System.out.println("Object resolution complete!");
    }

    /**
     * Analyzes all database classes to count how many classes reference each class
     * through ID-type fields.
     * This information is stored in each DODatabaseClass and can be used by
     * exporters to determine
     * whether ID objects should be flattened (single reference) or kept as IDs
     * (multiple references).
     */
    private void analyzeIDTypeReferences() {
        System.out.println("Analyzing ID-type field references...");

        if (database == null || database.getClasses() == null) {
            return;
        }

        // First, initialize all reference counts to 0
        for (DODatabaseClass dbClass : database.getClasses()) {
            dbClass.setReferenceCount(0);
        }

        // Build a map of class names to database classes for quick lookup
        java.util.Map<String, DODatabaseClass> classMap = new java.util.HashMap<>();
        for (DODatabaseClass dbClass : database.getClasses()) {
            classMap.put(dbClass.getAbsoluteName(), dbClass);
        }

        // Scan all classes and their fields
        for (DODatabaseClass dbClass : database.getClasses()) {
            java.util.Set<String> referencedTypesInThisClass = new java.util.HashSet<>();

            // Get all fields including inherited ones
            java.util.List<DOField> allFields = getAllFieldsForClass(dbClass);

            for (DOField field : allFields) {
                String typeName = field.getTypeName();
                // Check if this is an ID-type field
                if (typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"))) {
                    // Only count each type once per class (avoid counting inherited fields multiple
                    // times)
                    referencedTypesInThisClass.add(typeName);
                }
            }

            // Increment reference count for each referenced type
            for (String referencedType : referencedTypesInThisClass) {
                DODatabaseClass referencedClass = classMap.get(referencedType);
                if (referencedClass != null) {
                    referencedClass.setReferenceCount(referencedClass.getReferenceCount() + 1);
                }
            }
        }

        // Log results
        int singleRefCount = 0;
        int multiRefCount = 0;
        for (DODatabaseClass dbClass : database.getClasses()) {
            int refCount = dbClass.getReferenceCount();
            if (refCount == 1) {
                singleRefCount++;
                System.out.println("  - " + dbClass.getShortName() + " (1 reference - can be flattened)");
            } else if (refCount > 1) {
                multiRefCount++;
                System.out.println("  - " + dbClass.getShortName() + " (" + refCount + " references - keep as ID)");
            }
        }
        System.out
                .println("ID-type reference analysis complete: " + singleRefCount + " classes with single reference, " +
                        multiRefCount + " classes with multiple references.");
    }

    /**
     * Helper method to get all fields for a class including inherited fields.
     */
    private java.util.List<DOField> getAllFieldsForClass(
            DODatabaseClass dbClass) {
        java.util.List<DOField> allFields = new java.util.ArrayList<>();

        // Traverse the class hierarchy
        DODatabaseClass currentClass = dbClass;
        while (currentClass != null) {
            DOField[] fields = currentClass.getFields();
            if (fields != null) {
                allFields.addAll(java.util.Arrays.asList(fields));
            }
            currentClass = currentClass.getParentClass();
        }

        return allFields;
    }

    public DOSchema getSchema() {
        return schema;
    }

    public DODatabase getDatabase() {
        return database;
    }

    public DOEngineMonitoring getMonitoring() {
        return monitoring;
    }

    public DOObjectReachabilityTracker getReachabilityTracker() {
        return reachabilityTracker;
    }

    public void close() {
        if (!closed) {
            closed = true;
            // Close database connection if it exists
            if (database != null && database.getContainer() != null) {
                try {
                    database.getContainer().close();
                } catch (Exception e) {
                    // Log error but don't throw - close should be idempotent
                    System.err.println("Warning: Error closing database container: " + e.getMessage());
                }
            }
        }
    }

}
