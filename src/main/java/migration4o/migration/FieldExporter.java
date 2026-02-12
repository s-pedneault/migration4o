package migration4o.migration;

import java.io.IOException;
import java.util.Collection;

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
import migration4o.recipes.RecipeCollectionActivation;
import migration4o.util.ClassUtil;
import migration4o.util.CollectionUtil;
import migration4o.util.DatabaseUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.ValueUtil;

/**
 * Handles field-level export operations.
 * Responsible for exporting all fields of an object, handling arrays,
 * collections, and references.
 */
public class FieldExporter {
    private final ExportOperation operation;
    private final XMLWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    private final ReferenceObjectExporter idEntiteResolver;

    public FieldExporter(ExportOperation operation, XMLWriter xmlWriter, XSDBuilder xsdBuilder) {
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
    public int countFieldsToExport(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass,
            DOSchema schema) {
        int count = 0;
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the immediate
            // class
            StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get schema field from current class AND ancestors
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass,
                            sourceFieldName, schema);

                    // Skip fields marked as not exported
                    if (schemaField != null && !schemaField.isExported) {
                        continue;
                    }

                    // Skip null fields if they meet skip conditions
                    if (fieldValue == null) {
                        if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
                            continue;
                        }
                        // Null field that will be exported
                        count++;
                        continue;
                    }

