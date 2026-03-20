package migration4o.database;

/**
 * Holds the object ID arrays for a database class.
 */
public class DODatabaseClassObjects {

    public final DODatabaseClass parentClass;

    public DODatabaseClassObjects(DODatabaseClass parentClass) {
        this.parentClass = parentClass;
    }

    public long[] objectIds;
    public long[] uniqueObjectIds;

}
