package dataobjects.impl.engine;

import dataobjects.api.engine.DOEngineMonitoring;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseClass;
import java.util.HashSet;
import java.util.Set;

public class DOEngineMonitoringImpl implements DOEngineMonitoring {

    private Set<DOSchemaClass> migratedSchemaClasses = new HashSet<>();
    private Set<DODatabaseClass> migratedDatabaseClasses = new HashSet<>();
    private Set<DOSchemaClass> schemaClassesWithNoDatabaseClass = new HashSet<>();

    @Override
    public void addMigratedSchemaClass(DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            migratedSchemaClasses.add(schemaClass);
        }
    }

    @Override
    public void addMigratedDatabaseClass(DODatabaseClass databaseClass) {
        if (databaseClass != null) {
            migratedDatabaseClasses.add(databaseClass);
        }
    }

    @Override
    public void addSchemaClassWithNoDatabaseClass(DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            schemaClassesWithNoDatabaseClass.add(schemaClass);
        }
    }

    @Override
    public void reset() {
        migratedSchemaClasses.clear();
        migratedDatabaseClasses.clear();
        schemaClassesWithNoDatabaseClass.clear();
    }

    @Override
    public Set<DOSchemaClass> getMigratedSchemaClasses() {
        return migratedSchemaClasses;
    }

    @Override
    public Set<DODatabaseClass> getMigratedDatabaseClasses() {
        return migratedDatabaseClasses;
    }

    @Override
    public Set<DOSchemaClass> getSchemaClassesWithNoDatabaseClass() {
        return schemaClassesWithNoDatabaseClass;
    }

}
