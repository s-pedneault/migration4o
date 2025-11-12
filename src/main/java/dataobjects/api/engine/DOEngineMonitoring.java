package dataobjects.api.engine;

import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseClass;
import java.util.Set;

public interface DOEngineMonitoring {

    // Methods to add classes to tracking sets
    void addMigratedSchemaClass(DOSchemaClass schemaClass);

    void addMigratedDatabaseClass(DODatabaseClass databaseClass);

    void addSchemaClassWithNoDatabaseClass(DOSchemaClass schemaClass);

    // Reset method to clear all tracking data
    void reset();

    // Getter methods to access the actual sets
    Set<DOSchemaClass> getMigratedSchemaClasses();

    Set<DODatabaseClass> getMigratedDatabaseClasses();

    Set<DOSchemaClass> getSchemaClassesWithNoDatabaseClass();

}
