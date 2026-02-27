package migration4o.database;

import com.db4o.Db4o;
import com.db4o.config.Configuration;
import com.db4o.config.DotnetSupport;
import com.db4o.reflect.jdk.JdkReflector;
import com.db4o.ta.TransparentActivationSupport;

/**
 * Utility class for creating DB4O Configuration objects based on encoding settings.
 */
public class DODatabaseConfiguration {

    /**
     * Creates a robust DB4O configuration based on the encoding settings.
     * This configuration is proven to work with various DB4O database formats.
     *
     * @param encodingConfig The encoding configuration to use
     * @return The DB4O Configuration object
     * @throws Exception if configuration fails
     */
    public static Configuration create(DODatabaseEncoding encodingConfig) throws Exception {
        Configuration config = Db4o.newConfiguration();
        config.activationDepth(0);
        config.updateDepth(10);

        if (encodingConfig.dotnetSupportEnabled) {
            config.add(new DotnetSupport());
        }

        config.add(new TransparentActivationSupport());

        // Use standard JDK reflection without complex instrumentation
        // This is more reliable and works with all DB4O database formats
        config.reflectWith(new JdkReflector(DODatabaseConfiguration.class.getClassLoader()));
        config.allowVersionUpdates(true);
        config.callConstructors(true);
        config.exceptionsOnNotStorable(false);
        config.unicode(encodingConfig.unicodeEnabled);
        config.internStrings(encodingConfig.internStringsEnabled);

        return config;
    }
}