package dataobjects.impl.migration.excel;

import dataobjects.impl.engine.DOEngine;
import dataobjects.impl.migration.excel.ExcelExportEngine;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaModule;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.database.DODatabaseObject;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOClass;
import dataobjects.util.ObjectResolverUtil;
import dataobjects.impl.migration.generic.ExportUtils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Implementation of Excel export engine.
 * Exports database contents organized by schema modules and classes.
 */
public class ExcelExportEngine {

    private static final String DEFAULT_OUTPUT_DIR = "output/excel";

    private DOEngine engine;

    /**
     * Represents a column in the Excel export, which can be either a direct field
     * or a flattened field from an ID-type object.
     */
    private static class ExportColumn {
        final DOField field;
        final String columnName;
        final boolean isFlattened;
        final DOField flattenedParentField; // The ID-type field that contains this flattened field
        final DODatabaseClass flattenedSourceClass; // The class this flattened field comes from

        // Constructor for regular (non-flattened) fields
        ExportColumn(DOField field, String columnName) {
            this.field = field;
            this.columnName = columnName;
            this.isFlattened = false;
            this.flattenedParentField = null;
            this.flattenedSourceClass = null;
        }

        // Constructor for flattened fields
        ExportColumn(DOField field, String columnName, DOField parentField, DODatabaseClass sourceClass) {
            this.field = field;
            this.columnName = columnName;
            this.isFlattened = true;
            this.flattenedParentField = parentField;
            this.flattenedSourceClass = sourceClass;
        }
    }

    public void exportToExcel(DOEngine engine) throws IOException {
        exportToExcel(engine, DEFAULT_OUTPUT_DIR);
    }

    public void exportToExcel(DOEngine engine, String outputDirectory) throws IOException {
        this.engine = engine;

        // Create output directory
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        DOSchema schema = engine.getSchema();
        if (schema == null || schema.getModules() == null) {
            throw new IOException("Schema or modules not available");
        }

        // Process each module
        for (DOSchemaModule module : schema.getModules()) {
            exportModule(module, outputDirectory);
        }
    }

    private void exportModule(DOSchemaModule module, String outputDirectory) throws IOException {
        String fileName = outputDirectory + "/" + ExportUtils.sanitizeModuleName(module.getName())
                + ".xlsx";
        System.out.println("Exporting module: " + module.getName() + " to " + fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Track which database classes we've already exported to avoid duplicates
            Set<DODatabaseClass> exportedDbClasses = new HashSet<>();
            Set<String> usedSheetNames = new HashSet<>();

            // Process each class in the module
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    DODatabaseClass dbClass = schemaClass.getDatabaseClass();

                    // Skip if we've already exported this database class
                    if (dbClass != null && !exportedDbClasses.contains(dbClass)) {
                        exportedDbClasses.add(dbClass);
                        exportClass(workbook, schemaClass, usedSheetNames);
                    } else if (dbClass != null) {
                        System.out.println("Skipping duplicate database class: " + schemaClass.getShortName());
                    }
                }
            }

