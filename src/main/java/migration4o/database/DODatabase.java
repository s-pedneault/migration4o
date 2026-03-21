package migration4o.database;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    public DODatabase() {
        this.classes = new DODatabaseClass[0];
    }

    /**
     * Adds a delegate and rebuilds the merged class array.
     * User-data delegate should be added first; static-data delegate second.
     * When the same class name exists in an earlier delegate, the later
     * delegate's copy is skipped (user DB wins).
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
     * Loads an object by its native DB4O ID, searching across all delegates.
     * Returns the object from the first delegate that recognises the ID.
     *
     * @param objectId native DB4O object ID
     * @return the loaded object and its owning delegate, or null if no delegate
     *         owns it
     */
    public ResolvedReference getByID(long objectId) {
        for (DODatabaseDelegate d : delegates) {
            try {
                Object obj = d.getByID(objectId);
                if (obj != null) {
                    return new ResolvedReference(objectId, d);
                }
            } catch (Exception e) {
                // try next delegate
            }
        }
        return null;
    }

    /**
     * Finds an entity object by its application-level mID field value,
     * searching across all delegates and all entity classes that match
     * {@code expectedType}.
     * <p>
     * Each {@link DODatabaseClass} routes lookups through its own delegate so
     * that objects in the static database are found when they are absent from
     * the user database.
     *
     * @param mID          the application-level mID to search for
     * @param expectedType fully-qualified or simple target class name (may be
     *                     null to search all entity classes)
     * @return the matching object's ID and its owning delegate, or null
     */
    public ResolvedReference findObjectByMID(Long mID, String expectedType) {
        if (mID == null || classes == null) {
            return null;
        }

        for (DODatabaseClass dbClass : classes) {
            if (dbClass.schemaClass == null || !dbClass.schemaClass.isEntite()) {
                continue;
            }

            String fullClassName = dbClass.attributes.source;

            if (expectedType != null && !fullClassName.equals(expectedType)) {
                String simpleClassName = ClassUtil.getSimpleName(fullClassName);
                if (!simpleClassName.equals(expectedType)) {
                    continue;
                }
            }

            DODatabaseDelegate classDelegate = dbClass.delegate;
            long[] objectIds = dbClass.objects.objectIds;
            if (objectIds != null && classDelegate != null) {
                for (long objectId : objectIds) {
                    try {
                        Object obj = classDelegate.getByID(objectId);
                        if (obj != null) {
                            Long objMID = IDEntityHandler.extractMID(classDelegate, obj);
                            if (mID.equals(objMID)) {
                                return new ResolvedReference(objectId, classDelegate);
                            }
                        }
                    } catch (Exception e) {
                        // skip objects that can't be processed
                    }
                }
            }
        }

        return null;
    }

    /**
     * Resolves the expected target entity type for an IDEntite class by
     * walking up the <strong>reference schema</strong> hierarchy to find a
     * {@code pointsTo} attribute.
     * <p>
     * Unlike a database-only walk, this uses the schema (which contains
     * abstract ancestor classes like {@code gest.gen.IDEntite} even when they
     * have no DB objects), so the chain never breaks for intermediate classes.
     *
     * @param idClassName fully-qualified IDEntite class name
     * @return the {@code pointsTo} target class name, or null if not found
     */
    public String resolveExpectedTypeFromSchema(String idClassName) {
        if (idClassName == null || schema == null) {
            return null;
        }
        DOSchemaClass sc = schema.findClassByName(idClassName);
        while (sc != null) {
            if (sc.attributes.pointsTo != null && !sc.attributes.pointsTo.isEmpty()) {
                return sc.attributes.pointsTo;
            }
            if (sc.attributes.parentClassName != null && !sc.attributes.parentClassName.isEmpty()) {
                sc = schema.findClassByName(sc.attributes.parentClassName);
            } else {
                sc = null;
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
