package dataobjects.impl.engine.resolvers;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.engine.resolvers.DOSchemaToDatabaseClassResolver;
import dataobjects.api.models.schema.DOSchemaClass;

public class DOSchemaToDatabaseClassResolverImpl implements DOSchemaToDatabaseClassResolver {

    @Override
    public void resolveReferences(DOSchemaClass schemaClass, DOEngine engine) {
        DODatabase database = engine.getDatabase();
        if (schemaClass == null || database == null) {
            return;
        }

        String schemaClassName = schemaClass.getAbsoluteName();
        if (schemaClassName == null || schemaClassName.isEmpty()) {
            return;
        }

        // Find the corresponding database class
        DODatabaseClass[] databaseClasses = database.getClasses();
        if (databaseClasses != null) {
            for (DODatabaseClass databaseClass : databaseClasses) {
                if (databaseClass != null && schemaClassName.equals(databaseClass.getAbsoluteName())) {
                    // Found a match - set the database class on the schema class
                    schemaClass.setDatabaseClass(databaseClass);
                    return;
                }
            }
        }

        // If we reach here, no matching database class was found
        // This is not necessarily an error - the schema may define classes that don't
        // exist in this database
        // The schema class will simply have a null database class
        engine.getMonitoring().addSchemaClassWithNoDatabaseClass(schemaClass);
    }
}
