package migration4o.engine.migration.generic;

/**
 * Base class for all export contexts, providing common functionality.
 */
public abstract class ExportContext {
    protected final String outputDirectory;

    protected ExportContext(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }
}