package migration4o.schema.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.layout.DetailLayout;

/**
 * Singleton service for managing the module structure. Ensures the module
 * structure is loaded once and shared across the entire application. All
 * components should use this service instead of loading/saving modules
 * directly.
 * 
 * This service follows the same pattern as DODatabaseService and
 * DOSchemaService, providing centralized access to the migration module
 * structure.
 */
public class DOModuleService {

    private static DOModuleService instance;

    static final String DEFAULT_MODULE_FILE = "schema/migration-format.xml";
    static final String BACKUP_MODULES_PATH = "local/schema/migration-format.xml";
    static final String CLASS_LAYOUTS_FILE = "schema/class-layouts.xml";
    static final String BACKUP_CLASS_LAYOUTS_PATH = "local/schema/class-layouts.xml";

    private List<DOSchemaModule> modules;
    private String currentModuleFilePath;
    /** Standalone layouts for embedded classes, keyed by source class name. */
    private Map<String, DetailLayout> classLayouts;

    private DOModuleService() {
        // Private constructor for singleton
        this.modules = new ArrayList<>();
        this.classLayouts = new LinkedHashMap<>();
    }

    /**
     * Get the singleton instance of the module service.
     */
    public static synchronized DOModuleService getInstance() {
        if (instance == null) {
            instance = new DOModuleService();
        }
        return instance;
    }

    /**
     * Load the module structure from the default file path. If modules are
     * already loaded from a different path, they will be replaced.
     * 
     * @return The loaded modules
     * @throws Exception If the module structure cannot be loaded
     */
    public synchronized List<DOSchemaModule> loadModuleStructure() throws Exception {
        return loadModuleStructure(DEFAULT_MODULE_FILE);
    }

    /**
     * Load the module structure from the specified path. If modules are already
     * loaded from a different path, they will be replaced.
     * 
     * @param filePath Path to the migration-format.xml file
     * @return The loaded modules
     * @throws Exception If the module structure cannot be loaded
     */
    public synchronized List<DOSchemaModule> loadModuleStructure(String filePath) throws Exception {
        DOModuleStructureReader reader = new DOModuleStructureReader();
        modules = reader.readMigrationFormat(filePath);
        currentModuleFilePath = filePath;

        // Migrate any layouts found in classRefs into the classLayouts map
        // (backward compatibility with old migration-format.xml that had inline layouts)
        int migrated = migrateInlineLayouts(modules);
        if (migrated > 0) {
            System.out.println("  Migrated " + migrated + " inline layout(s) to class layouts");
            // Persist the migration: write class-layouts.xml and re-save
            // migration-format.xml without the inline layouts
            saveClassLayouts();
            saveModuleStructure(modules, filePath);
            System.out.println("  Persisted migration to disk");
        }

        System.out.println("Module structure loaded: " + filePath);
        System.out.println("  Modules: " + modules.size());

        return new ArrayList<>(modules); // Return a copy to prevent external
                                         // modification
    }

    /**
     * Extract layouts from ClassExportConfigs into the classLayouts map
     * and clear them from the configs. This migrates layouts from the old
     * inline format (in migration-format.xml) to the new standalone file.
     */
    private int migrateInlineLayouts(List<DOSchemaModule> modules) {
        int count = 0;
        for (DOSchemaModule module : modules) {
            count += migrateInlineLayoutsFromModule(module);
        }
        return count;
    }

    private int migrateInlineLayoutsFromModule(DOSchemaModule module) {
        int count = 0;
        for (ClassExportConfig config : module.classConfigs) {
            if (config.hasLayout()) {
                // Only migrate if not already present in classLayouts
                if (!classLayouts.containsKey(config.getClassName())) {
                    classLayouts.put(config.getClassName(), config.getLayout());
                }
                config.setLayout(null);
                count++;
            }
        }
        for (DOSchemaModule child : module.children) {
            count += migrateInlineLayoutsFromModule(child);
        }
        return count;
    }

    /**
     * Get the currently loaded modules.
     * 
     * @return A copy of the module list, or empty list if no modules are loaded
     */
    public synchronized List<DOSchemaModule> getModules() {
        return new ArrayList<>(modules); // Return a copy to prevent external
                                         // modification
    }

    /**
     * Save the module structure to the default file path.
     * 
     * @param modules The modules to save
     * @throws Exception If the module structure cannot be saved
     */
    public synchronized void saveModuleStructure(List<DOSchemaModule> modules) throws Exception {
        String filePath = (currentModuleFilePath != null) ? currentModuleFilePath : DEFAULT_MODULE_FILE;
        saveModuleStructure(modules, filePath);
    }

