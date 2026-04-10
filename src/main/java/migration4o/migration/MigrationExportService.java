package migration4o.migration;

import java.nio.file.Files;
import java.util.List;

import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.migration.tasks.ExportModuleLoop;
import migration4o.migration.tasks.ExportPreSelection;
import migration4o.migration.tasks.ModuleExporter;
import migration4o.models.schema.DOSchemaModule;
import migration4o.schema.DOSchemaService;
import migration4o.util.JsViewerHtmlGenerator;

/**
 * Service for coordinating export operations. Handles validation and multi-format export execution via {@link FormatHandler} hooks.
 * <p>
 * All exports — whether triggered from the UI Export button or {@code --repeat-export} — use the single {@link #exportModules} method. The main loop runs class-by-class so all format handlers write one class's file before moving to the next, keeping statistics and reference tracking shared across formats.
 */
public class MigrationExportService {

    private final DOSchemaService schemaService = DOSchemaService.getInstance();

    public ValidationResult validateExportPrerequisites(migration4o.database.DODatabaseContext dbContext) {
        if (dbContext == null || !dbContext.isDatabaseOpen()) {
            return ValidationResult.error("No database is currently open. Please open a database first.", "No Database");
        }

        if (!schemaService.isSchemaLoaded()) {
            return ValidationResult.error("No reference schema loaded. Please load the schema first.", "No Schema");
        }

        return ValidationResult.success();
    }

    /**
     * Exports all modules to all requested formats via handler hooks.
     * <p>
     * Runs a class-to-class loop: all handlers write one class's file before moving to the next class, so statistics and reference tracking are shared across formats.
     *
     * @param request Fully-configured export request (must have database set)
     * @param modules Modules to export (in order)
     * @return shared export statistics
     */
    public ExportStatistics exportModules(ExportRequest request, List<DOSchemaModule> modules) throws Exception {
        if (request.database == null) {
            throw new IllegalStateException("No database is open.");
        }

        List<FormatHandler> handlers = ExportOutputOption.toHandlers(request.outputOptions);

        ExportCurrentState ctx = new ExportCurrentState(request);
        ctx.basePath = request.getBaseOutputPath(request.baseOutputPath);
        ctx.statistics = new ExportStatistics(request.monitor);
        ctx.statistics.fullTracking = request.fullTracking;
        // Let HtmlFormatHandler.init() find module list for nav tree building
        ctx.exportModules = modules;
        ctx.organizationFilter = request.organizationConfig != null ? new OrganizationFilter(request.organizationConfig) : null;

        Files.createDirectories(ctx.basePath);
        JsViewerHtmlGenerator.copyGlobalAssets(ctx.basePath);

        new ExportPreSelection(request).run(modules);

        ModuleExporter me = new ModuleExporter(request);
        int totalClasses = 0;
        for (DOSchemaModule m : modules) {
            totalClasses += me.countTotalClasses(m);
        }
        if (request.monitor != null) {
            request.monitor.onExportStart("All modules", totalClasses);
        }

        // Init — one call per handler before any file is opened
        for (FormatHandler handler : handlers) {
            handler.init(ctx);
        }

        // Class-to-class export loop
        Throwable exportError = null;
        try {
            new ExportModuleLoop(ctx, handlers).runAll(modules);

            for (FormatHandler handler : handlers) {
                handler.done(ctx);
            }
        } catch (Throwable t) {
            exportError = t;
        }

        ctx.statistics.schemaWarnings.clear();
        ctx.statistics.schemaWarnings.addAll(ctx.statistics.duplicationDetector.generateDuplicateWarnings());

        if (request.monitor != null) {
            if (exportError != null) {
                String msg = exportError.getMessage() != null ? exportError.getMessage() : exportError.getClass().getSimpleName();
                request.monitor.onExportError("All modules", msg);
            } else {
                request.monitor.onExportComplete("All modules", ctx.statistics.getUniqueExportedCount(), ctx.statistics.schemaWarnings.size());
            }
        }

        if (exportError instanceof Error)
            throw (Error) exportError;
        if (exportError != null)
            throw (Exception) exportError;
        return ctx.statistics;
    }

}