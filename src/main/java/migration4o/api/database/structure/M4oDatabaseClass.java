package migration4o.api.database.structure;

/**
 * Represents a class in the database structure.
 * Composed of a chain of M4oDatabaseAbstractClass instances, starting with the class itself.
 */
public interface M4oDatabaseClass {

    public M4oDatabaseAbstractClass getLeafClass();

    public M4oDatabaseAbstractClass[] getClassHierarchy();

    public long[] getDB4OIDs();

}
