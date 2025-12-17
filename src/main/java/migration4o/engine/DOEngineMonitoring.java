package migration4o.engine;

import migration4o.engine.DOEngineMonitoring;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.database.DODatabaseClass;
import java.util.HashSet;
import java.util.Set;

public class DOEngineMonitoring {

    private Set<DOSchemaClass> migratedSchemaClasses = new HashSet<>();
    private Set<DODatabaseClass> migratedDatabaseClasses = new HashSet<>();
    private Set<DOSchemaClass> schemaClassesWithNoDatabaseClass = new HashSet<>();

    public void addMigratedSchemaClass(DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            migratedSchemaClasses.add(schemaClass);
        }
    }

    public void addMigratedDatabaseClass(DODatabaseClass databaseClass) {
        if (databaseClass != null) {
            migratedDatabaseClasses.add(databaseClass);
        }
    }

    public void addSchemaClassWithNoDatabaseClass(DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            schemaClassesWithNoDatabaseClass.add(schemaClass);
        }
    }

    public void reset() {
        migratedSchemaClasses.clear();
        migratedDatabaseClasses.clear();
        schemaClassesWithNoDatabaseClass.clear();
    }

    public Set<DOSchemaClass> getMigratedSchemaClasses() {
        return migratedSchemaClasses;
    }

    public Set<DODatabaseClass> getMigratedDatabaseClasses() {
        return migratedDatabaseClasses;
    }

    public Set<DOSchemaClass> getSchemaClassesWithNoDatabaseClass() {
        return schemaClassesWithNoDatabaseClass;
    }

}
