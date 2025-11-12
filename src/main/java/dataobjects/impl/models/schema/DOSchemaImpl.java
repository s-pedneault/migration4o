
package dataobjects.impl.models.schema;

import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;

public class DOSchemaImpl implements DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaModule[] modules;

    public DOSchemaImpl(DOSchemaClass[] classes, DOSchemaModule[] modules) {
        this.classes = classes != null ? classes : new DOSchemaClass[0];
        this.modules = modules != null ? modules : new DOSchemaModule[0];
    }

    @Override
    public DOSchemaClass[] getClasses() {
        return classes;
    }

    @Override
    public DOSchemaModule[] getModules() {
        return modules;
    }
}
