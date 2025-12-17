package migration4o.engine.migration.generic;

import java.io.IOException;

import migration4o.engine.DOEngine;

/**
 * Main API class for the enhanced export system.
 * Provides a clean, simple interface for performing exports.
 */
public class DOGenericExportEngine {

    /**
     * Export using a format handler with its default output directory.
     */
    public static void export(DOEngine engine, ExportFormatHandler handler) throws IOException {
        ExportOrchestrator orchestrator = new ExportOrchestrator(engine);
        orchestrator.export(handler);
    }

    /**
     * Export using a format handler with a custom output directory.
     */
    public static void export(DOEngine engine, ExportFormatHandler handler, String outputDirectory)
            throws IOException {
        ExportOrchestrator orchestrator = new ExportOrchestrator(engine);
        orchestrator.export(handler, outputDirectory);
    }
}