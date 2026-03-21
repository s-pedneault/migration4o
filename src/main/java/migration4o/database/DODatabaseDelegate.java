package migration4o.database;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.SystemInfo;

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

    // ── Identity ────────────────────────────────────────────────────────

    public String getFilePath() {
        return filePath;
    }

    // ── Object access ───────────────────────────────────────────────────

    public Object getByID(long objectId) {
        return container.ext().getByID(objectId);
    }

    public long getID(Object obj) {
        return container.ext().getID(obj);
    }

    // ── Activation ──────────────────────────────────────────────────────

    public void activate(Object obj, int depth) {
        container.activate(obj, depth);
    }

    // ── Metadata ────────────────────────────────────────────────────────

    public StoredClass storedClass(Object obj) {
        return container.ext().storedClass(obj);
    }

    public StoredClass storedClass(String className) {
        return container.ext().storedClass(className);
    }

    public StoredClass[] storedClasses() {
        return container.storedClasses();
    }

    // ── Database introspection ──────────────────────────────────────────

    public long version() {
        return container.version();
    }

    public SystemInfo systemInfo() {
        return container.ext().systemInfo();
    }

    public long creationTime() {
        return container.identity().getCreationTime();
    }

    public byte[] signature() {
        return container.identity().getSignature();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    public boolean isClosed() {
        return container.ext().isClosed();
    }

    public void close() {
        container.close();
    }
}
