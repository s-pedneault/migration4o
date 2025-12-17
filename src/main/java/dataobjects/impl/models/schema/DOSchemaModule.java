package dataobjects.impl.models.schema;

import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.schema.DOSchemaModule;

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
