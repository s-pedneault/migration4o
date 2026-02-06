package migration4o.util;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Utility methods for working with schema modules.
 */
public class ModuleUtil {

    /**
     * Checks if a class is listed in any module.
     * 
     * @param schema      The schema containing modules
     * @param schemaClass The class to check
     * @return true if the class is listed in at least one module, false otherwise
     */
    public static boolean isClassListedInAnyModule(DOSchema schema, DOSchemaClass schemaClass) {
        if (schema == null || schema.getModules() == null || schemaClass == null) {
            return false;
        }

        String className = schemaClass.source;
        for (var module : schema.getModules()) {
            if (module.getClasses() != null) {
                for (var moduleClass : module.getClasses()) {
                    if (moduleClass.source != null && moduleClass.source.equals(className)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
