package dataobjects.api.migration.generic;

import java.util.List;
import java.util.ArrayList;

/**
 * Wrapper class for collection values that allows format handlers to decide
 * how to render collections (exploded or flattened).
 */
public class CollectionValue {
    private final List<String> items;
    private final String fieldName;
    private final boolean isIDCollection;
    private final String referencedClassName;
    private final String exportModuleName;

    public CollectionValue(String fieldName, boolean isIDCollection) {
        this(fieldName, isIDCollection, null, null);
    }

    public CollectionValue(String fieldName, boolean isIDCollection, String referencedClassName,
            String exportModuleName) {
        this.fieldName = fieldName;
        this.isIDCollection = isIDCollection;
        this.referencedClassName = referencedClassName;
        this.exportModuleName = exportModuleName;
        this.items = new ArrayList<>();
    }

    public void addItem(String item) {
        if (item != null && !item.trim().isEmpty()) {
            this.items.add(item.trim());
        }
    }

    public List<String> getItems() {
        return new ArrayList<>(items);
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isIDCollection() {
        return isIDCollection;
    }

    public String getReferencedClassName() {
        return referencedClassName;
    }

    public String getExportModuleName() {
        return exportModuleName;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /**
     * Returns the traditional comma-separated string representation for
     * backward compatibility with Excel format.
     */
    public String toCommaSeparatedString() {
        if (isEmpty()) {
            return null;
        }
        return String.join(", ", items);
    }

    @Override
    public String toString() {
        return toCommaSeparatedString();
    }
}