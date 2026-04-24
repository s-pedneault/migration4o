package migration4o.application;

import migration4o.models.schema.DOSchema;
import migration4o.schema.DOSchemaService;
import migration4o.schema.indicators.ProcessingIndicatorService;
import migration4o.schema.modules.DOModuleService;

/**
 * Central application service responsible for initializing all core services. This service ensures that schemas and modules are loaded at startup, eliminating the need for UI components to check initialization state.
 * 
 * Services initialized at startup: - DOSchemaService: Reference schema from schema/reference-schema.xml - DOModuleService: Module structure from schema/migration-format.xml
 * 
 * Services NOT initialized here: - DODatabaseService: Requires user to select a database file
 */
public class ApplicationService {

    private static ApplicationService instance;

    private boolean initialized = false;

    private ApplicationService() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance of the application service.
     */
    public static synchronized ApplicationService getInstance() {
        if (instance == null) {
            instance = new ApplicationService();
        }
        return instance;
    }

    /**
     * Initialize all core application services. This should be called once at application startup. Subsequent calls will be ignored if already initialized.
     * 
     * @throws Exception If any service fails to initialize
     */
    public synchronized void initialize() throws Exception {
        if (initialized) {
            System.out.println("ApplicationService already initialized, skipping...");
            return;
        }

        System.out.println("=== Initializing Application Services ===");

        // Initialize schema service
        initializeSchemaService();

        // Initialize module service
        initializeModuleService();

        // Initialize processing indicator service
        ProcessingIndicatorService.getInstance().load();

        initialized = true;
        System.out.println("=== Application Services Initialized ===");
    }

    /**
     * Initialize the schema service by loading the default reference schema.
     */
    private void initializeSchemaService() throws Exception {
        DOSchemaService schemaService = DOSchemaService.getInstance();

        if (!schemaService.isSchemaLoaded()) {
            // String schemaPath = DOReferenceSchemaConstants.DEFAULT_SCHEMA_PATH;
            // System.out.println("Loading reference schema from: " + schemaPath);
            schemaService.loadReferenceSchema();
            DOSchema schema = schemaService.getReferenceSchema();
            System.out.println("  Schema classes: " + (schema != null && schema.getClasses() != null ? schema.getClasses().length : 0));
        } else {
            System.out.println("Reference schema already loaded");
        }
    }

    /**
     * Initialize the module service by loading the default module structure.
     */
    private void initializeModuleService() throws Exception {
        DOModuleService moduleService = DOModuleService.getInstance();

        if (!moduleService.hasModules()) {
            String moduleFile = DOModuleService.getDefaultModuleFile();
            System.out.println("Loading module structure from: " + moduleFile);
            moduleService.loadModuleStructure(moduleFile);
        } else {
            System.out.println("Module structure already loaded");
        }

        // Load standalone class layouts (class-layouts.xml)
        try {
            moduleService.loadClassLayouts();
        } catch (Exception e) {
            System.out.println("No class layouts file found (will be created on first save)");
        }
    }

    /**
     * Check if the application services have been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Reset the initialization state (primarily for testing). This does NOT clear the services themselves, only the initialization flag.
     */
    public synchronized void resetInitializationState() {
        initialized = false;
    }
}
