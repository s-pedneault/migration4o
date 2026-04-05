package migration4o.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.migration.recipes.IDEntityHandler;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;
import migration4o.util.ResolvedReference;

public class DODatabase {

    public DOSchema schema;
    public DODatabaseAttributes attributes = new DODatabaseAttributes();
    public DODatabaseClass[] classes;

    private final List<DODatabaseDelegate> delegates = new ArrayList<>();

    /**
     * Lazily-built mID index: maps each DODatabaseClass instance to its mID → list of ResolvedReferences. List-based to handle entity classes (like DSI2003) where mID values are not unique — disambiguation is done at lookup time via {@link DOSchemaClass.PointsToFilter}. Keyed by object identity so that same-named classes from different delegates (user vs static) get separate indexes.
     */
    private final Map<DODatabaseClass, Map<Long, List<ResolvedReference>>> mIdIndex = new java.util.IdentityHashMap<>();
    /** Tracks which DODatabaseClass instances have already been indexed. */
    private final Set<DODatabaseClass> mIdIndexedClasses = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public DODatabase() {
        this.classes = new DODatabaseClass[0];
    }

    /**
     * Adds a delegate and rebuilds the merged class array. User-data delegate should be added first; static-data delegate second. When the same class name exists in an earlier delegate, the later delegate's copy is skipped (user DB wins).
     */
    public void addDelegate(DODatabaseDelegate delegate) {
        delegates.add(delegate);
        rebuildMergedClasses();
        // Primary delegate's attributes become the database-level attributes
        if (delegates.size() == 1) {
            this.attributes = delegate.attributes;
        }
    }

    public List<DODatabaseDelegate> getDelegates() {
        return delegates;
    }

    /**
     * Returns the user-data delegate (first registered), or null if none.
     */
    public DODatabaseDelegate getUserDelegate() {
        return delegates.isEmpty() ? null : delegates.get(0);
    }

    /**
     * Returns the static-data delegate (second registered), or null if none.
     */
    public DODatabaseDelegate getStaticDelegate() {
        return delegates.size() > 1 ? delegates.get(1) : null;
    }

    public DODatabaseClass[] getClasses() {
        return classes;
    }

    public DODatabaseClass findClassByName(String className) {
        if (className == null || classes == null) {
            return null;
        }
        for (DODatabaseClass dbClass : classes) {
            if (dbClass.attributes.source.equals(className)) {
                return dbClass;
            }
        }
        String searchSimpleName = ClassUtil.getSimpleName(className);
        for (DODatabaseClass dbClass : classes) {
            String classSimpleName = ClassUtil.getSimpleName(dbClass.attributes.source);
            if (classSimpleName.equals(searchSimpleName)) {
                return dbClass;
            }
        }
        return null;
    }

    // ── Multi-delegate lookup methods ────────────────────────────────

    /**
     * Loads an object by its native DB4O ID. When no IDEntite context is available, defaults to the <strong>user delegate</strong> to avoid cross-database reads that corrupt the static DB's memory image.
     *
     * @param objectId native DB4O object ID
     * @return the loaded object and its owning delegate, or null
     */
    public ResolvedReference getByID(long objectId) {
        DODatabaseDelegate userDelegate = getUserDelegate();
        if (userDelegate != null) {
            try {
                Object obj = userDelegate.getByID(objectId);
                if (obj != null) {
                    return new ResolvedReference(objectId, userDelegate);
                }
            } catch (Exception e) {
                // user delegate didn't own this ID
            }
        }
        return null;
    }

    /**
     * Finds an entity object by its application-level mID field value, routed to the correct delegate based on the target entity class's {@code isStatic} flag.
     * <p>
     * When {@code targetEntityClass} is provided its {@code isStatic} attribute determines which delegate is searched (static DB vs user DB), and its source name narrows the entity-class scan. When {@code null}, only the user delegate is searched.
     *
     * @param mID the application-level mID to search for
     * @param targetEntityClass the target Entite schema class (determines both type filter and delegate routing); may be null to search user delegate only
     * @return the matching object's ID and its owning delegate, or null
     */
    public ResolvedReference findObjectByMID(Long mID, DOSchemaClass targetEntityClass) {
        return findObjectByMID(mID, targetEntityClass, null);
    }

