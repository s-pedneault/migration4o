package migration4o.database.processors;

import java.util.LinkedHashMap;
import java.util.Map;

import com.db4o.ext.StoredField;

/**
 * Processor for deduplicating DB4O stored fields.
 * Keeps first occurrence by field name, but prefers array variants on duplicates.
 */
public class DOStoredFieldDeduplicationProcessor {

    /**
     * Private constructor to prevent instantiation.
     */
    private DOStoredFieldDeduplicationProcessor() {
    }

    /**
     * Deduplicates stored fields by field name.
     * If duplicate names exist, an array field replaces a non-array field.
     *
     * @param storedFields The source stored fields
     * @return A map keyed by field name with deduplicated StoredField values
     */
    public static Map<String, StoredField> deduplicateByNamePreferArray(StoredField[] storedFields) {
        Map<String, StoredField> fieldMap = new LinkedHashMap<>();

        for (StoredField sf : storedFields) {
            String fieldName = sf.getName();
            StoredField existing = fieldMap.get(fieldName);

            if (existing == null) {
                fieldMap.put(fieldName, sf);
            } else if (sf.isArray() && !existing.isArray()) {
                fieldMap.put(fieldName, sf);
            }
        }

        return fieldMap;
    }
}