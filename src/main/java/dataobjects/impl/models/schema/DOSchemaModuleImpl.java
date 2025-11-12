package dataobjects.impl.models.schema;

import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;

public class DOSchemaModuleImpl implements DOSchemaModule {
    private final String name;
    private final DOSchemaClass[] classes;

    public DOSchemaModuleImpl(String name, DOSchemaClass[] classes) {
        this.name = name;
        this.classes = classes != null ? classes : new DOSchemaClass[0];
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DOSchemaClass[] getClasses() {
        return classes;
    }
}
