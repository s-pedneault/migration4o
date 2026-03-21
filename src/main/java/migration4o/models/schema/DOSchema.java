
package migration4o.models.schema;

import migration4o.models.schema.analysis.DOSchemaAnomaly;
import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DOSchema implements DOReferenceSchema {
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
        return schemaClass.isDescendantOf(ancestorClassName);
    }

    public DOSchemaClass findClassByName(String className) {
        if (className == null || classes == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : classes) {
            if (schemaClass.attributes.source.equals(className)) {
                return schemaClass;
            }
        }
        String searchSimpleName = ClassUtil.getSimpleName(className);
        for (DOSchemaClass schemaClass : classes) {
            String schemaSimpleName = ClassUtil.getSimpleName(schemaClass.attributes.source);
            if (schemaSimpleName.equals(searchSimpleName)) {
                System.err.println("Warning: Used simple-class-name fallback, but we should not have done that.");
                return schemaClass;
            }
        }

        return null;
    }
}