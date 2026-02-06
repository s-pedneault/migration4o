package migration4o.migration.monitoring;

public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;
    private final String errorTitle;

    private ValidationResult(boolean valid, String errorMessage, String errorTitle) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.errorTitle = errorTitle;
    }

    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult error(String message, String title) {
        return new ValidationResult(false, message, title);
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorTitle() {
        return errorTitle;
    }
}
