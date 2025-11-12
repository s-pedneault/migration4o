package dataobjects.api.migration.excel;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Engine for exporting database contents to Excel files.
 * Creates one Excel file per schema module, with one sheet per class.
 */
public interface DOExcelExportEngine {

    /**
     * Export the database to Excel files in the default output directory.
     * 
     * @param engine The database engine containing the data to export
     * @throws IOException if an error occurs during export
     */
    void exportToExcel(DOEngine engine) throws IOException;

    /**
     * Export the database to Excel files in the specified directory.
     * 
     * @param engine          The database engine containing the data to export
     * @param outputDirectory The directory where Excel files will be created
     * @throws IOException if an error occurs during export
     */
    void exportToExcel(DOEngine engine, String outputDirectory) throws IOException;
}
