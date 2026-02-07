
package migration4o.models.schema;

import java.util.LinkedHashMap;
import java.util.Map;

public class DOSchemaField {
    public String source;
    public String destinationName;
    public String type;
    public boolean isExported;
    public String skipWhen; // Comma-separated skip conditions (NULL,ZERO,MINUS_ONE,etc.)
    public boolean isCollection;
    public boolean embedContents;
    public String childrenType;
    public String title;
    public String description;
    public String pointsTo;
    public Map<String, String> valueMap; // Maps database values to export values

    // Shared field definition support
    public String definitionId; // If set, this field references a shared definition

    public DOSchemaClass childrenSchemaClass;
    public DOSchemaClass parentClass; // The class that contains this field

    public DOSchemaField() {
    }

    /**
     * Gets the mapped value for the given database value, or returns the original
     * value if no mapping exists.
     */
    public String getMappedValue(String databaseValue) {
        if (valueMap == null || valueMap.isEmpty() || databaseValue == null) {
            return databaseValue;
        }
        return valueMap.getOrDefault(databaseValue, databaseValue);
    }

    /**
     * Adds a value mapping.
     */
    public void addValueMapping(String fromValue, String toValue) {
        if (valueMap == null) {
            valueMap = new LinkedHashMap<>();
        }
        valueMap.put(fromValue, toValue);
    }

    /**
     * Returns true if this field is a reference to a shared field definition.
     */
    public boolean isSharedField() {
        return definitionId != null && !definitionId.trim().isEmpty();
    }

    /**
     * Creates a deep copy of this field (used when instantiating shared fields).
     */
    public DOSchemaField copy() {
        DOSchemaField copy = new DOSchemaField();
        copy.source = this.source;
        copy.destinationName = this.destinationName;
        copy.type = this.type;
        copy.isExported = this.isExported;
        copy.skipWhen = this.skipWhen;
        copy.isCollection = this.isCollection;
        copy.embedContents = this.embedContents;
        copy.childrenType = this.childrenType;
        copy.title = this.title;
        copy.description = this.description;
        copy.pointsTo = this.pointsTo;
        copy.definitionId = this.definitionId;

        // Deep copy value map
        if (this.valueMap != null) {
            copy.valueMap = new LinkedHashMap<>(this.valueMap);
        }

        // Note: childrenSchemaClass and parentClass are not copied as they're set later
        return copy;
    }
}