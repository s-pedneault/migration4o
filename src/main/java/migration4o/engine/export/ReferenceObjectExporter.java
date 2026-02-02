package migration4o.engine.export;

import java.io.IOException;

import com.db4o.ext.ExtObjectContainer;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ReferenceUtil;

/**
 * Handles the export of IDEntite reference objects.
 */
public class ReferenceObjectExporter {
    private final DOSchema databaseSchema;

    public ReferenceObjectExporter(DOSchema databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    /**
     * Exports an IDEntite reference, either as-is or resolved to its target object.
     * 
     * @param container            Database container
     * @param idEntiteObj          The IDEntite object
     * @param idClassName          Class name of the IDEntite object
     * @param schemaField          Schema field definition
     * @param objectExportCallback Callback to export the resolved object
     * @param indentLevel          Indentation level for export
     * @throws IOException If export fails
     */
    public void resolveAndExport(ExtObjectContainer container, Object idEntiteObj, String idClassName,
            DOSchemaField schemaField, ObjectExportCallback objectExportCallback, int indentLevel) throws IOException {

        // Check if we should skip based on skipWhen conditions
        if (schemaField != null && schemaField.skipWhen != null && !schemaField.skipWhen.isEmpty()) {
            Long mID = ReferenceUtil.extractMIDField(container, idEntiteObj);
            // Check if MINUS_ONE is in skipWhen and mID is -1
            if (mID != null && mID == -1 && schemaField.skipWhen.contains("MINUS_ONE")) {
                // Skip this field - it's an empty reference
                return;
            }
        }

        // Use ReferenceUtil to determine which object ID to export
        long objectIdToExport = ReferenceUtil.resolveIDEntiteForExport(container, idEntiteObj, idClassName,
                schemaField, databaseSchema);

        // Export the determined object using the callback
        objectExportCallback.exportObject(objectIdToExport, indentLevel);
    }

    /**
     * Callback interface for exporting an object by its ID.
     */
    @FunctionalInterface
    public interface ObjectExportCallback {
        void exportObject(long objectId, int indentLevel) throws IOException;
    }
}
