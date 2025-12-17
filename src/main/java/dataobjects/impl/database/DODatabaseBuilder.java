package dataobjects.impl.database;

import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.database.DODatabaseBuilder;
import dataobjects.impl.database.DODatabaseEncoding;
import dataobjects.impl.database.DODatabaseOpener;
import dataobjects.impl.database.DODatabaseReader;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.resolution.DOInheritanceResolver;
import dataobjects.impl.resolution.DOInheritanceResolver;
import dataobjects.util.FileUtil;

import com.db4o.ext.ExtObjectContainer;

import java.io.File;

/**
 * Simplified DODatabaseBuilder that delegates to specialized components.
 * Uses DODatabaseOpener for robust database opening and DODatabaseReader for
 * information extraction.
 */
public class DODatabaseBuilder {

    private final DODatabaseOpener databaseOpener;
    private final DODatabaseReader databaseReader;
    private final DOInheritanceResolver inheritanceResolver;

    public DODatabaseBuilder() {
        this.databaseOpener = new DODatabaseOpener();
        this.databaseReader = new DODatabaseReader();
        this.inheritanceResolver = new DOInheritanceResolver();
    }

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
        // Note: Object resolution now happens in DOEngine after initialization
        DODatabase database = databaseReader.readDatabaseMeta(container, encoding, databaseSize, schema);

        // Resolve inheritance relationships here (needed before object resolution)
        inheritanceResolver.resolveInheritance(database, schema);

        return database;
    }
}
