package migration4o.migration.tasks;

import java.util.HashSet;
import java.util.Set;

import migration4o.migration.ExportOperation;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;

/**
 * Utility class for module-level operations shared by the export engine.
 * <p>
 * Delegates per-class file writing to the format handlers via
 * {@link migration4o.migration.ExportEngine#exportModules}.
 */
public class ModuleExporter {

    private final ExportOperation operation;

    public ModuleExporter(ExportOperation operation) {
        this.operation = operation;
    }

    // ── Counting ──────────────────────────────────────────────────────────────

    /** Returns the total number of class configs across the module tree. */
    public int countTotalClasses(DOSchemaModule module) {
        int count = module.classConfigs.size();
        for (DOSchemaModule child : module.children) {
            count += countTotalClasses(child);
        }
        return count;
    }

    // ── Reference-tracker registration ───────────────────────────────────────

    /**
     * Recursively registers all class names in the module tree with the given
     * {@link ReferencedClassTracker} so the tracker can distinguish between
     * "known module classes" and truly foreign references.
     */
    public void registerModuleClasses(DOSchemaModule module, ReferencedClassTracker tracker) {
        Set<String> classNames = new HashSet<>();
        for (ClassExportConfig c : module.classConfigs) {
            classNames.add(c.getClassName());
        }
        tracker.registerModule(module.name, classNames);
        for (DOSchemaModule child : module.children) {
            registerModuleClasses(child, tracker);
        }
    }
}
