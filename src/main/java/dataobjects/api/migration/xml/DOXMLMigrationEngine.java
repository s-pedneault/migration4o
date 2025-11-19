package dataobjects.api.migration.xml;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Engine for exporting database contents to XML files with schema and migration
 * report.
 * Creates:
 * - An XSD schema file describing the structure
 * - One XML data file per schema module
 * - One XML file for unreached objects
 * - A comprehensive migration report
 */
public interface DOXMLMigrationEngine {

    /**
     * Export the database to XML files in the default output directory.
     * Creates subdirectories for schema, data files, and report.
     * 
     * @param engine The database engine containing the data to export
     * @throws IOException if an error occurs during export
     */
    void exportToXML(DOEngine engine) throws IOException;

    /**
     * Export the database to XML files in the specified directory.
     * Creates subdirectories for schema, data files, and report.
     * 
     * @param engine          The database engine containing the data to export
     * @param outputDirectory The base directory where output files will be created
     * @throws IOException if an error occurs during export
     */
    void exportToXML(DOEngine engine, String outputDirectory) throws IOException;
}
