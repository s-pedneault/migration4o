
package migration4o.models.schema;

import migration4o.models.ui.MigrationModule;
import migration4o.util.SchemaUtil;

import java.util.List;

public class DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaModule[] modules;
    private final DOSchemaClass[] foundationClasses;
    private List<MigrationModule> migrationModules;

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

    public List<MigrationModule> getMigrationModules() {
        return migrationModules;
    }

    public void setMigrationModules(List<MigrationModule> migrationModules) {
        this.migrationModules = migrationModules;
    }
}