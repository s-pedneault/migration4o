package migration4o.models.schema;

/** @deprecated No longer used. DODatabase replaces database-derived DOSchema. */
@Deprecated
public interface DODatabaseSchema {
    public DOSchemaClass[] getClasses();

    public boolean isDescendant(DOSchemaClass schemaClass, String ancestorClassName);

    public DOSchemaClass findClassByName(String className);

}
