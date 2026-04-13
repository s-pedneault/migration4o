package migration4o.demo;

import com.db4o.ObjectContainer;
import com.db4o.reflect.generic.GenericClass;
import com.db4o.reflect.generic.GenericField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaConstants;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;

import java.util.*;

/**
 * Creates GenericObject instances for all exported schema classes and stores them
 * in the DB4O container. Uses a two-pass strategy:
 *
 * Pass 1: Create all entity objects (EntiteContientID, EntiteParam) with unique mIDs.
 *         Track created IDs per class for cross-referencing.
 * Pass 2: Wire IDEntite reference objects — use pointsTo to pick valid target IDs.
 *
 * Embedded objects (embedContents=true) are created inline during Pass 1.
 * Collections are populated with the appropriate number of child objects.
 */
public class DemoObjectFactory {

    private final ObjectContainer container;
    private final DOSchema schema;
    private final SchemaClassRegistrar registrar;
    private final DataGenerator dataGen;

    /** Tracks created mID values per class name, for cross-referencing in Pass 2. */
    private final Map<String, List<Integer>> createdIds = new HashMap<>();

    /** Total objects stored. */
    private int totalObjectCount = 0;

    /** Index of the object currently being created within its class, used for sequential field assignment. */
    private int currentClassObjectIndex = 0;

    /** Total number of objects being created for the current class. */
    private int currentClassObjectCount = 0;

    public DemoObjectFactory(ObjectContainer container, DOSchema schema, SchemaClassRegistrar registrar, DataGenerator dataGen) {
        this.container = container;
        this.schema = schema;
        this.registrar = registrar;
        this.dataGen = dataGen;
    }

    /**
     * Generates and stores all demo objects. Returns total object count.
     */
    public int generateAll() {
        List<DOSchemaClass> entiteClasses = new ArrayList<>();
        List<DOSchemaClass> idEntiteClasses = new ArrayList<>();
        List<DOSchemaClass> paramClasses = new ArrayList<>();
        List<DOSchemaClass> otherClasses = new ArrayList<>();

        for (String className : registrar.getRegisteredClassNames()) {
            DOSchemaClass sc = schema.findClassByName(className);
            if (sc == null)
                continue;

            // Skip abstract base classes — we only create leaf/concrete objects
            if (isAbstractBase(sc))
                continue;

            if (sc.isIDEntite()) {
                idEntiteClasses.add(sc);
            } else if (sc.isParam()) {
                paramClasses.add(sc);
            } else if (sc.isEntite()) {
                entiteClasses.add(sc);
            } else {
                otherClasses.add(sc);
            }
        }

        // Pass 1: Create params first (they're referenced by entities)
        System.out.println("[factory] Pass 1a: Creating " + paramClasses.size() + " EntiteParam classes...");
        for (DOSchemaClass sc : paramClasses) {
            createObjectsForClass(sc, true);
        }

        // Pass 1b: Create entities
        System.out.println("[factory] Pass 1b: Creating " + entiteClasses.size() + " Entite classes...");
        for (DOSchemaClass sc : entiteClasses) {
            createObjectsForClass(sc, false);
        }

        // Pass 1c: Other non-IDEntite classes
        System.out.println("[factory] Pass 1c: Creating " + otherClasses.size() + " other classes...");
        for (DOSchemaClass sc : otherClasses) {
            createObjectsForClass(sc, false);
        }

        // Pass 2: Create IDEntite reference wrapper objects
        System.out.println("[factory] Pass 2: Creating " + idEntiteClasses.size() + " IDEntite classes...");
        for (DOSchemaClass sc : idEntiteClasses) {
            createIdEntiteObjects(sc);
        }

        container.commit();
        System.out.println("[factory] Committed " + totalObjectCount + " objects total.");
        return totalObjectCount;
    }

    public int getTotalObjectCount() {
        return totalObjectCount;
    }

    // ── Pass 1: Entity object creation ───────────────────────────────────────

    private void createObjectsForClass(DOSchemaClass sc, boolean isParam) {
        String className = sc.attributes.source;
        GenericClass gc = registrar.getGenericClass(className);
        if (gc == null)
            return;

        int count = dataGen.getObjectCount(sc.attributes.source, isParam, sc.attributes.isStatic, sc.attributes.alwaysExportAll);
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            currentClassObjectIndex = i;
            currentClassObjectCount = count;
            GenericObject obj = (GenericObject) gc.newInstance();
            registrar.initializePrimitiveDefaults(obj, gc);
            int assignedId = populateFields(obj, sc, gc);
            if (assignedId > 0) {
                ids.add(assignedId);
            }
            try {
                container.store(obj);
            } catch (Exception e) {
                System.err.println("[factory] ERROR storing " + className + " #" + i + ": " + e.getMessage());
                continue; // Skip this object, don't abort the whole generation
            }
            totalObjectCount++;
        }

