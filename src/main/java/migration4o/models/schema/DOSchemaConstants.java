package migration4o.models.schema;

public class DOSchemaConstants {

    public static final String ANCESTOR_IDENTITE = "gest.gen.IDEntite";
    public static final String ANCESTOR_ENTITE_CONTIENT_ID = "gest.gen.EntiteContientID";
    public static final String ANCESTOR_ENTITE_PARAM = "gest.gen.EntiteParam";
    public static final String ANCESTOR_ENTITE = "gest.gen.Entite";

    /** Well-known collection base classes for ancestry-based detection. */
    public static final String[] COLLECTION_BASE_CLASSES = { "java.util.Vector", "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.TreeSet", "java.util.LinkedHashSet", "java.util.AbstractList", "java.util.AbstractCollection", "java.util.AbstractSet", "java.util.Hashtable", "java.util.HashMap", "java.util.TreeMap", "java.util.AbstractMap", "java.util.Dictionary" };

    /** Well-known map base classes for ancestry-based detection. */
    public static final String[] MAP_BASE_CLASSES = { "java.util.Hashtable", "java.util.HashMap", "java.util.TreeMap", "java.util.LinkedHashMap", "java.util.AbstractMap", "java.util.Dictionary" };

    public static final String VIRTUAL_FIELD_PREFIX = "@";
    public static final String METHOD_CALL_SUFFIX = "()";

    public static final String METADATA_GENERATOR = "Migration4o";
    public static final String METADATA_SCHEMA_VERSION = "2.0";
    public static final String METADATA_PROVIDER = "Gestion Technologies";

}
