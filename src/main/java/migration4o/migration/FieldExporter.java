package migration4o.migration;

import java.io.IOException;
import java.util.Collection;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.recipes.ArrayTraverser;
import migration4o.migration.recipes.CollectionExtractor;
import migration4o.migration.recipes.FieldValueMapper;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.migration.recipes.IDReferenceDetector;
import migration4o.migration.recipes.IDReferenceExporter;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ClassUtil;
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
     * @throws IOException if XML writing fails
     */
    public void exportAllFields(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass,
            int indentLevel, String destinationClassName, String sourceClassName, long parentObjectId)
            throws IOException {
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return;
            }

            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String sourceFieldName = field.getName();

                    // Get destination field name from schema
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByName(parentClass, sourceFieldName);
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
                        continue;
                    }

                    // Check schema flag first - DB4O collections may not be Java Collection
                    // instances
                    // CRITICAL: Always use schema-driven extraction for DB4O objects, even if they
                    // implement Collection interface, because calling .size() on GenericObject
                    // proxies
                    // may return incorrect values before proper activation and extraction
                    if (schemaField != null && schemaField.isCollection) {
                        exportSchemaCollectionField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        exportCollectionField(container, (Collection<?>) fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                    } else if (fieldValue.getClass().isArray()) {
                        exportArrayField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                    } else {
                        exportRegularField(container, fieldValue, schemaField, indentLevel,
                                destinationClassName, sourceClassName, parentObjectId);
                    }
                } catch (Exception e) {
                    // Error exporting field - silently skip
                }
            }
        } catch (Exception e) {
            // Error accessing fields
        }
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

        // Try to extract items from the collection object
        // VectRechID extends HVector which extends Vector, so look for Vector's
        // internal array
        Collection<?> items = CollectionExtractor.extractAndActivate(container, collectionObj);

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

            xmlWriter.writeStartElement(fieldName, indentLevel);
            exportFieldValue(container, fieldValue, schemaField, indentLevel + 1,
                    parentClassName, parentSourceClassName, parentObjectId);
            xmlWriter.writeEndElement(fieldName, indentLevel);
        } else {
            // Primitive or non-persistent value - write inline
            String stringValue = fieldValue.toString();

            // Skip this field if skip conditions are met
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
        if (objectId > 0) {
            operation.objectExporter.exportObjectRecursively(container, objectId, indentLevel, isEmbedded, fieldName,
                    parentClassName, sourceFieldName, parentSourceClassName, false, parentObjectId);
        } else {
            // Primitive value - write inline
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
        }
    }
}
