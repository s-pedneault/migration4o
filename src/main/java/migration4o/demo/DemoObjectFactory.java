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
 * Creates GenericObject instances for all exported schema classes and stores them in the DB4O container.
 *
 * <p>
 * Generation strategy:
 * <ol>
 * <li>Pass 1a — Param (lookup-table) classes: all params, standalone. They are needed as IDEntite resolution targets for entity fields.</li>
 * <li>Pass 1b — Entite (module-root) classes: entities listed in migration-format.xml module class configs, standalone. These are the root objects iterated by the export engine.</li>
 * <li>Pass 1c — Other module-root classes (non-Entite, non-Param, non-IDEntite) that appear in migration-format.xml: standalone.</li>
 * </ol>
 *
 * <p>
 * Classes that are NOT module roots (embedded value types, inner classes, IDEntite wrappers) are created INLINE only — when populating the fields of their parent entity objects — and are never stored as standalone objects. This ensures every object in the database is reachable from an export root, matching the structure of a real DB4O database.
 */
public class DemoObjectFactory {

    private final ObjectContainer container;
    private final DOSchema schema;
    private final SchemaClassRegistrar registrar;
    private final DataGenerator dataGen;

    /**
     * Source class names of all classes that appear in any module's classConfigs. Only classes present here (plus all param classes) are created as standalone root objects. All other schema classes are created inline during field population and never stored directly.
     */
    private final Set<String> moduleRootClassNames;

    /** Tracks created mID values per class name, for cross-referencing in Pass 2. */
    private final Map<String, List<Integer>> createdIds = new HashMap<>();

    /**
     * All standalone GenericObjects stored during Pass 1, keyed by schema source class name. Used in Pass 2 to wire IDEntite reference fields once all createdIds are known.
     */
    private final Map<String, List<GenericObject>> storedObjectsByClass = new LinkedHashMap<>();

    /** Total objects stored. */
    private int totalObjectCount = 0;

    /** Summary lines for each created organization (ParamConfigSSI). */
    private final List<String> createdOrgSummaries = new ArrayList<>();

    /** When true, suppresses the shouldBeNull roll — used for org objects that must be fully defined. */
    private boolean currentClassSkipNulls = false;

    /** Index of the object currently being created within its class, used for sequential field assignment. */
    private int currentClassObjectIndex = 0;

    /** Total number of objects being created for the current class. */
    private int currentClassObjectCount = 0;

    /**
     * Constructor with explicit module root class names.
     *
     * @param moduleRootClassNames source class names of all module-listed root classes
     */
    public DemoObjectFactory(ObjectContainer container, DOSchema schema, SchemaClassRegistrar registrar, DataGenerator dataGen, Set<String> moduleRootClassNames) {
        this.container = container;
        this.schema = schema;
        this.registrar = registrar;
        this.dataGen = dataGen;
        this.moduleRootClassNames = moduleRootClassNames != null ? moduleRootClassNames : Collections.emptySet();
    }

