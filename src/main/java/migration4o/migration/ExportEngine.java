package migration4o.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import migration4o.database.DODatabaseContext;
import migration4o.migration.format.ExportContext;
import migration4o.migration.format.FormatHandler;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.tasks.ExportSelectionAdvisor;
import migration4o.migration.tasks.ModuleExporter;
import migration4o.migration.tasks.ModulePathUtil;
import migration4o.migration.tasks.ObjectExportLoop;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.SchemaUtil;
import migration4o.util.tools.structuredwriter.StructuredWriterMetadata;

/**
 * Orchestrates multi-format export operations via {@link FormatHandler} hooks.
 * <p>
 * The main entry point is {@link #exportModules}: it runs a class-to-class loop
 * so all format handlers write one class's file before moving to the next
 * class, keeping statistics and reference tracking shared across formats.
 */
public class ExportEngine {
    public final ExportOperation operation;

    /**
     * Creates an export engine backed by the given database context.
     *
     * @param schema Reference schema
     * @param databaseSchema Database schema
     * @param databasePath Database file path (for naming output folders)
     * @param dbContext Database context (holds the open container)
     */
    public ExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath, DODatabaseContext dbContext) {
        this.operation = new ExportOperation();
        this.operation.referenceSchema = schema;
        this.operation.databaseSchema = databaseSchema;
        this.operation.databasePath = databasePath;
        this.operation.dbContext = dbContext;
        this.operation.maxObjectsPerClass = null;
        this.operation.exportNativeIds = false;
        this.operation.outputFormat = "XML";
        this.operation.applyUserSelectedFieldExclusions = true;
        this.operation.applySkipWhenConditions = true;
        this.operation.applyExportCriteriaFilters = true;
        this.operation.skipObjectsWithoutExportableFields = true;
        this.operation.availableSkipUserOptions = SchemaUtil.collectSkipUserOptions(schema);
        this.operation.selectedSkipUserOptions = new ArrayList<>();
        this.operation.exportedObjectIds = new HashSet<>();
        this.operation.useSharedTracking = false;
        this.operation.container = dbContext != null ? dbContext.container : null;

        if (operation.container == null) {
            throw new IllegalStateException("No database is open.");
        }
    }

    // ── Main entry point
    // ──────────────────────────────────────────────────────

    /**
     * Exports all modules to all requested formats via handler hooks. Runs a
     * class-to-class loop: all handlers write one class's file before moving to
     * the next class, so statistics and reference tracking are shared across
     * formats.
     *
     * @param modules Modules to export (in order)
     * @param modulePaths Optional display-path prefix per module (used by
     * {@code HtmlFormatHandler.init()} for the nav tree)
     * @param handlers Pre-created handler instances (one per format)
     * @return shared export statistics
     */
    public ExportStatistics exportModules(List<DOSchemaModule> modules, List<String> modulePaths, List<FormatHandler> handlers) throws Exception {

        operation.statistics = new ExportStatistics(operation.monitor);
        operation.statistics.fullTracking = operation.fullTracking;

        ExportContext ctx = new ExportContext(operation);
        ctx.basePath = operation.getBaseOutputPath(operation.baseOutputPath);
        ctx.statistics = operation.statistics;
        ctx.referencedClassTracker = new ReferencedClassTracker();

        // Let HtmlFormatHandler.init() find module list for nav tree building
        operation.exportModules = modules;
        operation.exportModulePaths = modulePaths;

        Files.createDirectories(ctx.basePath);

        // When seed queries are defined, run seed-based selection to pick
        // objects matching the queries and their bidirectional closure.
        if (operation.seedQueries != null && !operation.seedQueries.isEmpty()) {
            System.out.println("[DEBUG-DossPrev] ExportEngine: SEED branch entered — " + operation.seedQueries.size() + " seed query(ies)");
            for (var sq : operation.seedQueries) {
                System.out.println("[DEBUG-DossPrev]   query: class=" + sq.getClassName() + ", conditions=" + (sq.getConditions() != null ? sq.getConditions().size() : 0));
            }
            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Seed-based selection: finding matching objects and related closure…");
            }
            ExportSelectionAdvisor advisor = new ExportSelectionAdvisor(operation.container, operation.referenceSchema, operation.databaseSchema, operation.seedQueries, operation.maxObjectsPerClass);
            ExportSelectionAdvisor.SelectionResult sel = advisor.computeSeedSelection(modules, operation.monitor);
            operation.preselectedObjectIds = sel.rankedIds;
            operation.preselectedRequiredCounts = sel.requiredCounts;
            if (operation.monitor != null) {
                int affected = operation.preselectedObjectIds != null ? operation.preselectedObjectIds.size() : 0;
                operation.monitor.onStatusMessage("Seed selection complete — " + affected + " class(es) with selected objects.");
            }
        }
        // When a per-class cap is active, run a pre-flight analysis to pick the
        // most mutually-referenced N objects per class rather than just the
        // first N.
        else if (operation.maxObjectsPerClass != null && operation.maxObjectsPerClass > 0) {
            System.out.println("[DEBUG-DossPrev] ExportEngine: CAP branch entered (no seeds) — maxObjectsPerClass=" + operation.maxObjectsPerClass);
            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Smart selection: analysing cross-class relationships…");
            }
            ExportSelectionAdvisor advisor = new ExportSelectionAdvisor(operation.container, operation.referenceSchema, operation.databaseSchema, operation.maxObjectsPerClass);
            ExportSelectionAdvisor.SelectionResult sel = advisor.computeSelection(modules, operation.monitor);
            operation.preselectedObjectIds = sel.rankedIds;
            operation.preselectedRequiredCounts = sel.requiredCounts;
            if (operation.monitor != null) {
                int affected = operation.preselectedObjectIds != null ? operation.preselectedObjectIds.size() : 0;
                operation.monitor.onStatusMessage("Smart selection complete — " + affected + " class(es) with optimised selection.");
            }
        }

        ModuleExporter me = new ModuleExporter(operation);
        int totalClasses = 0;
        for (DOSchemaModule m : modules) {
            totalClasses += me.countTotalClasses(m);
        }
        if (operation.monitor != null) {
            operation.monitor.onExportStart("All modules", totalClasses);
        }

        // init — one call per handler before any file is opened
        for (FormatHandler handler : handlers) {
            handler.init(ctx);
        }

        // Register all module classes so referenced-class detection works
        // correctly
        for (DOSchemaModule m : modules) {
            me.registerModuleClasses(m, ctx.referencedClassTracker);
        }

        // Class-to-class export loop
        Throwable exportError = null;
        try {
            for (DOSchemaModule module : modules) {
                exportModuleRecursiveNew(ctx, module, handlers);
            }

            // referenced-class pass and final done hook
            for (int i = 0; i < handlers.size(); i++) {
                if (i > 0 && operation.statistics != null) {
                    operation.statistics.skipDiagnostics = true;
                }
                try {
                    handlers.get(i).onReferencedClasses(ctx);
                } finally {
                    if (operation.statistics != null) {
                        operation.statistics.skipDiagnostics = false;
                    }
                }
            }
            for (FormatHandler handler : handlers) {
                handler.done(ctx);
            }
        } catch (Throwable t) {
            exportError = t;
        }

        operation.statistics.schemaWarnings.clear();
        operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());

        if (operation.monitor != null) {
            if (exportError != null) {
                String msg = exportError.getMessage() != null ? exportError.getMessage() : exportError.getClass().getSimpleName();
                operation.monitor.onExportError("All modules", msg);
            } else {
                operation.monitor.onExportComplete("All modules", operation.statistics.getUniqueExportedCount(), operation.statistics.schemaWarnings.size());
            }
        }

        if (exportError instanceof Error)
            throw (Error) exportError;
        if (exportError != null)
            throw (Exception) exportError;
        return operation.statistics;
    }

    // ── Private helpers
    // ───────────────────────────────────────────────────────

    private void exportModuleRecursiveNew(ExportContext ctx, DOSchemaModule module, List<FormatHandler> handlers) throws Exception {
        ctx.pushModule(module);
        try {
            if (operation.monitor != null) {
                operation.monitor.onModuleStart(module.name, module.classConfigs.size(), ctx.moduleChain.size() - 1);
            }

            for (ClassExportConfig config : module.classConfigs) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;

                String className = config.getClassName();
                DOSchemaClass schemaClass = operation.referenceSchema.findClassByName(className);
                if (schemaClass == null) {
                    // Fire onClassComplete so the progress counter advances
                    // even for classes that only exist in module config.
                    for (FormatHandler h : handlers) {
                        if (operation.monitor != null)
                            operation.monitor.onClassComplete(className, 0, h.displayName());
                    }
                    continue;
                }
                DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
                if (dbSchemaClass == null) {
                    for (FormatHandler h : handlers) {
                        if (operation.monitor != null)
                            operation.monitor.onClassComplete(className, 0, h.displayName());
                    }
                    continue;
                }

                ctx.setClass(schemaClass, config);
                try {
                    exportClassToAllHandlers(ctx, dbSchemaClass, handlers);
                } catch (Throwable t) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    System.err.println("[Export error] class " + className + ": " + msg);
                    if (operation.statistics != null) {
                        Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
                        operation.statistics.addError(-1, className, "Class export failed: " + msg, wrapped);
                    }
                    if (operation.monitor != null) {
                        operation.monitor.onStatusMessage("Error exporting " + className + ": " + msg);
                    }
                } finally {
                    ctx.clearClass();
                }
            }

            for (DOSchemaModule child : module.children) {
                if (operation.monitor != null && operation.monitor.isCancelled())
                    break;
                exportModuleRecursiveNew(ctx, child, handlers);
            }

            if (operation.monitor != null) {
                operation.monitor.onModuleComplete(module.name);
            }
        } finally {
            ctx.popModule();
        }
    }

    private void exportClassToAllHandlers(ExportContext ctx, DOSchemaClass dbSchemaClass, List<FormatHandler> handlers) throws Exception {
        String className = dbSchemaClass.source;
        // ObjectExporter nulls ctx.schemaClass in its finally block after each
        // handler's loop.
        // Snapshot here and restore at the top of every iteration so every
        // format handler
        // (HTML, etc.) sees the correct class identity in onClassStart /
        // onObjectProgress.
        DOSchemaClass refClass = ctx.schemaClass;
        ClassExportConfig refConfig = ctx.exportConfig;
        Path moduleRelPath = ctx.moduleRelativePath();
        for (int i = 0; i < handlers.size(); i++) {
            ctx.setClass(refClass, refConfig); // restore after previous
                                               // handler's ObjectExporter
                                               // nulled it
            FormatHandler handler = handlers.get(i);
            Path filePath = ctx.basePath.resolve(handler.folderName()).resolve(moduleRelPath).resolve(ctx.exportConfig.getDestinationFileName() + handler.extension());
            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Exporting " + handler.displayName() + ": " + className);
            }
            handler.openWriter(filePath);
            // Skip expensive diagnostics on non-primary handlers to avoid
            // accumulating duplicate relationship/decision tracking data.
            // The first handler (usually XML) records full diagnostics;
            // subsequent handlers (e.g. HTML) still traverse the objects
            // (already cached in memory) but skip the map-heavy tracking.
            if (i > 0 && operation.statistics != null) {
                operation.statistics.skipDiagnostics = true;
            }
            try {
                handler.open(ctx);
                new ObjectExportLoop(ctx, handler).run(dbSchemaClass);
            } finally {
                try {
                    if (operation.monitor != null) {
                        operation.monitor.onStatusMessage("Writing " + handler.displayName() + " file: " + filePath.getFileName());
                    }
                    handler.close(ctx);
                } catch (Throwable t) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    System.err.println("[Export error] handler.close() [" + handler.displayName() + "] " + className + ": " + msg);
                    if (operation.statistics != null) {
                        Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
                        operation.statistics.addError(-1, className, handler.displayName() + " write failed: " + msg, wrapped);
                    }
                    if (operation.monitor != null) {
                        operation.monitor.onStatusMessage("[ERROR] " + handler.displayName() + " write failed for " + className + ": " + msg);
                    }
                } finally {
                    if (operation.statistics != null) {
                        operation.statistics.skipDiagnostics = false;
                    }
                }
            }
        }
    }

    // ── Utilities
    // ─────────────────────────────────────────────────────────────

    public static StructuredWriterMetadata getMetadata(String module, String type, int objects) {
        StructuredWriterMetadata metadata = new StructuredWriterMetadata();
        metadata.generator = "Migration4o";
        metadata.provider = "Gestion Technologies";
        metadata.module = ModulePathUtil.sanitizeModuleName(module);
        metadata.type = type;
        metadata.objects = objects >= 0 ? String.valueOf(objects) : "";
        metadata.date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return metadata;
    }
}
