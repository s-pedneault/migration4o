
package migration4o.models.schema;

import java.util.ArrayList;
import java.util.List;

import migration4o.models.DOReference;
import migration4o.models.database.DODatabaseClass;

public class DOSchemaClass {
    public String source;
    public String destinationName;
    public String parentClassName;
    public boolean migrate;
    public String title;
    public String description;
    public DOSchemaField[] fields;
    public DOSchemaReference[] schemaReferences;
    public List<DOReference> referenceList;
    public DODatabaseClass databaseClass;
    public long[] objectIds; // Object IDs from database
    public long[] uniqueObjectIds; // Unique object IDs after deduplication
    public long[] reachedObjectIds; // Object IDs reached during reach analysis
    public String pointsTo; // For IDEntite classes: the target class name this points to

    public DOSchemaClass() {
        referenceList = new ArrayList<>();
    }
}
