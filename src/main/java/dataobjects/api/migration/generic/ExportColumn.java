package dataobjects.api.migration.generic;

import dataobjects.api.models.DOField;
import dataobjects.api.models.database.DODatabaseClass;

/**
 * Represents a column in the export, which can be either a direct field
 * or a flattened field from an ID-type object.
 */
public class ExportColumn {
    public final DOField field;
    public final String columnName;
    public final boolean isFlattened;
    public final DOField flattenedParentField; // The ID-type field that contains this flattened field
    public final DODatabaseClass flattenedSourceClass; // The class this flattened field comes from

    /**
     * Constructor for regular (non-flattened) fields
     */
    public ExportColumn(DOField field, String columnName) {
        this.field = field;
        this.columnName = columnName;
        this.isFlattened = false;
        this.flattenedParentField = null;
        this.flattenedSourceClass = null;
    }

    /**
     * Constructor for flattened fields
     */
    public ExportColumn(DOField field, String columnName, DOField parentField, DODatabaseClass sourceClass) {
        this.field = field;
        this.columnName = columnName;
        this.isFlattened = true;
        this.flattenedParentField = parentField;
        this.flattenedSourceClass = sourceClass;
    }
}
