
package dataobjects.impl.models.schema;

import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;

public class DOSchemaImpl implements DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaModule[] modules;
    private final DOSchemaClass[] foundationClasses;

    public DOSchemaImpl(DOSchemaClass[] classes, DOSchemaModule[] modules) {
        this(classes, modules, new DOSchemaClass[0]);
    }

    public DOSchemaImpl(DOSchemaClass[] classes, DOSchemaModule[] modules, DOSchemaClass[] foundationClasses) {
        this.classes = classes != null ? classes : new DOSchemaClass[0];
        this.modules = modules != null ? modules : new DOSchemaModule[0];
        this.foundationClasses = foundationClasses != null ? foundationClasses : new DOSchemaClass[0];
    }

    @Override
    public DOSchemaClass[] getClasses() {
        return classes;
    }

    @Override
    public DOSchemaModule[] getModules() {
        return modules;
    }

    @Override
    public DOSchemaClass[] getFoundationClasses() {
        return foundationClasses;
    }
}
