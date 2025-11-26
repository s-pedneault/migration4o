package dataobjects.impl.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.DOField;
import dataobjects.api.migration.generic.ExportColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for building the list of columns to export.
 * Handles field sorting, flattening logic, and column name generation.
 */
public class ColumnBuilder {

    private final DOEngine engine;

    // Fields to export first (in this order) if they exist
    private static final String[] PRIORITY_FIELDS = {
            "mID",
            "mIDSSI"
    };

    public ColumnBuilder(DOEngine engine) {
        this.engine = engine;
    }

    /**
     * Build the complete list of export columns for a database class.
     * This includes regular fields, ID fields, and flattened fields.
     */
    public List<ExportColumn> buildColumns(DODatabaseClass dbClass) {
        List<DOField> sortedFields = getSortedFields(dbClass);
        List<ExportColumn> columns = new ArrayList<>();

        for (DOField field : sortedFields) {
            if (isIDTypeField(field)) {
                addIDFieldColumns(columns, field, dbClass);
            } else {
                addRegularFieldColumn(columns, field);
            }
        }

        return columns;
    }

    /**
     * Add columns for an ID-type field (may create multiple flattened columns).
     */
    private void addIDFieldColumns(List<ExportColumn> columns, DOField field, DODatabaseClass dbClass) {
        // Check if this ID type should be flattened
        DODatabaseClass idTypeClass = findDatabaseClassByName(field.getTypeName());

        if (shouldFlattenIDField(idTypeClass)) {
            addFlattenedColumns(columns, field, idTypeClass);
        } else {
            addIDReferenceColumn(columns, field);
        }
    }

    /**
     * Add a regular non-ID field column.
     */
    private void addRegularFieldColumn(List<ExportColumn> columns, DOField field) {
        String cleanedFieldName = cleanFieldName(field.getName());
        columns.add(new ExportColumn(field, cleanedFieldName));
    }

    /**
     * Add an ID field as a simple reference (mID value).
     */
    private void addIDReferenceColumn(List<ExportColumn> columns, DOField field) {
        String cleanedFieldName = cleanFieldName(field.getName());
        columns.add(new ExportColumn(field, cleanedFieldName));
    }

    /**
     * Add flattened columns for fields from the target object.
     */
    private void addFlattenedColumns(List<ExportColumn> columns, DOField parentField, DODatabaseClass idTypeClass) {
        DODatabaseClass targetClass = findTargetClassForIDType(idTypeClass);

        if (targetClass == null) {
            // Couldn't find target, just add as ID reference
            addIDReferenceColumn(columns, parentField);
            return;
        }

        String targetClassExportName = getExportNameForClass(targetClass);
        List<DOField> targetFields = getAllFields(targetClass);

        for (DOField targetField : targetFields) {
            // Skip ID-type fields within the flattened object to avoid infinite recursion
            if (!isIDTypeField(targetField)) {
                String cleanedFieldName = cleanFieldName(targetField.getName());
                String prefixedName = targetClassExportName + "." + cleanedFieldName;
                columns.add(new ExportColumn(targetField, prefixedName, parentField, targetClass));
            }
        }
    }

    /**
     * Check if an ID field should be flattened.
     */
    private boolean shouldFlattenIDField(DODatabaseClass idTypeClass) {
        if (idTypeClass == null || idTypeClass.getReferenceCount() != 1) {
            return false;
        }

        DODatabaseClass targetClass = findTargetClassForIDType(idTypeClass);
        return targetClass != null && !isClassExportedInSchema(targetClass);
    }

    /**
     * Get all fields sorted with priority fields first.
     */
    private List<DOField> getSortedFields(DODatabaseClass dbClass) {
        List<DOField> allFields = getAllFields(dbClass);

        List<DOField> priorityFields = new ArrayList<>();
        List<DOField> otherFields = new ArrayList<>();

        for (DOField field : allFields) {
            if (isPriorityField(field.getName())) {
                priorityFields.add(field);
            } else {
                otherFields.add(field);
            }
        }

        // Combine priority fields first, then others
        List<DOField> sortedFields = new ArrayList<>();

        // Add priority fields in the order defined in PRIORITY_FIELDS
        for (String priorityName : PRIORITY_FIELDS) {
            for (DOField field : priorityFields) {
                if (priorityName.equals(field.getName())) {
                    sortedFields.add(field);
                    break;
                }
            }
        }

        sortedFields.addAll(otherFields);
        return sortedFields;
    }

    /**
     * Check if a field name is a priority field.
     */
    private boolean isPriorityField(String fieldName) {
        for (String priorityName : PRIORITY_FIELDS) {
            if (priorityName.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all fields for a database class (including inherited fields).
     */
    private List<DOField> getAllFields(DODatabaseClass dbClass) {
        List<DOField> allFields = new ArrayList<>();

        if (dbClass.getFields() != null) {
            for (DOField field : dbClass.getFields()) {
                allFields.add(field);
            }
        }

        // Add inherited fields
        if (dbClass.getParentClass() != null) {
            allFields.addAll(getAllFields(dbClass.getParentClass()));
        }

        return allFields;
    }

    /**
     * Check if a field is an ID-type field.
     */
    private boolean isIDTypeField(DOField field) {
        String typeName = field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    /**
     * Clean up field names for export (remove prefixes, etc.).
     */
    private String cleanFieldName(String fieldName) {
        if (fieldName == null) {
            return "unnamed";
        }

        // Remove 'm' prefix if present
        if (fieldName.startsWith("m") && fieldName.length() > 1 && Character.isUpperCase(fieldName.charAt(1))) {
            return fieldName.substring(1);
        }

        return fieldName;
    }

    /**
     * Get the export name for a database class.
     */
    private String getExportNameForClass(DODatabaseClass dbClass) {
        // Try to find the module containing this class
        if (engine.getSchema() != null && engine.getSchema().getModules() != null) {
            for (dataobjects.api.models.schema.DOSchemaModule module : engine.getSchema().getModules()) {
                if (module.getClasses() != null) {
                    for (dataobjects.api.models.schema.DOSchemaClass schemaClass : module.getClasses()) {
                        if (schemaClass.getDatabaseClass() == dbClass) {
                            return module.getName();
                        }
                    }
                }
            }
        }
        // Fallback to database class short name
        return dbClass.getShortName();
    }

    /**
     * Find a database class by its type name.
     */
    private DODatabaseClass findDatabaseClassByName(String typeName) {
        // This would search through all database classes
        // Placeholder implementation
        return null;
    }

    /**
     * Find the target class for an ID type (e.g., IDCaserne -> Caserne).
     */
    private DODatabaseClass findTargetClassForIDType(DODatabaseClass idTypeClass) {
        // This would implement the logic to find the target class
        // Placeholder implementation
        return null;
    }

    /**
     * Check if a class is exported as its own sheet/file in the schema.
     */
    private boolean isClassExportedInSchema(DODatabaseClass dbClass) {
        // This would check if the class appears in the schema
        // Placeholder implementation
        return true;
    }
}