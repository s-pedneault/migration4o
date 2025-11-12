package dataobjects.impl.database;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.database.DODatabaseBuilder;
import dataobjects.api.database.DODatabaseEncoding;
import dataobjects.api.database.DODatabaseOpener;
import dataobjects.api.database.DODatabaseReader;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.util.FileUtil;

import com.db4o.ext.ExtObjectContainer;

import java.io.File;

/**
 * Simplified DODatabaseBuilder that delegates to specialized components.
 * Uses DODatabaseOpener for robust database opening and DODatabaseReader for
 * information extraction.
 */
public class DODatabaseBuilderImpl implements DODatabaseBuilder {

    private final DODatabaseOpener databaseOpener;
    private final DODatabaseReader databaseReader;

    public DODatabaseBuilderImpl() {
        this.databaseOpener = new DODatabaseOpenerImpl();
        this.databaseReader = new DODatabaseReaderImpl();
    }

    // Constructor for dependency injection (useful for testing)
    public DODatabaseBuilderImpl(DODatabaseOpener databaseOpener, DODatabaseReader databaseReader) {
        this.databaseOpener = databaseOpener;
        this.databaseReader = databaseReader;
    }

    @Override
    public DODatabase buildDatabase(String filePath, DOSchema schema) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }

        File dbFile = new File(filePath);
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Database file does not exist: " + filePath);
        }

        // Step 1: Open the database with robust encoding detection
        ExtObjectContainer container = databaseOpener.openDatabase(filePath);

        // Step 2: Get the successful encoding used
        DODatabaseEncoding encoding = databaseOpener.getSuccessfulEncoding();

        // Step 3: Calculate file size
        String databaseSize = FileUtil.formatFileSize(dbFile.length());

        // Step 4: Read database information (without full resolution)
        // Note: Object resolution now happens in DOEngineImpl after initialization
        DODatabase database = databaseReader.readDatabaseInformation(container, encoding, databaseSize, schema);

        // Resolve inheritance relationships here (needed before object resolution)
        dataobjects.api.resolution.DOInheritanceResolver inheritanceResolver = new dataobjects.impl.resolution.DOInheritanceResolverImpl();
        inheritanceResolver.resolveInheritance(database, schema);

        return database;
    }
}
