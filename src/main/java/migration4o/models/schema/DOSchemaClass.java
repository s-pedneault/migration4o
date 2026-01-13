
package migration4o.models.schema;

import java.util.ArrayList;
import java.util.List;

import migration4o.models.DOReference;
import migration4o.models.database.DODatabaseClass;

public class DOSchemaClass {
    private String source;
    private String destinationName;
    private String parentClassName;
    private boolean migrate;
    private String title;
    private String description;
    private DOSchemaField[] fields;
    private DOSchemaReference[] schemaReferences;
    private final List<DOReference> referenceList;
    private DODatabaseClass databaseClass;
    private long[] objectIds; // Object IDs from database
    private long[] uniqueObjectIds; // Unique object IDs after deduplication

    public DOSchemaClass(String source, String destinationName, String parentClassName,
            boolean migrate, String title, DOSchemaField[] fields) {
        this(source, destinationName, null, title, parentClassName, fields, null, migrate);
    }

    public DOSchemaClass(String absoluteName, String simpleName, String description, String title,
            String parentClassName,
            DOSchemaField[] fields, String exportName) {
        this(absoluteName, exportName != null ? exportName : simpleName, description, title,
                parentClassName, fields, null, true);
    }

    public DOSchemaClass(String absoluteName, String simpleName, String description, String title,
            String parentClassName, DOSchemaField[] fields, DOSchemaReference[] schemaReferences, boolean migrate) {
        this(absoluteName, simpleName, description, title, parentClassName, fields, schemaReferences, migrate, null);
    }

    public DOSchemaClass(String absoluteName, String simpleName, String description, String title,
            String parentClassName, DOSchemaField[] fields, DOSchemaReference[] schemaReferences, boolean migrate,
            long[] objectIds) {
        this.source = absoluteName;
        this.destinationName = simpleName;
        this.description = description;
        this.parentClassName = parentClassName;
        this.migrate = migrate;
        this.title = title;
        this.fields = fields != null ? fields : new DOSchemaField[0];
        this.schemaReferences = schemaReferences != null ? schemaReferences : new DOSchemaReference[0];
        this.referenceList = new ArrayList<>();
        this.objectIds = objectIds;
        // Initialize uniqueObjectIds as a copy of objectIds (will be deduplicated later
        // if needed)
        this.uniqueObjectIds = objectIds != null ? objectIds.clone() : null;
    }

    public String getSourceName() {
        return source;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getParentClass() {
        return parentClassName;
    }

    public boolean isMigrate() {
        return migrate;
    }

    public String getAbsoluteName() {
        return source;
    }

    public String getShortName() {
        return destinationName;
    }

    public String getSuperClassAbsoluteName() {
        return parentClassName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getExportName() {
        return destinationName;
    }

    public DOSchemaField[] getFields() {
        return fields;
    }

    public DOSchemaReference[] getSchemaReferences() {
        return schemaReferences;
    }

    public DOReference[] getReferences() {
        return referenceList.toArray(new DOReference[0]);
    }

    public void setReferences(DOReference[] references) {
        referenceList.clear();
        if (references != null) {
            for (DOReference ref : references) {
                if (ref != null) {
                    referenceList.add(ref);
                }
            }
        }
    }

    public void addReference(DOReference reference) {
        if (reference != null) {
            referenceList.add(reference);
        }
    }

    public DODatabaseClass getDatabaseClass() {
        return databaseClass;
    }

    public void setDatabaseClass(DODatabaseClass databaseClass) {
        this.databaseClass = databaseClass;
    }

    public void setShortName(String destinationName) {
        this.destinationName = destinationName;
    }

    public void setParentClass(String parentClassName) {
        this.parentClassName = parentClassName;
    }

    public void setMigrate(boolean migrate) {
        this.migrate = migrate;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long[] getObjectIds() {
        return objectIds;
    }

    public int getObjectCount() {
        return objectIds != null ? objectIds.length : 0;
    }

    public long[] getUniqueObjectIds() {
        return uniqueObjectIds;
    }

    public void setUniqueObjectIds(long[] uniqueObjectIds) {
        this.uniqueObjectIds = uniqueObjectIds;
    }

    public int getUniqueObjectCount() {
        return uniqueObjectIds != null ? uniqueObjectIds.length : 0;
    }
}
