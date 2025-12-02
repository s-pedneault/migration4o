package dataobjects.impl.migration.generic;

/**
 * Development configuration for testing exports with specific modules.
 * 
 * To enable development mode:
 * 1. Set DEVELOPMENT_MODE = true
 * 2. Set TARGET_MODULE to the module name you want to test (e.g., "Dossier
 * adresse")
 * 
 * When development mode is enabled:
 * - Only the target module will be exported
 * - General.xml will only contain foundation classes referenced by the target
 * module
 * - All other modules will be skipped
 */
public class DevelopmentConfig {

    /**
     * Enable/disable development mode.
     * Set to true to export only the target module
     */
    public static final boolean DEVELOPMENT_MODE = true;

    /**
     * The module to export when in development mode.
     * Must match the exact module name from the schema.
     * Examples: "Dossier adresse", "Intervention", "Prévention", etc.
     */
    public static final String TARGET_MODULE = "Dossier adresse";

    /**
     * Check if a module should be exported based on development mode settings.
     * 
     * @param moduleName the name of the module to check
     * @return true if the module should be exported, false otherwise
     */
    public static boolean shouldExportModule(String moduleName) {
        if (!DEVELOPMENT_MODE) {
            return true; // Export all modules in production mode
        }

        // In development mode, only export the target module
        return moduleName != null && moduleName.equals(TARGET_MODULE);
    }

    /**
     * Get a description of the current configuration for logging.
     */
    public static String getConfigDescription() {
        if (DEVELOPMENT_MODE) {
            return "DEVELOPMENT MODE - Only exporting module: " + TARGET_MODULE + " + General.xml (filtered)";
        } else {
            return "PRODUCTION MODE - Exporting all modules";
        }
    }
}
