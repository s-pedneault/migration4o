package migration4o.database.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.DODatabaseContext;
import migration4o.database.DODatabaseMonitor;
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
    public static DOSchemaField[] convertStoredFieldsToSchemaFields(StoredClass storedClass, DODatabaseContext context) {
        return convertStoredFieldsToSchemaFields(storedClass, context, null);
    }

    /**
     * Converts stored fields to schema fields.
     * Deduplicates fields with the same name (keeps array version if both exist).
     * 
     * @param storedClass The stored class containing the fields
     * @param context     The database context containing container and stored class
     *                    map
     * @param monitor     Optional monitor for progress feedback
     * @return Array of converted schema fields
     */
    public static DOSchemaField[] convertStoredFieldsToSchemaFields(StoredClass storedClass, DODatabaseContext context, DODatabaseMonitor monitor) {

        try {
            StoredField[] storedFields = storedClass.getStoredFields();

            if (monitor != null) {
                monitor.onConvertingFields(storedClass.getName(), storedFields.length);
            }

            // Deduplicate fields by name (prefers array version on duplicates)
            Map<String, StoredField> fieldMap = DOStoredFieldDeduplicationProcessor.deduplicateByNamePreferArray(storedFields);

            // Convert deduplicated fields to DOSchemaField array
            List<DOSchemaField> schemaFields = new ArrayList<>();
            for (StoredField sf : fieldMap.values()) {
                try {
                    DOSchemaField schemaField = DOFieldConverter.convertStoredFieldToSchemaField(sf, context);
                    schemaFields.add(schemaField);
                } catch (Exception e) {
                    String errorMsg = "Could not convert field '" + sf.getName() + "': " + e.getMessage();
                    if (monitor != null) {
                        monitor.onFieldConversionWarning(storedClass.getName(), sf.getName(), e.getMessage());
                    } else {
                        System.out.println("Warning: " + errorMsg);
                    }
                }
            }

            return schemaFields.toArray(new DOSchemaField[0]);

        } catch (Exception e) {
            String errorMsg = "Error converting fields for class " + storedClass.getName() + ": " + e.getMessage();
            if (monitor != null) {
                monitor.onFieldConversionError(storedClass.getName(), e.getMessage());
            } else {
                System.out.println(errorMsg);
            }
            return new DOSchemaField[0];
        }
    }
}
