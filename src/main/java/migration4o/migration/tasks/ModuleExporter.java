package migration4o.migration.tasks;

import migration4o.migration.ExportRequest;
import migration4o.models.schema.DOSchemaModule;

/**
 * Utility class for module-level operations shared by the export engine.
 * <p>
 * Delegates per-class file writing to the format handlers via
 * {@link migration4o.migration.MigrationExportService#exportModules}.
 */
public class ModuleExporter {

    private final ExportRequest operation;

    public ModuleExporter(ExportRequest operation) {
        this.operation = operation;
    }

    // ── Counting
    // ──────────────────────────────────────────────────────────────

    /** Returns the total number of class configs across the module tree. */
    public int countTotalClasses(DOSchemaModule module) {
        int count = module.classConfigs.size();
        for (DOSchemaModule child : module.children) {
            count += countTotalClasses(child);
        }
        return count;
    }

}
