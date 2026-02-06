package migration4o.migration.recipes;

import java.io.IOException;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

import migration4o.migration.FieldExporter;
import migration4o.models.schema.DOSchemaClass;

/**
 * Recipe for exporting DB4O GenericObject instances.
 * Handles the GenericObject-specific export workflow including stored class
 * retrieval and field export.
 */
public class GenericObjectExporter {

    /**
     * Exports a GenericObject's fields if it is a GenericObject.
     * Returns true if the object was a GenericObject and was exported.
     * 
     * @param container     DB4O container
     * @param obj           The object to potentially export as GenericObject
     * @param schemaClass   Schema class for the object
     * @param objectId      Object ID
     * @param fieldExporter Field exporter to delegate field export
     * @param indentLevel   Current indentation level
     * @return true if object was a GenericObject and was exported, false otherwise
     * @throws IOException if export fails
     */
    public static boolean exportIfGenericObject(
            ExtObjectContainer container,
            Object obj,
            DOSchemaClass schemaClass,
            long objectId,
            FieldExporter fieldExporter,
            int indentLevel) throws IOException {

        if (!(obj instanceof GenericObject)) {
            return false;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);

        if (storedClass != null) {
            String currentClassName = schemaClass.destinationName;
            String currentSourceClassName = schemaClass.source; // Full source class name
            fieldExporter.exportAllFields(container, genericObj, schemaClass, indentLevel + 1,
                    currentClassName, currentSourceClassName, objectId);
        }

        return true;
    }
}
