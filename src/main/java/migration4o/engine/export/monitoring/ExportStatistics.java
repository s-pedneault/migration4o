package migration4o.engine.export.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchemaClass;
import migration4o.ui.common.DOExportMonitor;

/**
 * Tracks statistics and errors during export operations.
 * Now supports optional progress monitoring for UI feedback.
 */
public class ExportStatistics {
    private final DOExportMonitor monitor;
    private final List<ExportError> errors = new ArrayList<>();
    private final List<SchemaWarning> schemaWarnings = new ArrayList<>();
    private int objectsAttempted = 0;
    private int objectsSucceeded = 0;
    private int objectsFiltered = 0; // Objects filtered out by export criteria
    private final Map<String, Integer> exportedClassCounts = new HashMap<>();
    private final Map<String, Set<Long>> exportedObjectIdsSet = new HashMap<>(); // Track unique object IDs per class
                                                                                 // using Set
    private String currentClassName = "";
    private int currentClassTotal = 0;

    /**
     * Creates export statistics without monitor (for backwards compatibility).
     */
    public ExportStatistics() {
        this(null);
    }

    /**
     * Creates export statistics with optional monitor for progress callbacks.
     */
    public ExportStatistics(DOExportMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Sets the current class being exported (for progress reporting).
     */
    public void setCurrentClass(String className, int totalObjects) {
        this.currentClassName = className;
        this.currentClassTotal = totalObjects;
    }

    public void incrementAttempted() {
        objectsAttempted++;

        // Report progress every 10 objects to reduce UI overhead
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
            // Use source name (not destinationName) for database schema lookup
            String className = schemaClass.source;

            // Get or create the object ID set for this class
            Set<Long> objectIds = exportedObjectIdsSet.computeIfAbsent(className, k -> new HashSet<>());

            // Set.add() returns true if element was added (wasn't already present)
            if (objectIds.add(objectId)) {
                // Object ID is new - increment counter
                int count = exportedClassCounts.getOrDefault(className, 0) + 1;
                exportedClassCounts.put(className, count);

                // Notify monitor
                if (monitor != null) {
                    monitor.onObjectExported(className, objectId);
                }
            }
            // If add() returned false, object ID was already in set - don't increment
            // counter
        }
    }

    public void addError(long objectId, String className, String errorMessage, Exception exception) {
        errors.add(new ExportError(objectId, className, errorMessage, exception));

        // Notify monitor
        if (monitor != null) {
            monitor.onObjectError(className, objectId, errorMessage);
        }
    }

    public void addSchemaWarning(ExportResult.SchemaWarning.WarningType type, long objectId,
            String className, String fieldName, String containingClass,
            String sourceContainingClass, String sourceFieldName,
            String message, int referenceCount) {
        schemaWarnings.add(new SchemaWarning(type, objectId, className, fieldName, containingClass,
                sourceContainingClass, sourceFieldName, message, referenceCount));

        // Notify monitor
        if (monitor != null) {
            monitor.onWarning(type.toString(), className, message);
        }
    }

    public int getObjectsAttempted() {
        return objectsAttempted;
    }

    public int getObjectsSucceeded() {
        return objectsSucceeded;
    }

    public int getObjectsFailed() {
        return errors.size();
    }

    public int getObjectsFiltered() {
        return objectsFiltered;
    }

    public List<ExportError> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<SchemaWarning> getSchemaWarnings() {
        return new ArrayList<>(schemaWarnings);
    }

    public Map<String, Integer> getExportedClassCounts() {
        return new HashMap<>(exportedClassCounts);
    }

    public Map<String, List<Long>> getExportedObjectIds() {
        // Convert Set<Long> to List<Long> for each class
        Map<String, List<Long>> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : exportedObjectIdsSet.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Prints a comprehensive export summary.
     * Summary output removed - use export progress dialog instead.
     */
    public void printSummary(String outputPath, String exportName) {
        // Summary output removed - all information is now displayed in
        // ExportProgressDialog
    }

    /**
     * Creates an ExportResult from the current statistics.
     */
    public ExportResult createResult(String exportName, String outputPath) {
        // Convert internal errors to public ExportResult.ExportError format
        List<ExportResult.ExportError> publicErrors = new ArrayList<>();
        for (ExportError internalError : errors) {
            publicErrors.add(new ExportResult.ExportError(
                    internalError.objectId,
                    internalError.className,
                    internalError.errorMessage,
                    internalError.exception));
        }

        // Convert internal warnings to public ExportResult.SchemaWarning format
        List<ExportResult.SchemaWarning> publicWarnings = new ArrayList<>();
        for (SchemaWarning internalWarning : schemaWarnings) {
            publicWarnings.add(new ExportResult.SchemaWarning(
                    internalWarning.type,
                    internalWarning.objectId,
                    internalWarning.className,
                    internalWarning.fieldName,
                    internalWarning.containingClass,
                    internalWarning.sourceContainingClass,
                    internalWarning.sourceFieldName,
                    internalWarning.message,
                    internalWarning.referenceCount));
        }

        return new ExportResult(exportName, outputPath, objectsAttempted, objectsSucceeded,
                objectsFiltered, publicErrors, publicWarnings, exportedClassCounts, getExportedObjectIds());
    }

    /**
     * Internal representation of an export error.
     */
    private static class ExportError {
        final long objectId;
        final String className;
        final String errorMessage;
        final Exception exception;

        ExportError(long objectId, String className, String errorMessage, Exception exception) {
            this.objectId = objectId;
            this.className = className;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }
    }

    /**
     * Internal representation of a schema warning.
     */
    private static class SchemaWarning {
        final ExportResult.SchemaWarning.WarningType type;
        final long objectId;
        final String className;
        final String fieldName;
        final String containingClass;
        final String sourceContainingClass;
        final String sourceFieldName;
        final String message;
        final int referenceCount;

        SchemaWarning(ExportResult.SchemaWarning.WarningType type, long objectId, String className,
                String fieldName, String containingClass, String sourceContainingClass,
                String sourceFieldName, String message, int referenceCount) {
            this.type = type;
            this.objectId = objectId;
            this.className = className;
            this.fieldName = fieldName;
            this.containingClass = containingClass;
            this.sourceContainingClass = sourceContainingClass;
            this.sourceFieldName = sourceFieldName;
            this.message = message;
            this.referenceCount = referenceCount;
        }
    }
}
