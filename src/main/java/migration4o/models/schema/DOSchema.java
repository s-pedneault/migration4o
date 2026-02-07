
package migration4o.models.schema;

import migration4o.util.SchemaUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DOSchema {
    public DOSchemaClass[] classes;
    public final List<DOSchemaAnomaly> anomalies = new ArrayList<>();
    public final Map<String, DOSchemaField> sharedFields = new LinkedHashMap<>();

    public DOSchema() {
        this.classes = new DOSchemaClass[0];
    }

    public DOSchemaClass[] getClasses() {
        return classes;
    }

    public boolean isDescendant(DOSchemaClass schemaClass, String ancestorClassName) {
        return SchemaUtil.isDescendantOf(schemaClass, ancestorClassName, this);
    }

    public DOSchemaClass findClassByName(String className) {
        return SchemaUtil.findClassByName(className, this);
    }
}