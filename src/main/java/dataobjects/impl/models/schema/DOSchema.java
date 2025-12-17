
package dataobjects.impl.models.schema;

import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.schema.DOSchemaModule;

public class DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaModule[] modules;
    private final DOSchemaClass[] foundationClasses;

    public DOSchema(DOSchemaClass[] classes, DOSchemaModule[] modules) {
        this(classes, modules, new DOSchemaClass[0]);
    }

    public DOSchema(DOSchemaClass[] classes, DOSchemaModule[] modules, DOSchemaClass[] foundationClasses) {
        this.classes = classes != null ? classes : new DOSchemaClass[0];
        this.modules = modules != null ? modules : new DOSchemaModule[0];
        this.foundationClasses = foundationClasses != null ? foundationClasses : new DOSchemaClass[0];
    }

    public DOSchemaClass[] getClasses() {
        return classes;
    }

    public DOSchemaModule[] getModules() {
        return modules;
    }

    public DOSchemaClass[] getFoundationClasses() {
        return foundationClasses;
    }
}
