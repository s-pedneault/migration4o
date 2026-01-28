package migration4o.database.processors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.DODatabaseContext;
import migration4o.models.schema.DOSchemaField;

/**
 * Converter for transforming DB4O StoredField arrays to DOSchemaField arrays.
 * Provides static methods for batch field conversion without requiring
 * instantiation.
 */
public class DOFieldsConverter {

    /**
     * Private constructor to prevent instantiation.
     */
    private DOFieldsConverter() {
    }

    /**
     * Converts stored fields to schema fields.
     * Deduplicates fields with the same name (keeps array version if both exist).
     * 
     * @param storedClass The stored class containing the fields
     * @param context     The database context containing container and stored class
     *                    map
     * @return Array of converted schema fields
     */
    public static DOSchemaField[] convertStoredFieldsToSchemaFields(
            StoredClass storedClass,
            DODatabaseContext context) {

        try {
            StoredField[] storedFields = storedClass.getStoredFields();

            // Use a map to deduplicate fields by name
            Map<String, StoredField> fieldMap = new LinkedHashMap<>();

            for (StoredField sf : storedFields) {
                String fieldName = sf.getName();
                StoredField existing = fieldMap.get(fieldName);

                if (existing == null) {
                    // First occurrence of this field name
                    fieldMap.put(fieldName, sf);
                } else {
                    // Duplicate field name - prefer array version
                    if (sf.isArray() && !existing.isArray()) {
                        // New field is array, existing is not - replace with array version
                        fieldMap.put(fieldName, sf);
                    }
                }
            }

            // Convert deduplicated fields to DOSchemaField array
            List<DOSchemaField> schemaFields = new ArrayList<>();
            for (StoredField sf : fieldMap.values()) {
                try {
                    DOSchemaField schemaField = DOFieldConverter.convertStoredFieldToSchemaField(sf, context);
                    schemaFields.add(schemaField);
                } catch (Exception e) {
                    System.out.println("Warning: Could not convert field '" +
                            sf.getName() + "': " + e.getMessage());
                }
            }

            return schemaFields.toArray(new DOSchemaField[0]);

        } catch (Exception e) {
            System.out.println("Error converting fields for class " + storedClass.getName() + ": " + e.getMessage());
            return new DOSchemaField[0];
        }
    }
}
