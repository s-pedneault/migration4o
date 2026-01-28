package migration4o.engine.export;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ObjectResolverUtil;

/**
 * Handles recursive object traversal and export to XML.
 * Responsible for reading objects from the database and delegating to XMLWriter
 * for output.
 */
public class ObjectExporter {
    private final DOSchema schema;
    private final DOSchema databaseSchema;
    private final XMLWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    private final ExportStatistics statistics;
    private final Set<Long> exportedObjectIds = new HashSet<>();

    public ObjectExporter(DOSchema schema, DOSchema databaseSchema, XMLWriter xmlWriter,
            XSDBuilder xsdBuilder, ExportStatistics statistics) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.statistics = statistics;
    }

    /**
     * Resets the state for a new export operation.
     */
    public void reset() {
        exportedObjectIds.clear();
    }

    /**
     * Recursively exports an object and all its referenced objects.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel)
            throws IOException {
        // Avoid exporting the same object twice
        if (!exportedObjectIds.add(objectId)) {
            return;
        }

        statistics.incrementAttempted();
        String className = null;
        try {
            // Get and activate the object
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return;
            }

            className = getClassName(obj);
            ObjectResolverUtil.activateObject(container, obj, objectId);

            // Write object opening tag using destination class name as element name
            DOSchemaClass schemaClass = findClassByName(className);
            String elementName = schemaClass != null ? schemaClass.destinationName
                    : XMLWriter.getSimpleClassName(className);
            xmlWriter.writeStartElement(elementName, indentLevel);

            // XSD: record this class structure
            if (schemaClass != null) {
                xsdBuilder.addClass(schemaClass);
            }

            // If it's a GenericObject, export all its fields
            if (obj instanceof GenericObject) {
                GenericObject genericObj = (GenericObject) obj;
                StoredClass storedClass = container.ext().storedClass(genericObj);
                if (storedClass != null) {
                    exportAllFields(container, genericObj, className, indentLevel + 1);
                }
            }

            // Write object closing tag
            xmlWriter.writeEndElement(elementName, indentLevel);
            statistics.incrementSucceeded();
            statistics.recordClassExport(className);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            statistics.addError(objectId, className, errorMsg, e);
            // Still write error marker in XML for debugging
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<!-- ERROR exporting object " + objectId + ": "
                    + XMLWriter.xmlEscape(errorMsg) + " -->\n");
        }
    }

    /**
     * Exports all fields of a GenericObject, following references recursively.
     */
    private void exportAllFields(ExtObjectContainer container, GenericObject obj, String parentClassName,
            int indentLevel) throws IOException {
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
                    DOSchemaField schemaField = findSchemaField(parentClassName, sourceFieldName);
                    String fieldName = schemaField != null ? schemaField.destinationName : sourceFieldName;

                    // XSD: record field type
                    if (schemaField != null) {
                        xsdBuilder.addField(parentClassName, schemaField);
                    }

                    if (fieldValue == null) {
                        xmlWriter.writeIndent(indentLevel);
                        xmlWriter.write("<" + fieldName + "/>\n");
                        continue;
                    }

                    // Handle collections
                    if (fieldValue instanceof Collection) {
                        Collection<?> collection = (Collection<?>) fieldValue;
                        if (collection.isEmpty()) {
                            xmlWriter.writeIndent(indentLevel);
                            xmlWriter.write("<" + fieldName + "/>\n");
                        } else {
                            xmlWriter.writeStartElement(fieldName, indentLevel);
                            for (Object item : collection) {
                                if (item != null) {
                                    exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                                }
                            }
                            xmlWriter.writeEndElement(fieldName, indentLevel);
                        }
                    } else if (fieldValue.getClass().isArray()) {
                        exportArrayField(container, fieldValue, fieldName, parentClassName, indentLevel);
                    } else {
                        exportRegularField(container, fieldValue, fieldName, parentClassName, indentLevel);
                    }
                } catch (Exception e) {
                    System.err.println("Error exporting field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing fields: " + e.getMessage());
        }
    }

    private void exportArrayField(ExtObjectContainer container, Object fieldValue, String fieldName,
            String parentClassName, int indentLevel) throws IOException {
        int length = java.lang.reflect.Array.getLength(fieldValue);
        if (length == 0) {
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + "/>\n");
        } else {
            xmlWriter.writeStartElement(fieldName, indentLevel);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(fieldValue, i);
                if (item != null) {
                    exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportRegularField(ExtObjectContainer container, Object fieldValue, String fieldName,
            String parentClassName, int indentLevel) throws IOException {
        long refId = container.ext().getID(fieldValue);
        if (refId > 0) {
            // This is a persistent object reference
            xmlWriter.writeStartElement(fieldName, indentLevel);
            exportFieldValue(container, fieldValue, fieldName, parentClassName, indentLevel + 1);
            xmlWriter.writeEndElement(fieldName, indentLevel);
        } else {
            // Primitive or non-persistent value - write inline
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
        }
    }

    /**
     * Exports a field value (handles both primitives and object references).
     */
    private void exportFieldValue(ExtObjectContainer container, Object fieldValue, String fieldName,
            String parentClassName, int indentLevel) throws IOException {
        if (fieldValue == null) {
            return;
        }

        String className = getClassName(fieldValue);

        // Check if this is an IDEntite reference (reference object pattern)
        if (isDescendantOf(findClassByName(className), "gest.gen.IDEntite")) {
            handleIDEntiteReference(container, fieldValue, className, fieldName, parentClassName, indentLevel);
            return;
        }

        // For regular objects, just export them recursively
        long objectId = container.ext().getID(fieldValue);
        if (objectId > 0) {
            exportObjectRecursively(container, objectId, indentLevel);
        } else {
            // Primitive value - write inline
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
        }
    }

    /**
     * Handles special IDEntite reference pattern.
     */
    private void handleIDEntiteReference(ExtObjectContainer container, Object idEntiteObj, String idClassName,
            String fieldName, String parentClassName, int indentLevel) throws IOException {
        try {
            long idEntiteId = container.ext().getID(idEntiteObj);

            // Activate the IDEntite object to read its mID
            ObjectResolverUtil.activateObject(container, idEntiteObj, idEntiteId);
            Long mID = extractMIDField(container, idEntiteObj);

            if (mID == null) {
                exportObjectRecursively(container, idEntiteId, indentLevel);
                return;
            }

            // Find schema field to get embedContents
            DOSchemaField schemaField = findSchemaField(parentClassName, fieldName);
            boolean embedContents = schemaField != null && schemaField.embedContents;

            if (!embedContents) {
                exportObjectRecursively(container, idEntiteId, indentLevel);
                return;
            }

            // embedContents=true: resolve IDEntite to its target object and export it fully
            String expectedType = extractExpectedTypeFromFieldName(fieldName, idClassName);
            findAndExportTargetObject(container, mID, expectedType, indentLevel);
        } catch (Exception e) {
            long idEntiteId = container.ext().getID(idEntiteObj);
            System.err.println("Error handling IDEntite relationship for object " + idEntiteId + ": " + e.getMessage());
        }
    }

    private void findAndExportTargetObject(ExtObjectContainer container, Long mID, String expectedType,
            int indentLevel) throws IOException {
        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                String fullClassName = schemaClass.source;

                // Only search in classes that match the expected type
                if (expectedType != null && !fullClassName.equals(expectedType)) {
                    continue;
                }

                long[] objectIds = schemaClass.objectIds;
                if (objectIds != null) {
                    for (long objectId : objectIds) {
                        try {
                            Object obj = container.ext().getByID(objectId);
                            if (obj != null) {
                                ObjectResolverUtil.activateObject(container, obj, objectId);
                                Long objMID = extractMIDField(container, obj);

                                if (mID.equals(objMID)) {
                                    exportObjectRecursively(container, objectId, indentLevel);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            // Skip objects that can't be processed
                        }
                    }
                }
            }
        }
    }

    private Long extractMIDField(ExtObjectContainer container, Object obj) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        StoredField[] fields = storedClass.getStoredFields();
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                } catch (Exception e) {
                    // Field access failed
                }
            }
        }
        return null;
    }

    private String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        if (fieldName.startsWith("mID")) {
            return fieldName.substring(3);
        }
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2);
        }
        return null;
    }

    private String getClassName(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            try {
                if (genericObj.getGenericClass() != null) {
                    return genericObj.getGenericClass().getName();
                }
            } catch (Exception e) {
                // Fall back to regular class name
            }
        }
        return obj.getClass().getName();
    }

    private DOSchemaClass findClassByName(String className) {
        return findClassByName(schema, className);
    }

    private DOSchemaClass findClassByName(DOSchema targetSchema, String className) {
        if (targetSchema == null || targetSchema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : targetSchema.getClasses()) {
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }

    private DOSchemaField findSchemaField(String className, String fieldName) {
        DOSchemaClass schemaClass = findClassByName(className);
        if (schemaClass == null || schemaClass.fields == null) {
            return null;
        }
        for (DOSchemaField field : schemaClass.fields) {
            if (field.source != null && field.source.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.source;
        while (currentClassName != null) {
            if (currentClassName.equals(ancestorClassName)) {
                return true;
            }
            DOSchemaClass currentClass = findClassByName(currentClassName);
            currentClassName = currentClass != null ? currentClass.parentClassName : null;
        }
        return false;
    }
}
