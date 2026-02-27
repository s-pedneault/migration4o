package migration4o.models.schema;

public interface DODatabaseSchema {
    public DOSchemaClass[] getClasses();

    public boolean isDescendant(DOSchemaClass schemaClass, String ancestorClassName);

    public DOSchemaClass findClassByName(String className);

}
