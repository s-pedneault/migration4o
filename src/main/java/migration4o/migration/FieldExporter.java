package migration4o.migration;

import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.recipes.ArrayTraverser;
import migration4o.migration.recipes.FieldValueMapper;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.migration.recipes.IDReferenceDetector;
import migration4o.migration.recipes.IDReferenceExporter;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.recipes.RecipeCollectionItems;
import migration4o.util.ClassUtil;
import migration4o.util.DatabaseUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.ValueUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Handles field-level export operations. Responsible for exporting all fields
 * of an object, handling arrays, collections, and references.
 */
public class FieldExporter {
    private final ExportOperation operation;
    private final StructuredWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    private final ReferenceObjectExporter idEntiteResolver;

    // Cache for preloaded objects by class name - load once per target class,
    // reuse
    // across all exports
    private final java.util.Map<String, java.util.List<GenericObject>> preloadedObjectsByClass = new java.util.HashMap<>();

    public FieldExporter(ExportOperation operation, StructuredWriter xmlWriter, XSDBuilder xsdBuilder) {
        this.operation = operation;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.idEntiteResolver = new ReferenceObjectExporter(operation.databaseSchema);
    }

    /**
     * Counts how many fields would be exported from this GenericObject (dry run).
     * Goes through all the same skip logic as exportAllFields but doesn't write
     * anything.
     * 
     * @param container   DB4O container
     * @param obj         The GenericObject to analyze
     * @param parentClass Schema class definition
     * @param schema      Reference schema for skip condition checking
     * @return number of fields that will be exported
     */
    public int countFieldsToExport(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass, DOSchema schema) {
        int count = 0;
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the
            // immediate
            // class
            StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get schema field from current class AND
                    // ancestors
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, sourceFieldName, schema);

                    // Skip fields marked as not exported
                    if (schemaField != null && !schemaField.isExported) {
                        continue;
                    }

