package migration4o.models.ui;

/**
 * Model class representing a table column definition.
 * Holds metadata for table column configuration including name, width, and data
 * type.
 */
public class ColumnDefinition {
    public final String name;
    public final int width;
    public final Class<?> columnClass;

    public ColumnDefinition(String name, int width, Class<?> columnClass) {
        this.name = name;
        this.width = width;
        this.columnClass = columnClass;
    }
}
