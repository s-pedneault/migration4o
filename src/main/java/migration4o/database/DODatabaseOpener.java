package migration4o.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.config.Configuration;
import com.db4o.config.DotnetSupport;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.io.MemoryIoAdapter;
import com.db4o.reflect.jdk.JdkReflector;
import com.db4o.ta.TransparentActivationSupport;

/**
 * DB4O database opener with support for multiple encodings.
 * This should be the only mechanism used to open DB4O databases in this
 * platform.
 */
public class DODatabaseOpener {

    private DODatabaseEncoding successfulEncoding;
    private DODatabaseMonitor monitor;

    /**
     * Creates a new database opener without a monitor
     */
    public DODatabaseOpener() {
        this(null);
    }

    /**
     * Creates a new database opener with an optional monitor
     */
    public DODatabaseOpener(DODatabaseMonitor monitor) {
        this.monitor = monitor;
    }

    public ExtObjectContainer openDatabase(String filePath) {
        return openDatabase(filePath, true);
    }

    /**
     * Opens a DB4O database file with optional in-memory caching.
     * 
     * @param filePath       Path to the database file
     * @param useMemoryCache If true, loads the entire file into memory for faster
     *                       access
     * @return Opened database container
     */
    public ExtObjectContainer openDatabase(String filePath, boolean useMemoryCache) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }

        File dbFile = new File(filePath);
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Database file does not exist: " + filePath);
        }

        // If memory caching is requested, load the file into memory first
        if (useMemoryCache) {
            return openDatabaseInMemory(filePath, dbFile);
        }

        // Define encoding configurations to try in order
        DODatabaseEncoding[] encodingConfigs = {
                new DODatabaseEncoding("UTF-8 (default)", true, true, true),
                new DODatabaseEncoding("Latin-1 (legacy)", false, true, true),
                new DODatabaseEncoding("UTF-8 no-intern", true, false, true),
                new DODatabaseEncoding("Latin-1 no-intern", false, false, true),
                new DODatabaseEncoding("UTF-8 no-dotnet", true, true, false),
                new DODatabaseEncoding("Latin-1 no-dotnet", false, true, false)
        };

        Exception lastException = null;
        StringBuilder attemptLog = new StringBuilder();

        // Try each encoding configuration until one works
        for (DODatabaseEncoding encodingConfig : encodingConfigs) {
            if (monitor != null) {
                monitor.onTryingEncoding(encodingConfig.getDescription());
            }

            ObjectContainer container = null;
            try {
                // Create robust configuration with current encoding
                Configuration config = createDatabaseConfiguration(encodingConfig);
                container = Db4o.openFile(config, filePath);

                // Success! We can open the database with this encoding
                if (monitor != null) {
                    monitor.onDatabaseOpened(encodingConfig.getDescription());
                } else {
                    System.out
                            .println("Successfully opened database with encoding: " + encodingConfig.getDescription());
                }

                // Store the successful encoding
                this.successfulEncoding = encodingConfig;

                return container.ext();

            } catch (Exception e) {
                // Close container if there was an error
                if (container != null) {
                    try {
                        container.close();
                    } catch (Exception closeException) {
                        // Ignore close exception, we're already handling an error
                    }
                }

                // Detect file lock/busy error (common DB4O message) - these should be reported
                // immediately
                if (e.getMessage() != null && (e.getMessage().contains("Another process is using the file") ||
                        e.getMessage().toLowerCase().contains("locked") ||
                        e.getMessage().toLowerCase().contains("in use") ||
                        e.getMessage().toLowerCase().contains("busy") ||
                        e.getMessage().toLowerCase().contains("cannot access the file"))) {
                    String errorMsg = "Database file is already open or locked by another process: " + e.getMessage();
                    if (monitor != null) {
                        monitor.onDatabaseOpenFailed(errorMsg);
                    }
                    throw new RuntimeException(errorMsg, e);
                }

                // Notify monitor of failed encoding
                String errorType = e.getClass().getSimpleName();
                if (monitor != null) {
                    monitor.onEncodingFailed(encodingConfig.getDescription(), errorType);
                }

                // Silently log this attempt for potential final error report
                attemptLog.append(encodingConfig.getDescription()).append(": ").append(errorType);
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    attemptLog.append(" - ").append(e.getMessage());
                }
                attemptLog.append("\n");

                lastException = e;
            }
        }

        // If we get here, all encoding configurations failed
        String errorMsg = "Failed to open database with any encoding configuration. Attempted encodings:\n"
                + attemptLog.toString().trim();
        if (monitor != null) {
            monitor.onDatabaseOpenFailed(errorMsg);
        }
        throw new RuntimeException(errorMsg, lastException);
    }

    public DODatabaseEncoding getSuccessfulEncoding() {
        return successfulEncoding;
    }

    /**
     * Creates a robust DB4O configuration based on the encoding settings.
     * This configuration is proven to work with various DB4O database formats.
     */
    private Configuration createDatabaseConfiguration(DODatabaseEncoding encodingConfig) throws Exception {
        Configuration config = Db4o.newConfiguration();
        config.activationDepth(0);
        config.updateDepth(10);

        if (encodingConfig.isDotnetSupportEnabled()) {
            config.add(new DotnetSupport());
        }

        config.add(new TransparentActivationSupport());

        // Use standard JDK reflection without complex instrumentation
        // This is more reliable and works with all DB4O database formats
        config.reflectWith(new JdkReflector(DODatabaseOpener.class.getClassLoader()));
        config.allowVersionUpdates(true);
        config.callConstructors(true);
        config.exceptionsOnNotStorable(false);
        config.unicode(encodingConfig.isUnicodeEnabled());
        config.internStrings(encodingConfig.isInternStringsEnabled());

        return config;
    }

    /**
     * Opens the database by loading it entirely into memory.
     * This dramatically speeds up all subsequent operations by eliminating disk
     * I/O.
     * 
     * @param filePath Path to the database file
     * @param dbFile   The database file
     * @return Opened database container with in-memory storage
     */
    private ExtObjectContainer openDatabaseInMemory(String filePath, File dbFile) {
        try {
            // Read entire database file into memory
            if (monitor != null) {
                monitor.onTryingEncoding("Loading database into memory...");
            }

            long startTime = System.currentTimeMillis();
            byte[] fileContent = Files.readAllBytes(dbFile.toPath());
            long loadTime = System.currentTimeMillis() - startTime;

            if (monitor != null) {
                String sizeMsg = String.format("Loaded %.2f MB in %d ms",
                        fileContent.length / (1024.0 * 1024.0), loadTime);
                monitor.onTryingEncoding(sizeMsg);
            } else {
                System.out.printf("Loaded database into memory: %.2f MB in %d ms%n",
                        fileContent.length / (1024.0 * 1024.0), loadTime);
            }

            // Try opening with different encodings using in-memory adapter
            DODatabaseEncoding[] encodingConfigs = {
                    new DODatabaseEncoding("UTF-8 (default)", true, true, true),
                    new DODatabaseEncoding("Latin-1 (legacy)", false, true, true),
                    new DODatabaseEncoding("UTF-8 no-intern", true, false, true),
                    new DODatabaseEncoding("Latin-1 no-intern", false, false, true),
                    new DODatabaseEncoding("UTF-8 no-dotnet", true, true, false),
                    new DODatabaseEncoding("Latin-1 no-dotnet", false, true, false)
            };

            Exception lastException = null;

            for (DODatabaseEncoding encodingConfig : encodingConfigs) {
                if (monitor != null) {
                    monitor.onTryingEncoding(encodingConfig.getDescription());
                }

                ObjectContainer container = null;
                try {
                    // Create configuration with in-memory storage
                    Configuration config = createDatabaseConfiguration(encodingConfig);

                    // Create in-memory adapter and load file content
                    MemoryIoAdapter memoryAdapter = new MemoryIoAdapter();
                    memoryAdapter.put(filePath, fileContent);
                    config.io(memoryAdapter);

                    // Open database from memory
                    container = Db4o.openFile(config, filePath);

                    // Success!
                    if (monitor != null) {
                        monitor.onDatabaseOpened(encodingConfig.getDescription() + " (in-memory)");
                    } else {
                        System.out.println("Successfully opened database from memory with encoding: "
                                + encodingConfig.getDescription());
                    }

                    this.successfulEncoding = encodingConfig;
                    return container.ext();

                } catch (Exception e) {
                    if (container != null) {
                        try {
                            container.close();
                        } catch (Exception closeException) {
                            // Ignore
                        }
                    }

                    lastException = e;
                    String errorMsg = "Failed with " + encodingConfig.getDescription() + ": " + e.getMessage();
                    if (monitor != null) {
                        monitor.onEncodingFailed(encodingConfig.getDescription(), e.getMessage());
                    }
                }
            }

            // All encodings failed
            throw new RuntimeException(
                    "Could not open database from memory with any encoding configuration. Last error: "
                            + (lastException != null ? lastException.getMessage() : "Unknown"),
                    lastException);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read database file into memory: " + e.getMessage(), e);
        }
    }
}
