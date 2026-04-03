package migration4o.migration.recipes;

import java.io.IOException;

import migration4o.database.DODatabaseDelegate;
import migration4o.migration.format.ExportCurrentState;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ValueUtil;
import migration4o.util.formatters.FormatterContext;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Recipe for exporting entity objects as ID reference wrappers. Creates synthetic ID objects (e.g., IDCompartiment) with the mID field set to the entity's DB object ID.
 */
public class IDReferenceExporter {

    /**
     * Exports an entity object as an ID reference.
     * 
     * @param container Database container
     * @param entity The entity object to reference
     * @param idClass The ID class schema (e.g., IDCompartiment)
     * @param xmlWriter XML writer for output
     * @param indentLevel Current indentation level
     * @param ctx Export state (contains objectExporter)
     * @throws IOException if write fails
     */
    public static void exportAsIDReference(DODatabaseDelegate delegate, Object entity, DOSchemaClass idClass, StructuredWriter xmlWriter, int indentLevel, ExportCurrentState ctx) throws IOException {

        // Get the DB object ID of the entity
        long entityObjectId = delegate.getID(entity);

        if (entityObjectId <= 0) {
            return;
        }

        // Export the ID object wrapper
        String idClassName = idClass.attributes.source; // e.g., "IDCompartiment"
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);

        xmlWriter.openStructure(simpleClassName);

        // Find the mID field in the ID class schema
        DOSchemaField idField = null;
        for (DOSchemaField field : idClass.fields) {
            if ("mID".equals(field.attributes.source)) {
                idField = field;
                break;
            }
        }

        if (idField != null) {
            // Export the ID value
            String formattedValue = ValueUtil.formatFieldValue(delegate, new FormatterContext(ctx.basePath, ctx.schemaClass, idField, ctx.currentObject().obj), String.valueOf(entityObjectId), idField);
            xmlWriter.elementWithContent(idField.attributes.destinationName, formattedValue, false);
        }

        xmlWriter.closeStructure(simpleClassName);

        // Ensure the actual entity object gets exported separately (not
        // embedded)
        ctx.objectExporter.exportObject(entityObjectId, false);
    }
}
