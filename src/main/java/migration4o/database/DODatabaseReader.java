package migration4o.database;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;

import migration4o.database.processors.DOClassConverter;
import migration4o.database.processors.DOClassesConverter;
import migration4o.database.processors.DOObjectDeduplicator;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.DatabaseUtil;

/**
 * Database reader that directly creates DOSchema* classes
 * instead of creating intermediary DODatabase* classes.
 */
public class DODatabaseReader {

    private DODatabaseMonitor monitor;

    /**
     * Creates a new database reader without a monitor
     */
    public DODatabaseReader() {
        this(null);
    }

    /**
     * Creates a new database reader with an optional monitor
     */
    public DODatabaseReader(DODatabaseMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Reads a database and directly creates a DOSchema representation.
     * 
     * @param container The database container
     * @return A DOSchema representing the database structure
     */
    public DOSchema readDatabaseAsSchema(ExtObjectContainer container) {
        if (container == null) {
            return createEmptySchema();
        }

        try {
            StoredClass[] storedClasses = DatabaseUtil.getStoredClassesSafely(container);

            if (monitor != null) {
                monitor.onStartingSchemaRead(storedClasses.length);
            }

            // Create database context
            if (monitor != null) {
                monitor.onCreatingDatabaseContext();
            }

            DODatabaseContext context = new DODatabaseContext(container.identity().toString(), monitor);
            context.container = container;
            context.storedClassMap = DOClassConverter.createStoredClassMap(storedClasses);

            // Convert stored classes to schema classes
            if (monitor != null) {
                monitor.onConvertingClasses(storedClasses.length);
            }

            DOSchemaClass[] schemaClasses = DOClassesConverter.convertStoredClassesToSchemaClasses(storedClasses, context, monitor);

            // Create schema
            if (monitor != null) {
                monitor.onCreatingSchema(schemaClasses.length);
            }

            DOSchema schema = new DOSchema();
            schema.classes = schemaClasses;

            // Deduplicate object IDs across inheritance hierarchies
            schema = DOObjectDeduplicator.deduplicateObjectIdsInInheritanceHierarchies(schema, monitor);

            if (monitor != null) {
                monitor.onSchemaReadComplete(schemaClasses.length);
            } else {
                System.out.println("Successfully created schema with " + schemaClasses.length + " classes");
            }
            return schema;

        } catch (Exception e) {
            if (monitor != null) {
                monitor.onSchemaReadError(e.getMessage());
            } else {
                System.out.println("Error reading database as schema: " + e.getMessage());
            }
            e.printStackTrace();
            return createEmptySchema();
        }
    }

    /**
     * Creates an empty schema when database is null or empty.
     */
    private DOSchema createEmptySchema() {
        return new DOSchema();
    }
}
