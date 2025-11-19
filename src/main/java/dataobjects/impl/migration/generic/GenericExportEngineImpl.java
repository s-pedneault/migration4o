package dataobjects.impl.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.migration.generic.DOGenericExportEngine;
import dataobjects.api.migration.generic.ExportFormatHandler;
import dataobjects.api.migration.generic.ExportColumn;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.DOField;
import dataobjects.api.models.DOClass;
import dataobjects.util.ObjectResolverUtil;

import java.io.IOException;
import java.util.*;

/**
 * Generic export engine implementation that delegates format-specific operations
 * to an ExportFormatHandler.
 * Contains all the common logic for iterating through modules, classes, and
 * objects,
 * and extracting field values with proper ID flattening.
 */
public class GenericExportEngineImpl implements DOGenericExportEngine {

    // Fields to export first (in this order) if they exist
    private static final String[] PRIORITY_FIELDS = {
            "mID",
            "mIDSSI"
    };

    private DOEngine engine;

    @Override
    public void export(DOEngine engine, ExportFormatHandler handler) throws IOException {
        export(engine, handler, handler.getDefaultOutputDirectory());
    }

    @Override
    public void export(DOEngine engine, ExportFormatHandler handler, String outputDirectory) throws IOException {
        this.engine = engine;

        // Initialize the format handler
        handler.initialize(outputDirectory);

        DOSchema schema = engine.getSchema();
        if (schema == null || schema.getModules() == null) {
            throw new IOException("Schema or modules not available");
        }

        // Process each module
        for (DOSchemaModule module : schema.getModules()) {
            exportModule(module, handler);
        }

        // Finalize the export
        handler.finalize();
    }

    private void exportModule(DOSchemaModule module, ExportFormatHandler handler) throws IOException {
        System.out.println("Exporting module: " + module.getName());

        // Begin module in the format handler
        Object moduleContext = handler.beginModule(module);

        // Track which database classes we've already exported to avoid duplicates
        Set<DODatabaseClass> exportedDbClasses = new HashSet<>();

        // Process each class in the module
        if (module.getClasses() != null) {
            for (DOSchemaClass schemaClass : module.getClasses()) {
                DODatabaseClass dbClass = schemaClass.getDatabaseClass();

                // Skip if we've already exported this database class
                if (dbClass != null && !exportedDbClasses.contains(dbClass)) {
                    exportedDbClasses.add(dbClass);
                    exportClass(moduleContext, schemaClass, handler);
                } else if (dbClass != null) {
                    System.out.println("Skipping duplicate database class: " + schemaClass.getShortName());
                }
            }
        }

        // End module in the format handler
        handler.endModule(moduleContext, module);

        System.out.println("Module exported successfully: " + module.getName());
    }

    private void exportClass(Object moduleContext, DOSchemaClass schemaClass, ExportFormatHandler handler)
            throws IOException {
        // Use export name if available, otherwise fall back to short name
        String exportName = schemaClass.getExportName();
        if (exportName == null || exportName.isEmpty()) {
            exportName = schemaClass.getShortName();
        }

        // Get the database class linked to this schema class
        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
        if (dbClass == null) {
            System.out.println("Warning: No database class linked for schema class: " + exportName);
            return;
        }

        // Get all resolved objects for this class
        // NOTE: Temporarily using getResolvedObjects() instead of getReachableObjects()
        // because reachability resolution may not be working correctly for module root classes
        DODatabaseObject[] objects = dbClass.getResolvedObjects();
        if (objects == null || objects.length == 0) {
            System.out.println("No objects found for class: " + exportName);
            return;
        }
        
        // DEBUG: Check reachability for DossPrev
        if (exportName.contains("DossierAdresse")) {
            int reachableCount = dbClass.getReachableObjects() != null ? dbClass.getReachableObjects().length : 0;
            System.out.println("DEBUG " + exportName + ": resolved=" + objects.length + ", reachable=" + reachableCount);
        }

        // Build the column structure (including flattened ID fields)
        List<ExportColumn> columns = buildExportColumns(dbClass);

        // Begin class in the format handler
        Object classContext = handler.beginClass(moduleContext, schemaClass, dbClass, columns, objects.length);

        // Export each object as a row
        int rowIndex = 0;
        for (DODatabaseObject obj : objects) {
            List<Object> cellValues = extractCellValues(obj, columns);
            handler.exportRow(classContext, obj, columns, rowIndex++, cellValues);
        }

        // End class in the format handler
        handler.endClass(classContext, schemaClass, objects.length);

        System.out.println("Exported " + objects.length + " objects for class: " + exportName);
    }

