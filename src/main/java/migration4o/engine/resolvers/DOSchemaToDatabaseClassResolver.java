package migration4o.engine.resolvers;

import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.engine.DOEngine;
import migration4o.engine.resolvers.DOSchemaToDatabaseClassResolver;
import migration4o.models.schema.DOSchemaClass;

public class DOSchemaToDatabaseClassResolver {

    public void resolveReferences(DOSchemaClass schemaClass, DOEngine engine) {
        DODatabase database = engine.getDatabase();
        if (schemaClass == null || database == null) {
            return;
        }

        String schemaClassName = schemaClass.source;
        if (schemaClassName == null || schemaClassName.isEmpty()) {
            return;
        }

        // Find the corresponding database class
        DODatabaseClass[] databaseClasses = database.getClasses();
        if (databaseClasses != null) {
            for (DODatabaseClass databaseClass : databaseClasses) {
                if (databaseClass != null && schemaClassName.equals(databaseClass.getAbsoluteName())) {
                    // Found a match - set the database class on the schema class
                    schemaClass.databaseClass = databaseClass;

                    // DEBUG: Log DossierAdresse linkage
                    if (schemaClassName.contains("IDDossPrev")) {
                        System.out.println("DEBUG: Linked schema class '" + schemaClassName + "' to database class: "
                                + databaseClass.getAbsoluteName() + " with " + databaseClass.getTotalObjectCount()
                                + " objects");
                    }
                    return;
                }
            }
        }

        // If we reach here, no matching database class was found
        // This is not necessarily an error - the schema may define classes that don't
        // exist in this database
        // The schema class will simply have a null database class
        if (schemaClassName.contains("IDDossPrev")) {
            System.out.println(
                    "DEBUG: FAILED to link schema class '" + schemaClassName + "' - no matching database class found!");
        }
        engine.getMonitoring().addSchemaClassWithNoDatabaseClass(schemaClass);
    }
}
