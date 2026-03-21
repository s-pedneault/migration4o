package migration4o.util;

import migration4o.database.DODatabaseDelegate;

/**
 * Result of resolving an IDEntite reference across delegates.
 * Carries both the target object ID and the delegate that owns it,
 * so callers can load the object from the correct container.
 */
public class ResolvedReference {
    public final long objectId;
    public final DODatabaseDelegate delegate;

    public ResolvedReference(long objectId, DODatabaseDelegate delegate) {
        this.objectId = objectId;
        this.delegate = delegate;
    }
}
