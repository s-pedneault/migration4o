package dataobjects.api.migration.generic;

import java.io.IOException;
import java.util.List;

/**
 * Interface for export format handlers in the generic export system.
 * Defines the contract that all format handlers must implement.
 */
public interface ExportFormatHandler {

    /**
     * Get the default output directory for this format.
     * 
     * @return Default output directory path
     */
    String getDefaultOutputDirectory();

    /**
     * Get the preferred output structure for this format.
     * 
     * @return The preferred structure (HIERARCHICAL or TABULAR)
     */
    OutputStructure getPreferredStructure();

    /**
     * Initialize the format handler with the output directory.
     * 
     * @param outputDirectory The directory where output files will be created
     * @throws IOException if initialization fails
     */
    void initialize(String outputDirectory) throws IOException;

    /**
     * Begin processing a module.
     * 
     * @param context The module export context
     * @return A format-specific handle for the module (can be used to track state)
     * @throws IOException if module initialization fails
     */
    Object beginModule(ModuleExportContext context) throws IOException;

    /**
     * Begin processing a class within a module.
     * 
     * @param moduleHandle The handle returned by beginModule
     * @param context      The class export context
     * @return A format-specific handle for the class (can be used to track state)
     * @throws IOException if class initialization fails
     */
    Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException;

    /**
     * Export a single object with formatted values.
     * 
     * @param classHandle The handle returned by beginClass
     * @param context     The object export context
     * @param values      The formatted field values for the object
     * @throws IOException if object export fails
     */
    void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException;

    /**
     * End processing of a class.
     * 
     * @param classHandle   The handle returned by beginClass
     * @param context       The class export context
     * @param exportedCount The number of objects actually exported for this class
     * @throws IOException if class finalization fails
     */
    void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException;

    /**
     * End processing of a module.
     * 
     * @param moduleHandle The handle returned by beginModule
     * @param context      The module export context
     * @throws IOException if module finalization fails
     */
    void endModule(Object moduleHandle, ModuleExportContext context) throws IOException;

    /**
     * Clean up any resources used by the format handler.
     * Called after all modules have been processed.
     * 
     * @throws IOException if cleanup fails
     */
    void cleanup() throws IOException;
}