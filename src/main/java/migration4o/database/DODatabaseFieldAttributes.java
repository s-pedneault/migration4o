package migration4o.database;

/**
 * Holds the properties discovered from the actual DB4O database for a stored field.
 */
public class DODatabaseFieldAttributes {

    public String source;
    public String type;
    public boolean isArray;
    public boolean isCollection;
    public String childrenType;

}
