package migration4o.migration.recipes;

import java.io.IOException;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.FieldExporter;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Recipe for exporting DB4O GenericObject instances.
 * Handles the GenericObject-specific export workflow including stored class
 * retrieval and field export.
 */
public class GenericObjectExporter {

    /**
     * Counts how many fields would be exported from this GenericObject (dry run).
     * This allows us to determine if object tags should be written before actually
     * writing any XML.
     * 
     * @param container     DB4O container
     * @param obj           The object to check
     * @param schemaClass   Schema class for the object
     * @param objectId      Object ID
     * @param fieldExporter Field exporter to delegate counting
     * @param schema        The reference schema for skip condition checks
     * @return number of fields that will be exported
     */
    public static int countFieldsToExport(
            ExtObjectContainer container,
            Object obj,
            DOSchemaClass schemaClass,
            long objectId,
            FieldExporter fieldExporter,
            DOSchema schema) {

        if (!(obj instanceof GenericObject)) {
            return 0;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);

        if (storedClass != null) {
            return fieldExporter.countFieldsToExport(container, genericObj, schemaClass, schema);
        }

        return 0;
    }

    /**
     * Exports a GenericObject's fields if it is a GenericObject.
     * Returns the number of fields written if the object was exported.
     * 
     * @param container     DB4O container
     * @param obj           The object to potentially export as GenericObject
     * @param schemaClass   Schema class for the object
     * @param objectId      Object ID
     * @param fieldExporter Field exporter to delegate field export
     * @param indentLevel   Current indentation level
     * @return number of fields written if object was a GenericObject, -1 if not a
     *         GenericObject
     * @throws IOException if export fails
     */
    public static int exportIfGenericObject(
            ExtObjectContainer container,
            Object obj,
            DOSchemaClass schemaClass,
            long objectId,
            FieldExporter fieldExporter,
            int indentLevel) throws IOException {

        if (!(obj instanceof GenericObject)) {
            return -1;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);

        if (storedClass != null) {
            String currentClassName = schemaClass.destinationName;
            String currentSourceClassName = schemaClass.source; // Full source class name
            return fieldExporter.exportAllFields(container, genericObj, schemaClass, indentLevel + 1,
                    currentClassName, currentSourceClassName, objectId);
        }

        return 0;
    }
}