                    // Check if this would be skipped based on value and schema settings
                    // For collections and arrays, they're always exported if not null (size
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
                        boolean isClassField = schemaField != null &&
                                (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

                        if (isClassField) {
                            // Extract className for skip check
                            String className;
                            if (fieldValue instanceof Class<?>) {
                                className = ((Class<?>) fieldValue).getName();
                            } else {
                                String str = fieldValue.toString();
                                className = str.startsWith("class ") ? str.substring(6) : str;
                            }

                            if (!ValueUtil.shouldSkipField(className, schemaField, schema)) {
                                count++;
                            }
                        } else {
                            // Regular object - check skip conditions
                            if (!ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
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
    public int exportAllFields(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass,
            int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId)
            throws IOException {
        int fieldsWritten = 0;
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return 0;
            }

            // CRITICAL FIX: Get fields from ALL ancestor classes, not just the immediate
            // class
            StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // CRITICAL: Get destination field name from schema (search ancestors too)
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(parentClass,
                            sourceFieldName, operation.referenceSchema);
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
                        if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                            continue;
                        }
                        xmlWriter.writeIndent(indentLevel);
                        xmlWriter.write("<" + fieldName + "/>\n");
                        fieldsWritten++;
                        continue;
                    }

                    // Check schema flag first - DB4O collections may not be Java Collection
                    // instances
                    // CRITICAL: Always use schema-driven extraction for DB4O objects, even if they
                    // implement Collection interface, because calling .size() on GenericObject
                    // proxies
                    // may return incorrect values before proper activation and extraction
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
                        exportSchemaCollectionField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        exportCollectionField(container, (Collection<?>) fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    } else if (fieldValue.getClass().isArray()) {
                        exportArrayField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    } else {
                        exportRegularField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                        fieldsWritten++;
                    }
                } catch (Exception e) {
                    // Skip fields that cause errors during export
                }
            }
        } catch (Exception e) {
            // Error accessing fields
        }
        return fieldsWritten;
    }

    /**
     * Exports a collection field that is marked as collection in the schema
     * but may be stored as a DB4O persistent object (like VectRechID).
     * This method extracts the collection items from the DB4O object structure.
     */
    private void exportSchemaCollectionField(ExtObjectContainer container, Object collectionObj,
            DOSchemaField schemaField, int indentLevel,
            String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField.destinationName;

        // DEBUG: Only log for specific field we're investigating
        if ("listeActionCondition".equals(fieldName) && "TypeActivHoraire".equals(parentClassName)) {
            System.err.println("DEBUG: exportSchemaCollectionField for TypeActivHoraire.listeActionCondition");
            System.err.println(
                    "  collectionObj type=" + (collectionObj != null ? collectionObj.getClass().getName() : "null"));
            System.err.println(
                    "  collectionObj instanceof Collection? " + (collectionObj instanceof java.util.Collection));
            if (collectionObj instanceof java.util.Collection) {
                System.err.println("  direct size=" + ((java.util.Collection<?>) collectionObj).size());
            }
        }

        // Try to extract items from the collection object
        // VectRechID extends HVector which extends Vector, so look for Vector's
        // internal array
        // NOTE: Parent object was already activated at max depth, so collection should
        // be activated too
        Collection<?> items = RecipeCollectionActivation.getItems(container, collectionObj);

        if ("listeActionCondition".equals(fieldName) && "TypeActivHoraire".equals(parentClassName)) {
            System.err.println("  extracted items: " + (items != null ? items.size() : "null"));
        }

        // Items extracted (or null/empty)

        if (items == null || items.isEmpty()) {
            // Check skip conditions
            if (ValueUtil.shouldSkipField(items, schemaField, operation.referenceSchema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int size = items.size();
            xmlWriter.writeStartElementWithSize(fieldName, size, indentLevel);

            // Check if we should export ID references instead of entities
            IDReferenceDetector.DetectionResult detection = IDReferenceDetector.detectIDReference(schemaField,
                    operation.referenceSchema);

            for (Object item : items) {
                if (item != null) {
                    if (detection.shouldExportAsIDReferences) {
                        // Export as ID reference
                        IDReferenceExporter.exportAsIDReference(container, item, detection.idClass, xmlWriter,
                                xsdBuilder,
                                indentLevel + 1, operation);
                    } else {
                        // Export normally
                        exportFieldValue(container, item, schemaField, indentLevel + 1,
                                parentClassName, parentSourceClassName, parentObjectId);
                    }
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportCollectionField(ExtObjectContainer container, Collection<?> collection,
            DOSchemaField schemaField, int indentLevel,
            String parentClassName, String parentSourceClassName, long parentObjectId) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        if (ValueUtil.isEmpty(collection, schemaField, operation.referenceSchema)) {
            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(collection, schemaField, operation.referenceSchema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int size = collection.size();
            xmlWriter.writeStartElementWithSize(fieldName, size, indentLevel);

            // Check if we should export ID references instead of entities
            IDReferenceDetector.DetectionResult detection = IDReferenceDetector.detectIDReference(schemaField,
                    operation.referenceSchema);

            for (Object item : collection) {
                if (item != null) {
                    if (detection.shouldExportAsIDReferences) {
                        // Export as ID reference
                        IDReferenceExporter.exportAsIDReference(container, item, detection.idClass, xmlWriter,
                                xsdBuilder,
                                indentLevel + 1, operation);
                    } else {
                        // Export normally
                        exportFieldValue(container, item, schemaField, indentLevel + 1,
                                parentClassName, parentSourceClassName, parentObjectId);
                    }
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportArrayField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            int indentLevel, String parentClassName, String parentSourceClassName,
            long parentObjectId) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        if (ValueUtil.isEmpty(fieldValue, schemaField, operation.referenceSchema)) {
            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int length = ArrayTraverser.getLength(fieldValue);
            xmlWriter.writeStartElementWithSize(fieldName, length, indentLevel);
            for (int i = 0; i < length; i++) {
                Object item = ArrayTraverser.getItem(fieldValue, i);
                if (item != null) {
                    exportFieldValue(container, item, schemaField, indentLevel + 1,
                            parentClassName, parentSourceClassName, parentObjectId);
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportRegularField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            int indentLevel, String parentClassName, String parentSourceClassName,
            long parentObjectId) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";

        // Check if this is a Class-typed field based on schema (DB4O may wrap Class in
        // GenericObject)
        boolean isClassField = schemaField != null &&
                (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

        // Special handling for Class objects - always export as string
        if (isClassField) {
            String className;
            if (fieldValue instanceof Class<?>) {
                className = ((Class<?>) fieldValue).getName();
            } else {
                // DB4O wrapped it - extract via toString which gives "class java.lang.String"
                String str = fieldValue.toString();
                if (str.startsWith("class ")) {
                    className = str.substring(6); // Remove "class " prefix
                } else {
                    className = str;
                }
            }

            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }

            xmlWriter.writeElement(fieldName, className, indentLevel);
            return;
        }

        long refId = container.ext().getID(fieldValue);
        if (refId > 0) {
            // This is a persistent object reference

            // Check if field should be skipped (includes IDEntite with mID == -1)
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }

            // Additional check for IDEntite objects - check if they'll be filtered by
            // resolveAndExport
            String className = ClassUtil.getClassName(fieldValue);
            DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, operation.referenceSchema);
            if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
                // Check if this IDEntite will be skipped due to mID == -1
                if (schemaField != null && schemaField.skipWhen != null && !schemaField.skipWhen.isEmpty()) {
                    if (IDEntityHandler.shouldSkipMinusOne(container, fieldValue)
                            && schemaField.skipWhen.contains("MINUS_ONE")) {
                        // This field will produce empty content, skip it entirely
                        return;
                    }
                }

                // For non-embedded IDEntite references, export as simple ID value
                // instead of nested structure
                if (schemaField != null && !schemaField.embedContents) {
                    Long mID = IDEntityHandler.extractMID(container, fieldValue);
                    if (mID != null) {
                        xmlWriter.writeElement(fieldName, mID.toString(), indentLevel);
                        return;
                    } else {
                        // mID is null - skip this field to avoid empty tags
                        return;
                    }
                }
            }

            // Before writing field wrapper tags, check if the referenced object has any
            // fields to export
            // (reuse className and fieldClass from above)

            // Count fields that will be exported from this object
            if (fieldValue instanceof GenericObject && fieldClass != null) {
                int fieldsToExport = countFieldsToExport(container, (GenericObject) fieldValue,
                        fieldClass, operation.referenceSchema);

                // If no fields will be exported, skip this field wrapper entirely
                if (fieldsToExport == 0) {
                    return;
                }
            }

            // Write element and export recursively
            xmlWriter.writeStartElement(fieldName, indentLevel);
            exportFieldValue(container, fieldValue, schemaField, indentLevel + 1,
                    parentClassName, parentSourceClassName, parentObjectId);
            xmlWriter.writeEndElement(fieldName, indentLevel);
        } else {
            // Primitive or non-persistent value - write inline
            String stringValue = fieldValue.toString();
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, operation.referenceSchema)) {
                return;
            }

            // Apply value mapping if defined for this field
            stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);

            xmlWriter.writeElement(fieldName, stringValue, indentLevel);
        }
    }

    /**
     * Exports a field value (handles both primitives and object references).
     */
    private void exportFieldValue(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            int indentLevel, String parentClassName, String parentSourceClassName,
            long parentObjectId) throws IOException {
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
        // - IDEntite objects: only embed if explicitly set to embedContents=true
        // (default is false for references)
        // - Regular objects: always embed unless explicitly set to embedContents=false
        // (default is true for value objects)
        boolean isEmbedded;
        if (schemaField != null) {
            if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
                // IDEntite: default to non-embedded (reference by ID), but allow explicit
                // embedContents=true
                isEmbedded = schemaField.embedContents;
            } else {
                // Regular objects: default to embedded (inline content), unless explicitly
                // embedContents=false
                // This prevents value objects like Adresse, Qte, etc. from being deduplicated
                isEmbedded = true; // Always embed non-IDEntite objects
            }
        } else {
            // No schema field - default to embedded for safety
            isEmbedded = true;
        }

        if (fieldClass != null && fieldClass.isIDEntite(operation.databaseSchema)) {
            // Track the referenced entity class if this is a non-embedded reference
            if (operation.referencedClassTracker != null && schemaField != null && !schemaField.embedContents) {
                // Use the pointsTo field to find what entity class this IDEntite references
                if (fieldClass.pointsTo != null && !fieldClass.pointsTo.isEmpty()) {
                    operation.referencedClassTracker.registerReferencedClass(fieldClass.pointsTo);
                }
            }

            // Pass through the embedded flag and field name for tracking
            idEntiteResolver.resolveAndExport(container, fieldValue, className, schemaField,
                    (objectId, indent) -> operation.objectExporter.exportObjectRecursively(container, objectId, indent,
                            isEmbedded, fieldName, parentClassName, sourceFieldName, parentSourceClassName, false,
                            parentObjectId),
                    indentLevel);
            return;
        }

        // For regular objects, check if they should be embedded or referenced
        long objectId = container.ext().getID(fieldValue);

        // Special handling for Class objects based on schema type
        boolean isClassField = schemaField != null &&
                (schemaField.type.equals("java.lang.Class") || schemaField.type.equals("Class"));

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
            xmlWriter.writeElement(fieldName, classNameValue, indentLevel);
            return;
        }

        if (objectId > 0) {
            operation.objectExporter.exportObjectRecursively(container, objectId, indentLevel, isEmbedded, fieldName,
                    parentClassName, sourceFieldName, parentSourceClassName, false, parentObjectId);
        } else {
            // Primitive value - write inline
            String stringValue;
            if (fieldValue instanceof Class<?>) {
                stringValue = ((Class<?>) fieldValue).getName();
            } else {
                stringValue = fieldValue.toString();
            }
            xmlWriter.writeElement(fieldName, stringValue, indentLevel);
        }
    }
}
