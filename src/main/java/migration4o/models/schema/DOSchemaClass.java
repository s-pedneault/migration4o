
package migration4o.models.schema;

import migration4o.models.schema.DOSchemaField;
import migration4o.models.DOReference;
import migration4o.models.database.DODatabaseClass;

import java.util.ArrayList;
import java.util.List;

public class DOSchemaClass {
    private final String sourceName;
    private final String destinationName;
    private final String parentClass;
    private final boolean migrate;
    private final String title;
    private final String description;
    private final DOSchemaField[] fields;
    private final List<DOReference> referenceList;
    private DODatabaseClass databaseClass;

    public DOSchemaClass(String sourceName, String destinationName, String parentClass,
            boolean migrate, String title, DOSchemaField[] fields) {
        this(sourceName, destinationName, null, title, parentClass, fields, migrate);
    }

    public DOSchemaClass(String absoluteName, String simpleName, String description, String title,
            String superClassAbsoluteName,
            DOSchemaField[] fields, String exportName) {
        this(absoluteName, exportName != null ? exportName : simpleName, description, title,
                superClassAbsoluteName, fields, true);
    }

    private DOSchemaClass(String sourceName, String destinationName, String description, String title,
            String parentClass, DOSchemaField[] fields, boolean migrate) {
        this.sourceName = sourceName;
        this.destinationName = destinationName;
        this.description = description;
        this.parentClass = parentClass;
        this.migrate = migrate;
        this.title = title;
        this.fields = fields != null ? fields : new DOSchemaField[0];
        this.referenceList = new ArrayList<>();
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getParentClass() {
        return parentClass;
    }

    public boolean isMigrate() {
        return migrate;
    }

    public String getAbsoluteName() {
        return sourceName;
    }

    public String getShortName() {
        return destinationName;
    }

    public String getSuperClassAbsoluteName() {
        return parentClass;
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
}
