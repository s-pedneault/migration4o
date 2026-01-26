package migration4o.models.ui;

import migration4o.models.schema.DOSchemaField;

/**
 * Model class representing a node in the synchronized tree panel.
 * Holds metadata about tree nodes including ghost status, differences, package
 * info, and field data.
 */
public class SyncTreeNode {
    private String key; // Unique key for matching nodes
    private String displayName; // Display text
    private boolean isGhost; // True if class doesn't exist in this schema
    private boolean hasDifferences; // True if class has field differences
    private boolean isPackage; // True if this is a package node
    private boolean isField; // True if this is a field node
    private boolean hasOnlyInSchema; // True if package contains classes only in schema (for blue color)
    private boolean isNotExported; // True if class is marked isMigrate=false in reference schema
    private boolean hasOnlyNotExported; // True if package only contains not exported classes
    private ClassDifference difference; // Associated difference object
    private DOSchemaField fieldData; // Associated field data (if isField is true)

    public SyncTreeNode(String key, String displayName, boolean isGhost, boolean hasDifferences) {
        this(key, displayName, isGhost, hasDifferences, false, false, false, false, null);
    }

    public SyncTreeNode(String key, String displayName, boolean isGhost,
            boolean hasDifferences, ClassDifference difference) {
        this(key, displayName, isGhost, hasDifferences, false, false, false, false, difference);
    }

    public SyncTreeNode(String key, String displayName, boolean isGhost,
            boolean hasDifferences, boolean isPackage, boolean hasOnlyInSchema,
            ClassDifference difference) {
        this(key, displayName, isGhost, hasDifferences, isPackage, hasOnlyInSchema, false, false, difference);
    }

    public SyncTreeNode(String key, String displayName, boolean isGhost,
            boolean hasDifferences, boolean isPackage, boolean hasOnlyInSchema,
            boolean isNotExported, ClassDifference difference) {
        this(key, displayName, isGhost, hasDifferences, isPackage, hasOnlyInSchema, isNotExported, false,
                difference);
    }

    public SyncTreeNode(String key, String displayName, boolean isGhost,
            boolean hasDifferences, boolean isPackage, boolean hasOnlyInSchema,
            boolean isNotExported, boolean hasOnlyNotExported, ClassDifference difference) {
        this.key = key;
        this.displayName = displayName;
        this.isGhost = isGhost;
        this.hasDifferences = hasDifferences;
        this.isPackage = isPackage;
        this.isField = false;
        this.hasOnlyInSchema = hasOnlyInSchema;
        this.isNotExported = isNotExported;
        this.hasOnlyNotExported = hasOnlyNotExported;
        this.difference = difference;
        this.fieldData = null;
    }

    // Constructor for field nodes
    public SyncTreeNode(String key, String displayName, boolean isGhost,
            boolean hasDifferences, DOSchemaField fieldData) {
        this.key = key;
        this.displayName = displayName;
        this.isGhost = isGhost;
        this.hasDifferences = hasDifferences;
        this.isPackage = false;
        this.isField = true;
        this.hasOnlyInSchema = false;
        this.isNotExported = false;
        this.hasOnlyNotExported = false;
        this.difference = null;
        this.fieldData = fieldData;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isGhost() {
        return isGhost;
    }

    public boolean hasDifferences() {
        return hasDifferences;
    }

    public ClassDifference getDifference() {
        return difference;
    }

    public boolean isPackage() {
        return isPackage;
    }

    public boolean hasOnlyInSchema() {
        return hasOnlyInSchema;
    }

    public boolean isField() {
        return isField;
    }

    public boolean isNotExported() {
        return isNotExported;
    }

    public boolean hasOnlyNotExported() {
        return hasOnlyNotExported;
    }

    public DOSchemaField getFieldData() {
        return fieldData;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
