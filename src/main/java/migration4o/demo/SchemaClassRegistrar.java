package migration4o.demo;

import com.db4o.ObjectContainer;
import com.db4o.reflect.ReflectClass;
import com.db4o.reflect.generic.GenericClass;
import com.db4o.reflect.generic.GenericField;
import com.db4o.reflect.generic.GenericObject;
import com.db4o.reflect.generic.GenericReflector;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;

import java.util.*;

/**
 * Registers all exported schema classes as GenericClass definitions in a DB4O container.
 * Must be called after the container is opened but before any objects are created.
 *
 * Classes are registered in topological order (parents before children) to ensure
 * that DB4O's field index offset mechanism works correctly: child fields are offset
 * by superclass.getFieldCount().
 */
public class SchemaClassRegistrar {

    private final ObjectContainer container;
    private final GenericReflector reflector;
    private final DOSchema schema;

    /** Map from fully-qualified class name to the registered GenericClass. */
    private final Map<String, GenericClass> registeredClasses = new LinkedHashMap<>();

    /** Map from fully-qualified class name to its GenericField array (for value setting later). */
    private final Map<String, GenericField[]> classFields = new LinkedHashMap<>();

    public SchemaClassRegistrar(ObjectContainer container, DOSchema schema) {
        this.container = container;
        this.reflector = container.ext().reflector();
        this.schema = schema;
    }

    /**
     * Registers all exported classes from the schema in topological order.
     * Returns the number of classes registered.
     */
    public int registerAll() {
        List<DOSchemaClass> sorted = topologicalSort();
        int count = 0;

        for (DOSchemaClass sc : sorted) {
            registerClass(sc);
            count++;
        }

        System.out.println("[registrar] Registered " + count + " classes.");
        return count;
    }

    /**
     * Returns the registered GenericClass for the given fully-qualified name, or null.
     */
    public GenericClass getGenericClass(String className) {
        return registeredClasses.get(className);
    }

    /**
     * Returns the GenericField array for the given fully-qualified class name.
     * These are only the fields declared on this class (not inherited).
     */
    public GenericField[] getFields(String className) {
        return classFields.get(className);
    }

    /**
     * Returns all registered class names in registration order.
     */
    public Set<String> getRegisteredClassNames() {
        return registeredClasses.keySet();
    }

