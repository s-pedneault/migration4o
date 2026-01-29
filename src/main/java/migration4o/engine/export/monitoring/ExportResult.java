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
    public List<ExportError> errors;
    public List<SchemaWarning> schemaWarnings;
    public Map<String, Integer> exportedClassCounts;

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors) {
        this(exportName, outputPath, objectsAttempted, objectsSucceeded, errors, new ArrayList<>(),
                new java.util.HashMap<>());
    }

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors, List<SchemaWarning> schemaWarnings,
            Map<String, Integer> exportedClassCounts) {
        this.exportName = exportName;
        this.outputPath = outputPath;
        this.objectsAttempted = objectsAttempted;
        this.objectsSucceeded = objectsSucceeded;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.schemaWarnings = schemaWarnings != null ? new ArrayList<>(schemaWarnings) : new ArrayList<>();
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

    /**
     * Represents a schema configuration warning.
     */
    public static class SchemaWarning {
        public enum WarningType {
            DUPLICATE_EMBEDDED_REFERENCE
        }

        public WarningType type;
        public long objectId;
        public String className;
        public String fieldName;
        public String message;
        public int referenceCount;

        public SchemaWarning(WarningType type, long objectId, String className, String fieldName,
                String message, int referenceCount) {
            this.type = type;
            this.objectId = objectId;
            this.className = className;
            this.fieldName = fieldName;
            this.message = message;
            this.referenceCount = referenceCount;
        }
    }
}
