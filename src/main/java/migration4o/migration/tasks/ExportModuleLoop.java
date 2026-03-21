package migration4o.migration.tasks;

import java.nio.file.Path;
import java.util.List;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.migration.format.ExportCurrentState;
import migration4o.migration.format.FormatHandler;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;

/**
 * Drives the recursive module/class export loop, dispatching each class to
 * every {@link FormatHandler} in order.
 * <p>
 * The class-to-class ordering (all handlers write one class before moving on)
 * keeps statistics and reference tracking shared across formats without
 * reopening files.
 */
public class ExportModuleLoop {

    private final ExportCurrentState ctx;
    private final List<FormatHandler> handlers;

    public ExportModuleLoop(ExportCurrentState ctx, List<FormatHandler> handlers) {
        this.ctx = ctx;
        this.handlers = handlers;
    }

    /** Exports all modules in order. */
    public void runAll(List<DOSchemaModule> modules) throws Exception {
        for (DOSchemaModule module : modules) {
            runModule(module);
        }
    }

    // ── Private helpers
    // ───────────────────────────────────────────────────────

    private void runModule(DOSchemaModule module) throws Exception {
        ctx.pushModule(module);
        try {
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onModuleStart(module.name, module.classConfigs.size(), ctx.moduleChain.size() - 1);
            }

            for (ClassExportConfig config : module.classConfigs) {
                if (ctx.request.monitor != null && ctx.request.monitor.isCancelled())
                    break;

                String className = config.getClassName();
                DOSchemaClass schemaClass = ctx.request.referenceSchema.findClassByName(className);
                if (schemaClass == null) {
                    fireClassComplete(className);
                    continue;
                }

                DODatabaseClass dbClass = null;
                if (ctx.request.database != null) {
                    dbClass = ctx.request.database.findClassByName(className);
                }
                if (dbClass == null) {
                    fireClassComplete(className);
                    continue;
                }
                if (dbClass.schemaClass == null) {
                    System.err.println("[WARNING] Database class '" + className + "' has no reference schema link — data may be lost during export.");
                }

                ctx.setClass(schemaClass, config);
                try {
                    runClass(dbClass);
                } catch (Throwable t) {
                    recordClassError(className, t);
                } finally {
                    ctx.clearClass();
                }
            }

            for (DOSchemaModule child : module.children) {
                if (ctx.request.monitor != null && ctx.request.monitor.isCancelled())
                    break;
                runModule(child);
            }

            if (ctx.request.monitor != null) {
                ctx.request.monitor.onModuleComplete(module.name);
            }
        } finally {
            ctx.popModule();
        }
    }

    private void runClass(DODatabaseClass dbClass) throws Exception {
        String className = dbClass.attributes.source;
        // Snapshot class/config: ObjectExportLoop nulls ctx.schemaClass in its
        // finally block, so each handler iteration must restore these.
        DOSchemaClass refClass = ctx.schemaClass;
        ClassExportConfig refConfig = ctx.exportConfig;
        Path moduleRelPath = ctx.moduleRelativePath();

        for (int i = 0; i < handlers.size(); i++) {
            ctx.setClass(refClass, refConfig);
            FormatHandler handler = handlers.get(i);
            Path filePath = ctx.basePath.resolve(handler.folderName()).resolve(moduleRelPath).resolve(ctx.exportConfig.getDestinationFileName() + handler.extension());

            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("Exporting " + handler.displayName() + ": " + className);
            }
            handler.openWriter(filePath);

            // Skip expensive diagnostics on non-primary handlers to avoid
            // accumulating duplicate relationship/decision tracking data.
            if (i > 0 && ctx.statistics != null) {
                ctx.statistics.skipDiagnostics = true;
            }
            try {
                handler.open(ctx);
                new ObjectExportLoop(ctx, handler).run(dbClass);
            } finally {
                try {
                    if (ctx.request.monitor != null) {
                        ctx.request.monitor.onStatusMessage("Writing " + handler.displayName() + " file: " + filePath.getFileName());
                    }
                    handler.close(ctx);
                } catch (Throwable t) {
                    recordHandlerCloseError(className, handler, t);
                } finally {
                    if (ctx.statistics != null) {
                        ctx.statistics.skipDiagnostics = false;
                    }
                }
            }
        }
    }

    private void fireClassComplete(String className) {
        for (FormatHandler h : handlers) {
            if (ctx.request.monitor != null)
                ctx.request.monitor.onClassComplete(className, 0, h.displayName());
        }
    }

    private void recordClassError(String className, Throwable t) {
        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        System.err.println("[Export error] class " + className + ": " + msg);
        if (ctx.statistics != null) {
            Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
            ctx.statistics.addError(-1, className, "Class export failed: " + msg, wrapped);
        }
        if (ctx.request.monitor != null) {
            ctx.request.monitor.onStatusMessage("Error exporting " + className + ": " + msg);
        }
    }

    private void recordHandlerCloseError(String className, FormatHandler handler, Throwable t) {
        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        System.err.println("[Export error] handler.close() [" + handler.displayName() + "] " + className + ": " + msg);
        if (ctx.statistics != null) {
            Exception wrapped = t instanceof Exception ? (Exception) t : new RuntimeException(msg, t);
            ctx.statistics.addError(-1, className, handler.displayName() + " write failed: " + msg, wrapped);
        }
        if (ctx.request.monitor != null) {
            ctx.request.monitor.onStatusMessage("[ERROR] " + handler.displayName() + " write failed for " + className + ": " + msg);
        }
    }
}