    /**
     * Legacy constructor (no module root filtering — all leaf classes become standalone). Kept for backward compatibility with existing tests.
     */
    public DemoObjectFactory(ObjectContainer container, DOSchema schema, SchemaClassRegistrar registrar, DataGenerator dataGen) {
        this(container, schema, registrar, dataGen, null);
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
                // Only create standalone param objects for classes that the export
                // engine will also iterate as root objects (i.e. those listed in
                // migration-format.xml). For non-module param classes, IDEntite
                // fields write the mID as a plain scalar (embedContents=false), so
                // no param object is ever traversed → any standalone object would
                // be permanently unreached and end up in Extra.xml.
                if (moduleRootClassNames.isEmpty() || moduleRootClassNames.contains(className)) {
                    paramClasses.add(sc);
                }
            } else if (sc.isEntite()) {
                // Entite classes (EntiteContientID descendants) — only if they
                // appear in a module's class list. Non-module entities are
                // embedded value types and must NOT be created standalone.
                if (moduleRootClassNames.isEmpty() || moduleRootClassNames.contains(className)) {
                    entiteClasses.add(sc);
                }
            } else {
                // Other classes (e.g. direct Entite subclasses, utility types)
                // — only if they are in a module's class list.
                if (moduleRootClassNames.isEmpty() || moduleRootClassNames.contains(className)) {
                    otherClasses.add(sc);
                }
            }
        }

        // Pass 1a: Create params first (they're referenced by entities via IDEntite)
        System.out.println("[factory] Pass 1a: Creating " + paramClasses.size() + " EntiteParam classes...");
        for (DOSchemaClass sc : paramClasses) {
            createObjectsForClass(sc, true);
        }

        // Pass 1b: Create module-root Entite classes
        System.out.println("[factory] Pass 1b: Creating " + entiteClasses.size() + " module-root Entite classes...");
        for (DOSchemaClass sc : entiteClasses) {
            createObjectsForClass(sc, false);
        }

        // Pass 1c: Other module-root classes (non-Entite, non-Param, non-IDEntite)
        // Non-module classes are not created standalone — they are created inline
        // by generateEmbeddedObject() / generateCollection() during Pass 1a/1b.
        System.out.println("[factory] Pass 1c: Creating " + otherClasses.size() + " other module-root classes...");
        for (DOSchemaClass sc : otherClasses) {
            createObjectsForClass(sc, false);
        }

        // NOTE: IDEntite objects are NOT stored standalone. They live exclusively
        // as inline field values embedded in their parent entity objects.
        // createIdEntiteObjects() was removed because standalone IDEntite objects
        // have no parent entity — they are unreachable during export and pollute
        // Extra.xml. Inline IDEntite values are created by generateIdEntiteInline()
        // during Pass 1 entity population.

        // Pass 2: wire IDEntite reference fields now that all createdIds are fully populated.
        // This fixes two issues from Pass 1:
        // 1. Forward-reference ordering: target class (e.g. TypeBatiment) may not have been
        // created yet when the referencing class (e.g. DossPrev) was processed.
        // 2. Self-references: a class's own createdIds is null during its own Pass 1 processing.
        System.out.println("[factory] Pass 2: wiring IDEntite references...");
        fixAllIdEntiteReferences();

        container.commit();
        System.out.println("[factory] Committed " + totalObjectCount + " objects total.");
        return totalObjectCount;
    }

    public int getTotalObjectCount() {
        return totalObjectCount;
    }

    public List<String> getCreatedOrgSummaries() {
        return createdOrgSummaries;
    }

    // ── Org tracking ─────────────────────────────────────────────────────────

    private void recordOrgCreated(GenericObject obj, GenericClass gc) {
        try {
            GenericField idSSIField = findInheritedField(gc, DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME);
            int idSSI = (idSSIField != null) ? (Integer) idSSIField.get(obj) : -1;

            String cityName = null;
            GenericField villeField = findInheritedField(gc, "mVille");
            if (villeField != null) {
                Object ville = villeField.get(obj);
                if (ville instanceof GenericObject) {
                    GenericClass villeGc = registrar.getGenericClass("gest.config.VilleGeo");
                    if (villeGc != null) {
                        GenericField nomField = findInheritedField(villeGc, "mNom");
                        if (nomField != null) {
                            Object nom = nomField.get(ville);
                            if (nom != null) {
                                cityName = nom.toString();
                            }
                        }
                    }
                }
            }

            String line = (cityName != null ? cityName : "(unknown)") + " (IDSSI=" + idSSI + ")";
            createdOrgSummaries.add(line);
        } catch (Exception e) {
            createdOrgSummaries.add("(error reading org: " + e.getMessage() + ")");
        }
    }

    // ── Pass 1: Entity object creation ───────────────────────────────────────

    private void createObjectsForClass(DOSchemaClass sc, boolean isParam) {
        String className = sc.attributes.source;
        GenericClass gc = registrar.getGenericClass(className);
        if (gc == null)
            return;

        int count = dataGen.getObjectCount(sc.attributes.source, isParam, sc.attributes.isStatic, sc.attributes.alwaysExportAll);
        List<Integer> ids = new ArrayList<>();

        currentClassSkipNulls = DOSchemaConstants.ORGANIZATION_CLASS_NAME.equals(className);

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
            storedObjectsByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(obj);
            totalObjectCount++;
            if (DOSchemaConstants.ORGANIZATION_CLASS_NAME.equals(className)) {
                recordOrgCreated(obj, gc);
            }
        }

        createdIds.put(className, ids);
    }

    /**
     * Populates all fields on a GenericObject from its schema definition. Returns the mID value if one was assigned, or -1.
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
                int idSSI = assignIdSSI();
                gf.set(obj, idSSI);
                continue;
            }

            // Skip non-exported fields to avoid cascade-storing orphan objects.
            // Exception: collection and map fields must receive an empty container
            // (Vector / Hashtable) rather than being left null. DB4O 7.4 writes the
            // "indirection buffer address" for variable-length fields inline in the
            // object slot. A null reference leaves those bytes uninitialized, which
            // produces negative/huge file offsets and IncompatibleFileFormatException
            // when the object is read back.
            if (!sf.attributes.isExported) {
                String ftype = sf.attributes.type;
                if (ftype != null && (sf.attributes.isCollection || CollectionTypeUtil.isCollectionType(ftype))) {
                    gf.set(obj, new java.util.Vector<>());
                } else if (ftype != null && CollectionTypeUtil.isMapType(ftype)) {
                    gf.set(obj, new java.util.Hashtable<>());
                }
                continue;
            }

            // IDEntite reference fields are deferred to Pass 2 (fixAllIdEntiteReferences),
            // when all createdIds are fully populated. Setting them now would either use an
            // incomplete createdIds pool (wrong IDs) or the shouldBeNull fallback (zero IDs).
            if (isIdEntiteField(sf)) {
                continue;
            }

            // Should this value be null sometimes?
            if (!currentClassSkipNulls && dataGen.shouldBeNull(sf)) {
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
                        gf.set(obj, assignIdSSI());
                        continue;
                    }

                    // Same guard as in populateFields — skip non-exported fields,
                    // but store empty Vector/Hashtable for collection/map types to
                    // prevent null indirection-buffer corruption in DB4O 7.4.
                    if (!sf.attributes.isExported) {
                        String ftype = sf.attributes.type;
                        if (ftype != null && (sf.attributes.isCollection || CollectionTypeUtil.isCollectionType(ftype))) {
                            gf.set(obj, new java.util.Vector<>());
                        } else if (ftype != null && CollectionTypeUtil.isMapType(ftype)) {
                            gf.set(obj, new java.util.Hashtable<>());
                        }
                        continue;
                    }

                    // Defer IDEntite fields to Pass 2
                    if (isIdEntiteField(sf)) {
                        continue;
                    }

                    if (!currentClassSkipNulls && dataGen.shouldBeNull(sf))
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
     * Assigns an mIDSSI value for the current object. ParamConfigSSI objects are the organizations themselves — they always receive a sequential org ID (1..FIRE_DEPT_COUNT), never -1. All other objects receive -1 (no org) with {@link DataGenerator#NO_ORG_PERCENT}% probability, otherwise a random org ID.
     */
    private int assignIdSSI() {
        // ParamConfigSSI: exactly one object per fire dept — sequential, never unassigned
        if (currentClassObjectCount == DataGenerator.FIRE_DEPT_COUNT) {
            return currentClassObjectIndex + 1;
        }
        if (dataGen.getRng().nextInt(100) < DataGenerator.NO_ORG_PERCENT) {
            return -1;
        }
        return 1 + dataGen.getRng().nextInt(DataGenerator.FIRE_DEPT_COUNT);
    }

    /**
     * Returns true if the field's type is an IDEntite class (a reference wrapper). These fields are deferred to Pass 2 so that createdIds for all classes is fully populated before wiring.
     */
    private boolean isIdEntiteField(DOSchemaField sf) {
        String type = sf.attributes.type;
        if (type == null || type.isEmpty())
            return false;
        DOSchemaClass fc = schema.findClassByName(type);
        return fc != null && fc.isIDEntite();
    }

    // ── Pass 2: IDEntite reference wiring ─────────────────────────────────────

    /**
     * After all standalone objects have been created and createdIds is fully populated, iterate every stored object and wire its IDEntite reference fields to a valid target ID. Re-stores each object so DB4O persists the update.
     */
    private void fixAllIdEntiteReferences() {
        int fixCount = 0;
        for (Map.Entry<String, List<GenericObject>> entry : storedObjectsByClass.entrySet()) {
            String className = entry.getKey();
            DOSchemaClass sc = schema.findClassByName(className);
            if (sc == null)
                continue;
            GenericClass gc = registrar.getGenericClass(className);
            if (gc == null)
                continue;

            for (GenericObject obj : entry.getValue()) {
                if (fixIdEntiteFieldsOn(obj, sc, gc)) {
                    container.store(obj);
                    fixCount++;
                }
            }
        }
        System.out.println("[factory] Pass 2: wired IDEntite references on " + fixCount + " objects.");
    }

    /**
     * Sets all IDEntite reference fields on the given object (direct + inherited). Returns true if any field was updated.
     */
    private boolean fixIdEntiteFieldsOn(GenericObject obj, DOSchemaClass sc, GenericClass gc) {
        boolean updated = false;
        Set<String> handled = new HashSet<>();

        // Walk the class chain — own fields first, then parent fields
        DOSchemaClass current = sc;
        while (current != null) {
            if (current.fields != null) {
                for (DOSchemaField sf : current.fields) {
                    if (sf.isVirtualField() || sf.isMethodCallField())
                        continue;
                    if (!sf.attributes.isExported)
                        continue;
                    if (!isIdEntiteField(sf))
                        continue;
                    if (!handled.add(sf.attributes.source))
                        continue; // child already defined this field

                    GenericField gf = findInheritedField(gc, sf.attributes.source);
                    if (gf == null)
                        continue;

                    Object value = generateFieldValue(sf);
                    if (value != null) {
                        try {
                            gf.set(obj, value);
                            updated = true;
                        } catch (Exception e) {
                            // field type mismatch — skip silently
                        }
                    }
                }
            }
            if (current.attributes.parentClassName == null)
                break;
            current = schema.findClassByName(current.attributes.parentClassName);
        }
        return updated;
    }

    /**
     * Generates a value for a field, handling embedded objects and collections.
     */
    private Object generateFieldValue(DOSchemaField sf) {
        String type = sf.attributes.type;
        if (type == null)
            // DataGenerator.generateValue() defaults null type to "string" and checks
            // valueMap first — so fields with no explicit type but a valueMap still get
            // a valid enum value instead of staying at the "" initialised by initializePrimitiveDefaults.
            return dataGen.generateValue(sf);

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
     * Previously created standalone IDEntite wrapper objects and stored them in the container for introspection purposes. Removed: standalone IDEntite objects are never referenced by any entity, making them permanently unreachable during export and causing them to appear in Extra.xml. IDEntite objects now live exclusively as inline field values created by generateIdEntiteInline() during Pass 1.
     */
    @SuppressWarnings("unused")
    private void createIdEntiteObjects(DOSchemaClass sc) {
        // Intentionally empty — see method Javadoc.
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
     * Sets mID on an IDEntite GenericObject to reference a valid target entity. Returns the ID that was assigned.
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