    /**
     * Save the module structure to the specified path.
     * 
     * @param modules The modules to save
     * @param filePath Path to the migration-format.xml file
     * @throws Exception If the module structure cannot be saved
     */
    public synchronized void saveModuleStructure(List<DOSchemaModule> modules, String filePath) throws Exception {
        DOModuleStructureWriter writer = new DOModuleStructureWriter();
        writer.writeMigrationFormat(modules, filePath);

        // Update cached modules and path
        this.modules = new ArrayList<>(modules);
        this.currentModuleFilePath = filePath;

        System.out.println("Module structure saved: " + filePath);
    }

    /**
     * Set the modules directly (used when modules are created
     * programmatically).
     * 
     * @param modules The modules to set
     */
    public synchronized void setModules(List<DOSchemaModule> modules) {
        this.modules = new ArrayList<>(modules);
        this.currentModuleFilePath = null; // Path not known when setting
                                           // directly
    }

    /**
     * Check if any modules are currently loaded.
     * 
     * @return true if modules are loaded, false otherwise
     */
    public synchronized boolean hasModules() {
        return modules != null && !modules.isEmpty();
    }

    /**
     * Get the path of the currently loaded module file.
     * 
     * @return The module file path, or null if no file is loaded
     */
    public synchronized String getCurrentModuleFilePath() {
        return currentModuleFilePath;
    }

    /**
     * Find a ClassExportConfig by its source class name across all modules.
     *
     * @param className The source class name (e.g. "gest.gen.Adresse")
     * @return The config, or null if not found
     */
    public synchronized ClassExportConfig findConfigByClassName(String className) {
        if (className == null || modules == null)
            return null;
        for (DOSchemaModule module : modules) {
            ClassExportConfig result = findConfigInModule(module, className);
            if (result != null)
                return result;
        }
        return null;
    }

    private ClassExportConfig findConfigInModule(DOSchemaModule module, String className) {
        for (ClassExportConfig config : module.classConfigs) {
            if (className.equals(config.getClassName()))
                return config;
        }
        for (DOSchemaModule child : module.children) {
            ClassExportConfig result = findConfigInModule(child, className);
            if (result != null)
                return result;
        }
        return null;
    }

    /**
     * Clear all loaded modules.
     */
    public synchronized void clear() {
        this.modules = new ArrayList<>();
        this.classLayouts = new LinkedHashMap<>();
        this.currentModuleFilePath = null;
    }

    // ── Standalone class layouts (class-layouts.xml) ──────────────

    /**
     * Get the standalone layout for an embedded class.
     *
     * @param className The source class name (e.g. "gest.gen.Adresse")
     * @return The layout, or null if none defined
     */
    public synchronized DetailLayout getClassLayout(String className) {
        return classLayouts.get(className);
    }

    /**
     * Set a standalone layout for an embedded class.
     *
     * @param className The source class name
     * @param layout    The layout (null to remove)
     */
    public synchronized void setClassLayout(String className, DetailLayout layout) {
        if (layout == null || layout.isEmpty()) {
            classLayouts.remove(className);
        } else {
            classLayouts.put(className, layout);
        }
    }

    /**
     * Get all standalone class layouts (unmodifiable view).
     */
    public synchronized Map<String, DetailLayout> getClassLayouts() {
        return new LinkedHashMap<>(classLayouts);
    }

    /**
     * Load standalone class layouts from the default file.
     */
    public synchronized void loadClassLayouts() throws Exception {
        loadClassLayouts(CLASS_LAYOUTS_FILE);
    }

    /**
     * Load standalone class layouts from a file.
     */
    public synchronized void loadClassLayouts(String filePath) throws Exception {
        ClassLayoutReader reader = new ClassLayoutReader();
        this.classLayouts = reader.readClassLayouts(filePath);
        System.out.println("Class layouts loaded: " + filePath + " (" + classLayouts.size() + " layouts)");
    }

    /**
     * Save standalone class layouts to the default file.
     */
    public synchronized void saveClassLayouts() throws Exception {
        saveClassLayouts(CLASS_LAYOUTS_FILE);
    }

    /**
     * Save standalone class layouts to a file.
     */
    public synchronized void saveClassLayouts(String filePath) throws Exception {
        ClassLayoutWriter writer = new ClassLayoutWriter();
        writer.writeClassLayouts(classLayouts, filePath);
        System.out.println("Class layouts saved: " + filePath + " (" + classLayouts.size() + " layouts)");
    }

    /**
     * Get the default module file path.
     * 
     * @return The default module file path
     */
    public static String getDefaultModuleFile() {
        return DEFAULT_MODULE_FILE;
    }
}
