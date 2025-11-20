package dataobjects.api.migration.generic.v2;

import java.io.IOException;
import java.util.List;

/**
 * Enhanced interface for format-specific export handlers.
 * 
 * This version 2 interface provides:
 * - Type-safe contexts instead of Object parameters
 * - Pre-formatted values to eliminate duplicate formatting logic
 * - Output structure preferences to optimize for different format types
 * - Cleaner separation between data processing and output formatting
 * 
 * Format handlers now focus purely on output generation, while the engine
 * handles all data extraction and value formatting.
 */
public interface ExportFormatHandler {

    /**
     * Get the preferred output structure for this format.
     * The engine will adapt its behavior based on this preference.
     * 
     * @return The output structure that best fits this format
     */
    OutputStructure getPreferredStructure();

    /**
     * Get the default output directory for this format.
     * 
     * @return The default directory path
     */
    String getDefaultOutputDirectory();

    /**
     * Called before processing any modules to initialize the handler.
     * 
     * @param outputDirectory The directory where output should be written
     * @throws IOException if initialization fails
     */
    void initialize(String outputDirectory) throws IOException;

    /**
     * Called when starting to process a new module.
     * 
     * @param context The module context with all module-level information
     * @return A format-specific module context (can be any type the handler needs)
     * @throws IOException if module initialization fails
     */
    Object beginModule(ModuleExportContext context) throws IOException;

    /**
     * Called when starting to process a new class within a module.
     * 
     * @param moduleHandle The handle returned by beginModule()
     * @param context      The class context with all class-level information
     * @return A format-specific class context (can be any type the handler needs)
     * @throws IOException if class initialization fails
     */
    Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException;

    /**
     * Called for each object to be exported.
     * 
     * @param classHandle The handle returned by beginClass()
     * @param context     The object context with object-level information
     * @param values      The pre-formatted values for all columns, ready for output
     * @throws IOException if object export fails
     */
    void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values) throws IOException;

    /**
     * Called when finished processing a class.
     * 
     * @param classHandle   The handle returned by beginClass()
     * @param context       The class context
     * @param exportedCount The number of objects that were actually exported
     * @throws IOException if class finalization fails
     */
    void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException;

    /**
     * Called when finished processing a module.
     * 
     * @param moduleHandle The handle returned by beginModule()
     * @param context      The module context
     * @throws IOException if module finalization fails
     */
    void endModule(Object moduleHandle, ModuleExportContext context) throws IOException;

    /**
     * Called after all modules have been processed.
     * 
     * @throws IOException if cleanup fails
     */
    void cleanup() throws IOException;
}