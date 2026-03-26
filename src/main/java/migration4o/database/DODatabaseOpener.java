package migration4o.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.io.IoAdapter;

/**
 * DB4O database opener with support for multiple encodings.
 * This should be the only mechanism used to open DB4O databases in this
 * platform.
 */
public class DODatabaseOpener {

    private DODatabaseMonitor monitor;

    /**
     * Creates a new database opener with an optional monitor
     */
    public DODatabaseOpener(DODatabaseMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Opens a DB4O database file with optional in-memory caching.
     * 
     * @param context       Path to the database file
     * @param useMemoryCache If true, loads the entire file into memory for faster
     *                       access
     * @return Opened database container
     */
    public ExtObjectContainer openDatabase(DODatabaseContext context, boolean useMemoryCache) {
        if (context == null || context.databaseFilePath == null || context.databaseFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }

        File dbFile = new File(context.databaseFilePath);
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Database file does not exist: " + context.databaseFilePath);
        }

        IoAdapter adapter = null;

        if (useMemoryCache) {
            adapter = createMemoryAdapter(context, dbFile);
        }

        Exception lastException = null;
        StringBuilder attemptLog = new StringBuilder();

        for (DODatabaseEncoding encodingConfig : DODatabaseEncoding.encodings) {
            if (monitor != null) {
                monitor.onTryingEncoding(encodingConfig.description);
            }

            try {
                return openWithEncoding(context, encodingConfig, adapter);

            } catch (Exception e) {
                if (isFileLockedError(e)) {
                    String errorMsg = "Database file is already open or locked by another process: " + e.getMessage();
                    if (monitor != null) {
                        monitor.onDatabaseOpenFailed(errorMsg);
                    }
                    throw new RuntimeException(errorMsg, e);
                }

                String errorType = e.getClass().getSimpleName();
                if (monitor != null) {
                    monitor.onEncodingFailed(encodingConfig.description, useMemoryCache ? e.getMessage() : errorType);
                }

                attemptLog.append(encodingConfig.description).append(": ").append(errorType);
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    attemptLog.append(" - ").append(e.getMessage());
                }
                attemptLog.append("\n");

                lastException = e;
            }
        }

        String errorMsg;
        if (useMemoryCache) {
            errorMsg = "Could not open database from memory with any encoding configuration. Attempted encodings:\n" + attemptLog.toString().trim();
        } else {
            errorMsg = "Failed to open database with any encoding configuration. Attempted encodings:\n" + attemptLog.toString().trim();
        }

        if (monitor != null) {
            monitor.onDatabaseOpenFailed(errorMsg);
        }
        throw new RuntimeException(errorMsg, lastException);
    }

    private IoAdapter createMemoryAdapter(DODatabaseContext context, File dbFile) {
        try {
            if (monitor != null) {
                monitor.onTryingEncoding("Loading database into memory...");
            }

            long startTime = System.currentTimeMillis();
            byte[] fileContent = Files.readAllBytes(dbFile.toPath());
            long loadTime = System.currentTimeMillis() - startTime;

            if (monitor != null) {
                String sizeMsg = String.format("Loaded %.2f MB in %d ms", fileContent.length / (1024.0 * 1024.0), loadTime);
                monitor.onTryingEncoding(sizeMsg);
            }

            SafeMemoryIoAdapter memoryAdapter = new SafeMemoryIoAdapter();
            memoryAdapter.put(context.databaseFilePath, fileContent);
            return memoryAdapter;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read database file into memory: " + e.getMessage(), e);
        }
    }

    private ExtObjectContainer openWithEncoding(DODatabaseContext context, DODatabaseEncoding encodingConfig, IoAdapter adapter) throws Exception {
        ObjectContainer container = null;
        try {
            Configuration config = DODatabaseConfiguration.create(encodingConfig);

            if (adapter != null) {
                config.io(adapter);
            }

            container = Db4o.openFile(config, context.databaseFilePath);

            if (monitor != null) {
                monitor.onDatabaseOpened(encodingConfig.description);
            }

            context.encoding = encodingConfig;
            return container.ext();
        } catch (Exception e) {
            if (container != null) {
                try {
                    container.close();
                } catch (Exception closeException) {
                    // Ignore close exception, we're already handling an error
                }
            }
            throw e;
        }
    }

    private boolean isFileLockedError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return message.contains("Another process is using the file") || lower.contains("locked") || lower.contains("in use") || lower.contains("busy") || lower.contains("cannot access the file");
    }
}
