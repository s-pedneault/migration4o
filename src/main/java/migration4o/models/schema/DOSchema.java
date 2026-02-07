
package migration4o.models.schema;

import migration4o.util.SchemaUtil;

import java.util.ArrayList;
import java.util.List;

public class DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaClass[] foundationClasses;
    public final List<DOSchemaAnomaly> anomalies = new ArrayList<>();

    public DOSchema(DOSchemaClass[] classes) {
        this(classes, new DOSchemaClass[0]);
    }

    public DOSchema(DOSchemaClass[] classes, DOSchemaClass[] foundationClasses) {
        this.classes = classes != null ? classes : new DOSchemaClass[0];
        this.foundationClasses = foundationClasses != null ? foundationClasses : new DOSchemaClass[0];
    }

    public DOSchemaClass[] getClasses() {
        return classes;
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