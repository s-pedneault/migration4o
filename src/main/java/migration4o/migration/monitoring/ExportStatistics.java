package migration4o.migration.monitoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchema;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.SchemaUtil;

/**
 * Tracks statistics and errors during export operations. Also serves as the
 * export result.
 */
public class ExportStatistics {
    public String exportName = "";
    public String outputPath = "";
    public int objectsAttempted = 0;
    public int objectsSucceeded = 0;
    public int objectsFiltered = 0;
    /** Unique object IDs successfully exported (root + embedded). */
    public final Set<Long> allExportedObjectIds = new HashSet<>();
    public final List<ExportError> errors = new ArrayList<>();
    public final List<ExportWarning> schemaWarnings = new ArrayList<>();
    public final Map<String, Integer> exportedClassCounts = new java.util.HashMap<>();
    public final Map<String, List<Long>> exportedObjectIds = new java.util.HashMap<>();
    public final Map<Long, Set<String>> objectDecisionNotes = new java.util.HashMap<>();
    public final Map<String, Set<String>> exportedRelationshipNotes = new java.util.HashMap<>();
    public final Map<String, Set<String>> skippedRelationshipNotes = new java.util.HashMap<>();

    public final DOExportMonitor monitor;
    public final ObjectDuplicationDetector duplicationDetector = new ObjectDuplicationDetector();

    /**
     * When {@code true}, expensive per-object diagnostic tracking
     * ({@link #recordObjectDecision}, {@link #recordRelationshipExported},
     * {@link #recordRelationshipSkipped}) is skipped. Set by the export engine
     * on non-primary format-handler passes to avoid accumulating duplicate
     * diagnostics while still tracking progress counters.
     */
    public boolean skipDiagnostics = false;

    /**
     * When {@code false}, all expensive analytical tracking is disabled:
     * {@link #allExportedObjectIds} is not populated, per-object decision notes
     * and relationship notes are not collected, and {@link #exportedObjectIds}
     * is not built in {@link #setExportInfo}. {@link #exportedClassCounts} is
     * always maintained for the count column in the coverage panel. Defaults to
     * {@code true} for full backwards-compatible behaviour.
     */
    public boolean fullTracking = true;
    public final Map<String, Set<Long>> exportedObjectIdsSet = new java.util.HashMap<>();
    public String currentClassName = "";
    public int currentClassTotal = 0;
    /**
     * Resets to 0 each time {@link #setCurrentClass} is called; counts every
     * activation attempt, including embedded objects.
     */
    public int currentClassAttempted = 0;

    public ExportStatistics() {
        this(null);
    }

    public ExportStatistics(DOExportMonitor monitor) {
        this.monitor = monitor;
    }

    public void setCurrentClass(String className, int totalObjects) {
        this.currentClassName = className;
        this.currentClassTotal = totalObjects;
        this.currentClassAttempted = 0;
    }

    public void incrementAttempted() {
        objectsAttempted++;
        currentClassAttempted++;
        if (monitor != null && currentClassAttempted % 10 == 0 && currentClassTotal > 0) {
            monitor.onObjectProgress(currentClassName, currentClassAttempted, currentClassTotal);
        }
    }

    public void incrementSucceeded() {
        objectsSucceeded++;
    }

    /**
     * Records a unique exported object ID. Because DB4O object IDs are globally
     * unique, using a set naturally deduplicates across classes and embedding
     * depths.
     * <p>No-op when {@link #fullTracking} is {@code false} — the global ID set
     * is only used for reachability coverage analysis.</p>
     */
    public void recordExportedObjectId(long objectId) {
        if (fullTracking) {
            allExportedObjectIds.add(objectId);
        }
    }

    /** Returns the count of unique objects exported (root + embedded). */
    public int getUniqueExportedCount() {
        return allExportedObjectIds.size();
    }

    public void incrementFiltered() {
        objectsFiltered++;
    }

    public void recordObjectDecision(long objectId, String className, String decision) {
        if (skipDiagnostics || !fullTracking)
            return;
        if (objectId <= 0 || decision == null || decision.trim().isEmpty()) {
            return;
        }

        String note = className != null && !className.isBlank() ? className + ": " + decision : decision;
        objectDecisionNotes.computeIfAbsent(objectId, key -> new LinkedHashSet<>()).add(note);
    }

    public void recordRelationshipExported(long parentObjectId, long childObjectId, String sourceContainingClass, String sourceFieldName, String detail) {
        if (skipDiagnostics || !fullTracking)
            return;
        if (parentObjectId <= 0 || childObjectId <= 0) {
            return;
        }
        String note = buildRelationshipNote(sourceContainingClass, sourceFieldName, detail != null && !detail.isBlank() ? detail : "exported");
        exportedRelationshipNotes.computeIfAbsent(edgeKey(parentObjectId, childObjectId), key -> new LinkedHashSet<>()).add(note);
    }

