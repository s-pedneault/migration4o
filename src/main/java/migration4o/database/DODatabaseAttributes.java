package migration4o.database;

/**
 * Holds the properties discovered from the actual DB4O database container.
 */
public class DODatabaseAttributes {

    public long version;
    public int classCount;

    // SystemInfo
    public long totalSize;
    public long freespaceSize;
    public int freespaceEntryCount;

    // Identity
    public long creationTime;
    public byte[] signature;

}
