
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
}