package dataobjects.impl.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.DOField;
import dataobjects.api.migration.generic.ExportColumn;
import dataobjects.util.ObjectResolverUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        DOField mIDField = findFieldByName(idFieldObj.getClass(), "mID");
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

            if (fieldObj != null && ObjectResolverUtil.isAnyCollectionType(fieldObj)) {
                return formatCollectionForExport(fieldObj);
            }

            return fieldObj;
        }

        return null;
    }

    /**
     * Check if a field is an ID-type field.
     */
    private boolean isIDTypeField(DOField field) {
        String typeName = field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Simple collection formatting (can be enhanced later).
     */
    private Object formatCollectionForExport(Object collectionObj) {
        return "[Collection: " + collectionObj.getClass().getSimpleName() + "]";
    }

    /**
     * Find a field by name using reflection (helper method).
     */
    private DOField findFieldByName(Class<?> clazz, String fieldName) {
        // This would need proper implementation based on your DOField structure
        // For now, returning null as placeholder
        return null;
    }
}