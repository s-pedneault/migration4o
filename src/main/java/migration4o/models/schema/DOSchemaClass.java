
package migration4o.models.schema;

import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;

public class DOSchemaClass {
    public String source;
    public String destinationName;
    public String parentClassName;
    public boolean migrate;
    public String title;
    public String description;
    public DOSchemaField[] fields;
    public DOSchemaReference[] schemaReferences;
    public long[] objectIds; // Object IDs from database
    public long[] uniqueObjectIds; // Unique object IDs after deduplication
    public long[] reachedObjectIds; // Object IDs reached during reach analysis
    public String pointsTo; // For IDEntite classes: the target class name this points to

    public DOSchemaClass() {
    }

    public String getSourcePackage() {
        return ClassUtil.getPackageName(source);
    }

    public String getSourceName() {
        return ClassUtil.getSimpleName(source);
    }

    public boolean isDescendantOf(String ancestorClassName, DOSchema schema) {
        return SchemaUtil.isDescendantOf(this, ancestorClassName, schema);
    }

    public boolean isIDEntite(DOSchema schema) {
        return isDescendantOf("gest.gen.IDEntite", schema);
    }

    public boolean isEntite(DOSchema schema) {
        return isDescendantOf("gest.gen.EntiteContientID", schema);
    }

    public boolean isParam(DOSchema schema) {
        return isDescendantOf("gest.gen.EntiteParam", schema);
    }

}
