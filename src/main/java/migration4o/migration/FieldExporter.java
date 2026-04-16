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
import java.util.Set;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabaseDelegate;
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
import migration4o.util.ResolvedReference;
import migration4o.util.ValueUtil;
import migration4o.migration.processors.ValuePostProcessors;
import migration4o.util.formatters.FormatterContext;
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
    public int countFieldsToExport(DODatabaseDelegate delegate, GenericObject obj, DOSchemaClass parentClass, DOSchema schema) {
        int count = 0;
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the
            // immediate
            // class
            StoredField[] fields = delegate.getAllFieldsIncludingAncestors(storedClass);
            // Sort fields by schema destination name for deterministic output
            fields = sortFieldsByDestinationName(fields, parentClass, schema);
            for (StoredField field : fields) {
                try {
                    String sourceFieldName = field.getName();
                    String countCtx = (parentClass != null ? parentClass.attributes.source : "?") + "#" + sourceFieldName;
                    migration4o.database.Db4oReadContext.set(countCtx + " [count-check]");
                    Object fieldValue;
                    try {
                        fieldValue = field.get(obj);
                    } finally {
                        migration4o.database.Db4oReadContext.clear();
                    }

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

            // Count method-call fields
            if (parentClass != null && parentClass.fields != null) {
                for (DOSchemaField schemaField : parentClass.fields) {
                    if (schemaField != null && schemaField.attributes.isExported && schemaField.isMethodCallField()) {
                        count++;
                    }
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
    public int exportAllFields(DODatabaseDelegate delegate, GenericObject obj, DOSchemaClass parentClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
        int fieldsWritten = 0;
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the
            // immediate
            // class
            StoredField[] fields = delegate.getAllFieldsIncludingAncestors(storedClass);
            // Sort fields by schema destination name for deterministic,
            // alphabetical output (enables xs:sequence)
            fields = sortFieldsByDestinationName(fields, parentClass, operation.referenceSchema);
            // Collect scalar virtual fields sorted by destination name so they
            // can be interleaved at their correct alphabetical position.
            List<DOSchemaField> pendingScalarVirtuals = new ArrayList<>();
            if (parentClass != null && parentClass.fields != null) {
                for (DOSchemaField svf : parentClass.fields) {
                    if (svf != null && svf.attributes.isExported && svf.isScalarVirtualField()) {
                        pendingScalarVirtuals.add(svf);
                    }
                }
                pendingScalarVirtuals.sort((a, b) -> a.attributes.destinationName.compareTo(b.attributes.destinationName));
            }
            // For xs:extension classes (depth-sorted), scalar virtuals are own-class
            // fields and must NOT be written before ancestor-depth stored fields —
            // the XSD base-type sequence must be satisfied first. Build the set of
            // own-class source names so the flush is gated accordingly.
            boolean ownClassOnly = hasExportedDirectParent(parentClass, operation.referenceSchema);
            Set<String> ownSourceNames = java.util.Collections.emptySet();
            if (ownClassOnly && parentClass != null && parentClass.fields != null) {
                ownSourceNames = new java.util.HashSet<>();
                for (DOSchemaField f : parentClass.fields) {
                    if (f != null && f.attributes.source != null && !f.isVirtualField() && !f.isMethodCallField()) {
                        ownSourceNames.add(f.attributes.source);
                    }
                }
            }
            for (StoredField field : fields) {
                Object fieldValue = null;
                try {
                    String sourceFieldName = field.getName();
                    migration4o.database.Db4oReadContext.set(sourceClassName + "#" + sourceFieldName + " [objectId=" + parentObjectId + "]");
                    try {
                        fieldValue = field.get(obj);
                    } finally {
                        migration4o.database.Db4oReadContext.clear();
                    }

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
                            markDisabledFieldDescendantsReached(delegate, fieldValue, schemaField);
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
                            String warningKey = schemaField.attributes.destinationName + ":" + schemaField.attributes.type;
                            if (!ctxRef.previousWarnings.contains(warningKey)) {
                                System.err.println("[WARN] Export skipping field '" + schemaField.attributes.destinationName + "' — field type '" + schemaField.attributes.type + "' is not exported (migrate=false).");
                                ctxRef.previousWarnings.add(warningKey);
                            }
                            continue;
                        }
                    }

                    // Flush scalar virtual fields whose destination name sorts
                    // before the current stored field, keeping the XML output
                    // in the same alphabetical order as the XSD xs:sequence.
                    // For xs:extension classes, only flush when we reach an
                    // own-class stored field — ancestor fields must be written
                    // first to satisfy the base-type sequence.
                    if (!ownClassOnly || ownSourceNames.contains(sourceFieldName)) {
                        java.util.Iterator<DOSchemaField> svIt = pendingScalarVirtuals.iterator();
                        while (svIt.hasNext()) {
                            DOSchemaField sv = svIt.next();
                            if (sv.attributes.destinationName.compareTo(fieldName) < 0) {
                                fieldsWritten += writeOneScalarVirtualField(delegate, obj, sv, indentLevel);
                                svIt.remove();
                            } else {
                                break;
                            }
                        }
                    }

                    // Apply value postprocessor — single transit point for all stored field values.
                    fieldValue = readFieldValue(delegate, obj, fieldValue, schemaField);

                    if (fieldValue == null) {
                        // Skip this field if skip conditions are met
                        if (shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                            continue;
                        }
                        // Null values for strict XSD primitive types (int,
                        // long, boolean, double, float, etc.) must be omitted
                        // entirely — an empty element like <field /> is not
                        // valid content for xs:int and friends. The XSD
                        // already declares minOccurs="0" so omission is legal.
                        if (isStrictXsdPrimitiveType(schemaField.attributes.type)) {
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
                                delegate.activate(fieldValue, 1);
                            } catch (Exception e) {
                                // Ignore activation errors
                            }
                        }
                        if (exportSchemaCollectionField(delegate, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        markCollectionWrapperReached(fieldValue, parentObjectId, sourceClassName, sourceFieldName);
                        if (exportCollectionField(delegate, (Collection<?>) fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof byte[]) {
                        // Special handling for byte arrays - export as Base64
                        // string
                        exportByteArrayField(delegate, (byte[]) fieldValue, schemaField, indentLevel);
                        fieldsWritten++;
                    } else if (fieldValue.getClass().isArray()) {
                        if (exportArrayField(delegate, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof java.util.Map) {
                        // Map type (Hashtable, HashMap, etc.) — export like a
                        // collection of entries
                        if (exportMapField(delegate, (java.util.Map<?, ?>) fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof GenericObject && schemaField != null && isFieldTypeMap(schemaField)) {
                        // Map type wrapped in GenericObject (DB4O stored
                        // Hashtable)
                        if (exportGenericMapField(delegate, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else if (fieldValue instanceof GenericObject && schemaField != null && isFieldTypeCollection(schemaField)) {
                        // Safety net: field type extends a collection base
                        // class (e.g. VectChampPerso extends VectRechID extends
                        // HVector extends Vector)
                        // but is not flagged collection=true in schema and
                        // arrived as GenericObject
                        markCollectionWrapperReached(fieldValue, parentObjectId, sourceClassName, sourceFieldName);
                        if (exportSchemaCollectionField(delegate, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId)) {
                            fieldsWritten++;
                        }
                    } else {
                        exportRegularField(delegate, fieldValue, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    }
                } catch (Exception e) {
                    // Field export failed — still mark the field value as
                    // reached if it's a persistent object
                    if (fieldValue != null && ctxRef.statistics != null) {
                        try {
                            long childId = ctxRef.delegate.getID(fieldValue);
                            if (childId > 0) {
                                ctxRef.statistics.recordReachedOnly(ClassUtil.getClassName(fieldValue), childId, operation.referenceSchema);
                            }
                        } catch (Exception ignored) {
                            // Best-effort reach recording
                        }
                    }
                }
            }

            // Flush any scalar virtual fields that sort after all stored fields.
            for (DOSchemaField sv : pendingScalarVirtuals) {
                fieldsWritten += writeOneScalarVirtualField(delegate, obj, sv, indentLevel);
            }

            // VIRTUAL FIELDS: Export schema-defined virtual fields that don't
            // exist in
            // database
            // Virtual fields start with @ and use criteria-based queries to
            // find related
            // objects
            fieldsWritten += exportVirtualFields(delegate, obj, parentClass, indentLevel, destinationClassName, sourceClassName, parentObjectId);

            // METHOD-CALL FIELDS: Export fields whose source ends with "()"
            // by reconstructing the native object and invoking the method
            fieldsWritten += exportMethodCallFieldsFromGenericObject(delegate, obj, parentClass, indentLevel, destinationClassName, sourceClassName, parentObjectId);

        } catch (Exception e) {
            // Error accessing fields
        }
        return fieldsWritten;
    }

    /**
     * Single transit point for all exported field values. Reads the raw value using the appropriate
     * strategy for the field type, then applies the configured {@link ValuePostProcessors} interceptor
     * if one is defined on the current schema class.
     *
     * <p>For stored fields, pass the already-read DB4O value as {@code preReadValue}; the method
     * applies the postprocessor and returns. For scalar virtual and method-call fields, pass
     * {@code null}; the method performs the read itself.
     *
     * @param delegate     Active database delegate
     * @param readFrom     Object to read from: the GenericObject for stored/virtual fields,
     *                     the reconstructed native object for method-call fields
     * @param preReadValue Pre-read value for stored fields; ignored for virtual/method-call fields
     * @param schemaField  Schema field definition
     * @return The (possibly postprocessor-overridden) field value
     */
    private Object readFieldValue(DODatabaseDelegate delegate, Object readFrom, Object preReadValue, DOSchemaField schemaField) {
        final Object rawValue;

        if (schemaField.isScalarVirtualField()) {
            String realFieldName = schemaField.getVirtualFieldName();
            StoredClass storedClass = delegate.storedClass(readFrom);
            Object found = null;
            if (storedClass != null) {
                for (StoredField sf : delegate.getAllFieldsIncludingAncestors(storedClass)) {
                    if (realFieldName.equals(sf.getName())) {
                        found = sf.get(readFrom);
                        break;
                    }
                }
            }
            rawValue = found;
        } else if (schemaField.isMethodCallField()) {
            String methodName = schemaField.getMethodCallName();
            try {
                java.lang.reflect.Method method = readFrom.getClass().getMethod(methodName);
                rawValue = method.invoke(readFrom);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Method-call field '" + schemaField.attributes.source + "' failed on " + readFrom.getClass().getName(), e);
            }
        } else {
            rawValue = preReadValue;
        }

        return ValuePostProcessors.processField(ctxRef.currentObject().obj, rawValue, schemaField, ctxRef);
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
    private boolean exportCollectionLikeField(DODatabaseDelegate delegate, Iterable<?> items, int size, Object itemsValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
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
                xmlWriter.openArray(fieldName, withSkippedBecauseAttribute(Map.of("size", size + ""), itemsValue, schemaField, operation.referenceSchema));
            } else {
                xmlWriter.openArray(fieldName, skippedBecauseAttributes(itemsValue, schemaField, operation.referenceSchema));
            }

            // Check if we should export ID references instead of entities
            IDReferenceDetector.DetectionResult detection = IDReferenceDetector.detectIDReference(schemaField, operation.referenceSchema);

            // When embedContents=true and childrenType is set, collection
            // items that are primitive mID values (Long/Integer) must be
            // resolved to the target entity and exported inline.
            boolean resolveEmbeddedMIDs = schemaField.attributes.embedContents && schemaField.attributes.childrenType != null && operation.database != null;

            // Pre-resolve the target entity schema class for embedded mID
            // collections. findObjectByMID uses its isStatic flag to
            // route to the correct delegate automatically.
            DOSchemaClass embeddedMIDTargetClass = null;
            if (resolveEmbeddedMIDs) {
                embeddedMIDTargetClass = operation.referenceSchema.findClassByName(schemaField.attributes.childrenType);
            }

            try {
                for (Object item : items) {
                    if (item != null) {
                        try {
                            if (detection.shouldExportAsIDReferences) {
                                // Export as ID reference
                                IDReferenceExporter.exportAsIDReference(delegate, item, detection.idClass, xmlWriter, indentLevel + 1, ctxRef);
                            } else if (resolveEmbeddedMIDs && (item instanceof Number)) {
                                // Primitive mID inside an embedContents collection —
                                // resolve to the target entity and export inline.
                                Long mID = ((Number) item).longValue();
                                ResolvedReference resolved = operation.database.findObjectByMID(mID, embeddedMIDTargetClass);
                                if (resolved != null) {
                                    DODatabaseDelegate savedDelegate = ctxRef.delegate;
                                    try {
                                        ctxRef.delegate = resolved.delegate;
                                        ctxRef.objectExporter.exportObject(resolved.objectId, true);
                                    } finally {
                                        ctxRef.delegate = savedDelegate;
                                    }
                                }
                            } else {
                                // Export normally
                                exportFieldValue(delegate, item, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
                            }
                        } catch (Exception e) {
                            // Item export failed — still mark as reached if
                            // persistent
                            if (ctxRef.statistics != null) {
                                try {
                                    long itemId = delegate.getID(item);
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
            } finally {
                xmlWriter.closeArray(fieldName);
            }
            return true;
        }
    }

    /**
     * Exports a collection field that is marked as collection in the schema but may be stored as a DB4O persistent object (like VectRechID). This method extracts the collection items from the DB4O object structure.
     * 
     * @return true if field was written, false if skipped
     */
    private boolean exportSchemaCollectionField(DODatabaseDelegate delegate, Object collectionObj, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Try to extract items from the collection object
        // VectRechID extends HVector which extends Vector, so look for Vector's
        // internal array
        // NOTE: Parent object was already activated at max depth, so collection
        // should
        // be activated too
        Collection<?> items = RecipeCollectionItems.getItems(delegate, collectionObj);
        int size = (items != null) ? items.size() : 0;

        return exportCollectionLikeField(delegate, items, size, items, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * @return true if field was written, false if skipped
     */
    private boolean exportCollectionField(DODatabaseDelegate delegate, Collection<?> collection, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Check if empty using ValueUtil (handles null case)
        boolean isEmpty = ValueUtil.isEmpty(collection, schemaField, operation.referenceSchema);
        int size = isEmpty ? 0 : collection.size();

        return exportCollectionLikeField(delegate, collection, size, collection, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * @return true if field was written, false if skipped
     */
    private boolean exportArrayField(DODatabaseDelegate delegate, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        // Check if empty using ValueUtil (handles null case)
        boolean isEmpty = ValueUtil.isEmpty(fieldValue, schemaField, operation.referenceSchema);
        int length = isEmpty ? 0 : ArrayTraverser.getLength(fieldValue);

        // Convert array to Iterable for unified processing
        Iterable<?> items = createArrayIterable(fieldValue, length);

        return exportCollectionLikeField(delegate, items, length, fieldValue, schemaField, indentLevel, parentClassName, parentSourceClassName, parentObjectId);
    }

    /**
     * Exports a Java Map field (Hashtable, HashMap, etc.) like a collection. Each map entry is written as an &lt;entry&gt; element with key and value children.
     *
     * @return true if field was written, false if skipped
     */
    private boolean exportMapField(DODatabaseDelegate delegate, java.util.Map<?, ?> map, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
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
            xmlWriter.openArray(fieldName, withSkippedBecauseAttribute(Map.of("size", size + ""), map, schemaField, operation.referenceSchema));
        } else {
            xmlWriter.openArray(fieldName, skippedBecauseAttributes(map, schemaField, operation.referenceSchema));
        }

        try {
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                xmlWriter.openStructure("entry", null);
                try {
                    Object key = entry.getKey();
                    Object value = entry.getValue();
                    if (key != null) {
                        exportFieldValue(delegate, key, schemaField, indentLevel + 2, parentClassName, parentSourceClassName, parentObjectId);
                    }
                    if (value != null) {
                        exportFieldValue(delegate, value, schemaField, indentLevel + 2, parentClassName, parentSourceClassName, parentObjectId);
                    }
                } finally {
                    xmlWriter.closeStructure("entry");
                }
            }
        } finally {
            xmlWriter.closeArray(fieldName);
        }
        return true;
    }

    /**
     * Exports a map field stored as a DB4O GenericObject. Attempts to activate the object and extract entries. If the map is empty or cannot be read, handles skip conditions gracefully.
     *
     * @return true if field was written, false if skipped
     */
    private boolean exportGenericMapField(DODatabaseDelegate delegate, Object genericObj, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.attributes.destinationName;

        // Try to activate the GenericObject
        try {
            delegate.activate(genericObj, 3);
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

    private void exportByteArrayField(DODatabaseDelegate delegate, byte[] byteArray, DOSchemaField schemaField, int indentLevel) throws IOException {
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
            base64String = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), base64String, schemaField);
            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(byteArray, schemaField, operation.referenceSchema), base64String, false);
        }
    }

    private void exportRegularField(DODatabaseDelegate delegate, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
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

            className = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), className, schemaField);
            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema), className, false);
            return;
        }

        long refId = delegate.getID(fieldValue);
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

            // Now handled by shouldSkipField above (which extracts mID for
            // IDEntite wrappers and checks all skipWhen conditions)
            DOSchemaClass fieldClass = operation.referenceSchema.findClassByName(className);
            if (fieldClass != null && fieldClass.isIDEntite()) {
                // ── Unified IDEntite handler (embedded + non-embedded) ──
                // All field-level IDEntite processing happens here so that
                // the format handler hook is invoked regardless of the
                // embedContents flag. Embedded IDEntite no longer falls
                // through to the generic-object path / exportFieldValue.

                if (ctxRef.statistics != null) {
                    long idEntiteObjectId = delegate.getID(fieldValue);
                    if (idEntiteObjectId > 0) {
                        ctxRef.statistics.recordReachedOnly(fieldClass, idEntiteObjectId, operation.referenceSchema);
                    }
                }

                Long mID = IDEntityHandler.extractMID(delegate, fieldValue);

                // Skip based on mID value (MINUS_ONE, NULL, etc.)
                if (mID != null && shouldSkipField(mID, schemaField, operation.referenceSchema)) {
                    if (ctxRef.statistics != null) {
                        long idEntiteObjectId = delegate.getID(fieldValue);
                        ctxRef.statistics.recordRelationshipSkipped(parentObjectId, idEntiteObjectId, parentSourceClassName, sourceFieldName, "IDEntite mID skipped by skipWhen conditions");
                    }
                    return;
                }

                // Format handler hook — gives HTML (or other) handlers a
                // chance to write a human-readable label for BOTH embedded
                // and non-embedded IDEntite fields.
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

                boolean isEmbedded = schemaField != null && schemaField.attributes.embedContents;

                if (isEmbedded) {
                    // Embedded IDEntite: resolve to target entity and export
                    // inline, wrapped in the field-name element.
                    ResolvedReference resolved = ReferenceUtil.resolveIDEntiteForExport(delegate, fieldValue, className, schemaField, operation.database);
                    if (resolved == null)
                        return;

                    if (ctxRef.statistics != null) {
                        ctxRef.statistics.recordRelationshipExported(parentObjectId, resolved.objectId, parentSourceClassName, sourceFieldName, "resolved IDEntite and traversed as embedded relationship");
                    }

                    xmlWriter.openStructure(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema));
                    DODatabaseDelegate savedDelegate = ctxRef.delegate;
                    try {
                        ctxRef.delegate = resolved.delegate;
                        ctxRef.objectExporter.exportObject(resolved.objectId, true);
                    } finally {
                        ctxRef.delegate = savedDelegate;
                        xmlWriter.closeStructure(fieldName);
                    }
                } else {
                    // Non-embedded IDEntite: export mID as a scalar value
                    if (mID != null) {
                        if (ctxRef.statistics != null) {
                            long idEntiteObjectId = delegate.getID(fieldValue);
                            ctxRef.statistics.recordRelationshipSkipped(parentObjectId, idEntiteObjectId, parentSourceClassName, sourceFieldName, "relationship exported as scalar mID (embedContents=false), target object not traversed");
                        }
                        Map<String, String> attrs = skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema);
                        String formattedId = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), mID.toString(), schemaField);
                        xmlWriter.elementWithContent(fieldName, attrs, formattedId, false);
                    } else {
                        // IDEntite wrapper exists in DB4O but mID could not be
                        // read — data integrity issue
                        long idEntiteObjectId = delegate.getID(fieldValue);
                        System.err.println("[WARN] IDEntite mID unreadable for field '" + sourceFieldName + "' on " + parentSourceClassName + " (objectId=" + parentObjectId + ", IDEntite objectId=" + idEntiteObjectId + ") — field skipped.");
                    }
                }
                return;
            }

            // Before writing field wrapper tags, check if the referenced object
            // has any
            // fields to export
            // (reuse className and fieldClass from above)

            Integer referencedFieldsToExport = null;
            // Count fields that will be exported from this object
            if (fieldValue instanceof GenericObject && fieldClass != null) {
                referencedFieldsToExport = countFieldsToExport(delegate, (GenericObject) fieldValue, fieldClass, operation.referenceSchema);

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
            try {
                exportFieldValue(delegate, fieldValue, schemaField, indentLevel + 1, parentClassName, parentSourceClassName, parentObjectId);
            } finally {
                xmlWriter.closeStructure(fieldName);
            }
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
            stringValue = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), stringValue, schemaField);

            xmlWriter.elementWithContent(fieldName, skippedBecauseAttributes(fieldValue, schemaField, operation.referenceSchema), stringValue, true);
        }
    }

    /**
     * Exports virtual fields defined in schema but not present in database. Virtual fields use @ prefix in source and criteria-based queries. Example: source="@mVectRapportOfficier" with criteria match="this.mID" with="mIDIntervention" Queries database for objects where mIDIntervention equals this object's mID.
     * 
     * @return number of virtual fields written
     */
    private int exportVirtualFields(DODatabaseDelegate delegate, GenericObject obj, DOSchemaClass parentClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
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

            // Scalar virtual field: handled earlier by writeOneScalarVirtualField(),
            // interleaved at its correct alphabetical position inside exportAllFields().
            if (schemaField.isScalarVirtualField()) {
                continue;
            }

            // Skip if no criteria defined
            if (schemaField.attributes.criterias == null || schemaField.attributes.criterias.isEmpty()) {
                continue;
            }

            try {
                // Execute query based on criteria
                Collection<?> queryResults = virtualFieldQuery.execute(delegate, obj, schemaField);
                int size = (queryResults != null) ? queryResults.size() : 0;

                // Export results using unified collection export
                boolean written = exportCollectionLikeField(delegate, queryResults, size, queryResults, schemaField, indentLevel, destinationClassName, sourceClassName, parentObjectId);

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
     * Writes a single scalar virtual field (source="@realField" with valueMap and/or format, no criteria) at the current XML position. Returns 1 if written, 0 if skipped.
     */
    private int writeOneScalarVirtualField(DODatabaseDelegate delegate, GenericObject obj, DOSchemaField schemaField, int indentLevel) {
        try {
            Object rawValue = readFieldValue(delegate, obj, null, schemaField);
            if (shouldSkipField(rawValue, schemaField, operation.referenceSchema)) {
                return 0;
            }
            if (rawValue instanceof byte[]) {
                exportByteArrayField(delegate, (byte[]) rawValue, schemaField, indentLevel);
                return 1;
            }
            String stringValue = rawValue != null ? rawValue.toString() : null;
            stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);
            FormatterContext fmtCtx = new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, obj);
            fmtCtx.filesDestination = ctxRef.request.filesDestination;
            stringValue = ValueUtil.formatFieldValue(delegate, fmtCtx, stringValue, schemaField);
            if (stringValue == null) {
                return 0;
            }
            xmlWriter.elementWithContent(schemaField.attributes.destinationName, skippedBecauseAttributes(rawValue, schemaField, operation.referenceSchema), stringValue, true);
            return 1;
        } catch (Exception e) {
            System.err.println("[WARN] Scalar virtual field '" + schemaField.attributes.source + "': " + e.getMessage());
            return 0;
        }
    }

    /**
     * Exports method-call fields for a native Java object (not GenericObject). Method-call fields have source ending with "()" and invoke the named no-arg method via reflection on the object.
     *
     * @return number of method-call fields written
     */
    public int exportMethodCallFields(DODatabaseDelegate delegate, Object nativeObj, DOSchemaClass schemaClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
        int fieldsWritten = 0;

        if (schemaClass == null || schemaClass.fields == null) {
            return 0;
        }

        for (DOSchemaField schemaField : schemaClass.fields) {
            if (schemaField == null || !schemaField.attributes.isExported) {
                continue;
            }

            if (!schemaField.isMethodCallField()) {
                continue;
            }

            try {
                Object result = readFieldValue(delegate, nativeObj, null, schemaField);

                if (result == null) {
                    if (shouldSkipField(null, schemaField, operation.referenceSchema)) {
                        continue;
                    }
                    xmlWriter.elementWithoutContent(schemaField.attributes.destinationName, skippedBecauseAttributes(null, schemaField, operation.referenceSchema));
                } else {
                    String stringValue = result.toString();
                    if (shouldSkipField(stringValue, schemaField, operation.referenceSchema)) {
                        continue;
                    }
                    stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);
                    stringValue = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), stringValue, schemaField);
                    xmlWriter.elementWithContent(schemaField.attributes.destinationName, skippedBecauseAttributes(result, schemaField, operation.referenceSchema), stringValue, true);
                }
                fieldsWritten++;
            } catch (Exception e) {
                System.err.println("[WARN] Method-call field '" + schemaField.attributes.source + "' failed on " + nativeObj.getClass().getName() + ": " + e.getMessage());
            }
        }

        return fieldsWritten;
    }

    /**
     * Exports method-call fields for a GenericObject by reconstructing the native Java object from stored field values and invoking the method.
     *
     * @return number of method-call fields written
     */
    private int exportMethodCallFieldsFromGenericObject(DODatabaseDelegate delegate, GenericObject obj, DOSchemaClass schemaClass, int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId) throws IOException {
        if (schemaClass == null || schemaClass.fields == null) {
            return 0;
        }

        // Check if there are any method-call fields before doing reconstruction
        boolean hasMethodCallFields = false;
        for (DOSchemaField schemaField : schemaClass.fields) {
            if (schemaField != null && schemaField.attributes.isExported && schemaField.isMethodCallField()) {
                hasMethodCallFields = true;
                break;
            }
        }
        if (!hasMethodCallFields) {
            return 0;
        }

        // Reconstruct the native object from stored fields
        Object nativeObj = reconstructNativeObject(delegate, obj);
        if (nativeObj == null) {
            System.err.println("[WARN] Could not reconstruct native object for " + sourceClassName + " — method-call fields skipped.");
            return 0;
        }

        return exportMethodCallFields(delegate, nativeObj, schemaClass, indentLevel, destinationClassName, sourceClassName, parentObjectId);
    }

    /**
     * Reconstructs a native Java object from a GenericObject by reading stored field values and setting them via reflection. Requires the class to be on the classpath and field access via --add-opens flags.
     */
    private Object reconstructNativeObject(DODatabaseDelegate delegate, GenericObject obj) {
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            if (storedClass == null) {
                return null;
            }

            String className = storedClass.getName();
            Class<?> clazz = Class.forName(className);

            // Use Unsafe.allocateInstance to create without calling any constructor
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            Object nativeObj = unsafe.allocateInstance(clazz);

            // Copy stored field values via reflection
            StoredField[] fields = delegate.getAllFieldsIncludingAncestors(storedClass);
            for (StoredField sf : fields) {
                Object value = sf.get(obj);
                if (value != null) {
                    try {
                        java.lang.reflect.Field jf = findDeclaredField(clazz, sf.getName());
                        if (jf != null) {
                            jf.setAccessible(true);
                            jf.set(nativeObj, value);
                        }
                    } catch (Exception e) {
                        // Skip fields that can't be set
                    }
                }
            }

            return nativeObj;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds a declared field by name, searching up the class hierarchy.
     */
    private java.lang.reflect.Field findDeclaredField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Exports items from a standalone collection or map object being exported as a root-level element (e.g., in Extra.xml). Normal field export finds no meaningful schema fields for these types — their items are stored via DB4O translator fields, not as schema-defined fields.
     *
     * @return number of items exported
     */
    int exportStandaloneCollectionItems(DODatabaseDelegate delegate, Object obj, DOSchemaClass schemaClass, long objectId) throws IOException {
        // Activated maps (Hashtable, HashMap, etc.) become real Java Maps
        if (obj instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
            if (map.isEmpty())
                return 0;
            xmlWriter.openArray("items", null);
            try {
                int count = 0;
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    xmlWriter.openStructure("entry", null);
                    try {
                        exportCollectionItem(delegate, entry.getKey(), objectId);
                        exportCollectionItem(delegate, entry.getValue(), objectId);
                    } finally {
                        xmlWriter.closeStructure("entry");
                    }
                    count++;
                }
                return count;
            } finally {
                xmlWriter.closeArray("items");
            }
        }

        // Collections (Vector, ArrayList, HashSet, etc.) and GenericObjects wrapping them
        Collection<?> items = RecipeCollectionItems.getItems(delegate, obj);
        if (items != null && !items.isEmpty()) {
            xmlWriter.openArray("items", null);
            try {
                int count = 0;
                for (Object item : items) {
                    if (item != null) {
                        exportCollectionItem(delegate, item, objectId);
                        count++;
                    }
                }
                return count;
            } finally {
                xmlWriter.closeArray("items");
            }
        }

        return 0;
    }

    /**
     * Exports a single item from a standalone collection/map. Persistent DB4O objects are exported recursively; primitives are written as text elements.
     */
    private void exportCollectionItem(DODatabaseDelegate delegate, Object item, long parentObjectId) throws IOException {
        if (item == null)
            return;

        long itemId = delegate.getID(item);
        if (itemId > 0) {
            // Persistent DB4O object — export recursively as embedded
            ctxRef.objectExporter.exportObject(itemId, true);
        } else {
            // Primitive value — use XSD-friendly primitive type as element name
            String elementName = primitiveElementName(item);
            String value;
            if (item instanceof Date) {
                value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) item);
            } else if (item instanceof byte[]) {
                value = Base64.getEncoder().encodeToString((byte[]) item);
            } else {
                value = item.toString();
            }
            xmlWriter.elementWithContent(elementName, value, true);
        }
    }

    /**
     * Maps a Java primitive/wrapper object to an XSD-friendly element name.
     */
    private static String primitiveElementName(Object value) {
        if (value instanceof Integer)
            return "int";
        if (value instanceof Long)
            return "long";
        if (value instanceof Double)
            return "double";
        if (value instanceof Float)
            return "float";
        if (value instanceof Boolean)
            return "boolean";
        if (value instanceof Short)
            return "short";
        if (value instanceof Byte)
            return "byte";
        if (value instanceof String)
            return "string";
        if (value instanceof Date)
            return "dateTime";
        if (value instanceof byte[])
            return "base64Binary";
        return "string";
    }

    /**
     * Exports a field value (handles both primitives and object references).
     */
    private void exportFieldValue(DODatabaseDelegate delegate, Object fieldValue, DOSchemaField schemaField, int indentLevel, String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
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
            // IDEntite inside a collection or map value — the primary
            // field-level IDEntite handling lives in exportRegularField;
            // this path is reached only for standalone items (e.g. Vector
            // elements, Hashtable values).

            if (ctxRef.statistics != null) {
                long idEntiteObjectId = delegate.getID(fieldValue);
                if (idEntiteObjectId > 0) {
                    ctxRef.statistics.recordReachedOnly(fieldClass, idEntiteObjectId, operation.referenceSchema);
                }
            }

            Long mID = IDEntityHandler.extractMID(delegate, fieldValue);
            if (mID != null && shouldSkipField(mID, schemaField, operation.referenceSchema)) {
                return;
            }

            // Format handler hook (same as exportRegularField)
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

            if (isEmbedded) {
                ResolvedReference resolved = ReferenceUtil.resolveIDEntiteForExport(delegate, fieldValue, className, schemaField, operation.database);
                if (resolved == null) {
                    return;
                }
                if (ctxRef.statistics != null) {
                    ctxRef.statistics.recordRelationshipExported(parentObjectId, resolved.objectId, parentSourceClassName, sourceFieldName, "resolved IDEntite and traversed as embedded relationship");
                }
                DODatabaseDelegate savedDelegate = ctxRef.delegate;
                try {
                    ctxRef.delegate = resolved.delegate;
                    ctxRef.objectExporter.exportObject(resolved.objectId, true);
                } finally {
                    ctxRef.delegate = savedDelegate;
                }
            } else {
                long objectId = delegate.getID(fieldValue);
                if (ctxRef.statistics != null) {
                    ctxRef.statistics.recordRelationshipExported(parentObjectId, objectId, parentSourceClassName, sourceFieldName, "IDEntite exported as object reference (embedContents=false)");
                }
                ctxRef.objectExporter.exportObject(objectId, false);
            }
            return;
        }

        // For regular objects, check if they should be embedded or referenced
        long objectId = delegate.getID(fieldValue);

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
            classNameValue = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), classNameValue, schemaField);
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
            stringValue = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctxRef.basePath, ctxRef.schemaClass, schemaField, ctxRef.currentObject().obj), stringValue, schemaField);
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
        // For IDEntite wrapper objects, extract the numeric mID before checking
        // skip conditions. The raw GenericObject wrapper fails Number-based checks
        // like MINUS_ONE and DEFAULT (which tests mID == -1 for IDEntite fields).
        Object checkValue = value;
        if (value instanceof GenericObject && field != null && schema != null) {
            DOSchemaClass fieldClass = schema.findClassByName(ClassUtil.getClassName(value));
            if (fieldClass != null && fieldClass.isIDEntite()) {
                Long mID = IDEntityHandler.extractMID(ctxRef.delegate, value);
                checkValue = (mID != null) ? mID : null;
            }
        }
        return ValueUtil.shouldSkipField(checkValue, field, schema, operation.selectedSkipUserOptions, operation.applyUserSelectedFieldExclusions, operation.applySkipWhenConditions);
    }

    /**
     * Returns true when the schema type maps to an XSD primitive that does not accept empty string as valid content (everything except xs:string). Used to suppress empty self-closing elements for null values — the XSD already declares minOccurs="0" so omission is schema-valid.
     */
    private static boolean isStrictXsdPrimitiveType(String schemaType) {
        if (schemaType == null || schemaType.isEmpty()) {
            return false;
        }
        return switch (schemaType.toLowerCase()) {
        case "int", "java.lang.integer", "long", "java.lang.long", "boolean", "java.lang.boolean", "double", "java.lang.double", "float", "java.lang.float", "byte", "java.lang.byte", "short", "java.lang.short", "java.util.date", "date" -> true;
        default -> false;
        };
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
            long childObjectId = ctxRef.delegate.getID(fieldValue);
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
            long wrapperObjectId = ctxRef.delegate.getID(fieldValue);
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
    private void markDisabledFieldDescendantsReached(DODatabaseDelegate delegate, Object fieldValue, DOSchemaField schemaField) {
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
                    items = RecipeCollectionItems.getItems(delegate, fieldValue);
                }

                if (items != null) {
                    for (Object item : items) {
                        if (item != null) {
                            markObjectReachedRecursiveShallow(delegate, item, 2);
                        }
                    }
                }
                return;
            }

            // Case 2: Persistent GenericObject — mark its children as reached
            if (fieldValue instanceof GenericObject) {
                markObjectReachedRecursiveShallow(delegate, fieldValue, 2);
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
    private void markObjectReachedRecursiveShallow(DODatabaseDelegate delegate, Object obj, int maxDepth) {
        if (obj == null || ctxRef.statistics == null) {
            return;
        }

        try {
            long objectId = delegate.getID(obj);
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
                StoredClass storedClass = delegate.storedClass(obj);
                if (storedClass == null) {
                    return;
                }

                StoredField[] fields = delegate.getAllFieldsIncludingAncestors(storedClass);
                for (StoredField field : fields) {
                    try {
                        Object childValue = field.get(obj);
                        if (childValue == null) {
                            continue;
                        }

                        // Mark persistent child objects
                        long childId = delegate.getID(childValue);
                        if (childId > 0) {
                            markObjectReachedRecursiveShallow(delegate, childValue, maxDepth - 1);
                        }

                        // For collection children, mark items too
                        if (childValue instanceof Collection) {
                            for (Object item : (Collection<?>) childValue) {
                                if (item != null) {
                                    long itemId = delegate.getID(item);
                                    if (itemId > 0) {
                                        markObjectReachedRecursiveShallow(delegate, item, maxDepth - 1);
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
     * Sorts StoredField[] to match the XSD content model.
     * <p>
     * When the class has an exported direct parent (xs:extension in XSD), fields are sorted in inheritance-aware order: ancestor fields first (alphabetical within each level), then own fields.
     * <p>
     * When the direct parent is NOT exported (flat xs:sequence in XSD), all fields are sorted in flat alphabetical order by destination name, regardless of which ancestor declares them.
     */
    private StoredField[] sortFieldsByDestinationName(StoredField[] fields, DOSchemaClass parentClass, DOSchema schema) {
        boolean useDepthSort = hasExportedDirectParent(parentClass, schema);

        // Build depth map only when using inheritance-aware sort
        Map<String, Integer> fieldDepth = new HashMap<>();
        if (useDepthSort) {
            List<DOSchemaClass> chain = new ArrayList<>();
            DOSchemaClass current = parentClass;
            while (current != null) {
                chain.add(0, current);
                String ancestorName = current.attributes.parentClassName;
                if (ancestorName == null || ancestorName.isEmpty())
                    break;
                DOSchemaClass ancestor = schema.findClassByName(ancestorName);
                if (ancestor == null)
                    break;
                current = ancestor;
            }
            for (int depth = 0; depth < chain.size(); depth++) {
                DOSchemaClass cls = chain.get(depth);
                if (cls.fields != null) {
                    for (DOSchemaField f : cls.fields) {
                        if (!fieldDepth.containsKey(f.attributes.source)) {
                            fieldDepth.put(f.attributes.source, depth);
                        }
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

            if (useDepthSort) {
                // Sort by hierarchy depth first (ancestor fields before own)
                int depthA = fieldDepth.getOrDefault(a.getName(), Integer.MAX_VALUE);
                int depthB = fieldDepth.getOrDefault(b.getName(), Integer.MAX_VALUE);
                if (depthA != depthB)
                    return Integer.compare(depthA, depthB);
            }

            // Alphabetical by destination name
            return sfA.attributes.destinationName.compareTo(sfB.attributes.destinationName);
        });
        return sorted;
    }

    /**
     * Checks if the class has an exported direct parent, matching the XSD logic in {@code XSDContext.getExportedParent()}.
     */
    private boolean hasExportedDirectParent(DOSchemaClass schemaClass, DOSchema schema) {
        if (schemaClass.attributes.parentClassName == null || schemaClass.attributes.parentClassName.isEmpty())
            return false;
        DOSchemaClass parent = schema.findClassByName(schemaClass.attributes.parentClassName);
        if (parent == null || !parent.attributes.migrate)
            return false;
        // When the child class redefines an ancestor field with a different type,
        // xs:extension cannot re-declare that field. The XSD writer falls back to
        // a flat alphabetical sequence in that case, so the export engine must use
        // the same flat alphabetical sort to keep XML and XSD in sync.
        return !hasTypeOverrideFields(schemaClass, schema);
    }

    /**
     * Returns true when the class declares an own field whose source name matches an ancestor field but with a different schema type string. This is the same condition that causes {@code XSDContext} to emit a flat sequence instead of an {@code xs:extension}, so both systems must agree on whether to use inheritance-aware (depth-first) or flat (alphabetical) field ordering.
     */
    private boolean hasTypeOverrideFields(DOSchemaClass schemaClass, DOSchema schema) {
        // Collect ancestor fields: source name → type string
        Map<String, String> ancestorTypes = new java.util.HashMap<>();
        String parentName = schemaClass.attributes.parentClassName;
        while (parentName != null && !parentName.isEmpty()) {
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            if (parent.fields != null) {
                for (DOSchemaField f : parent.fields) {
                    if (f.attributes.isExported && f.attributes.type != null && !f.attributes.type.isEmpty()) {
                        ancestorTypes.putIfAbsent(f.attributes.source, f.attributes.type);
                    }
                }
            }
            parentName = parent.attributes.parentClassName;
        }
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (!f.attributes.isExported || f.attributes.type == null || f.attributes.type.isEmpty())
                    continue;
                String ancestorType = ancestorTypes.get(f.attributes.source);
                if (ancestorType != null && !ancestorType.equals(f.attributes.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFieldTypeCollection(DOSchemaField schemaField) {
        DOSchemaClass typeClass = operation.referenceSchema.findClassByName(schemaField.attributes.type);
        return typeClass != null ? typeClass.isCollection() : CollectionTypeUtil.isCollectionType(schemaField.attributes.type);
    }

    private boolean isFieldTypeMap(DOSchemaField schemaField) {
        DOSchemaClass typeClass = operation.referenceSchema.findClassByName(schemaField.attributes.type);
        return typeClass != null ? typeClass.isMap() : CollectionTypeUtil.isMapType(schemaField.attributes.type);
    }
}
