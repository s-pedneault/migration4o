package dataobjects.api.migration.generic;

import dataobjects.api.engine.DOEngine;
import java.io.IOException;

/**
 * Generic export engine that can export to multiple formats.
 * Delegates format-specific operations to an ExportFormatHandler.
 */
public interface DOGenericExportEngine {

    /**
     * Export database contents using the specified format handler.
     * Creates output files in the default directory for the format.
     *
     * @param engine  The fully-loaded DOEngine instance
     * @param handler The format-specific handler that controls output generation
     * @throws IOException if there's an error writing the output files
     */
    void export(DOEngine engine, ExportFormatHandler handler) throws IOException;

    /**
     * Export database contents using the specified format handler to a custom
     * directory.
     *
     * @param engine          The fully-loaded DOEngine instance
     * @param handler         The format-specific handler that controls output
     *                        generation
     * @param outputDirectory The directory where output files should be created
     * @throws IOException if there's an error writing the output files
     */
    void export(DOEngine engine, ExportFormatHandler handler, String outputDirectory) throws IOException;

}
