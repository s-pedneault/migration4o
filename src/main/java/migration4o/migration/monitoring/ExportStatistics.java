package migration4o.migration.monitoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchemaClass;
import migration4o.ui.common.DOExportMonitor;

/**
 * Tracks statistics and errors during export operations.
 * Also serves as the export result.
 */
public class ExportStatistics {
    public String exportName = "";
    public String outputPath = "";
    public int objectsAttempted = 0;
    public int objectsSucceeded = 0;
    public int objectsFiltered = 0;
    public final List<ExportError> errors = new ArrayList<>();
    public final List<ExportWarning> schemaWarnings = new ArrayList<>();
    public final Map<String, Integer> exportedClassCounts = new java.util.HashMap<>();
    public final Map<String, List<Long>> exportedObjectIds = new java.util.HashMap<>();

    public final DOExportMonitor monitor;
    public final ObjectDuplicationDetector duplicationDetector = new ObjectDuplicationDetector();
    public final Map<String, Set<Long>> exportedObjectIdsSet = new java.util.HashMap<>();
    public String currentClassName = "";
    public int currentClassTotal = 0;

    public ExportStatistics() {
        this(null);
    }

    public ExportStatistics(DOExportMonitor monitor) {
        this.monitor = monitor;
    }

    public void setCurrentClass(String className, int totalObjects) {
        this.currentClassName = className;
        this.currentClassTotal = totalObjects;
    }

    public void incrementAttempted() {
        objectsAttempted++;
        if (monitor != null && objectsAttempted % 10 == 0 && currentClassTotal > 0) {
            int current = exportedClassCounts.getOrDefault(currentClassName, 0);
            monitor.onObjectProgress(currentClassName, current, currentClassTotal);
        }
    }

    public void incrementSucceeded() {
        objectsSucceeded++;
    }

    public void incrementFiltered() {
        objectsFiltered++;
    }

    public void recordClassExport(DOSchemaClass schemaClass, long objectId) {
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
        exportedObjectIds.clear();
        for (Map.Entry<String, Set<Long>> entry : exportedObjectIdsSet.entrySet()) {
            exportedObjectIds.put(entry.getKey(), new ArrayList<>(entry.getValue()));
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
