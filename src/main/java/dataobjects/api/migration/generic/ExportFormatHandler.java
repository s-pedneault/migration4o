package dataobjects.api.migration.generic;

import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.DOField;

import java.io.IOException;
import java.util.List;

/**
 * Interface for format-specific export handlers.
 * Implementations control how data is written to specific output formats
 * (Excel,
 * XML, etc.)
 */
public interface ExportFormatHandler {

    /**
     * Called before processing any modules.
     * 
     * @param outputDirectory The directory where output should be written
     * @throws IOException if initialization fails
     */
    void initialize(String outputDirectory) throws IOException;

    /**
     * Called when starting to process a new module.
     * 
     * @param module The schema module being processed
     * @return A module context object that will be passed to subsequent calls
     * @throws IOException if module creation fails
     */
    Object beginModule(DOSchemaModule module) throws IOException;

    /**
     * Called when starting to process a new class within a module.
     * 
     * @param moduleContext The context returned by beginModule()
     * @param schemaClass   The schema class being processed
     * @param dbClass       The corresponding database class
     * @param columns       The list of columns to export (from buildExportColumns)
     * @param objectCount   The number of objects that will be exported for this
     *                      class
     * @return A class context object that will be passed to subsequent row calls
     * @throws IOException if class creation fails
     */
    Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,
            List<ExportColumn> columns, int objectCount) throws IOException;

    /**
     * Called for each object to be exported.
     * 
     * @param classContext The context returned by beginClass()
     * @param obj          The database object being exported
     * @param columns      The list of columns to export
     * @param rowIndex     The index of this row (0-based)
     * @param cellValues   The extracted values for each column
     * @throws IOException if row export fails
     */
    void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,
            List<Object> cellValues) throws IOException;

    /**
     * Called when finished processing a class.
     * 
     * @param classContext  The context returned by beginClass()
     * @param schemaClass   The schema class that was processed
     * @param exportedCount The number of objects that were exported
     * @throws IOException if finalization fails
     */
    void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException;

    /**
     * Called when finished processing a module.
     * 
     * @param moduleContext The context returned by beginModule()
     * @param module        The module that was processed
     * @throws IOException if finalization fails
     */
    void endModule(Object moduleContext, DOSchemaModule module) throws IOException;

    /**
     * Called after all modules have been processed.
     * 
     * @throws IOException if finalization fails
     */
    void finalize() throws IOException;

    /**
     * Get the default output directory for this format.
     * 
     * @return The default directory path
     */
    String getDefaultOutputDirectory();

}
