package migration4o.ui.common;

/**
 * Callback interface for monitoring export progress.
 * Allows UI components to track and display the progress of export operations.
 * 
 * Similar to DODatabaseMonitor but specialized for export operations.
 */
public interface DOExportMonitor {
    
    /**
     * Called when export operation begins.
     * 
     * @param exportName The name of the export (class name or module name)
     * @param totalClasses The total number of classes to be exported
     */
    void onExportStart(String exportName, int totalClasses);
    
    /**
     * Called when export operation completes successfully.
     * 
     * @param exportName The name of the export
     * @param objectsExported Total number of objects exported
     * @param warnings Total number of warnings encountered
     */
    void onExportComplete(String exportName, int objectsExported, int warnings);
    
    /**
     * Called when export operation fails.
     * 
     * @param exportName The name of the export
     * @param error The error that caused the failure
     */
    void onExportError(String exportName, String error);
    
    /**
     * Called when a module export begins.
     * 
     * @param moduleName The name of the module
     * @param classCount Number of classes in this module
     * @param depth Nesting depth (0 = root module)
     */
    void onModuleStart(String moduleName, int classCount, int depth);
    
    /**
     * Called when a module export completes.
     * 
     * @param moduleName The name of the module
     */
    void onModuleComplete(String moduleName);
    
    /**
     * Called when a class export begins.
     * 
     * @param className The full class name
     * @param simpleName The simple class name (for display)
     * @param objectCount Number of objects to export for this class
     */
    void onClassStart(String className, String simpleName, int objectCount);
    
    /**
     * Called when a class export completes.
     * 
     * @param className The full class name
     * @param objectsExported Number of objects successfully exported
     */
    void onClassComplete(String className, int objectsExported);
    
    /**
     * Called periodically during object export to report progress.
     * 
     * @param className The class being exported
     * @param current Current object number
     * @param total Total objects for this class
     */
    void onObjectProgress(String className, int current, int total);
    
    /**
     * Called when an object export succeeds.
     * 
     * @param className The class name
     * @param objectId The object ID
     */
    void onObjectExported(String className, long objectId);
    
    /**
     * Called when an object export fails.
     * 
     * @param className The class name
     * @param objectId The object ID
     * @param error Error message
     */
    void onObjectError(String className, long objectId, String error);
    
    /**
     * Called when a warning is encountered during export.
     * 
     * @param warningType Type of warning (e.g., "ALREADY_EXPORTED", "MISSING_SCHEMA")
     * @param className The class name
     * @param message Warning message
     */
    void onWarning(String warningType, String className, String message);
    
    /**
     * Called when XSD schema generation begins.
     * 
     * @param schemaPath Path where XSD will be written
     */
    void onXSDGenerationStart(String schemaPath);
    
    /**
     * Called when XSD schema generation completes.
     * 
     * @param schemaPath Path where XSD was written
     */
    void onXSDGenerationComplete(String schemaPath);
    
    /**
     * Called with general status messages during export.
     * 
     * @param message Status message
     */
    void onStatusMessage(String message);
    
    /**
     * Returns true if the export should be cancelled.
     * 
     * @return true to cancel export
     */
    boolean isCancelled();
}
