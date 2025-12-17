package migration4o.engine.migration.generic;

import java.io.IOException;
import java.util.List;

/**
 * Base class for export format handlers in the generic export system.
 * Defines the contract that all format handlers must implement.
 */
public abstract class ExportFormatHandler {

    /**
     * Get the default output directory for this format.
     * 
     * @return Default output directory path
     */
    public abstract String getDefaultOutputDirectory();

    /**
     * Get the preferred output structure for this format.
     * 
     * @return The preferred structure (HIERARCHICAL or TABULAR)
     */
    public abstract OutputStructure getPreferredStructure();

    /**
     * Initialize the format handler with the output directory.
     * 
     * @param outputDirectory The directory where output files will be created
     * @throws IOException if initialization fails
     */
    public void initialize(String outputDirectory) throws IOException {
        // Default implementation for backwards compatibility
        initialize(outputDirectory, null);
    }

    /**
     * Initialize the format handler with the output directory and reference
     * tracker.
     * 
     * @param outputDirectory The directory where output files will be created
     * @param tracker         The reference tracker for cross-module reference
     *                        detection (may be null)
     * @throws IOException if initialization fails
     */
    public abstract void initialize(String outputDirectory, ReferenceTracker tracker) throws IOException;

    /**
     * Begin processing a module.
     * 
     * @param context The module export context
     * @return A format-specific handle for the module (can be used to track state)
     * @throws IOException if module initialization fails
     */
    public abstract Object beginModule(ModuleExportContext context) throws IOException;

    /**
     * Begin processing a class within a module.
     * 
     * @param moduleHandle The handle returned by beginModule
     * @param context      The class export context
     * @return A format-specific handle for the class (can be used to track state)
     * @throws IOException if class initialization fails
     */
    public abstract Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException;

    /**
     * Export a single object with formatted values.
     * 
     * @param classHandle The handle returned by beginClass
     * @param context     The object export context
     * @param values      The formatted field values for the object
     * @throws IOException if object export fails
     */
    public abstract void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException;

    /**
     * End processing of a class.
     * 
     * @param classHandle   The handle returned by beginClass
     * @param context       The class export context
     * @param exportedCount The number of objects actually exported for this class
     * @throws IOException if class finalization fails
     */
    public abstract void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException;

    /**
     * End processing of a module.
     * 
     * @param moduleHandle The handle returned by beginModule
     * @param context      The module export context
     * @throws IOException if module finalization fails
     */
    public abstract void endModule(Object moduleHandle, ModuleExportContext context) throws IOException;

    /**
     * Clean up any resources used by the format handler.
     * Called after all modules have been processed.
     * 
     * @throws IOException if cleanup fails
     */
    public abstract void cleanup() throws IOException;
}