            // Write the workbook to file
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
            }
        }

        System.out.println("Module exported successfully: " + module.getName());
    }

    private void exportClass(Workbook workbook, DOSchemaClass schemaClass, Set<String> usedSheetNames) {
        // Use export name if available, otherwise fall back to short name
        String exportName = schemaClass.getExportName();
        if (exportName == null || exportName.isEmpty()) {
            exportName = schemaClass.getShortName();
        }

        String sheetName = getUniqueSheetName(exportName, usedSheetNames);
        Sheet sheet = workbook.createSheet(sheetName);

        // Get the database class linked to this schema class
        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
        if (dbClass == null) {
            System.out.println("Warning: No database class linked for schema class: " + exportName);
            return;
        }

        // Get all resolved objects for this class
        // NOTE: Temporarily using getResolvedObjects() instead of getReachableObjects()
        // because reachability resolution may not be working correctly for module root
        // classes
        DODatabaseObject[] objects = dbClass.getResolvedObjects();
        if (objects == null || objects.length == 0) {
            System.out.println("No objects found for class: " + exportName);
            return;
        }

        // Build the column structure (including flattened ID fields)
        List<ExportColumn> columns = buildExportColumns(dbClass);

        // Create header row
        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);

        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int colNum = 0; colNum < columns.size(); colNum++) {
            Cell cell = headerRow.createCell(colNum);
            cell.setCellValue(columns.get(colNum).columnName);
            cell.setCellStyle(headerStyle);
        }

        // Export each object as a row
        for (DODatabaseObject obj : objects) {
            exportObjectRow(sheet, rowNum++, obj, columns);
        }

        // Auto-size columns
        for (int colNum = 0; colNum < columns.size(); colNum++) {
            sheet.autoSizeColumn(colNum);
        }

        System.out.println("Exported " + (rowNum - 1) + " objects for class: " + exportName);
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
            for (dataobjects.impl.models.schema.DOSchemaClass schemaClass : engine.getSchema().getClasses()) {
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

    private void exportObjectRow(Sheet sheet, int rowNum, DODatabaseObject obj, List<ExportColumn> columns) {
        Row row = sheet.createRow(rowNum);

        // Get the container and actual object
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        Object actualObj = container.getByID(obj.getObjectId());
        if (actualObj != null) {
            ObjectResolverUtil.activateObject(container, actualObj, obj.getObjectId());
        }

        // Also extract primitive field values for regular fields
        Map<String, ObjectResolverUtil.PrimitiveFieldValue> fieldValues = ObjectResolverUtil
                .extractPrimitiveFieldValues(container, obj.getObjectId(), obj.getAllClasses());

        for (int colNum = 0; colNum < columns.size(); colNum++) {
            ExportColumn column = columns.get(colNum);
            Cell cell = row.createCell(colNum);

            if (column.isFlattened) {
                // This is a flattened field from an ID-type object
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
                                Object flattenedValue = ObjectResolverUtil.getFieldValue(container, targetObject,
                                        column.field);
                                if (flattenedValue != null) {
                                    setCellValue(cell, flattenedValue, false); // Flattened fields are not ID fields
                                }
                            }
                        }
                    }
                }
            } else if (isIDTypeField(column.field)) {
                // Non-flattened ID field: extract mID value
                Object idFieldValue = actualObj != null
                        ? ObjectResolverUtil.getFieldValue(container, actualObj, column.field)
                        : null;

                if (idFieldValue != null) {
                    Long idObjectId = ObjectResolverUtil.getObjectId(container, idFieldValue);
                    if (idObjectId != null) {
                        Object mIdValue = extractMIdFromIDObject(container, idObjectId, column.field.getTypeClass());
                        if (mIdValue != null) {
                            setCellValue(cell, mIdValue, true); // true = is ID field
                        }
                    }
                }
            } else {
                // Regular field (primitive or collection)
                ObjectResolverUtil.PrimitiveFieldValue fieldValue = fieldValues.get(column.field.getName());
                if (fieldValue != null && fieldValue.value != null) {
                    setCellValue(cell, fieldValue.value, false); // false = not ID field
                } else {
                    // Field not in primitive values - might be a collection
                    Object fieldObj = actualObj != null
                            ? ObjectResolverUtil.getFieldValue(container, actualObj, column.field)
                            : null;

                    if (fieldObj != null) {
                        // Check if it's a collection and handle it
                        if (ObjectResolverUtil.isAnyCollectionType(fieldObj)) {
                            String collectionValue = formatCollectionForExport(container, fieldObj, column.field);
                            if (collectionValue != null && !collectionValue.isEmpty()) {
                                cell.setCellValue(collectionValue);
                            }
                        }
                    }
                }
            }
        }
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

    private void setCellValue(Cell cell, Object value, boolean isIDField) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Boolean) {
            // Handle real Boolean objects first - no conversion needed
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Number) {
            Number numValue = (Number) value;
            // Only skip -1 values for ID fields (indicates no reference)
            if (isIDField && numValue.intValue() == -1) {
                cell.setCellValue("");
            } else {
                cell.setCellValue(numValue.doubleValue());
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else if (value instanceof String) {
            String strValue = (String) value;
            // Convert French boolean strings to actual booleans (for legacy data if it
            // exists)
            if ("VRAI".equalsIgnoreCase(strValue) || "true".equalsIgnoreCase(strValue)) {
                cell.setCellValue(true);
            } else if ("FAUX".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                cell.setCellValue(false);
            } else {
                cell.setCellValue(strValue);
            }
        } else {
            // Fallback for any other type
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Format a collection for Excel export as a comma-delimited string.
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
                    // Primitive or simple object - use toString()
                    result.append(item.toString());
                    count++;
                }
            }

            return result.toString();
        } catch (Exception e) {
            System.err.println("Error formatting collection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get all fields sorted with priority fields first, then non-collection fields,
     * then collection fields.
     */
    private List<DOField> getSortedFields(DODatabaseClass dbClass) {
        List<DOField> allFields = getAllFields(dbClass);
        return ExportUtils.sortFieldsForExport(allFields);
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

    /**
     * Remove accents from text by normalizing and removing diacritical marks.
     */
    private String sanitizeSheetName(String name) {
        // First remove accents
        String withoutAccents = ExportUtils.removeAccents(name);
        // Excel sheet names have restrictions: no \/:*?[]
        String sanitized = withoutAccents.replaceAll("[\\\\/:*?\\[\\]]", "_");
        // Max length is 31 characters
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }

    /**
     * Get a unique sheet name by appending a counter if needed.
     * Excel doesn't allow duplicate sheet names in the same workbook.
     */
    private String getUniqueSheetName(String baseName, Set<String> usedNames) {
        String sanitized = sanitizeSheetName(baseName);

        // If the name is unique, use it as-is
        if (!usedNames.contains(sanitized)) {
            usedNames.add(sanitized);
            return sanitized;
        }

        // Otherwise, append a counter
        int counter = 2;
        String uniqueName;
        while (true) {
            // Make sure we stay within 31 character limit
            String suffix = "_" + counter;
            int maxBaseLength = 31 - suffix.length();
            String truncatedBase = sanitized.length() > maxBaseLength
                    ? sanitized.substring(0, maxBaseLength)
                    : sanitized;
            uniqueName = truncatedBase + suffix;

            if (!usedNames.contains(uniqueName)) {
                usedNames.add(uniqueName);
                return uniqueName;
            }
            counter++;
        }
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
