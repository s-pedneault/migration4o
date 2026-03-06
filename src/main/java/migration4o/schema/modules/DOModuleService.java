package migration4o.schema.modules;

import java.util.ArrayList;
import java.util.List;

import migration4o.models.schema.DOSchemaModule;

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

    private List<DOSchemaModule> modules;
    private String currentModuleFilePath;

    private DOModuleService() {
        // Private constructor for singleton
        this.modules = new ArrayList<>();
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

        System.out.println("Module structure loaded: " + filePath);
        System.out.println("  Modules: " + modules.size());

        return new ArrayList<>(modules); // Return a copy to prevent external
                                         // modification
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
     * Clear all loaded modules.
     */
    public synchronized void clear() {
        this.modules = new ArrayList<>();
        this.currentModuleFilePath = null;
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