    /**
     * Extract cell values for all columns for a given object.
     */
    private List<Object> extractCellValues(DODatabaseObject obj, List<ExportColumn> columns) {
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
            Object value = null;

            if (column.isFlattened) {
                // This is a flattened field from an ID-type object
                value = extractFlattenedFieldValue(container, actualObj, column);
            } else if (isIDTypeField(column.field)) {
                // Non-flattened ID field: extract mID value
                value = extractIDFieldValue(container, actualObj, column);
            } else {
                // Regular field (primitive or collection)
                ObjectResolverUtil.PrimitiveFieldValue fieldValue = fieldValues.get(column.field.getName());
                if (fieldValue != null && fieldValue.value != null) {
                    value = fieldValue.value;
                } else {
                    // Field not in primitive values - might be a collection
                    Object fieldObj = actualObj != null
                            ? ObjectResolverUtil.getFieldValue(container, actualObj, column.field)
                            : null;

                    if (fieldObj != null && ObjectResolverUtil.isAnyCollectionType(fieldObj)) {
                        value = formatCollectionForExport(container, fieldObj, column.field);
                    }
                }
            }

            values.add(value);
        }

        return values;
    }

    /**
     * Extract a flattened field value from an ID-type object.
     */
    private Object extractFlattenedFieldValue(com.db4o.ext.ExtObjectContainer container, Object actualObj,
            ExportColumn column) {
        try {
            // First, get the ID object from the parent field
            Object idObject = actualObj != null
                    ? ObjectResolverUtil.getFieldValue(container, actualObj, column.flattenedParentField)
                    : null;

            if (idObject != null) {
                Long idObjectId = ObjectResolverUtil.getObjectId(container, idObject);
                if (idObjectId != null) {
                    // Activate the ID object
                    ObjectResolverUtil.activateObject(container, idObject, idObjectId);

                    // Extract the mID field from the ID object - this is the db4o internal ID of
                    // the target
                    Object mIdValue = ObjectResolverUtil.getFieldValue(container, idObject,
                            findMIdField(column.flattenedParentField.getTypeClass()));

                    if (mIdValue instanceof Long) {
                        Long targetObjectId = (Long) mIdValue;

                        // Get the actual target object (e.g., Caserne, not IDCaserne)
                        Object targetObject = container.getByID(targetObjectId);
                        if (targetObject != null) {
                            // Activate the target object
                            ObjectResolverUtil.activateObject(container, targetObject, targetObjectId);

                            // Extract the field value from the target object
                            return ObjectResolverUtil.getFieldValue(container, targetObject, column.field);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting flattened field: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract mID value from an ID field.
     */
    private Object extractIDFieldValue(com.db4o.ext.ExtObjectContainer container, Object actualObj,
            ExportColumn column) {
        try {
            Object idFieldValue = actualObj != null
                    ? ObjectResolverUtil.getFieldValue(container, actualObj, column.field)
                    : null;

            if (idFieldValue != null) {
                Long idObjectId = ObjectResolverUtil.getObjectId(container, idFieldValue);
                if (idObjectId != null) {
                    return extractMIdFromIDObject(container, idObjectId, column.field.getTypeClass());
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting ID field: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract the mID value from an ID-type object.
     */
    private Object extractMIdFromIDObject(com.db4o.ext.ExtObjectContainer container, Long idObjectId, DOClass idClass) {
        try {
            if (idClass != null) {
                DOClass[] idClasses = new DOClass[] { idClass };
                Map<String, ObjectResolverUtil.PrimitiveFieldValue> idFieldValues = ObjectResolverUtil
                        .extractPrimitiveFieldValues(container, idObjectId, idClasses);

                ObjectResolverUtil.PrimitiveFieldValue mIdValue = idFieldValues.get("mID");
                if (mIdValue != null) {
                    return mIdValue.value;
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting mID from ID object: " + e.getMessage());
        }
        return null;
    }

    /**
     * Format a collection for export as a comma-delimited string.
     * For collections of ID objects, exports the mID values.
     * For collections of primitives, exports the values directly.
     */
    private String formatCollectionForExport(com.db4o.ext.ExtObjectContainer container, Object collectionObj,
            DOField field) {
        try {
            StringBuilder result = new StringBuilder();
            int count = 0;

            // Convert collection to iterable
            Iterable<?> iterable = null;
            if (collectionObj instanceof Iterable) {
                iterable = (Iterable<?>) collectionObj;
            } else if (collectionObj instanceof Object[]) {
                iterable = Arrays.asList((Object[]) collectionObj);
            }

            if (iterable == null) {
                return null;
            }

            // Check if this is a collection of ID objects
            String contentTypeName = field.getContentTypeName();
            boolean isIDCollection = contentTypeName != null
                    && (contentTypeName.startsWith("gen.util.ID") || contentTypeName.contains(".ID"));

            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }

                if (count > 0) {
                    result.append(", ");
                }

                if (isIDCollection) {
                    // Extract mID from ID object
                    Long itemId = ObjectResolverUtil.getObjectId(container, item);
                    if (itemId != null) {
                        ObjectResolverUtil.activateObject(container, item, itemId);

                        // Try to find the mID field
                        DOClass itemClass = field.getContentTypeClass();
                        if (itemClass != null) {
                            DOField mIdField = findMIdField(itemClass);
                            if (mIdField != null) {
                                Object mIdValue = ObjectResolverUtil.getFieldValue(container, item, mIdField);
                                if (mIdValue != null && !"-1".equals(mIdValue.toString())) {
                                    result.append(mIdValue.toString());
                                    count++;
                                }
                            }
                        }
                    }
                } else {
                    // Non-ID collection - check if items are database objects (GenericObject or regular objects)
                    Long itemId = ObjectResolverUtil.getObjectId(container, item);
                    if (itemId != null) {
                        // This is a database object (including GenericObject) - extract its mID
                        ObjectResolverUtil.activateObject(container, item, itemId);
                        
                        // Try to find mID field in the content type class
                        DOClass itemClass = field.getContentTypeClass();
                        if (itemClass != null) {
                            DOField mIdField = findMIdField(itemClass);
                            if (mIdField != null) {
                                Object mIdValue = ObjectResolverUtil.getFieldValue(container, item, mIdField);
                                if (mIdValue != null && !"-1".equals(mIdValue.toString())) {
                                    result.append(mIdValue.toString());
                                    count++;
                                }
                            }
                        }
                    } else {
                        // Truly primitive value - use toString()
                        result.append(item.toString());
                        count++;
                    }
                }
            }

            return result.toString();
        } catch (Exception e) {
            System.err.println("Error formatting collection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Build the list of columns to export, including flattened fields from
     * single-reference ID objects.
     */
    private List<ExportColumn> buildExportColumns(DODatabaseClass dbClass) {
        List<DOField> allFields = getSortedFields(dbClass);
        List<ExportColumn> columns = new ArrayList<>();

        for (DOField field : allFields) {
            if (isIDTypeField(field)) {
                // Check if this ID type should be flattened
                DODatabaseClass idTypeClass = findDatabaseClassByName(field.getTypeName());

                if (idTypeClass != null && idTypeClass.getReferenceCount() == 1) {
                    // Find the actual target class (e.g., IDCaserne -> Caserne)
                    DODatabaseClass targetClass = findTargetClassForIDType(idTypeClass);

                    // Only flatten if:
                    // 1. We found the target class
                    // 2. The target class is NOT exported as its own sheet in the schema
                    if (targetClass != null && !isClassExportedInSchema(targetClass)) {
                        // Flatten: Add all fields from the TARGET object (not the ID object)
                        String targetClassExportName = getExportNameForClass(targetClass);
                        List<DOField> targetFields = getAllFields(targetClass);

                        for (DOField targetField : targetFields) {
                            // Skip ID-type fields within the flattened object to avoid infinite recursion
                            if (!isIDTypeField(targetField)) {
                                String cleanedFieldName = cleanFieldName(targetField.getName());
                                String prefixedName = targetClassExportName + "." + cleanedFieldName;
                                // Store the target field and target class, not the ID class
                                columns.add(new ExportColumn(targetField, prefixedName, field, targetClass));
                            }
                        }
                    } else {
                        // Target class will be exported separately OR couldn't find it
                        // Keep as ID reference: just show the mID value
                        String cleanedFieldName = cleanFieldName(field.getName());
                        columns.add(new ExportColumn(field, cleanedFieldName));
                    }
                } else {
                    // Keep as ID reference: just show the mID value
                    String cleanedFieldName = cleanFieldName(field.getName());
                    columns.add(new ExportColumn(field, cleanedFieldName));
                }
            } else {
                // Regular non-ID field
                String cleanedFieldName = cleanFieldName(field.getName());
                columns.add(new ExportColumn(field, cleanedFieldName));
            }
        }

        return columns;
    }

    /**
     * Get the export name for a database class (prefers schema class export name if
     * available).
     */
    private String getExportNameForClass(DODatabaseClass dbClass) {
        // Try to find the corresponding schema class
        if (engine.getSchema() != null && engine.getSchema().getClasses() != null) {
            for (dataobjects.api.models.schema.DOSchemaClass schemaClass : engine.getSchema().getClasses()) {
                if (schemaClass.getDatabaseClass() == dbClass) {
                    String exportName = schemaClass.getExportName();
                    if (exportName != null && !exportName.isEmpty()) {
                        return exportName;
                    }
                    return schemaClass.getShortName();
                }
            }
        }
        // Fallback to database class short name
        return dbClass.getShortName();
    }

    /**
     * Get all fields sorted with priority fields first.
     */
    private List<DOField> getSortedFields(DODatabaseClass dbClass) {
        List<DOField> allFields = getAllFields(dbClass);

        // Separate priority fields from other fields
        List<DOField> priorityFields = new ArrayList<>();
        List<DOField> otherFields = new ArrayList<>();

        for (DOField field : allFields) {
            boolean isPriority = false;
            for (String priorityName : PRIORITY_FIELDS) {
                if (priorityName.equals(field.getName())) {
                    isPriority = true;
                    break;
                }
            }

            if (isPriority) {
                priorityFields.add(field);
            } else {
                otherFields.add(field);
            }
        }

        // Sort priority fields in the order defined in PRIORITY_FIELDS
        priorityFields.sort((f1, f2) -> {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < PRIORITY_FIELDS.length; i++) {
                if (PRIORITY_FIELDS[i].equals(f1.getName())) {
                    index1 = i;
                }
                if (PRIORITY_FIELDS[i].equals(f2.getName())) {
                    index2 = i;
                }
            }
            return Integer.compare(index1, index2);
        });

        // Combine: priority fields first, then others
        List<DOField> sortedFields = new ArrayList<>();
        sortedFields.addAll(priorityFields);
        sortedFields.addAll(otherFields);

        return sortedFields;
    }

    private List<DOField> getAllFields(DODatabaseClass dbClass) {
        List<DOField> allFields = new ArrayList<>();

        // Traverse the class hierarchy manually using getParentClass()
        DODatabaseClass currentClass = dbClass;
        while (currentClass != null) {
            DOField[] fields = currentClass.getFields();
            if (fields != null) {
                allFields.addAll(Arrays.asList(fields));
            }

            // Move to parent class
            currentClass = currentClass.getParentClass();
        }

        return allFields;
    }

    /**
     * Clean field name by removing leading 'm' if present.
     */
    private String cleanFieldName(String fieldName) {
        if (fieldName != null && fieldName.length() > 1 && fieldName.startsWith("m")
                && Character.isUpperCase(fieldName.charAt(1))) {
            // Remove leading 'm' from mXxx pattern
            return fieldName.substring(1);
        }
        return fieldName;
    }

    private boolean isIDTypeField(DOField field) {
        String typeName = field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Find a database class by its absolute name.
     */
    private DODatabaseClass findDatabaseClassByName(String className) {
        if (className == null || engine.getDatabase() == null) {
            return null;
        }

        for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
            if (className.equals(dbClass.getAbsoluteName())) {
                return dbClass;
            }
        }
        return null;
    }

    /**
     * Finds the target class for an ID-type class.
     * E.g., IDCaserne -> Caserne, IDEmploye -> Employe
     */
    private DODatabaseClass findTargetClassForIDType(DODatabaseClass idClass) {
        if (idClass == null) {
            return null;
        }

        String idClassName = idClass.getShortName();
        if (idClassName == null || !idClassName.startsWith("ID")) {
            return null;
        }

        // Remove "ID" prefix to get the target class name
        String targetClassName = idClassName.substring(2);

        // Search for the target class in the database
        DODatabase database = engine.getDatabase();
        for (DODatabaseClass dbClass : database.getClasses()) {
            if (targetClassName.equals(dbClass.getShortName())) {
                return dbClass;
            }
        }

        return null;
    }

    /**
     * Checks if a database class is exported in the schema (has a corresponding
     * schema class).
     * If a class is in the schema, it will be exported as its own sheet.
     */
    private boolean isClassExportedInSchema(DODatabaseClass dbClass) {
        if (dbClass == null || engine.getSchema() == null) {
            return false;
        }

        // Check if any schema class references this database class
        for (DOSchemaModule module : engine.getSchema().getModules()) {
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    if (schemaClass.getDatabaseClass() == dbClass) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Finds the mID field in an ID-type class.
     */
    private DOField findMIdField(DOClass idClass) {
        if (idClass == null) {
            return null;
        }

        DOField[] fields = idClass.getFields();
        if (fields == null) {
            return null;
        }

        // Look for common ID field names
        for (DOField field : fields) {
            String fieldName = field.getName();
            if ("mID".equals(fieldName) || "mId".equals(fieldName) || "id".equals(fieldName)) {
                return field;
            }
        }

        return null;
    }
}
