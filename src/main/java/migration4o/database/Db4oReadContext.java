package migration4o.database;

/**
 * Thread-local breadcrumb for DB4O I/O error reporting.
 * <p>
 * Set by the export engine just before reading a field value so that {@link SafeMemoryIoAdapter} can include "which field triggered this" in its invalid-read log line.
 */
public final class Db4oReadContext {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    /** Set to true by {@link SafeMemoryIoAdapter} when an invalid read occurs on this thread. */
    private static final ThreadLocal<Boolean> HAD_ERROR = new ThreadLocal<>();

    private Db4oReadContext() {
    }

    /** Sets the current context string (e.g. "gest.intervention.Intervention#mVectRapport [objectId=42]"). */
    public static void set(String context) {
        CONTEXT.set(context);
    }

    /** Clears the context after the read operation completes. */
    public static void clear() {
        CONTEXT.remove();
    }

    /** Returns the current context, or {@code null} if none is set. */
    public static String get() {
        return CONTEXT.get();
    }

    /** Called by {@link SafeMemoryIoAdapter} when an invalid read is intercepted. */
    public static void markError() {
        HAD_ERROR.set(Boolean.TRUE);
    }

    /** Returns {@code true} if a SafeIO error was detected since the last {@link #clearError()} call. */
    public static boolean hadError() {
        return Boolean.TRUE.equals(HAD_ERROR.get());
    }

    /** Resets the error flag — call before each export attempt. */
    public static void clearError() {
        HAD_ERROR.remove();
    }
}