    /**
     * Initializes all primitive fields (own + inherited) on a GenericObject to their
     * default values (0, 0.0, false). This prevents DB4O from encountering null in
     * primitive field slots during store().
     */
    public void initializePrimitiveDefaults(GenericObject obj, GenericClass gc) {
        GenericClass current = gc;
        while (current != null) {
            GenericField[] fields = classFields.get(current.getName());
            if (fields != null) {
                for (GenericField gf : fields) {
                    if (gf.isPrimitive()) {
                        String typeName = gf.getFieldType() != null ? gf.getFieldType().getName() : "";
                        Object defaultVal = primitiveDefault(typeName);
                        if (defaultVal != null) {
                            try {
                                gf.set(obj, defaultVal);
                            } catch (Exception e) {
                                System.err.println("[registrar] WARN: default for " + gf.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
            try {
                ReflectClass superClass = current.getSuperclass();
                if (superClass instanceof GenericClass) {
                    current = (GenericClass) superClass;
                } else {
                    break;
                }
            } catch (Exception e) {
                break;
            }
        }
    }

    private Object primitiveDefault(String typeName) {
        if (typeName == null)
            return 0;
        switch (typeName) {
        case "int":
        case "java.lang.Integer":
            return 0;
        case "long":
        case "java.lang.Long":
            return 0L;
        case "double":
        case "java.lang.Double":
            return 0.0;
        case "float":
        case "java.lang.Float":
            return 0.0f;
        case "boolean":
        case "java.lang.Boolean":
            return false;
        case "byte":
        case "java.lang.Byte":
            return (byte) 0;
        case "short":
        case "java.lang.Short":
            return (short) 0;
        case "char":
        case "java.lang.Character":
            return (char) 0;
        default:
            return 0;
        }
    }

    // ── Registration ─────────────────────────────────────────────────────────

    private void registerClass(DOSchemaClass sc) {
        String className = sc.attributes.source;

        // Skip if already registered (e.g., parent registered via dependency)
        if (registeredClasses.containsKey(className)) {
            return;
        }

        // Resolve parent GenericClass (may be null for root classes)
        GenericClass parentGC = null;
        if (sc.attributes.parentClassName != null && !sc.attributes.parentClassName.isEmpty()) {
            parentGC = registeredClasses.get(sc.attributes.parentClassName);
            // If parent isn't registered yet, register it first
            if (parentGC == null) {
                DOSchemaClass parentSC = schema.findClassByName(sc.attributes.parentClassName);
                if (parentSC != null) {
                    registerClass(parentSC);
                    parentGC = registeredClasses.get(sc.attributes.parentClassName);
                }
            }
        }

        // Create GenericClass: (reflector, delegateClass=null, name, superClass)
        GenericClass gc = new GenericClass(reflector, null, className, parentGC);

        // Build field list from schema (only this class's own fields, not inherited)
        List<GenericField> fieldList = new ArrayList<>();
        if (sc.fields != null) {
            for (DOSchemaField sf : sc.fields) {
                if (sf.isVirtualField() || sf.isMethodCallField()) {
                    continue; // Virtual/method fields don't exist in DB4O storage
                }
                ReflectClass fieldType = resolveFieldType(sf);
                boolean isPrimitive = isPrimitiveDbType(sf.attributes.type);
                GenericField gf = new GenericField(sf.attributes.source, fieldType, isPrimitive);
                fieldList.add(gf);
            }
        }

        GenericField[] fields = fieldList.toArray(new GenericField[0]);
        gc.initFields(fields);
        reflector.register(gc);

        registeredClasses.put(className, gc);
        classFields.put(className, fields);
    }

    // ── Type resolution ──────────────────────────────────────────────────────

    private ReflectClass resolveFieldType(DOSchemaField sf) {
        String typeName = sf.attributes.type;
        if (typeName == null || typeName.isEmpty()) {
            typeName = "object";
        }

        // Collections: register element as Vector (DB4O stores them this way)
        if (sf.attributes.isCollection || CollectionTypeUtil.isCollectionType(typeName)) {
            return reflector.forClass(java.util.Vector.class);
        }
        if (CollectionTypeUtil.isMapType(typeName)) {
            return reflector.forClass(java.util.Hashtable.class);
        }

        // Primitive / standard types
        String lower = typeName.toLowerCase();
        switch (lower) {
        case "string":
        case "java.lang.string":
            return reflector.forClass(String.class);
        case "int":
        case "java.lang.integer":
            return reflector.forClass(int.class);
        case "double":
        case "java.lang.double":
            return reflector.forClass(double.class);
        case "boolean":
        case "java.lang.boolean":
            return reflector.forClass(boolean.class);
        case "long":
        case "java.lang.long":
            return reflector.forClass(long.class);
        case "float":
        case "java.lang.float":
            return reflector.forClass(float.class);
        case "byte":
        case "java.lang.byte":
            return reflector.forClass(byte.class);
        case "short":
        case "java.lang.short":
            return reflector.forClass(short.class);
        case "char":
        case "java.lang.character":
            return reflector.forClass(char.class);
        case "date":
        case "java.util.date":
        case "java.sql.date":
            return reflector.forClass(java.util.Date.class);
        case "object":
        case "java.lang.object":
            return reflector.forClass(Object.class);
        case "java.lang.class":
            return reflector.forClass(String.class); // Store class names as strings
        case "java.util.uuid":
            return reflector.forClass(String.class); // Store UUIDs as strings
        case "java.awt.color":
            return reflector.forClass(int.class); // Store color as int RGB
        }

        // Reference types (gest.*) — resolve to the registered GenericClass
        GenericClass refClass = registeredClasses.get(typeName);
        if (refClass != null) {
            return refClass;
        }

        // Not yet registered? Check if it exists in the schema and will be registered later.
        // For now, use Object as a safe fallback — the object factory will create the right type.
        DOSchemaClass typeClass = schema.findClassByName(typeName);
        if (typeClass != null) {
            // Try to register it eagerly
            registerClass(typeClass);
            GenericClass resolved = registeredClasses.get(typeName);
            if (resolved != null) {
                return resolved;
            }
        }

        // Truly unknown type — fall back to Object
        return reflector.forClass(Object.class);
    }

    private boolean isPrimitiveDbType(String typeName) {
        if (typeName == null)
            return false;
        String lower = typeName.toLowerCase();
        return lower.equals("int") || lower.equals("double") || lower.equals("boolean") || lower.equals("long") || lower.equals("float") || lower.equals("byte") || lower.equals("short") || lower.equals("char");
    }

    // ── Topological sort ─────────────────────────────────────────────────────

    /**
     * Returns schema classes in topological order: parents before children.
     * Only includes classes that should appear in the demo database.
     */
    private List<DOSchemaClass> topologicalSort() {
        // Build the full set of classes to register. Start with exported classes,
        // then add any parent classes they depend on.
        Map<String, DOSchemaClass> classMap = new LinkedHashMap<>();
        for (DOSchemaClass sc : schema.getClasses()) {
            if (shouldRegister(sc)) {
                classMap.put(sc.attributes.source, sc);
            }
        }

        // Add parent classes that aren't in the exportable set but are needed for inheritance
        List<DOSchemaClass> toScan = new ArrayList<>(classMap.values());
        for (DOSchemaClass sc : toScan) {
            addAncestors(sc, classMap);
        }

        // Topological sort: visit parents before children
        Set<String> visited = new LinkedHashSet<>();
        List<DOSchemaClass> result = new ArrayList<>();

        for (DOSchemaClass sc : new ArrayList<>(classMap.values())) {
            visit(sc, classMap, visited, result);
        }
        return result;
    }

    private void addAncestors(DOSchemaClass sc, Map<String, DOSchemaClass> classMap) {
        String parentName = sc.attributes.parentClassName;
        while (parentName != null && !parentName.isEmpty() && !classMap.containsKey(parentName)) {
            // Don't add Java standard library classes as ancestors
            if (!parentName.startsWith("gest.") && !parentName.startsWith("gen."))
                break;
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            classMap.put(parentName, parent);
            parentName = parent.attributes.parentClassName;
        }
    }

    private void visit(DOSchemaClass sc, Map<String, DOSchemaClass> classMap, Set<String> visited, List<DOSchemaClass> result) {
        String name = sc.attributes.source;
        if (visited.contains(name))
            return;
        visited.add(name);

        // Visit parent first
        if (sc.attributes.parentClassName != null && !sc.attributes.parentClassName.isEmpty()) {
            DOSchemaClass parent = classMap.get(sc.attributes.parentClassName);
            if (parent != null) {
                visit(parent, classMap, visited, result);
            }
        }

        result.add(sc);
    }

    /**
     * Determines whether a schema class should be registered in the demo DB.
     * Includes exported classes and their ancestor chain.
     */
    private boolean shouldRegister(DOSchemaClass sc) {
        // Skip collection wrapper types and primitives
        if (sc.isPrimitive() || sc.isCollectionOrMap()) {
            return false;
        }
        // Only register domain classes (gest.* and gen.*), not Java standard library
        String name = sc.attributes.source;
        if (!name.startsWith("gest.") && !name.startsWith("gen.")) {
            return false;
        }
        // Include classes marked for export
        if (sc.attributes.migrate) {
            return true;
        }
        // Also include non-exported classes that serve as parents for exported classes
        // (the topological sort visit method handles this via parent chasing)
        return false;
    }
}
