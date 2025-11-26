package dataobjects.api.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.impl.migration.generic.ExportOrchestrator;
import java.io.IOException;

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