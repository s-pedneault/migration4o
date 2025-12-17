package migration4o.models.schema;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;

public class DOSchemaModule {
    private final String name;
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
}
