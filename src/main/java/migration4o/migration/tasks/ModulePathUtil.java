package migration4o.migration.tasks;

import migration4o.models.schema.DOSchemaModule;

/**
 * Static helpers for module names, paths, and XSD schema locations.
 */
public final class ModulePathUtil {

    private ModulePathUtil() {
    }

    /**
     * Returns the folder identifier for a module: the module's ID when non-blank, otherwise falls back to the module's display name.
     */
    public static String moduleId(DOSchemaModule m) {
        String id = m.id;
        return (id != null && !id.isBlank()) ? id : m.name;
    }
}
