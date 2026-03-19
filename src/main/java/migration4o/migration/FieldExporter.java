package migration4o.migration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.recipes.ArrayTraverser;
import migration4o.migration.recipes.FieldValueMapper;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.migration.recipes.IDReferenceDetector;
import migration4o.migration.recipes.IDReferenceExporter;
import migration4o.migration.recipes.VirtualFieldQueryEngine;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.recipes.RecipeCollectionItems;
import migration4o.util.ClassUtil;
import migration4o.util.CollectionTypeUtil;
import migration4o.util.DatabaseUtil;
import migration4o.util.ReferenceUtil;
import migration4o.util.ValueUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Handles field-level export operations. Responsible for exporting all fields of an object, handling arrays, collections, and references.
 */
public class FieldExporter {
    private final ExportRequest operation;
    private final StructuredWriter xmlWriter;
    private final FormatHandler handlerRef;
    private final ExportCurrentState ctxRef;
    private final VirtualFieldQueryEngine virtualFieldQuery = new VirtualFieldQueryEngine();

    /** Creates a FieldExporter driven by FormatHandler hooks. */
    public FieldExporter(ExportCurrentState ctx, FormatHandler handler, ObjectExporter objectExporter) {
        this.operation = ctx.request;
        this.xmlWriter = handler.writer;
        this.handlerRef = handler;
        this.ctxRef = ctx;
    }

