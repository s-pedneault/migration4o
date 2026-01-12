package migration4o.models.schema;

public class DOSchemaModule {
    private String name;
    private final DOSchemaClass[] classes;

    public DOSchemaModule(String name, DOSchemaClass[] classes) {
        this.name = name;
        this.classes = classes != null ? classes : new DOSchemaClass[0];
    }

    public String getName() {
        return name;
    }

    public DOSchemaClass[] getClasses() {
        return classes;
    }

    public void setName(String name) {
        this.name = name;
    }
}
