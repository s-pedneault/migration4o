
package migration4o.models.schema;

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
