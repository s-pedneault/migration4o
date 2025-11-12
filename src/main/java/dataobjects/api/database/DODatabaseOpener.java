package dataobjects.api.database;

import com.db4o.ext.ExtObjectContainer;

/**
 * Specialized interface for opening DB4O databases with robust encoding
 * detection.
 * Handles various DB4O database formats and encoding configurations.
 */
public interface DODatabaseOpener {

    /**
     * Opens a DB4O database file using robust encoding detection.
     * Tries multiple encoding configurations until one succeeds.
     * 
     * @param filePath The path to the DB4O database file
     * @return An opened ExtObjectContainer with the successful encoding
     * @throws RuntimeException if the database cannot be opened with any encoding
     */
    ExtObjectContainer openDatabase(String filePath);

    /**
     * Gets the encoding configuration that was successfully used to open the
     * database.
     * Only valid after a successful openDatabase() call.
     * 
     * @return The encoding configuration used, or null if no database has been
     *         opened
     */
    DODatabaseEncoding getSuccessfulEncoding();
}