    /**
     * Finds an entity object by its application-level mID field value, routed to the correct delegate based on the target entity class's {@code isStatic} flag.
     * <p>
     * When {@code filter} is non-null and multiple objects share the same mID, the filter is used to disambiguate by checking an additional field value on each candidate (e.g. {@code mCode=E2} for DSI2003 entries).
     *
     * @param mID the application-level mID to search for
     * @param targetEntityClass the target Entite schema class (determines both type filter and delegate routing); may be null to search user delegate only
     * @param filter optional field=value filter to disambiguate when multiple objects share the same mID
     * @return the matching object's ID and its owning delegate, or null
     */
    public ResolvedReference findObjectByMID(Long mID, DOSchemaClass targetEntityClass, DOSchemaClass.PointsToFilter filter) {
        if (mID == null || classes == null) {
            return null;
        }

        // Determine delegate scope from the target class's isStatic flag.
        // When no target class is provided, default to the user delegate.
        DODatabaseDelegate scopeDelegate;
        String expectedType;
        if (targetEntityClass != null) {
            scopeDelegate = targetEntityClass.attributes.isStatic ? getStaticDelegate() : getUserDelegate();
            expectedType = targetEntityClass.attributes.source;
        } else {
            scopeDelegate = getUserDelegate();
            expectedType = null;
        }

        // Iterate raw delegate classes — NOT the deduped merged array —
        // so that same-named classes from both user and static delegates
        // are visible for delegate-scoped lookups.
        for (DODatabaseDelegate delegate : delegates) {
            if (delegate.classes == null) {
                continue;
            }
            for (DODatabaseClass dbClass : delegate.classes) {
                if (dbClass.schemaClass == null || !dbClass.schemaClass.isEntite()) {
                    continue;
                }

                if (scopeDelegate != null && dbClass.delegate != scopeDelegate) {
                    continue;
                }

                String fullClassName = dbClass.attributes.source;

                if (expectedType != null && !fullClassName.equals(expectedType)) {
                    String simpleClassName = ClassUtil.getSimpleName(fullClassName);
                    if (!simpleClassName.equals(ClassUtil.getSimpleName(expectedType))) {
                        continue;
                    }
                }

                // Build the mID index for this class on first access
                ensureMIdIndexBuilt(dbClass);

                Map<Long, List<ResolvedReference>> classIndex = mIdIndex.get(dbClass);
                if (classIndex != null) {
                    List<ResolvedReference> refs = classIndex.get(mID);
                    if (refs != null) {
                        if (refs.size() == 1 || filter == null) {
                            return refs.get(0);
                        }
                        ResolvedReference match = findByFilter(refs, filter);
                        if (match != null)
                            return match;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Builds the mID → ResolvedReference index for a single entity class. Scans all object IDs once, extracting mID values with shallow activation (depth 2) instead of the previous per-lookup full scan.
     */
    private void ensureMIdIndexBuilt(DODatabaseClass dbClass) {
        if (mIdIndexedClasses.contains(dbClass)) {
            return;
        }
        mIdIndexedClasses.add(dbClass);

        DODatabaseDelegate classDelegate = dbClass.delegate;
        long[] objectIds = dbClass.objects.objectIds;
        if (objectIds == null || classDelegate == null) {
            return;
        }

        Map<Long, List<ResolvedReference>> classIndex = new HashMap<>();
        for (long objectId : objectIds) {
            try {
                Object obj = classDelegate.getByID(objectId);
                if (obj != null) {
                    Long objMID = IDEntityHandler.extractMID(classDelegate, obj);
                    if (objMID != null) {
                        classIndex.computeIfAbsent(objMID, k -> new ArrayList<>()).add(new ResolvedReference(objectId, classDelegate));
                    }
                    // Release the object after extracting its mID — we only
                    // need the index entry, not the activated object in memory.
                    classDelegate.deactivate(obj, 1);
                }
            } catch (Exception e) {
                // skip objects that can't be processed
            }
        }
        mIdIndex.put(dbClass, classIndex);
    }

    /**
     * Disambiguates multiple mID matches by checking a field value on the target entity object against the expected value from the filter.
     */
    private ResolvedReference findByFilter(List<ResolvedReference> candidates, DOSchemaClass.PointsToFilter filter) {
        for (ResolvedReference ref : candidates) {
            try {
                Object obj = ref.delegate.getByID(ref.objectId);
                if (obj != null) {
                    String value = IDEntityHandler.extractFieldValue(ref.delegate, obj, filter.fieldName());
                    ref.delegate.deactivate(obj, 1);
                    if (filter.expectedValue().equals(value)) {
                        return ref;
                    }
                }
            } catch (Exception e) {
                // skip unreadable candidates
            }
        }
        return null;
    }

    /**
     * Loads an object by its native DB4O ID, routing to the correct delegate when the caller provides the IDEntite schema class that triggered the lookup. Uses {@code idEntiteClass.getPointsToClass()} to determine if the target entity lives in the static DB.
     *
     * @param objectId native DB4O object ID
     * @param idEntiteClass the IDEntite schema class that owns this reference (may be null)
     * @return the loaded object and its owning delegate, or null
     */
    public ResolvedReference getByID(long objectId, DOSchemaClass idEntiteClass) {
        DODatabaseDelegate targetDelegate = getUserDelegate();
        if (idEntiteClass != null) {
            DOSchemaClass pointsTo = idEntiteClass.getPointsToClass();
            if (pointsTo != null && pointsTo.attributes.isStatic) {
                targetDelegate = getStaticDelegate();
            }
        }
        if (targetDelegate != null) {
            try {
                Object obj = targetDelegate.getByID(objectId);
                if (obj != null) {
                    return new ResolvedReference(objectId, targetDelegate);
                }
            } catch (Exception e) {
                // targeted delegate didn't own this ID
            }
        }
        return null;
    }

    private void rebuildMergedClasses() {
        Set<String> seen = new LinkedHashSet<>();
        List<DODatabaseClass> merged = new ArrayList<>();
        for (DODatabaseDelegate delegate : delegates) {
            if (delegate.classes != null) {
                for (DODatabaseClass dbClass : delegate.classes) {
                    if (seen.add(dbClass.attributes.source)) {
                        merged.add(dbClass);
                    }
                }
            }
        }
        this.classes = merged.toArray(new DODatabaseClass[0]);
    }

}