    /**
     * Counts how many fields would be exported from this GenericObject (dry run). Goes through all the same skip logic as exportAllFields but doesn't write anything.
     * 
     * @param container DB4O container
     * @param obj The GenericObject to analyze
     * @param parentClass Schema class definition
     * @param schema Reference schema for skip condition checking
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
            // Sort fields by schema destination name for deterministic output
            fields = sortFieldsByDestinationName(fields, parentClass, schema);
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get schema field from current class AND
                    // ancestors
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, sourceFieldName, schema);

                    // Skip fields without schema mapping (unmapped fields are
                    // not exported)
                    if (schemaField == null) {
                        continue;
                    }

                    // Skip fields marked as not exported
                    if (schemaField != null && !schemaField.attributes.isExported) {
                        continue;
                    }

                    // Skip null fields if they meet skip conditions
                    if (fieldValue == null) {
                        if (shouldSkipField(fieldValue, schemaField, schema)) {
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
                    if (schemaField != null && schemaField.attributes.isCollection) {
                        count++;
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        count++;
                    } else if (fieldValue.getClass().isArray()) {
                        count++;
                    } else {
                        // Regular field - would it be exported?
                        // Check Class fields that might be skipped
                        boolean isClassField = schemaField != null && (schemaField.attributes.type.equals("java.lang.Class") || schemaField.attributes.type.equals("Class"));

                        if (isClassField) {
                            // Extract className for skip check
                            String className;
                            if (fieldValue instanceof Class<?>) {
                                className = ((Class<?>) fieldValue).getName();
                            } else {
                                String str = fieldValue.toString();
                                className = str.startsWith("class ") ? str.substring(6) : str;
                            }

                            if (!shouldSkipField(className, schemaField, schema)) {
                                count++;
                            }
                        } else {
                            // Regular object - check skip conditions
                            if (!shouldSkipField(fieldValue, schemaField, schema)) {
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
     * @param container DB4O container for object activation and ID lookups
     * @param obj The GenericObject whose fields are being exported
     * @param parentClass Schema class definition for the object being exported (not the parent in object graph)
     * @param indentLevel Current XML indentation level
     * @param destinationClassName Destination class name from schema (e.g., "Vehicule") - used for tracking field context
     * @param sourceClassName Source class name from schema (e.g., "gest.vehicule.Vehicule") - used for tracking field context
     * @param parentObjectId DB4O object ID of the object being exported - used for duplicate detection and tracking
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
            // Sort fields by schema destination name for deterministic,
            // alphabetical output (enables xs:sequence)
            fields = sortFieldsByDestinationName(fields, parentClass, operation.referenceSchema);
            for (StoredField field : fields) {
                Object fieldValue = null;
                try {
                    fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get destination field name from schema (search
                    // ancestors too)
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, sourceFieldName, operation.referenceSchema);

                    // Skip fields without schema mapping (unmapped fields are
                    // not exported)
                    if (schemaField == null) {
                        continue;
                    }

                    String fieldName = schemaField.attributes.destinationName;

                    // Skip fields marked as not exported
                    if (schemaField != null && !schemaField.attributes.isExported) {
                        recordRelationshipSkippedIfPersistent(parentObjectId, sourceClassName, sourceFieldName, fieldValue, "field disabled in reference schema (isExported=false)");
                        // For disabled collection fields, still mark individual
                        // items as reached
                        if (fieldValue != null) {
                            markDisabledFieldDescendantsReached(container, fieldValue, schemaField);
                        }
                        continue;
                    }

                    // Skip fields whose type resolves to a non-exported class
                    // (migrate=false). This keeps the export consistent with
                    // the XSD which also skips these fields (spec 3.8).
                    // No exception for primitive-type aliases like UUID — if a
                    // class is explicitly marked migrate=false, the field is
                    // skipped.
                    if (schemaField != null && schemaField.attributes.type != null && !schemaField.attributes.type.isEmpty()) {
                        DOSchemaClass fieldTypeClass = operation.referenceSchema.findClassByName(schemaField.attributes.type);
                        if (fieldTypeClass != null && !fieldTypeClass.attributes.migrate) {
                            System.err.println("[WARN] Export skipping field '" + schemaField.attributes.destinationName + "' — type '" + schemaField.attributes.type + "' is not exported (migrate=false).");
                            continue;
                        }
                    }

                    if (fieldValue == null) {
                        // Skip this field if skip conditions are met
                        if (shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                            continue;
                        }
                        xmlWriter.elementWithoutContent(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema));
                        fieldsWritten++;
                        continue;
                    }

                    // Check schema flag first - DB4O collections may not be
                    // Java Collection instances
                    // CRITICAL: Always use schema-driven extraction for DB4O
                    // objects, even if they implement Collection interface,
                    // because calling .size() on GenericObject proxies may
                    // return incorrect values before proper activation and
                    // extraction
                    if (schemaField != null && schemaField.attributes.isCollection) {
                        markCollectionWrapperReached(fieldValue, parentObjectId, sourceClassName, sourceFieldName);
                        // CRITICAL FIX: Activate collection fields directly to
                        // populate their contents
                        // This matches the working pattern from
                        // ClassObjectsDialog.getFieldValue
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
                        markCollectionWrapperReached(fieldValue, parentObjectId, sourceClassName, sourceFieldName);
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
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof java.util.Map) {
                        // Map type (Hashtable, HashMap, etc.) — export like a
                        // collection of entries
                        if (exportMapField(container, (java.util.Map<?, ?>) fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof GenericObject && schemaField != null && CollectionTypeUtil.isMapByAncestry(schemaField.attributes.type, new DOSchema[] { operation.referenceSchema, operation.databaseSchema })) {
                        // Map type wrapped in GenericObject (DB4O stored
                        // Hashtable)
                        if (exportGenericMapField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof GenericObject && schemaField != null && CollectionTypeUtil.isCollectionByAncestry(schemaField.attributes.type, new DOSchema[] { operation.referenceSchema, operation.databaseSchema })) {
                        // Safety net: field type extends a collection base
                        // class (e.g. VectChampPerso extends VectRechID extends
                        // HVector extends Vector)
                        // but is not flagged collection=true in schema and
                        // arrived as GenericObject
                        markCollectionWrapperReached(fieldValue, parentObjectId, sourceClassName, sourceFieldName);
                        if (exportSchemaCollectionField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else {
                        exportRegularField(container, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    }
                } catch (Exception e) {
                    // Field export failed — still mark the field value as
                    // reached if it's a persistent object
                    if (fieldValue != null && ctxRef.statistics != null) {
                        try {
                            long childId = operation.container.ext().getID(fieldValue);
                            if (childId > 0) {
                                ctxRef.statistics.recordReachedOnly(ClassUtil.getClassName(fieldValue), childId, operation.referenceSchema);
                            }
                        } catch (Exception ignored) {
                            // Best-effort reach recording
                        }
                    }
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
     * Unified method to export any collection-like field (Collection, array, or schema collection). Handles all the common logic: skip conditions, size attributes, ID reference detection, and item export.
     * 
     * @param container DB4O container
     * @param items Iterable of items to export (extracted from collection/array)
     * @param size Number of items
     * @param itemsValue Original collection/array value for skip condition checking
     * @param schemaField Schema field definition
     * @param indentLevel Current indentation level
     * @param parentClassName Parent class name for tracking
     * @param parentSourceClassName Parent source class name for tracking
     * @param parentObjectId Parent object ID for tracking
     * @return true if field was written, false if skipped
     * @throws IOException if XML writing fails
     */
    private boolean exportCollectionLikeField(ExtObjectContainer container, Iterable<?> items, int size, Object itemsValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.attributes.destinationName;
        boolean includeSizeMetadata = xmlWriter.includeCollectionSizeMetadata();

        if (size == 0 || items == null) {
            // Check skip conditions
            if (shouldSkipField(itemsValue, schemaField, operation.referenceSchema)) {
                return false;
            }
            if (includeSizeMetadata) {
                xmlWriter.elementWithoutContent(fieldName, withSkippedBecauseAttribute(Map.of("size", "0"), itemsValue, schemaField, operation.referenceSchema));
            } else {
                xmlWriter.elementWithoutContent(fieldName, skippedBecauseAttributes(itemsValue, schemaField, operation.referenceSchema));
            }
            return true;
        } else {
            if (includeSizeMetadata) {
                xmlWriter.openStructure(fieldName, withSkippedBecauseAttribute(Map.of("size", size + ""), itemsValue, schemaField, operation.referenceSchema));
            } else {
                xmlWriter.openStructure(fieldName, skippedBecauseAttributes(itemsValue, schemaField, operation.referenceSchema));
            }

            // Check if we should export ID references instead of entities
            IDReferenceDetector.DetectionResult detection = IDReferenceDetector.detectIDReference(schemaField, operation.referenceSchema);

            for (Object item : items) {
                if (item != null) {
                    try {
                        if (detection.shouldExportAsIDReferences) {
                            // Export as ID reference
                            IDReferenceExporter.exportAsIDReference(container, item, detection.idClass, xmlWriter, indentLevel + 1, ctxRef);
                        } else {
                            // Export normally
                            exportFieldValue(container, item, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
                        }
                    } catch (Exception e) {
                        // Item export failed — still mark as reached if
                        // persistent
                        if (ctxRef.statistics != null) {
                            try {
                                long itemId = container.ext().getID(item);
                                if (itemId > 0) {
                                    ctxRef.statistics.recordReachedOnly(ClassUtil.getClassName(item), itemId, operation.referenceSchema);
                                }
                            } catch (Exception ignored) {
                                // Best-effort reach recording
                            }
                        }
                    }
                }
            }
            xmlWriter.closeStructure(fieldName);
            return true;
        }
    }

    /**
     * Exports a collection field that is marked as collection in the schema but may be stored as a DB4O persistent object (like VectRechID). This method extracts the collection items from the DB4O object structure.
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
     * Exports a Java Map field (Hashtable, HashMap, etc.) like a collection. Each map entry is written as an &lt;entry&gt; element with key and value children.
     *
     * @return true if field was written, false if skipped
     */
    private boolean exportMapField(ExtObjectContainer container, java.util.Map<?, ?> map, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.attributes.destinationName;
        int size = map.size();
        boolean includeSizeMetadata = xmlWriter.includeCollectionSizeMetadata();

        if (size == 0) {
            if (shouldSkipField(map, schemaField, operation.referenceSchema)) {
                return false;
            }
            if (includeSizeMetadata) {
                xmlWriter.elementWithoutContent(fieldName, withSkippedBecauseAttribute(Map.of("size", "0"), map, schemaField, operation.referenceSchema));
            } else {
                xmlWriter.elementWithoutContent(fieldName, skippedBecauseAttributes(map, schemaField, operation.referenceSchema));
            }
            return true;
        }

        if (includeSizeMetadata) {
            xmlWriter.openStructure(fieldName, withSkippedBecauseAttribute(Map.of("size", size + ""), map, schemaField, operation.referenceSchema));
        } else {
            xmlWriter.openStructure(fieldName, skippedBecauseAttributes(map, schemaField, operation.referenceSchema));
        }

        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            xmlWriter.openStructure("entry", null);
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null) {
                exportFieldValue(container, key, schemaField, indentLevel + 2, parentClassName, parentSourceClassName, parentObjectId);
            }
            if (value != null) {
                exportFieldValue(container, value, schemaField, indentLevel + 2, parentClassName, parentSourceClassName, parentObjectId);
            }
            xmlWriter.closeStructure("entry");
        }

