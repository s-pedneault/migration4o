package dataobjects.api.migration.generic;

import dataobjects.api.models.DOField;
import dataobjects.api.models.database.DODatabaseClass;

/**
 * Represents a column in the export output.
 * Contains metadata about the column including source field, name, and
 * flattening information.
 */
public class ExportColumn {

    /**
     * The field from the schema/database that this column represents.
     */
    public final DOField field;

    /**
     * The name to use for this column in the export output.
     */
    public final String columnName;

    /**
     * Whether this is a flattened field (from a referenced object).
     */
    public final boolean isFlattened;

    /**
     * If this is a flattened field, this is the parent field that contains the
     * reference.
     */
    public final DOField parentField;

    /**
     * If this is a flattened field, this is the class that contains the flattened
     * field.
     */
    public final DODatabaseClass flattenedFromClass;

    /**
     * Create a regular export column.
     */
    public ExportColumn(DOField field, String columnName) {
        this.field = field;
        this.columnName = columnName;
        this.isFlattened = false;
        this.parentField = null;
        this.flattenedFromClass = null;
    }

    /**
     * Create a flattened export column.
     */
    public ExportColumn(DOField field, String columnName, DOField parentField, DODatabaseClass flattenedFromClass) {
        this.field = field;
        this.columnName = columnName;
        this.isFlattened = true;
        this.parentField = parentField;
        this.flattenedFromClass = flattenedFromClass;
    }

    /**
     * Get a display name for this column (for debugging).
     */
    @Override
    public String toString() {
        if (isFlattened) {
            return "ExportColumn{" + columnName + " (flattened from " + parentField.getName() + ")}";
        } else {
            return "ExportColumn{" + columnName + "}";
        }
    }
}