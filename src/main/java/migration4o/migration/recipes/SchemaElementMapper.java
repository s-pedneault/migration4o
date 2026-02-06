package migration4o.migration.recipes;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;

/**
 * Recipe for mapping class names to XML element names using schema.
 * Provides consistent element name resolution across exports.
 */
public class SchemaElementMapper {

    /**
     * Gets the XML element name for a class.
     * Uses schema destinationName if available, otherwise simple class name.
     * 
     * @param className Full class name (e.g., gest.vehicule.Vehicule)
     * @param schema    Reference schema
     * @return XML element name (e.g., Vehicule)
     */
    public static String getElementName(String className, DOSchema schema) {
        DOSchemaClass schemaClass = SchemaUtil.findClassByName(className, schema);
        if (schemaClass != null) {
            return schemaClass.destinationName;
        }
        return ClassUtil.getSimpleName(className);
    }

    /**
     * Gets the schema class for a class name.
     * 
     * @param className Full class name
     * @param schema    Reference schema
     * @return Schema class or null
     */
    public static DOSchemaClass getSchemaClass(String className, DOSchema schema) {
        return SchemaUtil.findClassByName(className, schema);
    }
}
