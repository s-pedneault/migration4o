package migration4o.engine.export.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import migration4o.models.schema.DOSchemaClass;

/**
 * Tracks statistics and errors during export operations.
 */
public class ExportStatistics {
    private final List<ExportError> errors = new ArrayList<>();
    private int objectsAttempted = 0;
    private int objectsSucceeded = 0;
    private final Map<String, Integer> exportedClassCounts = new HashMap<>();

    public void incrementAttempted() {
        objectsAttempted++;
    }

    public void incrementSucceeded() {
        objectsSucceeded++;
    }

    public void recordClassExport(DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            String className = schemaClass.destinationName;
            exportedClassCounts.put(className, exportedClassCounts.getOrDefault(className, 0) + 1);
        }
    }

    public void addError(long objectId, String className, String errorMessage, Exception exception) {
        errors.add(new ExportError(objectId, className, errorMessage, exception));
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

    public List<ExportError> getErrors() {
        return new ArrayList<>(errors);
    }

    public Map<String, Integer> getExportedClassCounts() {
        return new HashMap<>(exportedClassCounts);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Prints a comprehensive export summary.
     */
    public void printSummary(String outputPath, String exportName) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EXPORT SUMMARY: " + exportName);
        System.out.println("=".repeat(80));
        System.out.println("Output file:       " + outputPath);
        System.out.println("Objects attempted: " + objectsAttempted);
        System.out.println("Objects succeeded: " + objectsSucceeded);
        System.out.println("Objects failed:    " + errors.size());

        if (!errors.isEmpty()) {
            System.out.println("\n" + "!".repeat(80));
            System.out.println("ERRORS ENCOUNTERED:");
            System.out.println("!".repeat(80));

            // Group errors by error message
            Map<String, List<ExportError>> errorsByMessage = new LinkedHashMap<>();
            for (ExportError error : errors) {
                errorsByMessage.computeIfAbsent(error.errorMessage, k -> new ArrayList<>()).add(error);
            }

            for (Map.Entry<String, List<ExportError>> entry : errorsByMessage.entrySet()) {
                String errorMsg = entry.getKey();
                List<ExportError> errorList = entry.getValue();

                System.out.println("\n[" + errorList.size() + " occurrences] " + errorMsg);
                System.out.print("  Object IDs: ");
                int showCount = Math.min(10, errorList.size());
                for (int i = 0; i < showCount; i++) {
                    if (i > 0)
                        System.out.print(", ");
                    System.out.print(errorList.get(i).objectId);
                }
                if (errorList.size() > showCount) {
                    System.out.print(" ... and " + (errorList.size() - showCount) + " more");
                }
                System.out.println();
            }
            System.out.println("!".repeat(80));
        }

        System.out.println("=".repeat(80) + "\n");
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
                publicErrors, exportedClassCounts);
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
