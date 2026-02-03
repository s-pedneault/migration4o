
package migration4o.models.schema;

import migration4o.util.SchemaUtil;

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

    public boolean isDescendant(DOSchemaClass schemaClass, String ancestorClassName) {
        return SchemaUtil.isDescendantOf(schemaClass, ancestorClassName, this);
    }

    public DOSchemaClass findClassByName(String className) {
        return SchemaUtil.findClassByName(className, this);
    }
}