        xmlWriter.closeStructure(fieldName);
        return true;
    }

    /**
     * Exports a map field stored as a DB4O GenericObject. Attempts to activate the object and extract entries. If the map is empty or cannot be read, handles skip conditions gracefully.
     *
     * @return true if field was written, false if skipped
     */
    private boolean exportGenericMapField(ExtObjectContainer container, Object genericObj, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.attributes.destinationName;

        // Try to activate the GenericObject
        try {
            container.activate(genericObj, 3);
        } catch (Exception e) {
            // Ignore activation errors
        }

        // GenericObject wrapping a Hashtable — treat as empty if we can't
        // iterate
        // The actual entries are stored internally by DB4O and we cannot
        // iterate
        // them without casting to a native Map. Write as empty.
        if (shouldSkipField(genericObj, schemaField, operation.referenceSchema)) {
            return false;
        }

        boolean includeSizeMetadata = xmlWriter.includeCollectionSizeMetadata();
        if (includeSizeMetadata) {
            xmlWriter.elementWithoutContent(fieldName, withSkippedBecauseAttribute(Map.of("size", "0"), genericObj, schemaField, operation.referenceSchema));
        } else {
            xmlWriter.elementWithoutContent(fieldName, skippedBecauseAttributes(genericObj, schemaField, operation.referenceSchema));
        }
        return true;
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
        String fieldName = schemaField.attributes.destinationName;

        // Check skip conditions for empty byte arrays
        if (byteArray.length == 0) {
            if (shouldSkipField(byteArray, schemaField, operation.referenceSchema)) {
                return;
            }
            xmlWriter.elementWithoutContent(fieldName, skippedBecauseAttributes(byteArray, schemaField, operation.referenceSchema));
        } else {
            // Convert byte array to Base64 string
            String base64String = Base64.getEncoder().encodeToString(byteArray);
            base64String = ValueUtil.formatFieldValue(base64String, schemaField);
            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(byteArray, schemaField, operation.referenceSchema), base64String, false);
        }
    }

    private void exportRegularField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.attributes.destinationName;
        String sourceFieldName = schemaField.attributes.source != null ? schemaField.attributes.source : fieldName;

        // Check if this is a Class-typed field based on schema (DB4O may wrap
        // Class in
        // GenericObject)
        boolean isClassField = schemaField != null && (schemaField.attributes.type.equals("java.lang.Class") || schemaField.attributes.type.equals("Class"));

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
            if (shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }

            className = ValueUtil.formatFieldValue(className, schemaField);
            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema), className, false);
            return;
        }

        long refId = container.ext().getID(fieldValue);
        if (refId > 0) {
            String className = ClassUtil.getClassName(fieldValue);
            // This is a persistent object reference

            // Check if field should be skipped (includes IDEntite with mID ==
            // -1)
            if (shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                if (ctxRef.statistics != null) {
                    ctxRef.statistics.recordReachedOnly(className, refId, operation.referenceSchema);
                    ctxRef.statistics.recordRelationshipSkipped(parentObjectId, refId, parentSourceClassName, sourceFieldName, buildSkipReason(schemaField));
                }
                return;
            }

            // Additional check for IDEntite objects - check if they'll be
            // filtered by
            // resolveAndExport
            DOSchemaClass fieldClass = operation.referenceSchema.findClassByName(className);
            if (fieldClass != null && fieldClass.isIDEntite()) {
                // Check if this IDEntite will be skipped due to mID == -1
                if (schemaField != null && schemaField.attributes.skipWhen != null && !schemaField.attributes.skipWhen.isEmpty()) {
                    if (operation.applySkipWhenConditions && IDEntityHandler.shouldSkipMinusOne(container, fieldValue) && schemaField.attributes.skipWhen.contains("MINUS_ONE")) {
                        if (ctxRef.statistics != null && refId > 0) {
                            ctxRef.statistics.recordReachedOnly(fieldClass, refId, operation.referenceSchema);
                        }
                        // This field will produce empty content, skip it
                        // entirely
                        if (ctxRef.statistics != null) {
                            ctxRef.statistics.recordRelationshipSkipped(parentObjectId, refId, parentSourceClassName, sourceFieldName, "reference skipped because mID=-1 and skipWhen includes MINUS_ONE");
                        }
                        return;
                    }
                }

                // For non-embedded IDEntite references, export as simple ID
                // value
                // instead of nested structure
                if (schemaField != null && !schemaField.attributes.embedContents) {
                    if (ctxRef.statistics != null) {
                        long idEntiteObjectId = container.ext().getID(fieldValue);
                        if (idEntiteObjectId > 0) {
                            ctxRef.statistics.recordReachedOnly(fieldClass, idEntiteObjectId, operation.referenceSchema);
                            ctxRef.statistics.recordRelationshipSkipped(parentObjectId, idEntiteObjectId, parentSourceClassName, sourceFieldName, "relationship exported as scalar mID (embedContents=false), target object not traversed");
                        }
                    }

                    Long mID = IDEntityHandler.extractMID(container, fieldValue);
                    if (mID != null) {
                        Map<String, String> attrs = skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema);
                        // Format-specific field hook / JS label resolution
                        if (handlerRef != null) {
                            ctxRef.setField(schemaField, fieldValue);
                            boolean handled;
                            try {
                                handled = handlerRef.onField(ctxRef);
                            } catch (Exception e) {
                                handled = false;
                            }
                            ctxRef.clearField();
                            if (handled)
                                return;
                        }
                        String formattedId = ValueUtil.formatFieldValue(mID.toString(), schemaField);
                        xmlWriter.elementWithContent(fieldName, attrs, formattedId, false);
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

            Integer referencedFieldsToExport = null;
            // Count fields that will be exported from this object
            if (fieldValue instanceof GenericObject && fieldClass != null) {
                referencedFieldsToExport = countFieldsToExport(container, (GenericObject) fieldValue, fieldClass, operation.referenceSchema);

                // If no fields will be exported, optionally skip this field
                // wrapper entirely
                if (operation.skipObjectsWithoutExportableFields && referencedFieldsToExport == 0) {
                    if (ctxRef.statistics != null) {
                        ctxRef.statistics.recordReachedOnly(fieldClass, refId, operation.referenceSchema);
                        ctxRef.statistics.recordRelationshipSkipped(parentObjectId, refId, parentSourceClassName, sourceFieldName, "target object has no exportable fields after skip/criteria rules");
                    }
                    return;
                }
            }

            // Write element and export recursively
            // TODO verify if we write tag twice
            boolean bypassedNoExportableFieldsSkip = !operation.skipObjectsWithoutExportableFields && referencedFieldsToExport != null && referencedFieldsToExport == 0;
            String extraReason = bypassedNoExportableFieldsSkip ? "no exportable fields" : null;
            xmlWriter.openStructure(fieldName, mergeSkippedBecauseAttributes(skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema), extraReason));
            exportFieldValue(container, fieldValue, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
            xmlWriter.closeStructure(fieldName);
        } else {
            // Primitive or non-persistent value - write inline
            String stringValue;

            // Special handling for byte arrays - convert to Base64
            if (fieldValue instanceof byte[]) {
                stringValue = Base64.getEncoder().encodeToString((byte[]) fieldValue);
            } else if (fieldValue instanceof Date) {
                // ISO 8601 date format for xs:dateTime compliance
                stringValue = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) fieldValue);
            } else {
                stringValue = fieldValue.toString();
            }

            if (shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }

            // Apply value mapping if defined for this field
            stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);
            stringValue = ValueUtil.formatFieldValue(stringValue, schemaField);

            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema), stringValue, true);
        }
    }

    /**
     * Exports virtual fields defined in schema but not present in database. Virtual fields use @ prefix in source and criteria-based queries. Example: source="@mVectRapportOfficier" with criteria match="this.mID" with="mIDIntervention" Queries database for objects where mIDIntervention equals this object's mID.
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
            if (schemaField == null || !schemaField.attributes.isExported) {
                continue;
            }

            // Check if this is a virtual field (source starts with @)
            if (!schemaField.isVirtualField()) {
                continue;
            }

            // Skip if no criteria defined
            if (schemaField.attributes.criterias == null || schemaField.attributes.criterias.isEmpty()) {
                continue;
            }

            try {
                // Execute query based on criteria
                Collection<?> queryResults = virtualFieldQuery.execute(container, obj, schemaField);
                int size = (queryResults != null) ? queryResults.size() : 0;

                // Export results using unified collection export
                boolean written = exportCollectionLikeField(container, queryResults, size, queryResults, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);

                if (written) {
                    fieldsWritten++;
                }

            } catch (Exception e) {
                // Log error for debugging
                e.printStackTrace();
            }
        }

        return fieldsWritten;
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
        String fieldName = schemaField != null ? schemaField.attributes.destinationName : className;
        String sourceFieldName = schemaField != null ? schemaField.attributes.source : null;

        // Check if this is an IDEntite reference (reference object pattern)
        DOSchemaClass fieldClass = operation.referenceSchema.findClassByName(className);

        // Determine if object should be embedded:
        // - IDEntite objects: only embed if explicitly set to
        // embedContents=true
        // (default is false for references)
        // - Regular objects: always embed unless explicitly set to
        // embedContents=false
        // (default is true for value objects)
        boolean isEmbedded;
        if (schemaField != null) {
            if (fieldClass != null && fieldClass.isIDEntite()) {
                // IDEntite: default to non-embedded (reference by ID), but
                // allow explicit
                // embedContents=true
                isEmbedded = schemaField.attributes.embedContents;
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

        if (fieldClass != null && fieldClass.isIDEntite()) {
            if (ctxRef.statistics != null) {
                long idEntiteObjectId = container.ext().getID(fieldValue);
                if (idEntiteObjectId > 0) {
                    ctxRef.statistics.recordReachedOnly(fieldClass, idEntiteObjectId, operation.referenceSchema);
                }
            }

            // Resolve and export the IDEntite reference
            // Skip empty references (mID == -1) when MINUS_ONE is in skipWhen
            if (schemaField != null && schemaField.attributes.skipWhen != null && !schemaField.attributes.skipWhen.isEmpty()) {
                Long mID = ReferenceUtil.extractMIDField(container, fieldValue);
                if (mID != null && mID == -1 && schemaField.attributes.skipWhen.contains("MINUS_ONE")) {
                    return;
                }
            }

            Long resolvedObjectId = ReferenceUtil.resolveIDEntiteForExport(container, fieldValue, className, schemaField, operation.databaseSchema);
            if (resolvedObjectId == null) {
                // Resolution failed — skip this reference (already logged by
                // ReferenceUtil)
                return;
            }
            if (ctxRef.statistics != null) {
                ctxRef.statistics.recordRelationshipExported(parentObjectId, resolvedObjectId, parentSourceClassName, sourceFieldName, isEmbedded ? "resolved IDEntite and traversed as embedded relationship" : "resolved IDEntite and traversed as object reference");
            }
            ctxRef.objectExporter.exportObject(resolvedObjectId, isEmbedded);
            return;
        }

        // For regular objects, check if they should be embedded or referenced
        long objectId = container.ext().getID(fieldValue);

        // Special handling for Class objects based on schema type
        boolean isClassField = schemaField != null && (schemaField.attributes.type.equals("java.lang.Class") || schemaField.attributes.type.equals("Class"));

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
            if (ctxRef.statistics != null) {
                ctxRef.statistics.recordRelationshipExported(parentObjectId, objectId, parentSourceClassName, sourceFieldName, isEmbedded ? "traversed as embedded relationship" : "traversed as object reference");
            }
            ctxRef.objectExporter.exportObject(objectId, isEmbedded);
        } else {
            // Primitive value - write inline
            String stringValue;
            if (fieldValue instanceof Class<?>) {
                stringValue = ((Class<?>) fieldValue).getName();
            } else if (fieldValue instanceof byte[]) {
                // Convert byte arrays to Base64
                stringValue = Base64.getEncoder().encodeToString((byte[]) fieldValue);
            } else if (fieldValue instanceof Date) {
                // ISO 8601 date format for xs:dateTime compliance
                stringValue = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) fieldValue);
            } else {
                stringValue = fieldValue.toString();
            }
            stringValue = ValueUtil.formatFieldValue(stringValue, schemaField);
            xmlWriter.elementWithContent(fieldName, stringValue, true);
        }
    }

    private String buildSkipReason(DOSchemaField schemaField) {
        if (schemaField == null) {
            return "field skipped by export rules";
        }

        if (schemaField.attributes.skipWhen != null && !schemaField.attributes.skipWhen.trim().isEmpty()) {
            return "field skipped due to skipWhen=" + schemaField.attributes.skipWhen;
        }

        return "field skipped by user/export rules";
    }

    private boolean shouldSkipField(Object value, DOSchemaField field, DOSchema schema) {
        return ValueUtil.shouldSkipField(value, field, schema, operation.selectedSkipUserOptions, operation.applyUserSelectedFieldExclusions, operation.applySkipWhenConditions);
    }

    private Map<String, String> skippedBecauseAttributes(Object value, DOSchemaField field, DOSchema schema) {
        String skippedBecause = getBypassedSkipReasons(value, field, schema);
        if (skippedBecause == null || skippedBecause.isBlank()) {
            return null;
        }
        return Map.of("skippedBecause", skippedBecause);
    }

    private Map<String, String> withSkippedBecauseAttribute(Map<String, String> baseAttributes, Object value, DOSchemaField field, DOSchema schema) {
        return mergeSkippedBecauseAttributes(baseAttributes, getBypassedSkipReasons(value, field, schema));
    }

    private Map<String, String> mergeSkippedBecauseAttributes(Map<String, String> baseAttributes, String extraReason) {
        if (extraReason == null || extraReason.isBlank()) {
            return baseAttributes;
        }

        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        if (baseAttributes != null && !baseAttributes.isEmpty()) {
            merged.putAll(baseAttributes);
        }
        String existingReason = merged.get("skippedBecause");
        if (existingReason == null || existingReason.isBlank()) {
            merged.put("skippedBecause", extraReason);
        } else if (!existingReason.contains(extraReason)) {
            merged.put("skippedBecause", existingReason + "; " + extraReason);
        }
        return merged;
    }

    private String getBypassedSkipReasons(Object value, DOSchemaField field, DOSchema schema) {
        if (field == null) {
            return null;
        }

        java.util.List<String> reasons = new java.util.ArrayList<>();

        boolean bypassedUserSelection = !operation.applyUserSelectedFieldExclusions && operation.selectedSkipUserOptions != null && operation.selectedSkipUserOptions.contains(field);
        if (bypassedUserSelection) {
            reasons.add("user-selected field exclusion");
        }

        boolean bypassedSkipWhen = !operation.applySkipWhenConditions && field.attributes.skipWhen != null && !field.attributes.skipWhen.trim().isEmpty() && ValueUtil.matchesSkipCondition(value, field.attributes.skipWhen, field, schema);
        if (bypassedSkipWhen) {
            reasons.add("skipWhen(" + field.attributes.skipWhen + ")");
        }

        if (reasons.isEmpty()) {
            return null;
        }

        return String.join("; ", reasons);
    }

    private void recordRelationshipSkippedIfPersistent(long parentObjectId, String parentSourceClassName, String sourceFieldName, Object fieldValue, String reason) {
        if (ctxRef.statistics == null || parentObjectId <= 0 || fieldValue == null) {
            return;
        }

        try {
            long childObjectId = operation.container.ext().getID(fieldValue);
            if (childObjectId > 0) {
                ctxRef.statistics.recordReachedOnly(ClassUtil.getClassName(fieldValue), childObjectId, operation.referenceSchema);
                ctxRef.statistics.recordRelationshipSkipped(parentObjectId, childObjectId, parentSourceClassName, sourceFieldName, reason);
            }
        } catch (Exception ignored) {
            // Best-effort diagnostics only
        }
    }

    private void markCollectionWrapperReached(Object fieldValue, long parentObjectId, String parentSourceClassName, String sourceFieldName) {
        if (ctxRef.statistics == null || fieldValue == null) {
            return;
        }

        try {
            long wrapperObjectId = operation.container.ext().getID(fieldValue);
            if (wrapperObjectId <= 0) {
                return;
            }

            String wrapperClassName = ClassUtil.getClassName(fieldValue);
            ctxRef.statistics.recordReachedOnly(wrapperClassName, wrapperObjectId, operation.referenceSchema);
            ctxRef.statistics.recordRelationshipExported(parentObjectId, wrapperObjectId, parentSourceClassName, sourceFieldName, "collection wrapper encountered; contents exported from this object");
        } catch (Exception ignored) {
            // Best-effort diagnostics only
        }
    }

    /**
     * When a field has isExported=false, we skip its export but must still mark all descendant objects as reached. This handles two cases:
     * 
     * 1. Collection fields: extract items and mark each as reached 2. Persistent GenericObject fields: traverse one level and mark child persistent objects as reached
     * 
     * Without this, objects only reachable through disabled fields would appear as "unreached" even though the engine encountered them.
     */
    private void markDisabledFieldDescendantsReached(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField) {
        if (ctxRef.statistics == null) {
            return;
        }

        try {
            // Case 1: Collection field — extract items and mark each as reached
            boolean isCollectionLike = schemaField.attributes.isCollection || (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) || fieldValue.getClass().isArray();

            if (isCollectionLike) {
                Collection<?> items = null;

                if (fieldValue instanceof Collection) {
                    items = (Collection<?>) fieldValue;
                } else if (fieldValue.getClass().isArray() && !(fieldValue instanceof byte[])) {
                    items = ValueUtil.arrayToList(fieldValue);
                } else {
                    // GenericObject collection wrapper — use standard
                    // extraction
                    items = RecipeCollectionItems.getItems(container, fieldValue);
                }

                if (items != null) {
                    for (Object item : items) {
                        if (item != null) {
                            markObjectReachedRecursiveShallow(container, item, 2);
                        }
                    }
                }
                return;
            }

            // Case 2: Persistent GenericObject — mark its children as reached
            if (fieldValue instanceof GenericObject) {
                markObjectReachedRecursiveShallow(container, fieldValue, 2);
            }
        } catch (Exception ignored) {
            // Best-effort reach recording
        }
    }

    /**
     * Recursively marks a persistent object and its immediate children as reached, without generating any XML output. Traverses up to maxDepth levels.
     * 
     * @param container DB4O container
     * @param obj The object to mark
     * @param maxDepth Maximum depth to traverse (0 = mark this object only)
     */
    private void markObjectReachedRecursiveShallow(ExtObjectContainer container, Object obj, int maxDepth) {
        if (obj == null || ctxRef.statistics == null) {
            return;
        }

        try {
            long objectId = container.ext().getID(obj);
            if (objectId <= 0) {
                return; // Not a persistent object
            }

            String className = ClassUtil.getClassName(obj);
            ctxRef.statistics.recordReachedOnly(className, objectId, operation.referenceSchema);

            if (maxDepth <= 0) {
                return;
            }

            // Traverse child fields of GenericObjects
            if (obj instanceof GenericObject) {
                StoredClass storedClass = container.ext().storedClass(obj);
                if (storedClass == null) {
                    return;
                }

                StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
                for (StoredField field : fields) {
                    try {
                        Object childValue = field.get(obj);
                        if (childValue == null) {
                            continue;
                        }

                        // Mark persistent child objects
                        long childId = container.ext().getID(childValue);
                        if (childId > 0) {
                            markObjectReachedRecursiveShallow(container, childValue, maxDepth - 1);
                        }

                        // For collection children, mark items too
                        if (childValue instanceof Collection) {
                            for (Object item : (Collection<?>) childValue) {
                                if (item != null) {
                                    long itemId = container.ext().getID(item);
                                    if (itemId > 0) {
                                        markObjectReachedRecursiveShallow(container, item, maxDepth - 1);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Best-effort per-field
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort reach recording
        }
    }

    /**
     * Sorts StoredField[] in inheritance-aware order to match the XSD {@code xs:extension} content model. Fields declared by ancestor classes come first (sorted alphabetically within each level), followed by the current class's own fields (also alphabetical). Fields without a schema mapping are placed at the end.
     *
     * <p>
     * This ensures the XML output satisfies the XSD content model where base type fields precede extension fields.
     * </p>
     */
    private StoredField[] sortFieldsByDestinationName(StoredField[] fields, DOSchemaClass parentClass, DOSchema schema) {
        // Build ancestry chain from root down to the current class
        List<DOSchemaClass> chain = new ArrayList<>();
        DOSchemaClass current = parentClass;
        while (current != null) {
            chain.add(0, current); // prepend so root is first
            String ancestorName = current.attributes.parentClassName;
            if (ancestorName == null || ancestorName.isEmpty())
                break;
            DOSchemaClass ancestor = schema.findClassByName(ancestorName);
            if (ancestor == null)
                break;
            current = ancestor;
        }

        // Build a map: source field name → hierarchy depth (0 = topmost
        // ancestor)
        Map<String, Integer> fieldDepth = new HashMap<>();
        for (int depth = 0; depth < chain.size(); depth++) {
            DOSchemaClass cls = chain.get(depth);
            if (cls.fields != null) {
                for (DOSchemaField f : cls.fields) {
                    // First declaration wins (don't let overrides change depth)
                    if (!fieldDepth.containsKey(f.attributes.source)) {
                        fieldDepth.put(f.attributes.source, depth);
                    }
                }
            }
        }

        StoredField[] sorted = Arrays.copyOf(fields, fields.length);
        Arrays.sort(sorted, (a, b) -> {
            DOSchemaField sfA = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, a.getName(), schema);
            DOSchemaField sfB = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass, b.getName(), schema);

            // Unmapped fields go last
            if (sfA == null && sfB == null)
                return 0;
            if (sfA == null)
                return 1;
            if (sfB == null)
                return -1;

            // Sort first by hierarchy depth (ancestor fields before own fields)
            int depthA = fieldDepth.getOrDefault(a.getName(), Integer.MAX_VALUE);
            int depthB = fieldDepth.getOrDefault(b.getName(), Integer.MAX_VALUE);
            if (depthA != depthB)
                return Integer.compare(depthA, depthB);

            // Within same depth, sort alphabetically by destination name
            return sfA.attributes.destinationName.compareTo(sfB.attributes.destinationName);
        });
        return sorted;
    }
}
