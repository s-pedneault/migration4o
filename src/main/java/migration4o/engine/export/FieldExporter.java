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
import migration4o.util.SchemaUtil;

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

    public FieldExporter(DOSchema schema, DOSchema databaseSchema, XMLWriter xmlWriter, XSDBuilder xsdBuilder,
            ReferenceObjectExporter idEntiteResolver) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.idEntiteResolver = idEntiteResolver;
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

                    // XSD: record field type
                    if (schemaField != null) {
                        xsdBuilder.addField(parentClass, schemaField);
                    }

                    if (fieldValue == null) {
                        xmlWriter.writeIndent(indentLevel);
                        xmlWriter.write("<" + fieldName + "/>\n");
                        continue;
                    }

                    // Handle collections
                    if (fieldValue instanceof Collection) {
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
                    System.err.println("Error exporting field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing fields: " + e.getMessage());
        }
    }

    private void exportCollectionField(ExtObjectContainer container, Collection<?> collection,
            DOSchemaField schemaField, DOSchemaClass parentClass, int indentLevel,
            ObjectExportDelegate objectExportDelegate) throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        if (collection.isEmpty()) {
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + "/>\n");
        } else {
            xmlWriter.writeStartElement(fieldName, indentLevel);
            for (Object item : collection) {
                if (item != null) {
                    exportFieldValue(container, item, schemaField, parentClass, indentLevel + 1,
                            objectExportDelegate);
                }
            }
            xmlWriter.writeEndElement(fieldName, indentLevel);
        }
    }

    private void exportArrayField(ExtObjectContainer container, Object fieldValue, DOSchemaField schemaField,
            DOSchemaClass parentClass, int indentLevel, ObjectExportDelegate objectExportDelegate)
            throws IOException {
        String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
        int length = java.lang.reflect.Array.getLength(fieldValue);
        if (length == 0) {
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<" + fieldName + "/>\n");
        } else {
            xmlWriter.writeStartElement(fieldName, indentLevel);
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
            xmlWriter.writeStartElement(fieldName, indentLevel);
            exportFieldValue(container, fieldValue, schemaField, parentClass, indentLevel + 1,
                    objectExportDelegate);
            xmlWriter.writeEndElement(fieldName, indentLevel);
        } else {
            // Primitive or non-persistent value - write inline
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
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

        // Check if this is an IDEntite reference (reference object pattern)
        DOSchemaClass fieldClass = SchemaUtil.findClassByName(className, schema);
        if (fieldClass != null && fieldClass.isIDEntite(databaseSchema)) {
            idEntiteResolver.resolveAndExport(container, fieldValue, className, schemaField,
                    (objectId, indent) -> objectExportDelegate.exportObject(objectId, indent), indentLevel);
            return;
        }

        // For regular objects, just export them recursively
        long objectId = container.ext().getID(fieldValue);
        if (objectId > 0) {
            objectExportDelegate.exportObject(objectId, indentLevel);
        } else {
            // Primitive value - write inline
            String fieldName = schemaField != null ? schemaField.destinationName : "unknown";
            xmlWriter.writeElement(fieldName, fieldValue.toString(), indentLevel);
        }
    }

    /**
     * Delegate interface for exporting objects.
     */
    public interface ObjectExportDelegate {
        void exportObject(long objectId, int indentLevel) throws IOException;
    }
}
