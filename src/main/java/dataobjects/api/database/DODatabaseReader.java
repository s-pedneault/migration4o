package dataobjects.api.database;

import com.db4o.ext.ExtObjectContainer;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.database.DODatabase;

/**
 * Specialized interface for reading database information and statistics.
 * Extracts metadata, class information, and object counts from opened
 * databases with full resolution of objects, collections, and inheritance.
 */
public interface DODatabaseReader {

        /**
         * Reads comprehensive database information from an opened database container,
         * using schema information to enhance field type resolution.
         * 
         * @param container    The opened database container
         * @param encoding     The encoding used to open the database
         * @param databaseSize The size of the database file
         * @param schema       The schema to use for enhanced field resolution (can be
         *                     null)
         * @return A complete DODatabase instance with all information
         * @throws Exception if reading database information fails
         */
        DODatabase readDatabaseMeta(ExtObjectContainer container, DODatabaseEncoding encoding,
                        String databaseSize,
                        DOSchema schema);

        /**
         * Reads database information with full resolution of objects, collections, and
         * inheritance.
         * This method integrates the analysis logic to provide fully resolved objects
         * at load time.
         * 
         * @param container    The opened database container
         * @param encoding     The encoding used to open the database
         * @param databaseSize The size of the database file
         * @param schema       The schema to use for resolution
         * @return A complete DODatabase instance with fully resolved objects
         * @throws Exception if reading database information fails
         */
        DODatabase readDatabase(ExtObjectContainer container, DODatabaseEncoding encoding,
                        String databaseSize, DOSchema schema);

}
