package migration4o.engine.export.monitoring;

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
    public int objectsFiltered; // Objects filtered out by export criteria
    public List<ExportError> errors;
    public List<ExportWarning> schemaWarnings;
    public Map<String, Integer> exportedClassCounts;
    public Map<String, List<Long>> exportedObjectIds; // Actual object IDs exported per class

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors) {
        this(exportName, outputPath, objectsAttempted, objectsSucceeded, 0, errors, new ArrayList<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>());
    }

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors, List<ExportWarning> schemaWarnings,
            Map<String, Integer> exportedClassCounts) {
        this(exportName, outputPath, objectsAttempted, objectsSucceeded, 0, errors, schemaWarnings,
                exportedClassCounts, new java.util.HashMap<>());
    }

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, int objectsFiltered, List<ExportError> errors, List<ExportWarning> schemaWarnings,
            Map<String, Integer> exportedClassCounts) {
        this(exportName, outputPath, objectsAttempted, objectsSucceeded, objectsFiltered, errors, schemaWarnings,
                exportedClassCounts, new java.util.HashMap<>());
    }

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, int objectsFiltered, List<ExportError> errors, List<ExportWarning> schemaWarnings,
            Map<String, Integer> exportedClassCounts, Map<String, List<Long>> exportedObjectIds) {
        this.exportName = exportName;
        this.outputPath = outputPath;
        this.objectsAttempted = objectsAttempted;
        this.objectsSucceeded = objectsSucceeded;
        this.objectsFiltered = objectsFiltered;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.schemaWarnings = schemaWarnings != null ? new ArrayList<>(schemaWarnings) : new ArrayList<>();
        this.exportedClassCounts = exportedClassCounts != null ? new java.util.HashMap<>(exportedClassCounts)
                : new java.util.HashMap<>();
        this.exportedObjectIds = exportedObjectIds != null ? new java.util.HashMap<>(exportedObjectIds)
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

    /**
     * Represents a schema configuration warning.
     */
}