    public void recordRelationshipSkipped(long parentObjectId, long childObjectId, String sourceContainingClass, String sourceFieldName, String reason) {
        if (skipDiagnostics || !fullTracking)
            return;
        if (parentObjectId <= 0 || childObjectId <= 0 || reason == null || reason.trim().isEmpty()) {
            return;
        }
        String note = buildRelationshipNote(sourceContainingClass, sourceFieldName, reason);
        skippedRelationshipNotes.computeIfAbsent(edgeKey(parentObjectId, childObjectId), key -> new LinkedHashSet<>()).add(note);
    }

    private static String buildRelationshipNote(String sourceContainingClass, String sourceFieldName, String detail) {
        StringBuilder builder = new StringBuilder();
        if (sourceContainingClass != null && !sourceContainingClass.isBlank()) {
            builder.append(sourceContainingClass);
        }
        if (sourceFieldName != null && !sourceFieldName.isBlank()) {
            if (builder.length() > 0) {
                builder.append(".");
            }
            builder.append(sourceFieldName);
        }
        if (builder.length() > 0) {
            builder.append(" → ");
        }
        builder.append(detail);
        return builder.toString();
    }

    public static String edgeKey(long parentObjectId, long childObjectId) {
        return parentObjectId + "->" + childObjectId;
    }

    public void recordClassExport(DOSchemaClass schemaClass, long objectId) {
        recordClassExport(schemaClass, objectId, null);
    }

    public void recordClassExport(DOSchemaClass schemaClass, long objectId, DOSchema hierarchySchema) {
        if (schemaClass != null) {
            String className = schemaClass.source;
            Set<Long> objectIds = exportedObjectIdsSet.computeIfAbsent(className, k -> new HashSet<>());
            if (objectIds.add(objectId)) {
                int count = exportedClassCounts.getOrDefault(className, 0) + 1;
                exportedClassCounts.put(className, count);
                if (monitor != null) {
                    monitor.onObjectExported(className, objectId);
                }
            }

            recordReachedOnly(className, objectId, hierarchySchema);
        }
    }

    /**
     * Records an object as reached for coverage tracking without counting it as
     * a successfully exported object in progress metrics. Used for objects
     * encountered during resolution flows (e.g. IDEntite) where another
     * resolved object may be exported instead.
     */
    public void recordReachedOnly(DOSchemaClass schemaClass, long objectId) {
        recordReachedOnly(schemaClass, objectId, null);
    }

    public void recordReachedOnly(DOSchemaClass schemaClass, long objectId, DOSchema hierarchySchema) {
        if (schemaClass == null) {
            return;
        }
        String className = schemaClass.source;
        recordReachedOnly(className, objectId, hierarchySchema);
    }

    public void recordReachedOnly(String className, long objectId) {
        recordReachedOnly(className, objectId, null);
    }

    public void recordReachedOnly(String className, long objectId, DOSchema hierarchySchema) {
        if (className == null || className.isBlank() || objectId <= 0) {
            return;
        }

        Set<String> hierarchy = new LinkedHashSet<>();
        String currentClass = className;
        while (currentClass != null && !currentClass.isBlank()) {
            if (!hierarchy.add(currentClass)) {
                break;
            }

            if (hierarchySchema == null) {
                break;
            }

            DOSchemaClass currentSchemaClass = SchemaUtil.findClassByName(currentClass, hierarchySchema);
            if (currentSchemaClass == null || currentSchemaClass.parentClassName == null || currentSchemaClass.parentClassName.isBlank()) {
                break;
            }
            currentClass = currentSchemaClass.parentClassName;
        }

        if (hierarchy.isEmpty()) {
            hierarchy.add(className);
        }

        for (String hierarchyClassName : hierarchy) {
            exportedObjectIdsSet.computeIfAbsent(hierarchyClassName, k -> new HashSet<>()).add(objectId);
        }
    }

    public void addError(long objectId, String className, String errorMessage, Exception exception) {
        errors.add(new ExportError(objectId, className, errorMessage, exception));
        if (monitor != null) {
            monitor.onObjectError(className, objectId, errorMessage);
        }
    }

    public void setExportInfo(String exportName, String outputPath) {
        this.exportName = exportName;
        this.outputPath = outputPath;
        // Only build the per-class ID lists (used for coverage drilldown) when
        // full tracking is enabled; skipping them saves memory on large exports.
        if (fullTracking) {
            exportedObjectIds.clear();
            for (Map.Entry<String, Set<Long>> entry : exportedObjectIdsSet.entrySet()) {
                exportedObjectIds.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    public static class ExportError {
        public final long objectId;
        public final String className;
        public final String errorMessage;
        public final Exception exception;

        public ExportError(long objectId, String className, String errorMessage, Exception exception) {
            this.objectId = objectId;
            this.className = className;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }
    }
}
