package migration4o.database;

import com.db4o.ext.DatabaseClosedException;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.ext.SystemInfo;
import com.db4o.reflect.generic.GenericObject;

/**
 * Wraps a single DB4O {@link ExtObjectContainer} and its loaded classes.
 * <p>
 * All DB4O operations throughout the application go through a delegate.
 * A {@link DODatabase} aggregates one or more delegates (e.g. user data
 * and static/lookup data) and presents a unified view.
 * <p>
 * Each {@link DODatabaseClass} knows which delegate it belongs to, so
 * object retrieval always routes to the correct container.
 */
public class DODatabaseDelegate {

    private final ExtObjectContainer container;
    private final String filePath;
    public DODatabaseAttributes attributes = new DODatabaseAttributes();
    public DODatabaseClass[] classes = new DODatabaseClass[0];

    public DODatabaseDelegate(ExtObjectContainer container, String filePath) {
        this.container = container;
        this.filePath = filePath;
    }

    // ── Exception logging ───────────────────────────────────────────────

    /**
     * Logs a DB4O operation failure with full stack trace, then returns
     * the exception so callers can {@code throw logDb4oException(...)}.
     */
    private RuntimeException logDb4oException(String operation, RuntimeException e) {
        System.err.println("[DB4O] " + operation + ": " + e.getClass().getName() + " - " + e.getMessage());
        e.printStackTrace(System.err);
        return e;
    }

    // ── Identity ────────────────────────────────────────────────────────

    public String getFilePath() {
        return filePath;
    }

    // ── Object access ───────────────────────────────────────────────────

    public Object getByID(long objectId) {
        try {
            return container.ext().getByID(objectId);
        } catch (com.db4o.ext.InvalidIDException e) {
            // Corrupt or invalid object slot — return null so callers skip gracefully
            return null;
        } catch (NegativeArraySizeException e) {
            // Corrupt slot or wrong-container ID — return null so callers skip gracefully
            return null;
        } catch (RuntimeException e) {
            throw logDb4oException("getByID(objectId=" + objectId + ")", e);
        }
    }

    public long getID(Object obj) {
        try {
            return container.ext().getID(obj);
        } catch (RuntimeException e) {
            throw logDb4oException("getID", e);
        }
    }

    // ── Activation ──────────────────────────────────────────────────────

    public void activate(Object obj, int depth) {
        try {
            container.activate(obj, depth);
        } catch (DatabaseClosedException e) {
            throw logDb4oException("activate(depth=" + depth + ")", e);
        } catch (RuntimeException e) {
            // DB4O 7.4 throws ReflectException (wrapping ClassCastException)
            // when activating objects whose fields use translated aspects
            // (TCollection casts GenericObject → Collection, fails because
            // the real Java class isn't on the classpath).
            // The object still gets activated as a GenericObject — harmless.
            if ("com.db4o.internal.ReflectException".equals(e.getClass().getName())) {
                return;
            }
            throw logDb4oException("activate(depth=" + depth + ")", e);
        }
    }

    /**
     * Releases the cached field values for {@code obj} up to {@code depth}
     * levels deep, freeing memory in DB4O's reference cache.
     * <p>
     * Must be called after an object has been fully exported/read to prevent
     * unbounded memory growth during large exports.
     * <p>
     * Silently catches exceptions because DB4O 7.4 throws
     * {@code ReflectException} / {@code ClassCastException} when deactivating
     * objects whose fields use translated aspects (e.g. Date, Hashtable).
     */
    public void deactivate(Object obj, int depth) {
        try {
            container.deactivate(obj, depth);
        } catch (DatabaseClosedException e) {
            throw logDb4oException("deactivate", e);
        } catch (Exception e) {
            // DB4O 7.4 throws ReflectException / ClassCastException when
            // deactivating objects with translated aspects (Date, Hashtable).
            // These are harmless — the object just stays activated.
        }
    }

    // ── Metadata ────────────────────────────────────────────────────────

