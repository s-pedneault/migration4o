package migration4o.engine.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the results of an XML export operation.
 * Used to communicate export statistics and errors between the export engine
 * and UI.
 */
public class ExportResult {
    private final String exportName;
    private final String outputPath;
    private final int objectsAttempted;
    private final int objectsSucceeded;
    private final List<ExportError> errors;

    public ExportResult(String exportName, String outputPath, int objectsAttempted,
            int objectsSucceeded, List<ExportError> errors) {
        this.exportName = exportName;
        this.outputPath = outputPath;
        this.objectsAttempted = objectsAttempted;
        this.objectsSucceeded = objectsSucceeded;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public String getExportName() {
        return exportName;
    }

    public String getOutputPath() {
        return outputPath;
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

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Represents an error that occurred during object export.
     */
    public static class ExportError {
        private final long objectId;
        private final String className;
        private final String errorMessage;
        private final Exception exception;

        public ExportError(long objectId, String className, String errorMessage, Exception exception) {
            this.objectId = objectId;
            this.className = className;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }

        public long getObjectId() {
            return objectId;
        }

        public String getClassName() {
            return className;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Exception getException() {
            return exception;
        }
    }
}
