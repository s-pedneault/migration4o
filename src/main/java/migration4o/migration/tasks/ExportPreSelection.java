package migration4o.migration.tasks;

import java.util.List;

import migration4o.migration.ExportRequest;
import migration4o.models.schema.DOSchemaModule;

/**
 * Runs the pre-flight object-selection phase before the main export loop.
 * <p>
 * Two mutually exclusive strategies are supported:
 * <ul>
 * <li><b>Seed selection</b>: when {@code operation.seedQueries} is non-empty,
 * resolves matching objects and their bidirectional closure.</li>
 * <li><b>Cap selection</b>: when {@code operation.maxObjectsPerClass} is set,
 * picks the best-N mutually-referenced objects per class.</li>
 * </ul>
 * Either way, the result is written into {@code operation.preselectedObjectIds}
 * and {@code operation.preselectedRequiredCounts}. When neither condition holds
 * the method is a no-op.
 */
public class ExportPreSelection {

    private final ExportRequest operation;

    public ExportPreSelection(ExportRequest operation) {
        this.operation = operation;
    }

    public void run(List<DOSchemaModule> modules) throws Exception {
        if (operation.seedQueries != null && !operation.seedQueries.isEmpty()) {
            runSeedSelection(modules);
        } else if (operation.maxObjectsPerClass != null && operation.maxObjectsPerClass > 0) {
            runCapSelection(modules);
        }
    }

    // ── Private helpers
    // ───────────────────────────────────────────────────────

    private void runSeedSelection(List<DOSchemaModule> modules) throws Exception {
        System.out.println("[DEBUG-DossPrev] ExportPreSelection: SEED branch entered — " + operation.seedQueries.size() + " seed query(ies)");
        for (var sq : operation.seedQueries) {
            System.out.println("[DEBUG-DossPrev]   query: class=" + sq.getClassName() + ", conditions=" + (sq.getConditions() != null ? sq.getConditions().size() : 0));
        }
        notify("Seed-based selection: finding matching objects and related closure…");

        ExportSelectionAdvisor advisor = new ExportSelectionAdvisor(operation.referenceSchema, operation.database, operation.seedQueries, operation.maxObjectsPerClass);
        applyResult(advisor.computeSeedSelection(modules, operation.monitor));

        int affected = operation.preselectedObjectIds != null ? operation.preselectedObjectIds.size() : 0;
        notify("Seed selection complete — " + affected + " class(es) with selected objects.");
    }

    private void runCapSelection(List<DOSchemaModule> modules) throws Exception {
        System.out.println("[DEBUG-DossPrev] ExportPreSelection: CAP branch entered (no seeds) — maxObjectsPerClass=" + operation.maxObjectsPerClass);
        notify("Smart selection: analysing cross-class relationships…");

        ExportSelectionAdvisor advisor = new ExportSelectionAdvisor(operation.referenceSchema, operation.database, operation.maxObjectsPerClass);
        applyResult(advisor.computeSelection(modules, operation.monitor));

        int affected = operation.preselectedObjectIds != null ? operation.preselectedObjectIds.size() : 0;
        notify("Smart selection complete — " + affected + " class(es) with optimised selection.");
    }

    private void applyResult(ExportSelectionAdvisor.SelectionResult sel) {
        operation.preselectedObjectIds = sel.rankedIds;
        operation.preselectedRequiredCounts = sel.requiredCounts;
    }

    private void notify(String message) {
        if (operation.monitor != null) {
            operation.monitor.onStatusMessage(message);
        }
    }
}
