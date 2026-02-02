package migration4o.engine.export;

import java.io.IOException;
import java.util.Collection;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ClassUtil;
import migration4o.util.DatabaseUtil;
import migration4o.util.ObjectResolverUtil;
import migration4o.util.ReferenceUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.ValueUtil;

/**
 * Handles field-level export operations.
 * Responsible for exporting all fields of an object, handling arrays,
 * collections, and references.
 */
public class FieldExporter {
    private final DOSchema schema;
    private final DOSchema databaseSchema;
    private final XMLWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    private final ReferenceObjectExporter idEntiteResolver;
    private ReferencedClassTracker referencedClassTracker;

    public FieldExporter(DOSchema schema, DOSchema databaseSchema, XMLWriter xmlWriter, XSDBuilder xsdBuilder,
            ReferenceObjectExporter idEntiteResolver) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.idEntiteResolver = idEntiteResolver;
    }

    /**
     * Sets the reference tracker for tracking referenced classes.
     */
    public void setReferencedClassTracker(ReferencedClassTracker tracker) {
        this.referencedClassTracker = tracker;
    }

    /**
     * Exports all fields of a GenericObject.
     */
    public void exportAllFields(ExtObjectContainer container, GenericObject obj, DOSchemaClass parentClass,
            int indentLevel, ObjectExportDelegate objectExportDelegate) throws IOException {
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
                        if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
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
                        exportSchemaCollectionField(container, fieldValue, schemaField, parentClass, indentLevel,
                                objectExportDelegate);
                    } else if (!(fieldValue instanceof GenericObject) && fieldValue instanceof Collection) {
                        exportCollectionField(container, (Collection<?>) fieldValue, schemaField, parentClass,
                                indentLevel, objectExportDelegate);
                    } else if (fieldValue.getClass().isArray()) {
                        exportArrayField(container, fieldValue, schemaField, parentClass, indentLevel,
                                objectExportDelegate);
                    } else {
                        exportRegularField(container, fieldValue, schemaField, parentClass, indentLevel,
                                objectExportDelegate);
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
            DOSchemaField schemaField, DOSchemaClass parentClass, int indentLevel,
            ObjectExportDelegate objectExportDelegate) throws IOException {
        String fieldName = schemaField.destinationName;
        String collectionType = schemaField.type != null ? schemaField.type : "unknown";

        // Activate the collection object to access its fields
        long collectionId = container.ext().getID(collectionObj);
        if (collectionId > 0) {
            ObjectResolverUtil.activateObject(container, collectionObj, collectionId);
        }

        // Try to extract items from the collection object
        // VectRechID extends HVector which extends Vector, so look for Vector's
        // internal array
        Collection<?> items = extractCollectionItems(container, collectionObj);

        // Items extracted (or null/empty)

        if (items == null || items.isEmpty()) {
            // Check skip conditions
            if (ValueUtil.shouldSkipField(items, schemaField, schema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int size = items.size();
            xmlWriter.writeStartElementWithSize(fieldName, size, indentLevel);

            // Check if we should export ID references instead of entities
            boolean shouldExportAsIDReferences = false;
            DOSchemaClass idClass = null;

            if (schemaField != null && !schemaField.embedContents && schemaField.childrenType != null) {
                // Find the corresponding ID class for this entity type
                idClass = findIDClassForEntity(schemaField.childrenType);
                shouldExportAsIDReferences = (idClass != null);
            }

            for (Object item : items) {
                if (item != null) {
                    if (shouldExportAsIDReferences) {
                        // Export as ID reference
                        exportAsIDReference(container, item, idClass, indentLevel + 1, objectExportDelegate);
                    } else {
                        // Export normally
                        exportFieldValue(container, item, schemaField, parentClass, indentLevel + 1,
                                objectExportDelegate);
                    }
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    /**
     * Extracts collection items from a DB4O persistent collection object.
     * Inspects the actual DB4O structure to understand how the data is stored.
     */
    private Collection<?> extractCollectionItems(ExtObjectContainer container, Object collectionObj) {
        if (collectionObj == null) {
            return null;
        }

        // If it's already a Java Collection, return it directly
        if (collectionObj instanceof Collection) {
            return (Collection<?>) collectionObj;
        }

        // For DB4O GenericObject, we need to inspect its structure
        if (!(collectionObj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) collectionObj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        String className = storedClass.getName();

        // Traverse the entire class hierarchy to find collection data
        // DB4O stores Vector data in translator fields like
        // "com.db4o.config.TCollection"
        StoredClass currentClass = storedClass;
        while (currentClass != null) {
            StoredField[] fields = currentClass.getStoredFields();

            for (StoredField field : fields) {
                String fieldName = field.getName();

                // DB4O translators start with "com.db4o.config.T"
                // TCollection is used for Vector and other collections
                if (fieldName.startsWith("com.db4o.config.T")) {
                    try {
                        Object value = field.get(genericObj);
                        if (value != null && value.getClass().isArray()) {
                            // This is the collection data array
                            java.util.List<Object> list = new java.util.ArrayList<>();
                            int length = java.lang.reflect.Array.getLength(value);
                            int nullCount = 0;

                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(value, i);
                                if (item != null) {
                                    list.add(item);
                                } else {
                                    nullCount++;
                                }
                            }

                            return list;
                        }
                    } catch (Exception e) {
                        // Failed to extract from DB4O translator field
                    }
                }
            }

            currentClass = currentClass.getParentStoredClass();
        }

        return null;
    }

    private void exportCollectionField(ExtObjectContainer container, Collection<?> collection,
            DOSchemaField schemaField, DOSchemaClass parentClass, int indentLevel,
            ObjectExportDelegate objectExportDelegate) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        if (ValueUtil.isEmpty(collection, schemaField, schema)) {
            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(collection, schemaField, schema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int size = collection.size();
            xmlWriter.writeStartElementWithSize(fieldName, size, indentLevel);

            // Check if we should export ID references instead of entities
            boolean shouldExportAsIDReferences = false;
            DOSchemaClass idClass = null;

            if (schemaField != null && !schemaField.embedContents && schemaField.childrenType != null) {
                // Find the corresponding ID class for this entity type
                idClass = findIDClassForEntity(schemaField.childrenType);
                shouldExportAsIDReferences = (idClass != null);
            }

            for (Object item : collection) {
                if (item != null) {
                    if (shouldExportAsIDReferences) {
                        // Export as ID reference
                        exportAsIDReference(container, item, idClass, indentLevel + 1, objectExportDelegate);
                    } else {
                        // Export normally
                        exportFieldValue(container, item, schemaField, parentClass, indentLevel + 1,
                                objectExportDelegate);
                    }
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportArrayField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            DOSchemaClass parentClass, int indentLevel, ObjectExportDelegate objectExportDelegate)
            throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        if (ValueUtil.isEmpty(fieldValue, schemaField, schema)) {
            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
                return;
            }
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + " size=\"0\"/>\n");
        } else {
            int length = java.lang.reflect.Array.getLength(fieldValue);
            xmlWriter.writeStartElementWithSize(fieldName, length, indentLevel);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(fieldValue, i);
                if (item != null) {
                    exportFieldValue(container, item, schemaField, parentClass, indentLevel + 1,
                            objectExportDelegate);
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportRegularField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            DOSchemaClass parentClass, int indentLevel, ObjectExportDelegate objectExportDelegate)
            throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        long refId = container.ext().getID(fieldValue);
        if (refId > 0) {
            // This is a persistent object reference

            // Check if field should be skipped (includes IDEntite with mID == -1)
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
                return;
            }

            // Additional check for IDEntite objects - check if they'll be filtered by
            // resolveAndExport
            String className = ClassUtil.getClassName(fieldValue);
            DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, schema);
            if (fieldClass != null && fieldClass.isIDEntite(databaseSchema)) {
                // Check if this IDEntite will be skipped due to mID == -1
                if (schemaField != null && schemaField.skipWhen != null && !schemaField.skipWhen.isEmpty()) {
                    Long mID = migration4o.util.ReferenceUtil.extractMIDField(container, fieldValue);
                    if (mID != null && mID == -1 && schemaField.skipWhen.contains("MINUS_ONE")) {
                        // This field will produce empty content, skip it entirely
                        return;
                    }
                }
            }

            xmlWriter.writeStartElement(fieldName, indentLevel);
            exportFieldValue(container, fieldValue, schemaField, parentClass, indentLevel + 1,
                    objectExportDelegate);
            xmlWriter.writeEndElement(fieldName, indentLevel);
        } else {
            // Primitive or non-persistent value - write inline
            String stringValue = fieldValue.toString();

            // Skip this field if skip conditions are met
            if (ValueUtil.shouldSkipField(fieldValue, schemaField, schema)) {
                return;
            }

            xmlWriter.writeElement(fieldName, stringValue, indentLevel);
        }
    }

    /**
     * Exports a field value (handles both primitives and object references).
     */
    private void exportFieldValue(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            DOSchemaClass parentClass, int indentLevel, ObjectExportDelegate objectExportDelegate)
            throws IOException {
        if (fieldValue == null) {
            return;
        }

        String className = ClassUtil.getClassName(fieldValue);

        // Check if this field is marked for embedding (applies to all object types)
        boolean isEmbedded = schemaField != null && schemaField.embedContents;
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        String sourceFieldName = schemaField != null ? schemaField.source : null;

        // Check if this is an IDEntite reference (reference object pattern)
        DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, schema);
        if (fieldClass != null && fieldClass.isIDEntite(databaseSchema)) {
            // Track the referenced entity class if this is a non-embedded reference
            if (referencedClassTracker != null && schemaField != null && !schemaField.embedContents) {
                // Use the pointsTo field to find what entity class this IDEntite references
                if (fieldClass.pointsTo != null && !fieldClass.pointsTo.isEmpty()) {
                    referencedClassTracker.registerReferencedClass(fieldClass.pointsTo);
                }
            }

            // Pass through the embedded flag and field name for tracking
            idEntiteResolver.resolveAndExport(container, fieldValue, className, schemaField,
                    (objectId, indent) -> objectExportDelegate.exportObject(objectId, indent, isEmbedded, fieldName,
                            sourceFieldName),
                    indentLevel);
            return;
        }

        // For regular objects, check if they should be embedded or referenced
        long objectId = container.ext().getID(fieldValue);
        if (objectId > 0) {
            objectExportDelegate.exportObject(objectId, indentLevel, isEmbedded, fieldName, sourceFieldName);
        } else {
            // Primitive value - write inline
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
        }
    }

    /**
     * Delegate interface for exporting objects.
     */
    public interface ObjectExportDelegate {
        void exportObject(long objectId, int indentLevel, boolean isEmbedded, String fieldName,
                String sourceFieldName) throws IOException;
    }

    /**
     * Finds the ID class (e.g., IDCompartiment) for a given entity class (e.g.,
     * Compartiment).
     * Searches schema for classes where pointsTo equals the given entity class
     * name.
     */
    private DOSchemaClass findIDClassForEntity(String entityClassName) {
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.pointsTo != null && schemaClass.pointsTo.equals(entityClassName)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Exports an entity object as an ID reference.
     * Creates a synthetic ID object (e.g., IDCompartiment) with the mID field set
     * to the entity's DB ID.
     * Also ensures the actual entity object is exported separately at the top
     * level.
     */
    private void exportAsIDReference(ExtObjectContainer container, Object entity, DOSchemaClass idClass,
            int indentLevel, ObjectExportDelegate objectExportDelegate) throws IOException {
        // Get the DB object ID of the entity
        long entityObjectId = container.ext().getID(entity);

        if (entityObjectId <= 0) {
            return;
        }

        // Export the ID object wrapper
        String idClassName = idClass.source; // e.g., "IDCompartiment"
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);

        // XSD: Register the ID class and its fields
        xsdBuilder.addClass(idClass);
        for (DOSchemaField field : idClass.fields) {
            xsdBuilder.addField(idClass, field);
        }

        xmlWriter.writeStartElement(simpleClassName, indentLevel);

        // Find the mID field in the ID class schema
        DOSchemaField idField = null;
        for (DOSchemaField field : idClass.fields) {
            if ("mID".equals(field.source)) {
                idField = field;
                break;
            }
        }

        if (idField != null) {
            // Export the ID value
            xmlWriter.writeElement(idField.destinationName, String.valueOf(entityObjectId), indentLevel + 1);
        }

        xmlWriter.writeEndElement(simpleClassName, indentLevel);

        // Ensure the actual entity object gets exported separately (not embedded)
        // Delegate to export the entity at top level
        objectExportDelegate.exportObject(entityObjectId, indentLevel, false, null, null);
    }
}
