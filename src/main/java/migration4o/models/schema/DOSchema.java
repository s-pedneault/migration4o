
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

    /**
     * Returns true if no other class in this schema declares {@code sc} as its parentClass.
     * A leaf class has no subclasses and should be the only one instantiated directly.
     */
    public boolean isLeafClass(DOSchemaClass sc) {
        if (sc == null || classes == null) {
            return true;
        }
        String sourceName = sc.attributes.source;
        for (DOSchemaClass other : classes) {
            if (sourceName.equals(other.attributes.parentClassName)) {
                return false;
            }
        }
        return true;
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
        // Only fall back to simple-name matching when a fully-qualified name was passed
        // (contains a package separator). Bare type names like "date", "string", "int"
        // have no dot and will never be found this way — skip the fallback entirely.
        if (className.contains(".")) {
            String searchSimpleName = ClassUtil.getSimpleName(className);
            for (DOSchemaClass schemaClass : classes) {
                String schemaSimpleName = ClassUtil.getSimpleName(schemaClass.attributes.source);
                if (schemaSimpleName.equals(searchSimpleName)) {
                    return schemaClass;
                }
            }
        }

        return null;
    }
}