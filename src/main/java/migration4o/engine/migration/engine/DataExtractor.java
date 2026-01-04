package migration4o.engine.migration.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import migration4o.engine.DOEngine;
import migration4o.models.database.DODatabaseField;
import migration4o.models.database.DODatabaseObject;
import migration4o.util.ObjectResolverUtil;

/**
 * Responsible for extracting raw data from database objects.
 * Handles all the complex database access, field resolution, and flattening
 * logic.
 */
public class DataExtractor {

    private final DOEngine engine;

    public DataExtractor(DOEngine engine) {
        this.engine = engine;
    }

    /**
     * Extract raw values for all columns from a database object.
     * This handles regular fields, ID fields, and flattened fields.
     */
    public List<Object> extractValues(DODatabaseObject obj, List<ExportColumn> columns) {
        List<Object> values = new ArrayList<>(columns.size());

        // Get the container and actual object
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        Object actualObj = container.getByID(obj.getObjectId());
        if (actualObj != null) {
            ObjectResolverUtil.activateObject(container, actualObj, obj.getObjectId());
        }

        // Extract primitive field values for regular fields
        Map<String, ObjectResolverUtil.PrimitiveFieldValue> fieldValues = ObjectResolverUtil
                .extractPrimitiveFieldValues(container, obj.getObjectId(), obj.getAllClasses());

        for (ExportColumn column : columns) {
            Object value = extractSingleValue(container, actualObj, column, fieldValues);
            values.add(value);
        }

        return values;
    }

    /**
     * Extract a single value for one column.
     */
    private Object extractSingleValue(com.db4o.ext.ExtObjectContainer container, Object actualObj,
            ExportColumn column, Map<String, ObjectResolverUtil.PrimitiveFieldValue> fieldValues) {

        if (column.isFlattened) {
            return extractFlattenedValue(container, actualObj, column);
        } else if (isIDTypeField(column.field)) {
            return extractIDValue(container, actualObj, column);
        } else {
            return extractRegularValue(container, actualObj, column, fieldValues);
        }
    }

    /**
     * Extract value from a flattened field (field from a referenced object).
     */
    private Object extractFlattenedValue(com.db4o.ext.ExtObjectContainer container, Object actualObj,
            ExportColumn column) {
        if (actualObj == null) {
            return null;
        }

        Object idFieldObj = ObjectResolverUtil.getFieldValue(container, actualObj, column.parentField);
        if (idFieldObj == null) {
            return null;
        }

        ObjectResolverUtil.activateObject(container, idFieldObj, -1L);
        return ObjectResolverUtil.getFieldValue(container, idFieldObj, column.field);
    }

    /**
     * Extract mID value from an ID-type field.
     */
    private Object extractIDValue(com.db4o.ext.ExtObjectContainer container, Object actualObj, ExportColumn column) {
        if (actualObj == null) {
            return null;
        }

        Object idFieldObj = ObjectResolverUtil.getFieldValue(container, actualObj, column.field);
        if (idFieldObj == null) {
            return null;
        }

        ObjectResolverUtil.activateObject(container, idFieldObj, -1L);

        // Get the mID field from the ID object
        DODatabaseField mIDField = findFieldByName(idFieldObj.getClass(), "mID");
        if (mIDField != null) {
            return ObjectResolverUtil.getFieldValue(container, idFieldObj, mIDField);
        }

        return null;
    }

    /**
     * Extract value from a regular (non-ID) field.
     */
    private Object extractRegularValue(com.db4o.ext.ExtObjectContainer container, Object actualObj,
            ExportColumn column, Map<String, ObjectResolverUtil.PrimitiveFieldValue> fieldValues) {

        // First try primitive values cache
        ObjectResolverUtil.PrimitiveFieldValue fieldValue = fieldValues.get(column.field.getName());
        if (fieldValue != null && fieldValue.value != null) {
            return fieldValue.value;
        }

        // Not in primitive values - might be a collection or complex object
        if (actualObj != null) {
            Object fieldObj = ObjectResolverUtil.getFieldValue(container, actualObj, column.field);

            // Return the raw collection/object - format handlers will process it
            if (fieldObj != null) {
                // Activate collections and complex objects for inspection
                if (ObjectResolverUtil.isAnyCollectionType(fieldObj) || !isPrimitiveOrWrapper(fieldObj.getClass())) {
                    ObjectResolverUtil.activateObject(container, fieldObj, -1L);
                }
            }

            return fieldObj;
        }

        return null;
    }

    /**
     * Check if a class is a primitive type or wrapper.
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Boolean.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Double.class ||
                clazz == Float.class ||
                clazz == Short.class ||
                clazz == Byte.class ||
                clazz == Character.class ||
                java.util.Date.class.isAssignableFrom(clazz);
    }

    /**
     * Check if a field is an ID-type field.
     */
    private boolean isIDTypeField(DODatabaseField field) {
        String typeName = field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Find a field by name using reflection (helper method).
     */
    private DODatabaseField findFieldByName(Class<?> clazz, String fieldName) {
        // This would need proper implementation based on your DODatabaseField structure
        // For now, returning null as placeholder
        return null;
    }
}