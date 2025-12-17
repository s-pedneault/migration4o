package dataobjects.impl.database;

import dataobjects.impl.database.DODatabaseOpener;
import dataobjects.impl.database.DODatabaseEncoding;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.reflect.jdk.JdkReflector;
import com.db4o.ta.TransparentActivationSupport;
import com.db4o.config.DotnetSupport;

import java.io.File;

/**
 * DB4O database opener with support for multiple encodings.
 * This should be the only mechanism used to open DB4O databases in this
 * platform.
 */
public class DODatabaseOpener {

    private DODatabaseEncoding successfulEncoding;

    public ExtObjectContainer openDatabase(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }

        File dbFile = new File(filePath);
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Database file does not exist: " + filePath);
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
            ObjectContainer container = null;
            try {
                // Create robust configuration with current encoding
                Configuration config = createDatabaseConfiguration(encodingConfig);
                container = Db4o.openFile(config, filePath);

                // Success! We can open the database with this encoding
                System.out.println("Successfully opened database with encoding: " + encodingConfig.getDescription());

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
                    throw new RuntimeException(
                            "Database file is already open or locked by another process: " + e.getMessage(), e);
                }

                // Silently log this attempt for potential final error report
                attemptLog.append(encodingConfig.getDescription()).append(": ").append(e.getClass().getSimpleName());
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    attemptLog.append(" - ").append(e.getMessage());
                }
                attemptLog.append("\n");

                lastException = e;
            }
        }

        // If we get here, all encoding configurations failed
        throw new RuntimeException(
                "Failed to open database with any encoding configuration. Attempted encodings:\n"
                        + attemptLog.toString().trim(),
                lastException);
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
}
