package migration4o.engine.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains the results of an XML export operation.
 * Used to communicate export statistics and errors between the export engine
 * and UI.
 */
public class ExportResult {
    public String exportName;
    public String outputPath;
    public int objectsAttempted;
    public int objectsSucceeded;
    public List<ExportError> errors;
    public Map<String, Integer> exportedClassCounts;

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors) {
        this(exportName, outputPath, objectsAttempted, objectsSucceeded, errors, new java.util.HashMap<>());
    }

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors, Map<String, Integer> exportedClassCounts) {
        this.exportName = exportName;
        this.outputPath = outputPath;
        this.objectsAttempted = objectsAttempted;
        this.objectsSucceeded = objectsSucceeded;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.exportedClassCounts = exportedClassCounts != null ? new java.util.HashMap<>(exportedClassCounts)
                : new java.util.HashMap<>();
    }

    /**
     * Represents an error that occurred during object export.
     */
    public static class ExportError {
        public long objectId;
        public String className;
        public String errorMessage;
        public Exception exception;

        public ExportError(long objectId, String className, String errorMessage, Exception exception) {
            this.objectId = objectId;
            this.className = className;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }

    }
}
