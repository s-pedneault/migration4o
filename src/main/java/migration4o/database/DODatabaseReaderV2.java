package migration4o.database;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;

import migration4o.database.processors.DOClassConverter;
import migration4o.database.processors.DOClassesConverter;
import migration4o.database.processors.DOObjectDeduplicator;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.util.DatabaseUtil;

/**
 * Version 2 of the database reader that directly creates DOSchema* classes
 * instead of creating intermediary DODatabase* classes.
 * 
 * This replaces the two-step process:
 * 1. DODatabaseReader creates DODatabase* classes
 * 2. DODatabaseSchemaInferrer converts DODatabase* to DOSchema* classes
 * 
 * With a single step:
 * - DODatabaseReaderV2 directly creates DOSchema* classes
 * 
 * This is more efficient for the UI which works exclusively with DOSchema*
 * classes.
 */
public class DODatabaseReaderV2 {

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
            System.out.println("DODatabaseReaderV2: Reading " + storedClasses.length + " classes directly as schema");

            // Create database context
            DODatabaseContext context = new DODatabaseContext(
                    container,
                    DOClassConverter.createStoredClassMap(storedClasses));

            // Convert stored classes to schema classes
            DOSchemaClass[] schemaClasses = DOClassesConverter.convertStoredClassesToSchemaClasses(
                    storedClasses,
                    context);

            // Create modules - single module containing all classes
            DOSchemaModule[] modules = new DOSchemaModule[] {
                    new DOSchemaModule("Database Classes", schemaClasses)
            };

            // Create schema
            DOSchema schema = new DOSchema(
                    schemaClasses,
                    modules,
                    new DOSchemaClass[0] // No foundation classes from database
            );

            // Deduplicate object IDs across inheritance hierarchies
            schema = DOObjectDeduplicator.deduplicateObjectIdsInInheritanceHierarchies(schema);

            System.out.println("Successfully created schema with " + schemaClasses.length + " classes");
            return schema;

        } catch (Exception e) {
            System.out.println("Error reading database as schema: " + e.getMessage());
            e.printStackTrace();
            return createEmptySchema();
        }
    }

    /**
     * Creates an empty schema when database is null or empty.
     */
    private DOSchema createEmptySchema() {
        return new DOSchema(
                new DOSchemaClass[0],
                new DOSchemaModule[0],
                new DOSchemaClass[0]);
    }
}