    public StoredClass storedClass(Object obj) {
        try {
            return container.ext().storedClass(obj);
        } catch (RuntimeException e) {
            throw logDb4oException("storedClass(Object)", e);
        }
    }

    public StoredClass storedClass(String className) {
        try {
            return container.ext().storedClass(className);
        } catch (RuntimeException e) {
            throw logDb4oException("storedClass('" + className + "')", e);
        }
    }

    public StoredClass[] storedClasses() {
        try {
            return container.storedClasses();
        } catch (RuntimeException e) {
            throw logDb4oException("storedClasses()", e);
        }
    }

    // ── Field access ────────────────────────────────────────────────────

    /**
     * Returns all stored fields for the given object, including fields
     * from all ancestor classes. Deduplicates by field name (most-derived
     * version wins).
     */
    public StoredField[] getAllFieldsIncludingAncestors(Object obj) {
        StoredClass sc = storedClass(obj);
        if (sc == null)
            return new StoredField[0];
        return getAllFieldsIncludingAncestors(sc);
    }

    /**
     * Returns all stored fields for the given StoredClass, including fields
     * from all ancestor classes. Deduplicates by field name (most-derived
     * version wins).
     */
    public StoredField[] getAllFieldsIncludingAncestors(StoredClass storedClass) {
        try {
            java.util.List<StoredField> allFields = new java.util.ArrayList<>();
            java.util.Set<String> seenNames = new java.util.HashSet<>();
            StoredClass currentClass = storedClass;
            while (currentClass != null) {
                StoredField[] classFields = currentClass.getStoredFields();
                if (classFields != null) {
                    for (StoredField field : classFields) {
                        if (seenNames.add(field.getName())) {
                            allFields.add(field);
                        }
                    }
                }
                currentClass = currentClass.getParentStoredClass();
            }
            return allFields.toArray(new StoredField[0]);
        } catch (RuntimeException e) {
            throw logDb4oException("getAllFieldsIncludingAncestors", e);
        }
    }

    /**
     * Reads a single stored field value from a DB4O object by source field
     * name. Walks up the stored class hierarchy so fields declared on a
     * parent class are found. Returns {@code null} if not found.
     */
    public Object getStoredFieldValue(Object obj, String fieldName) {
        if (!(obj instanceof GenericObject))
            return null;
        try {
            StoredClass current = storedClass(obj);
            while (current != null) {
                StoredField field = current.storedField(fieldName, null);
                if (field != null) {
                    return field.get(obj);
                }
                current = current.getParentStoredClass();
            }
            return null;
        } catch (RuntimeException e) {
            throw logDb4oException("getStoredFieldValue(fieldName=" + fieldName + ")", e);
        }
    }

    /**
     * Traverses a dotted source field path (e.g. {@code "mAdresse.mRue"})
     * through a chain of DB4O objects and returns the leaf value.
     */
    public Object getFieldValueByPath(Object obj, String fieldPath) {
        if (obj == null || fieldPath == null || fieldPath.isEmpty())
            return null;
        String[] segments = fieldPath.split("\\.");
        Object current = obj;
        for (String segment : segments) {
            if (current == null)
                return null;
            current = getStoredFieldValue(current, segment);
        }
        return current;
    }

    // ── Database introspection ──────────────────────────────────────────

    public long version() {
        try {
            return container.version();
        } catch (RuntimeException e) {
            throw logDb4oException("version()", e);
        }
    }

    public SystemInfo systemInfo() {
        try {
            return container.ext().systemInfo();
        } catch (RuntimeException e) {
            throw logDb4oException("systemInfo()", e);
        }
    }

    public long creationTime() {
        try {
            return container.identity().getCreationTime();
        } catch (RuntimeException e) {
            throw logDb4oException("creationTime()", e);
        }
    }

    public byte[] signature() {
        try {
            return container.identity().getSignature();
        } catch (RuntimeException e) {
            throw logDb4oException("signature()", e);
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    public boolean isClosed() {
        return container.ext().isClosed();
    }

    public void close() {
        container.close();
    }
}
