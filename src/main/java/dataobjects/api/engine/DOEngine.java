package dataobjects.api.engine;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.resolution.DOObjectReachabilityTracker;
import java.io.Closeable;

/**
 * A DataObject Engine is a single-use work session with a database.
 * It holds a schema and a database connection.
 * It also provides monitoring capabilities and reachability tracking.
 */
public interface DOEngine extends Closeable {

    public DOSchema getSchema();

    public DODatabase getDatabase();

    public DOEngineMonitoring getMonitoring();

    /**
     * Get the reachability tracker that contains exact information about
     * which objects are reachable from module roots.
     * This is populated during the object resolution phase.
     */
    public DOObjectReachabilityTracker getReachabilityTracker();

}
