package migration4o.api.database.structure;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

/** 
 * Represents an abstract class in the database structure.
 * Vector, AbstractList, AbstractCollection and Object are all M4oDatabaseAbstractClass instances of M4oDatabaseClass Vector.
 */
public interface M4oDatabaseAbstractClass {

    public StoredClass getDB4OClass();

    public StoredClass getDB4OParentClass();

    public StoredField[] getDB4OFields();

    public int getDB4OInstanceCount();

    public long[] getDB4OIDs();

    public M4oDatabaseAbstractClass getParent();

    public M4oDatabaseField[] getFields();

    public String getAbsoluteName();

    public String getSimpleName();

}
