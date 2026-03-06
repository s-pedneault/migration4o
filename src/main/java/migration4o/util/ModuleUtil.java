package migration4o.util;

import migration4o.models.schema.DOSchemaModule;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.schema.modules.DOModuleService;

import java.util.List;

/**
 * Utility methods for working with migration modules.
 */
public class ModuleUtil {

    /**
     * Checks if a class is listed in any export module (from
     * migration-format.xml).
     * 
     * @param schemaClass The class to check
     * @return true if the class is listed in at least one migration module,
     * false otherwise
     */
    public static boolean isClassListedInAnyModule(DOSchemaClass schemaClass) {
        if (schemaClass == null) {
            return false;
        }

        String className = schemaClass.source;
        List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();

        for (DOSchemaModule module : modules) {
            if (moduleContainsClass(module, className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the export module (from migration-format.xml) that contains the
     * specified class.
     * 
     * @param schemaClass The class to find
     * @return the module name if found, or null if not in any migration module
     */
    public static String findModuleForClass(DOSchemaClass schemaClass) {
        if (schemaClass == null) {
            return null;
        }

        String className = schemaClass.source;
        List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();

        for (DOSchemaModule module : modules) {
            if (moduleContainsClass(module, className)) {
                return module.name;
            }
        }
        return null;
    }

    private static boolean moduleContainsClass(DOSchemaModule module, String className) {
        for (ClassExportConfig config : module.classConfigs) {
            if (config.getClassName().equals(className))
                return true;
        }
        for (DOSchemaModule child : module.children) {
            if (moduleContainsClass(child, className))
                return true;
        }
        return false;
    }
}