                    // Skip null fields if they meet skip conditions
                    if (fieldValue == null) {
                        if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema, operation.selectedSkipUserOptions)) {
                            continue;
                        }
                        // Null field that will be exported
                        count++;
                        continue;
                    }

                    // Check if this would be skipped based on value and schema
                    // settings
                    // For collections and arrays, they're always exported if
                    // not null (size
                    // attribute)
                    if (schemaField != null && schemaField.isCollection) {
                        count++;
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        count++;
                    } else if (fieldValue.getClass().isArray()) {
                        count++;
                    } else {
                        // Regular field - would it be exported?
                        // Check Class fields that might be skipped
                        boolean isClassField = schemaField != null && (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

                        if (isClassField) {
                            // Extract className for skip check
                            String className;
                            if (fieldValue instanceof Class<?>) {
                                className = ((Class<?>) fieldValue).getName();
                            } else {
                                String str = fieldValue.toString();
                                className = str.startsWith("class ") ? str.substring(6) : str;
                            }

                            if (!ValueUtil.shouldSkipField(className, schemaField, schema, operation.selectedSkipUserOptions)) {
                                count++;
                            }
                        } else {
                            // Regular object - check skip conditions
                            if (!ValueUtil.shouldSkipField(fieldValue, schemaField, schema, operation.selectedSkipUserOptions)) {
                                count++;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip fields that cause errors
                }
            }
        } catch (Exception e) {
            // Error accessing fields
        }
        return count;
    }

    /**
     * Exports all fields of a GenericObject.
     * 
     * @param container            DB4O container for object activation and ID
     *                             lookups
     * @param obj                  The GenericObject whose fields are being exported
     * @param parentClass          Schema class definition for the object being
     *                             exported (not the parent in object graph)
     * @param indentLevel          Current XML indentation level
     * @param destinationClassName Destination class name from schema (e.g.,
     *                             "Vehicule") - used for tracking field context
     * @param sourceClassName      Source class name from schema (e.g.,
     *                             "gest.vehicule.Vehicule") - used for tracking
     *                             field context
     * @param parentObjectId       DB4O object ID of the object being exported -
     *                             used for duplicate detection and tracking
     * @return the number of fields actually written to XML
     * @throws IOException if XML writing fails
     */
    public int exportAllFields(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
        int fieldsWritten = 0;
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the
            // immediate
            // class
            StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get destination field name from schema (search
                    // ancestors too)
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, sourceFieldName, operation.referenceSchema);
                    String fieldName = schemaField != null ? schemaField.destinationName : sourceFieldName;

                    // Skip fields marked as not exported
                    if (schemaField != null && !schemaField.isExported) {
                        continue;
                    }

                    // XSD: record field type
                    if (schemaField != null) {
                        xsdBuilder.addField(parentClass, schemaField);
                    }

                    if (fieldValue == null) {
                        // Skip this field if skip conditions are met
                        if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                            continue;
                        }
                        xmlWriter.elementWithoutContent(fieldName);
                        fieldsWritten++;
                        continue;
                    }

                    // Check schema flag first - DB4O collections may not be Java Collection instances
                    // CRITICAL: Always use schema-driven extraction for DB4O objects, even if they implement Collection interface, because calling .size() on GenericObject proxies may return incorrect values before proper activation and extraction
                    if (schemaField != null && schemaField.isCollection) {
                        // CRITICAL FIX: Activate collection fields directly to populate their contents
                        // This matches the working pattern from ClassObjectsDialog.getFieldValue
                        if (fieldValue instanceof Collection) {
                            try {
                                container.activate(fieldValue, 1);
                            } catch (Exception e) {
                                // Ignore activation errors
                            }
                        }
                        if (exportSchemaCollectionField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        if (exportCollectionField(container, (Collection<?>) fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof byte[]) {
                        // Special handling for byte arrays - export as Base64
                        // string
                        exportByteArrayField((byte[]) fieldValue, schemaField, indentLevel);
                        fieldsWritten++;
                    } else if (fieldValue.getClass().isArray()) {
                        if (exportArrayField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else {
                        exportRegularField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    }
                } catch (Exception e) {
                    // Skip fields that cause errors during export
                }
            }

            // VIRTUAL FIELDS: Export schema-defined virtual fields that don't
            // exist in
            // database
            // Virtual fields start with @ and use criteria-based queries to
            // find related
            // objects
            fieldsWritten += exportVirtualFields(container, obj, parentClass, indentLevel, destinationClassName, sourceClassName, parentObjectId);

        } catch (Exception e) {
            // Error accessing fields
        }
        return fieldsWritten;
    }

    /**
     * Unified method to export any collection-like field (Collection, array, or
     * schema collection). Handles all the common logic: skip conditions, size
     * attributes, ID reference detection, and item export.
     * 
     * @param container             DB4O container
     * @param items                 Iterable of items to export (extracted from
     *                              collection/array)
     * @param size                  Number of items
     * @param itemsValue            Original collection/array value for skip
     *                              condition checking
     * @param schemaField           Schema field definition
     * @param indentLevel           Current indentation level
     * @param parentClassName       Parent class name for tracking
     * @param parentSourceClassName Parent source class name for tracking
     * @param parentObjectId        Parent object ID for tracking
     * @return true if field was written, false if skipped
     * @throws IOException if XML writing fails
     */
    private boolean exportCollectionLikeField(ExtObjectContainer container, Iterable<?> items, int size, Object itemsValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        boolean includeSizeMetadata = xmlWriter.includeCollectionSizeMetadata();

        if (size == 0 || items == null) {
            // Check skip conditions
            if (ValueUtil.shouldSkipField(itemsValue, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                return false;
            }
            if (includeSizeMetadata) {
                xmlWriter.elementWithoutContent(fieldName, Map.of("size", "0"));
            } else {
                xmlWriter.elementWithoutContent(fieldName);
            }
            return true;
        } else {
            if (includeSizeMetadata) {
                xmlWriter.openStructure(fieldName, Map.of("size", size + ""));
            } else {
                xmlWriter.openStructure(fieldName);
            }

            // Check if we should export ID references instead of entities
            IDReferenceDetector.DetectionResult detection = IDReferenceDetector.detectIDReference(schemaField, operation.referenceSchema);

            for (Object item : items) {
                if (item != null) {
                    if (detection.shouldExportAsIDReferences) {
                        // Export as ID reference
                        IDReferenceExporter.exportAsIDReference(container, item, detection.idClass, xmlWriter, xsdBuilder, indentLevel + 1, operation);
                    } else {
                        // Export normally
                        exportFieldValue(container, item, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
                    }
                }
            }
            xmlWriter.closeStructure(fieldName);
            return true;
        }
    }

    /**
     * Exports a collection field that is marked as collection in the schema but may
     * be stored as a DB4O persistent object (like VectRechID). This method extracts
     * the collection items from the DB4O object structure.
     * 
     * @return true if field was written, false if skipped
     */
    private boolean exportSchemaCollectionField(ExtObjectContainer container, Object collectionObj, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Try to extract items from the collection object
        // VectRechID extends HVector which extends Vector, so look for Vector's
        // internal array
        // NOTE: Parent object was already activated at max depth, so collection
        // should
        // be activated too
        Collection<?> items = RecipeCollectionItems.getItems(container, collectionObj);
        int size = (items != null) ? items.size() : 0;

        return exportCollectionLikeField(container, items, size, items, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * @return true if field was written, false if skipped
     */
    private boolean exportCollectionField(ExtObjectContainer container, Collection<?> collection, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Check if empty using ValueUtil (handles null case)
        boolean isEmpty = ValueUtil.isEmpty(collection, schemaField, operation.referenceSchema);
        int size = isEmpty ? 0 : collection.size();

        return exportCollectionLikeField(container, collection, size, collection, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * @return true if field was written, false if skipped
     */
    private boolean exportArrayField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Check if empty using ValueUtil (handles null case)
        boolean isEmpty = ValueUtil.isEmpty(fieldValue, schemaField, operation.referenceSchema);
        int length = isEmpty ? 0 : ArrayTraverser.getLength(fieldValue);

        // Convert array to Iterable for unified processing
        Iterable<?> items = createArrayIterable(fieldValue, length);

        return exportCollectionLikeField(container, items, length, fieldValue, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * Creates an Iterable wrapper around an array for unified processing.
     */
    private Iterable<?> createArrayIterable(Object array, int length) {
        java.util.List<Object> list = new java.util.ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(ArrayTraverser.getItem(array, i));
        }
        return list;
    }

    private void exportByteArrayField(byte[] byteArray, DOSchemaField schemaField, int indentLevel) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";

        // Check skip conditions for empty byte arrays
        if (byteArray.length == 0) {
            if (ValueUtil.shouldSkipField(byteArray, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                return;
            }
            xmlWriter.elementWithoutContent(fieldName);
        } else {
            // Convert byte array to Base64 string
            String base64String = Base64.getEncoder().encodeToString(byteArray);
            base64String = ValueUtil.formatFieldValue(base64String, schemaField);
            xmlWriter.elementWithContent(fieldName, null, base64String, false);
        }
    }

    private void exportRegularField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";

        // Check if this is a Class-typed field based on schema (DB4O may wrap
        // Class in
        // GenericObject)
        boolean isClassField = schemaField != null && (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

        // Special handling for Class objects - always export as string
        if (isClassField) {
            String className;
            if (fieldValue instanceof Class<?>) {
                className = ((Class<?>) fieldValue).getName();
            } else {
                // DB4O wrapped it - extract via toString which gives "class
                // java.lang.String"
                String str = fieldValue.toString();
                if (str.startsWith("class ")) {
                    className = str.substring(6); // Remove "class " prefix
                } else {
                    className = str;
                }
            }

            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                return;
            }

            className = ValueUtil.formatFieldValue(className, schemaField);
            xmlWriter.elementWithContent(fieldName, null, className, false);
            return;
        }

        long refId = container.ext().getID(fieldValue);
        if (refId > 0) {
            // This is a persistent object reference

            // Check if field should be skipped (includes IDEntite with mID ==
            // -1)
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                return;
            }

            // Additional check for IDEntite objects - check if they'll be
            // filtered by
            // resolveAndExport
            String className = ClassUtil.getClassName(fieldValue);
            DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, operation.referenceSchema);
            if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
                // Check if this IDEntite will be skipped due to mID == -1
                if (schemaField != null && schemaField.skipWhen != null && !schemaField.skipWhen.isEmpty()) {
                    if (IDEntityHandler.shouldSkipMinusOne(container, fieldValue) && schemaField.skipWhen.contains("MINUS_ONE")) {
                        // This field will produce empty content, skip it
                        // entirely
                        return;
                    }
                }

                // For non-embedded IDEntite references, export as simple ID
                // value
                // instead of nested structure
                if (schemaField != null && !schemaField.embedContents) {
                    Long mID = IDEntityHandler.extractMID(container, fieldValue);
                    if (mID != null) {
                        String formattedId = ValueUtil.formatFieldValue(mID.toString(), schemaField);
                        xmlWriter.elementWithContent(fieldName, formattedId, false);
                        return;
                    } else {
                        // mID is null - skip this field to avoid empty tags
                        return;
                    }
                }
            }

            // Before writing field wrapper tags, check if the referenced object
            // has any
            // fields to export
            // (reuse className and fieldClass from above)

            // Count fields that will be exported from this object
            if (fieldValue instanceof GenericObject && fieldClass != null) {
                int fieldsToExport = countFieldsToExport(container, (GenericObject) fieldValue, fieldClass, operation.referenceSchema);

                // If no fields will be exported, skip this field wrapper
                // entirely
                if (fieldsToExport == 0) {
                    return;
                }
            }

            // Write element and export recursively
            // TODO verify if we write tag twice
            xmlWriter.openStructure(fieldName);
            exportFieldValue(container, fieldValue, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
            xmlWriter.closeStructure(fieldName);
        } else {
            // Primitive or non-persistent value - write inline
            String stringValue;

            // Special handling for byte arrays - convert to Base64
            if (fieldValue instanceof byte[]) {
                stringValue = Base64.getEncoder().encodeToString((byte[]) fieldValue);
            } else {
                stringValue = fieldValue.toString();
            }

            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema, operation.selectedSkipUserOptions)) {
                return;
            }

            // Apply value mapping if defined for this field
            stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);
            stringValue = ValueUtil.formatFieldValue(stringValue, schemaField);

            xmlWriter.elementWithContent(fieldName, null, stringValue, true);
        }
    }

    /**
     * Exports virtual fields defined in schema but not present in database. Virtual
     * fields use @ prefix in source and criteria-based queries. Example:
     * source="@mVectRapportOfficier" with criteria match="this.mID"
     * with="mIDIntervention" Queries database for objects where mIDIntervention
     * equals this object's mID.
     * 
     * @return number of virtual fields written
     */
    private int exportVirtualFields(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
        int fieldsWritten = 0;

        if (parentClass == null || parentClass.fields == null) {
            return 0;
        }

        // Iterate through schema fields to find virtual ones
        for (DOSchemaField schemaField : parentClass.fields) {
            if (schemaField == null || !schemaField.isExported) {
                continue;
            }

            // Check if this is a virtual field (source starts with @)
            if (!schemaField.isVirtualField()) {
                continue;
            }

            // Skip if no criteria defined
            if (schemaField.criterias == null || schemaField.criterias.isEmpty()) {
                continue;
            }

            try {
                // Execute query based on criteria
                Collection<?> queryResults = executeVirtualFieldQuery(container, obj, schemaField);
                int size = (queryResults != null) ? queryResults.size() : 0;

                // Export results using unified collection export
                boolean written = exportCollectionLikeField(container, queryResults, size, queryResults, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);

                if (written) {
                    fieldsWritten++;
                    // XSD: record field type
                    xsdBuilder.addField(parentClass, schemaField);
                }

            } catch (Exception e) {
                // Log error for debugging
                e.printStackTrace();
            }
        }

        return fieldsWritten;
    }

    /**
     * Executes a database query for a virtual field based on its criteria. Uses a
     * preloaded cache of all objects of the target class type, loading them once
     * per class and reusing across all exports for efficiency. Finds objects where
     * criterion.with field matches value from criterion.match field of current
     * object. Supports multiple criteria combined with AND/OR operators and
     * comparison operators.
     * 
     * @param container   DB4O container
     * @param obj         Current object being exported
     * @param schemaField Virtual field definition with criterias
     * @return Collection of matching objects
     */
    private Collection<?> executeVirtualFieldQuery(ExtObjectContainer container, GenericObject obj, DOSchemaField schemaField) {
        java.util.List<Object> results = new java.util.ArrayList<>();

        // Get the target class name from the virtual field
        // The field type should contain the class name of objects to query
        String targetClassName = schemaField.type;
        if (targetClassName == null || targetClassName.isEmpty()) {
            return results;
        }

        // Preload all objects of this class if not already cached
        if (!preloadedObjectsByClass.containsKey(targetClassName)) {
            java.util.List<GenericObject> allObjects = new java.util.ArrayList<>();

            // Use the proper DB4O API to get objects by class name
            StoredClass storedClass = container.ext().storedClass(targetClassName);
            if (storedClass != null) {
                long[] objectIds = storedClass.getIDs();

                // Load each object by ID and activate it to depth 2 for nested
                // field access
                for (long objectId : objectIds) {
                    try {
                        Object loadedObj = container.ext().getByID(objectId);
                        if (loadedObj instanceof GenericObject) {
                            // CRITICAL: Activate to depth 2 so nested fields
                            // like mIDIntervention.mID are
                            // accessible
                            container.activate(loadedObj, 2);
                            allObjects.add((GenericObject) loadedObj);
                        }
                    } catch (Exception e) {
                        // Skip objects that fail to load
                    }
                }
            }

            preloadedObjectsByClass.put(targetClassName, allObjects);
        }

        // Get the preloaded objects
        java.util.List<GenericObject> targetObjects = preloadedObjectsByClass.get(targetClassName);

        // Determine the logical operator for combining criteria (default: AND)
        String criteriasOperator = schemaField.criteriasOperator != null ? schemaField.criteriasOperator : "AND";
        boolean useAndLogic = criteriasOperator.equalsIgnoreCase("AND");

        // Extract match values from current object for all criteria
        java.util.List<CriterionMatchData> criteriaData = new java.util.ArrayList<>();
        for (migration4o.models.schema.DOFieldCriteria criterion : schemaField.criterias) {
            try {
                // Extract the match value from current object
                String matchFieldName = criterion.match;
                if (matchFieldName.startsWith("this.")) {
                    matchFieldName = matchFieldName.substring(5); // Remove
                                                                  // "this."
                                                                  // prefix
                }

                // Get the value from current object's field
                StoredClass storedClass = container.ext().storedClass(obj);
                if (storedClass == null) {
                    continue;
                }

                StoredField matchField = storedClass.storedField(matchFieldName, null);
                if (matchField == null) {
                    continue;
                }

                Object matchValue = matchField.get(obj);
                // For OR logic, skip null values; for AND logic, null means no
                // matches
                if (matchValue == null && useAndLogic) {
                    return results; // AND logic with null value = no results
                }
                if (matchValue == null) {
                    continue; // OR logic - skip this criterion
                }

                criteriaData.add(new CriterionMatchData(criterion, matchValue));
            } catch (Exception e) {
            }
        }

        if (criteriaData.isEmpty()) {
            return results;
        }

        // Search through preloaded objects in memory
        int objectIndex = 0;
        for (GenericObject targetObj : targetObjects) {
            objectIndex++;
            try {
                boolean matchesAllCriteria = true;
                boolean matchesAnyCriterion = false;

                // Check each criterion
                for (CriterionMatchData data : criteriaData) {
                    // Navigate through field path (supports dotted paths like
                    // "mIDIntervention.mID")
                    Object withValue = getFieldValueByPath(container, targetObj, data.criterion.with, false);

                    if (withValue == null) {
                        matchesAllCriteria = false;
                        if (useAndLogic) {
                            break; // AND logic - one failure means no match
                        }
                        continue;
                    }

                    // Compare values using the operator
                    boolean matches = compareCriterionValues(data.matchValue, withValue, data.criterion.operator);

                    if (matches) {
                        matchesAnyCriterion = true;
                    } else {
                        matchesAllCriteria = false;
                        if (useAndLogic) {
                            break; // AND logic - one failure means no match
                        }
                    }
                }

                // Add to results based on AND/OR logic
                if ((useAndLogic && matchesAllCriteria) || (!useAndLogic && matchesAnyCriterion)) {
                    results.add(targetObj);
                }
            } catch (Exception e) {
                // Skip objects that cause errors
            }
        }

        return results;
    }

    /**
     * Helper class to store criterion and its extracted match value.
     */
    private static class CriterionMatchData {
        final migration4o.models.schema.DOFieldCriteria criterion;
        final Object matchValue;

        CriterionMatchData(migration4o.models.schema.DOFieldCriteria criterion, Object matchValue) {
            this.criterion = criterion;
            this.matchValue = matchValue;
        }
    }

    /**
     * Compares two values based on the specified operator.
     * 
     * 
     * /** Gets a field value by navigating through a dotted path (e.g.,
     * "mIDIntervention.mID"). Supports nested object navigation for virtual field
     * queries.
     * 
     * @param container DB4O container
     * @param obj       Starting object
     * @param fieldPath Dotted field path (e.g., "mIDIntervention.mID")
     * @param debug     Whether to print debug information
     * @return The value at the end of the path, or null if any step fails
     */
    private Object getFieldValueByPath(ExtObjectContainer container, Object obj, String fieldPath, boolean debug) {
        if (obj == null || fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }

        // Split the path by dots
        String[] pathSegments = fieldPath.split("\\.");
        Object currentValue = obj;

        for (int i = 0; i < pathSegments.length; i++) {
            String fieldName = pathSegments[i];
            if (currentValue == null) {
                return null;
            }

            // Get the field from the current object
            if (currentValue instanceof GenericObject) {
                StoredClass storedClass = container.ext().storedClass(currentValue);
                if (storedClass == null) {
                    return null;
                }

                StoredField field = storedClass.storedField(fieldName, null);
                if (field == null) {
                    return null;
                }

                currentValue = field.get(currentValue);
            } else {
                // For non-GenericObject (shouldn't normally happen in DB4O)
                return null;
            }
        }
        return currentValue;
    }

    /**
     * Compares two values based on the specified operator. Supports: equals,
     * notEquals, greaterThan, lessThan, greaterOrEqual, lessOrEqual
     */
    private boolean compareCriterionValues(Object matchValue, Object withValue, String operator) {
        if (matchValue == null || withValue == null) {
            return operator.equals("equals") ? (matchValue == withValue) : (matchValue != withValue);
        }

        switch (operator.toLowerCase()) {
        case "equals":
            return matchValue.equals(withValue);

        case "notequals":
            return !matchValue.equals(withValue);

        case "greaterthan":
            return compareNumeric(matchValue, withValue) > 0;

        case "lessthan":
            return compareNumeric(matchValue, withValue) < 0;

        case "greaterorequal":
            return compareNumeric(matchValue, withValue) >= 0;

        case "lessorequal":
            return compareNumeric(matchValue, withValue) <= 0;

        default:
            // Unknown operator - default to equals
            return matchValue.equals(withValue);
        }
    }

    /**
     * Compares two values numerically. Handles Number types and attempts string
     * parsing.
     */
    @SuppressWarnings("unchecked")
    private int compareNumeric(Object val1, Object val2) {
        try {
            if (val1 instanceof Number && val2 instanceof Number) {
                double d1 = ((Number) val1).doubleValue();
                double d2 = ((Number) val2).doubleValue();
                return Double.compare(d1, d2);
            }

            if (val1 instanceof Comparable && val2 instanceof Comparable && val1.getClass().equals(val2.getClass())) {
                return ((Comparable<Object>) val1).compareTo(val2);
            }

            // Try parsing as numbers
            double d1 = Double.parseDouble(val1.toString());
            double d2 = Double.parseDouble(val2.toString());
            return Double.compare(d1, d2);
        } catch (Exception e) {
            // Fall back to string comparison
            return val1.toString().compareTo(val2.toString());
        }
    }

    /**
     * Exports a field value (handles both primitives and object references).
     */
    private void exportFieldValue(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        if (fieldValue == null) {
            return;
        }

        String className = ClassUtil.getClassName(fieldValue);

        // Check if this field is marked for embedding
        // Default behavior: embed regular objects (true), but respect explicit
        // embedContents=false for IDEntite references
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        String sourceFieldName = schemaField != null ? schemaField.source : null;

        // Check if this is an IDEntite reference (reference object pattern)
        DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, operation.referenceSchema);

        // Determine if object should be embedded:
        // - IDEntite objects: only embed if explicitly set to
        // embedContents=true
        // (default is false for references)
        // - Regular objects: always embed unless explicitly set to
        // embedContents=false
        // (default is true for value objects)
        boolean isEmbedded;
        if (schemaField != null) {
            if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
                // IDEntite: default to non-embedded (reference by ID), but
                // allow explicit
                // embedContents=true
                isEmbedded = schemaField.embedContents;
            } else {
                // Regular objects: default to embedded (inline content), unless
                // explicitly
                // embedContents=false
                // This prevents value objects like Adresse, Qte, etc. from
                // being deduplicated
                isEmbedded = true; // Always embed non-IDEntite objects
            }
        } else {
            // No schema field - default to embedded for safety
            isEmbedded = true;
        }

        if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
            // Track the referenced entity class if this is a non-embedded
            // reference
            if (operation.referencedClassTracker != null && schemaField != null && !schemaField.embedContents) {
                // Use the pointsTo field to find what entity class this
                // IDEntite references
                if (fieldClass.pointsTo != null && !fieldClass.pointsTo.isEmpty()) {
                    operation.referencedClassTracker.registerReferencedClass(fieldClass.pointsTo);
                }
            }

            // Pass through the embedded flag and field name for tracking
            idEntiteResolver.resolveAndExport(container, fieldValue, className, schemaField, (objectId, indent) -> operation.objectExporter.exportObjectRecursively(container, objectId, indent, isEmbedded, fieldName, parentClassName, sourceFieldName, parentSourceClassName, false, parentObjectId), indentLevel);
            return;
        }

        // For regular objects, check if they should be embedded or referenced
        long objectId = container.ext().getID(fieldValue);

        // Special handling for Class objects based on schema type
        boolean isClassField = schemaField != null && (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

        if (isClassField) {
            String classNameValue;
            if (fieldValue instanceof Class<?>) {
                classNameValue = ((Class<?>) fieldValue).getName();
            } else {
                // DB4O wrapped it - extract via toString
                String str = fieldValue.toString();
                if (str.startsWith("class ")) {
                    classNameValue = str.substring(6);
                } else {
                    classNameValue = str;
                }
            }
            classNameValue = ValueUtil.formatFieldValue(classNameValue, schemaField);
            xmlWriter.elementWithContent(fieldName, classNameValue, false);
            return;
        }

        if (objectId > 0) {
            operation.objectExporter.exportObjectRecursively(container, objectId, indentLevel, isEmbedded, fieldName, parentClassName, sourceFieldName, parentSourceClassName, false, parentObjectId);
        } else {
            // Primitive value - write inline
            String stringValue;
            if (fieldValue instanceof Class<?>) {
                stringValue = ((Class<?>) fieldValue).getName();
            } else if (fieldValue instanceof byte[]) {
                // Convert byte arrays to Base64
                stringValue = Base64.getEncoder().encodeToString((byte[]) fieldValue);
            } else {
                stringValue = fieldValue.toString();
            }
            stringValue = ValueUtil.formatFieldValue(stringValue, schemaField);
            xmlWriter.elementWithContent(fieldName, stringValue, true);
        }
    }
}
