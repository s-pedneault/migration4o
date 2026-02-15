package migration4o.migration.recipes;

import java.io.IOException;

import com.db4o.ext.ExtObjectContainer;

import migration4o.migration.ExportOperation;
import migration4o.migration.XMLWriter;
import migration4o.migration.XSDBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ValueUtil;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Recipe for exporting entity objects as ID reference wrappers. Creates
 * synthetic ID objects (e.g., IDCompartiment) with the mID field set to the
 * entity's DB object ID.
 */
public class IDReferenceExporter {

    /**
     * Exports an entity object as an ID reference.
     * 
     * @param container Database container
     * @param entity The entity object to reference
     * @param idClass The ID class schema (e.g., IDCompartiment)
     * @param xmlWriter XML writer for output
     * @param xsdBuilder XSD builder for schema
     * @param indentLevel Current indentation level
     * @param operation Export operation context (contains objectExporter)
     * @throws IOException if write fails
     */
    public static void exportAsIDReference(ExtObjectContainer container, Object entity, DOSchemaClass idClass, StructuredWriter xmlWriter, XSDBuilder xsdBuilder, int indentLevel, ExportOperation operation) throws IOException {

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

        xmlWriter.openStructure(simpleClassName);

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
            String formattedValue = ValueUtil.formatFieldValue(String.valueOf(entityObjectId), idField);
            xmlWriter.elementWithContent(idField.destinationName, formattedValue, false);
        }

        xmlWriter.closeStructure(simpleClassName);

        // Ensure the actual entity object gets exported separately (not
        // embedded)
        operation.objectExporter.exportObjectRecursively(container, entityObjectId, indentLevel, false, null, null, null, null, false, null);
    }
}