        createdIds.put(className, ids);
    }

    /**
     * Populates all fields on a GenericObject from its schema definition.
     * Returns the mID value if one was assigned, or -1.
     */
    private int populateFields(GenericObject obj, DOSchemaClass sc, GenericClass gc) {
        GenericField[] gFields = registrar.getFields(sc.attributes.source);
        if (gFields == null || sc.fields == null)
            return -1;

        int assignedId = -1;

        // Build field name → GenericField lookup (only for this class's own fields)
        Map<String, GenericField> fieldMap = new LinkedHashMap<>();
        for (GenericField gf : gFields) {
            fieldMap.put(gf.getName(), gf);
        }

        for (DOSchemaField sf : sc.fields) {
            if (sf.isVirtualField() || sf.isMethodCallField())
                continue;

            GenericField gf = fieldMap.get(sf.attributes.source);
            if (gf == null) {
                // Field may be inherited — try parent fields
                gf = findInheritedField(gc, sf.attributes.source);
                if (gf == null)
                    continue;
            }

            // Special handling for mID — assign unique sequential ID
            // Respect the schema type (usually int, but some classes like Fichier use string)
            if (DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME.equals(sf.attributes.source)) {
                int id = dataGen.nextId();
                String fieldType = sf.attributes.type != null ? sf.attributes.type.toLowerCase() : "int";
                if (fieldType.equals("string") || fieldType.equals("java.lang.string")) {
                    gf.set(obj, String.valueOf(id));
                } else {
                    gf.set(obj, id);
                }
                assignedId = id;
                continue;
            }

            // Special handling for mIDSSI — assign sequential org ID so detection always works
            if (DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME.equals(sf.attributes.source)) {
                int idSSI = (currentClassObjectCount == DataGenerator.FIRE_DEPT_COUNT) ? (currentClassObjectIndex + 1) : (1 + dataGen.getRng().nextInt(DataGenerator.FIRE_DEPT_COUNT));
                gf.set(obj, idSSI);
                continue;
            }

            // Should this value be null sometimes?
            if (dataGen.shouldBeNull(sf)) {
                continue; // Leave as default (null/0/false)
            }

            Object value = generateFieldValue(sf);
            if (value != null) {
                try {
                    gf.set(obj, value);
                } catch (Exception e) {
                    System.err.println("[factory] WARN: field " + sf.attributes.source + " on " + sc.attributes.source + ": " + e.getMessage());
                }
            }
        }

        // Also populate inherited fields from parent classes
        populateInheritedFields(obj, sc, gc);

        return assignedId;
    }

    /**
     * Populates fields from ancestor classes that aren't in this class's own field list.
     */
    private void populateInheritedFields(GenericObject obj, DOSchemaClass sc, GenericClass gc) {
        if (sc.attributes.parentClassName == null)
            return;

        DOSchemaClass parent = schema.findClassByName(sc.attributes.parentClassName);
        while (parent != null) {
            GenericField[] parentFields = registrar.getFields(parent.attributes.source);
            if (parentFields != null && parent.fields != null) {
                Map<String, GenericField> pfMap = new LinkedHashMap<>();
                for (GenericField gf : parentFields) {
                    pfMap.put(gf.getName(), gf);
                }

                for (DOSchemaField sf : parent.fields) {
                    if (sf.isVirtualField() || sf.isMethodCallField())
                        continue;

                    // Check if this field was already set by the child class
                    boolean alreadyInChild = false;
                    if (sc.fields != null) {
                        for (DOSchemaField csf : sc.fields) {
                            if (csf.attributes.source.equals(sf.attributes.source)) {
                                alreadyInChild = true;
                                break;
                            }
                        }
                    }
                    if (alreadyInChild)
                        continue;

                    GenericField gf = findInheritedField(gc, sf.attributes.source);
                    if (gf == null)
                        continue;

                    if (DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME.equals(sf.attributes.source)) {
                        // mID already handled in populateFields
                        continue;
                    }

                    if (DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME.equals(sf.attributes.source)) {
                        // Sequential IDs when this class has exactly one instance per fire dept; random otherwise
                        int idSSI = (currentClassObjectCount == DataGenerator.FIRE_DEPT_COUNT) ? (currentClassObjectIndex + 1) : (1 + dataGen.getRng().nextInt(DataGenerator.FIRE_DEPT_COUNT));
                        gf.set(obj, idSSI);
                        continue;
                    }

                    if (dataGen.shouldBeNull(sf))
                        continue;

                    Object value = generateFieldValue(sf);
                    if (value != null) {
                        try {
                            gf.set(obj, value);
                        } catch (Exception e) {
                            System.err.println("[factory] WARN: inherited field " + sf.attributes.source + ": " + e.getMessage());
                        }
                    }
                }
            }

            if (parent.attributes.parentClassName == null)
                break;
            parent = schema.findClassByName(parent.attributes.parentClassName);
        }
    }

    /**
     * Generates a value for a field, handling embedded objects and collections.
     */
    private Object generateFieldValue(DOSchemaField sf) {
        String type = sf.attributes.type;
        if (type == null)
            return null;

        // Collection fields
        if (sf.attributes.isCollection || CollectionTypeUtil.isCollectionType(type)) {
            return generateCollection(sf);
        }

        // Check if this is a primitive/standard type first — even if embedContents is set
        // (e.g., java.awt.Color is mapped to int in the registrar)
        String lower = type.toLowerCase();
        if (isPrimitiveOrStandard(lower)) {
            return dataGen.generateValue(sf);
        }

        // Embedded object fields
        if (sf.attributes.embedContents) {
            return generateEmbeddedObject(sf);
        }

        // IDEntite reference fields — will be wired in Pass 2 via IDEntite objects
        // For inline IDEntite fields on entities, create a simple reference wrapper
        DOSchemaClass fieldTypeClass = schema.findClassByName(type);
        if (fieldTypeClass != null && fieldTypeClass.isIDEntite()) {
            return generateIdEntiteInline(fieldTypeClass);
        }

        // Primitive / standard types
        return dataGen.generateValue(sf);
    }

    private boolean isPrimitiveOrStandard(String lowerType) {
        switch (lowerType) {
        case "string":
        case "java.lang.string":
        case "int":
        case "java.lang.integer":
        case "double":
        case "java.lang.double":
        case "boolean":
        case "java.lang.boolean":
        case "long":
        case "java.lang.long":
        case "float":
        case "java.lang.float":
        case "byte":
        case "java.lang.byte":
        case "short":
        case "java.lang.short":
        case "char":
        case "java.lang.character":
        case "date":
        case "java.util.date":
        case "java.sql.date":
        case "object":
        case "java.lang.object":
        case "java.lang.class":
        case "java.util.uuid":
        case "java.awt.color":
            return true;
        default:
            return false;
        }
    }

    // ── Collection generation ────────────────────────────────────────────────

    private Object generateCollection(DOSchemaField sf) {
        Vector<Object> vec = new Vector<>();
        String childType = sf.attributes.childrenType;
        if (childType == null)
            return vec;

        int count = dataGen.collectionSize();
        DOSchemaClass childClass = schema.findClassByName(childType);

        for (int i = 0; i < count; i++) {
            if (childClass != null && !childClass.isPrimitive()) {
                if (childClass.isIDEntite()) {
                    Object ref = generateIdEntiteInline(childClass);
                    if (ref != null)
                        vec.add(ref);
                } else {
                    Object embedded = createEmbeddedGenericObject(childClass);
                    if (embedded != null)
                        vec.add(embedded);
                }
            } else {
                // Primitive collection elements — generate directly by type and field name
                String fieldName = sf.attributes.source != null ? sf.attributes.source.toLowerCase() : "";
                Object val = dataGen.generatePrimitiveValue(childType, fieldName);
                if (val != null)
                    vec.add(val);
            }
        }
        return vec;
    }

    // ── Embedded object generation ───────────────────────────────────────────

    private Object generateEmbeddedObject(DOSchemaField sf) {
        DOSchemaClass embeddedClass = schema.findClassByName(sf.attributes.type);
        if (embeddedClass == null)
            return null;
        return createEmbeddedGenericObject(embeddedClass);
    }

    private GenericObject createEmbeddedGenericObject(DOSchemaClass sc) {
        GenericClass gc = registrar.getGenericClass(sc.attributes.source);
        if (gc == null)
            return null;

        GenericObject obj = (GenericObject) gc.newInstance();
        registrar.initializePrimitiveDefaults(obj, gc);
        GenericField[] gFields = registrar.getFields(sc.attributes.source);
        if (gFields == null || sc.fields == null)
            return obj;

        Map<String, GenericField> fieldMap = new LinkedHashMap<>();
        for (GenericField gf : gFields) {
            fieldMap.put(gf.getName(), gf);
        }

        for (DOSchemaField sf : sc.fields) {
            if (sf.isVirtualField() || sf.isMethodCallField())
                continue;
            GenericField gf = fieldMap.get(sf.attributes.source);
            if (gf == null)
                continue;

            // Don't recurse too deep — only generate primitive values for embedded objects
            Object val = dataGen.generateValue(sf);
            if (val != null) {
                try {
                    gf.set(obj, val);
                } catch (Exception e) {
                    System.err.println("[factory] WARN: embedded field " + sf.attributes.source + ": " + e.getMessage());
                }
            }
        }
        return obj;
    }

    // ── Pass 2: IDEntite reference objects ────────────────────────────────────

    /**
     * Creates IDEntite wrapper objects. These are lightweight objects with
     * an mID field that references an existing entity's ID (via pointsTo).
     */
    private void createIdEntiteObjects(DOSchemaClass sc) {
        String className = sc.attributes.source;
        GenericClass gc = registrar.getGenericClass(className);
        if (gc == null)
            return;

        // Find the target class this IDEntite points to
        String targetClassName = sc.attributes.pointsTo;
        List<Integer> targetIds = null;
        if (targetClassName != null) {
            targetIds = createdIds.get(targetClassName);
        }

        // IDEntite objects are not created standalone — they exist as field values
        // within parent entities. We still register a small set so storedClasses()
        // shows them.
        int count = Math.min(dataGen.getScale().objectsPerClass, 10);
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            GenericObject obj = (GenericObject) gc.newInstance();
            registrar.initializePrimitiveDefaults(obj, gc);
            int refId = populateIdEntiteFields(obj, className, targetIds);
            ids.add(refId);

            GenericField[] gFields = registrar.getFields(className);
            if (gFields != null) {
                for (GenericField gf : gFields) {
                    if ("mContrainte".equals(gf.getName())) {
                        gf.set(obj, dataGen.getRng().nextInt(3));
                    }
                }
            }

            container.store(obj);
            totalObjectCount++;
        }
        createdIds.put(className, ids);
    }

    /**
     * Creates an inline IDEntite reference (used as a field value within an entity).
     */
    private GenericObject generateIdEntiteInline(DOSchemaClass idEntiteClass) {
        GenericClass gc = registrar.getGenericClass(idEntiteClass.attributes.source);
        if (gc == null)
            return null;

        GenericObject obj = (GenericObject) gc.newInstance();
        registrar.initializePrimitiveDefaults(obj, gc);

        String targetClassName = idEntiteClass.attributes.pointsTo;
        List<Integer> targetIds = (targetClassName != null) ? createdIds.get(targetClassName) : null;
        populateIdEntiteFields(obj, idEntiteClass.attributes.source, targetIds);
        return obj;
    }

    /**
     * Sets mID on an IDEntite GenericObject to reference a valid target entity.
     * Returns the ID that was assigned.
     */
    private int populateIdEntiteFields(GenericObject obj, String className, List<Integer> targetIds) {
        GenericField[] gFields = registrar.getFields(className);
        int assignedId = pickTargetId(targetIds);

        if (gFields != null) {
            for (GenericField gf : gFields) {
                if (DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME.equals(gf.getName())) {
                    gf.set(obj, assignedId);
                }
            }
        }
        return assignedId;
    }

    /**
     * Picks a valid target ID from the list, or generates a fallback random ID.
     */
    private int pickTargetId(List<Integer> targetIds) {
        if (targetIds != null && !targetIds.isEmpty()) {
            return targetIds.get(dataGen.getRng().nextInt(targetIds.size()));
        }
        return 1 + dataGen.getRng().nextInt(100);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GenericField findInheritedField(GenericClass gc, String fieldName) {
        GenericClass current = gc;
        while (current != null) {
            try {
                GenericField gf = (GenericField) current.getDeclaredField(fieldName);
                if (gf != null)
                    return gf;
            } catch (Exception e) {
                // not found on this level
            }
            try {
                current = (GenericClass) current.getSuperclass();
            } catch (ClassCastException e) {
                break;
            }
        }
        return null;
    }

    /**
     * Checks if a schema class is an abstract base that shouldn't have direct instances.
     */
    private boolean isAbstractBase(DOSchemaClass sc) {
        String name = sc.attributes.source;
        // Skip non-domain classes
        if (!name.startsWith("gest.") && !name.startsWith("gen."))
            return true;
        // Skip well-known abstract root classes
        if (name.equals(DOSchemaConstants.ANCESTOR_ENTITE) || name.equals(DOSchemaConstants.ANCESTOR_ENTITE_CONTIENT_ID) || name.equals(DOSchemaConstants.ANCESTOR_IDENTITE) || name.equals(DOSchemaConstants.ANCESTOR_ENTITE_PARAM))
            return true;
        // Skip any class that has subclasses in the schema — only instantiate leaf classes
        return !schema.isLeafClass(sc);
    }
}
