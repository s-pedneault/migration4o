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
    private final List<ExportWarning> schemaWarnings = new ArrayList<>();
    private final Map<Long, List<ObjectReference>> objectReferences = new HashMap<>(); // Track all object references
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

    /**
     * Records a reference to an exported object.
     * Call this every time an object is exported to track all references.
     * 
     * IMPORTANT: Records EVERY reference, including:
     * - Multiple fields from the same parent object referencing the same child
     * - Multiple parent objects referencing the same child
     * No deduplication is performed - this is intentional for complete tracking.
     */
    public void recordObjectReference(long objectId, String className, Long parentObjectId,
            String sourceContainingClass, String sourceFieldName) {
        ObjectReference ref;
        if (parentObjectId != null) {
            // Field reference from parent object
            ref = new ObjectReference(objectId, className, parentObjectId, sourceContainingClass, sourceFieldName);
        } else {
            // Module/root export
            ref = new ObjectReference(objectId, className);
        }
        objectReferences.computeIfAbsent(objectId, k -> new ArrayList<>()).add(ref);
    }

    /**
     * Analyzes recorded references to generate duplicate warnings.
     * Call this after export completes.
     */
    public void generateDuplicateWarnings() {
        schemaWarnings.clear();

        System.out.println("\n=== Generating Duplicate Warnings ===");
        System.out.println("Total unique objects tracked: " + objectReferences.size());

        for (Map.Entry<Long, List<ObjectReference>> entry : objectReferences.entrySet()) {
            List<ObjectReference> refs = entry.getValue();

            // Only create warning if object was exported multiple times
            if (refs.size() > 1) {
                long objectId = entry.getKey();
                String className = refs.get(0).className;

                // Create warning - it will analyze the references internally
                ExportWarning warning = new ExportWarning(objectId, className, refs);
                schemaWarnings.add(warning);

                // Debug output
                System.out.println(String.format("DUPLICATE EXPORT: Object %d (%s) exported %d times:",
                        objectId, className, refs.size()));
                for (String display : warning.getReferenceDisplayStrings()) {
                    System.out.println("  - " + display);
                }

                if (warning.type == ExportWarning.WarningType.MISSING_EMBED_CONTENTS) {
                    System.out.println("  ⚠️  SCHEMA ISSUE: Class exported as both embedded and standalone!");
                    System.out.println("      Add embedContents=\"true\" to the parent field definition.");
                }
            }
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

    public List<ExportWarning> getSchemaWarnings() {
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

        return new ExportResult(exportName, outputPath, objectsAttempted, objectsSucceeded,
                objectsFiltered, publicErrors, schemaWarnings, exportedClassCounts, getExportedObjectIds());
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

}
