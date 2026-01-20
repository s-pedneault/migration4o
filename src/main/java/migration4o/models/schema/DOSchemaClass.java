
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
        // All fields are public and default to null/false
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

    public long[] getReachedObjectIds() {
        return reachedObjectIds;
    }

    public void setReachedObjectIds(long[] reachedObjectIds) {
        this.reachedObjectIds = reachedObjectIds;
    }

    public int getReachedObjectCount() {
        return reachedObjectIds != null ? reachedObjectIds.length : 0;
    }

    public String getPointsTo() {
        return pointsTo;
    }

    public void setPointsTo(String pointsTo) {
        this.pointsTo = pointsTo;
    }
}
