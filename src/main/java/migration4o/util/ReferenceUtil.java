package migration4o.util;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseDelegate;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for resolving object references, particularly IDEntite patterns.
 * All multi-delegate lookups are delegated to {@link DODatabase}.
 */
public class ReferenceUtil {

    /**
     * Determines which object should be exported for an IDEntite reference. If embedContents is false, returns the IDEntite object itself (with its delegate). If embedContents is true, attempts to resolve to the target object across all delegates via {@link DODatabase}. Returns null if resolution fails (caller must skip the export).
     * 
     * @param delegate Database delegate that owns the IDEntite wrapper object
     * @param idEntiteObj The IDEntite object
     * @param idClassName Class name of the IDEntite object
     * @param schemaField Schema field definition (may be null)
     * @param database The database (DODatabase) containing all classes across all delegates
     * @return The resolved reference (objectId + owning delegate), or null if embedContents resolution failed
     */
    public static ResolvedReference resolveIDEntiteForExport(DODatabaseDelegate delegate, Object idEntiteObj, String idClassName, DOSchemaField schemaField, DODatabase database) {
        long idEntiteId = delegate.getID(idEntiteObj);

        // Check if we should embed the target object's contents
        boolean embedContents = schemaField != null && schemaField.attributes.embedContents;

        if (!embedContents) {
            // Export the IDEntite object itself — it belongs to the current delegate
            return new ResolvedReference(idEntiteId, delegate);
        }

        // embedContents=true: try to resolve to the target object across all delegates.
        // Walk the reference schema hierarchy for pointsTo (uses schema, not DB classes,
        // so abstract classes like gest.gen.IDEntite don't break the chain).
        String expectedType = database.resolveExpectedTypeFromSchema(idClassName);
        String expectedTypeSource = (expectedType != null) ? "schema" : null;
        if (expectedType == null) {
            String fieldName = schemaField != null ? schemaField.attributes.destinationName : null;
            expectedType = extractExpectedTypeFromFieldName(fieldName, idClassName);
            expectedTypeSource = "heuristic";
        }

        // Extract mID for diagnostics before attempting resolution
        Long mID = IDEntityHandler.extractMID(delegate, idEntiteObj);

        // Resolve the reference to find the target object (searches all delegates)
        ResolvedReference resolved = resolveIDEntiteReference(delegate, idEntiteObj, expectedType, database);

        if (resolved == null) {
            String fieldName = schemaField != null ? schemaField.attributes.destinationName : null;
            System.err.println("[WARN] IDEntite resolution failed for field '" + fieldName + "' (" + idClassName + ", objectId=" + idEntiteId + ", mID=" + mID + ", expectedType=" + expectedType + " [" + expectedTypeSource + "]) - skipping unresolvable reference");
            return null;
        }

        return resolved;
    }

    /**
     * Resolves an IDEntite reference to find the target object across all delegates.
     * Extracts the mID from the wrapper, then uses {@link DODatabase#findObjectByMID}
     * to search all delegates.
     * 
     * @param delegate Database delegate that owns the IDEntite wrapper object
     * @param idEntiteObj The IDEntite object to resolve
     * @param expectedType Expected type name (simple or absolute class name) - can be null
     * @param database The database (DODatabase) containing all classes across all delegates
     * @return The resolved reference (objectId + owning delegate), or null if not found
     */
    public static ResolvedReference resolveIDEntiteReference(DODatabaseDelegate delegate, Object idEntiteObj, String expectedType, DODatabase database) {
        try {
            // Activate the IDEntite object to read its mID (uses the wrapper's own delegate)
            Long mID = IDEntityHandler.extractMID(delegate, idEntiteObj);

            if (mID == null) {
                return null;
            }

            // Search for the target object with matching mID across all delegates
            return database.findObjectByMID(mID, expectedType);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @deprecated Use {@link DODatabase#findObjectByMID} directly.
     */
    @Deprecated
    public static ResolvedReference findObjectByMID(Long mID, String expectedType, DODatabase database) {
        if (database == null) {
            return null;
        }
        return database.findObjectByMID(mID, expectedType);
    }

    /**
     * @deprecated Use {@link IDEntityHandler#extractMID} instead.
     */
    @Deprecated
    public static Long extractMIDField(DODatabaseDelegate delegate, Object obj) {
        return IDEntityHandler.extractMID(delegate, obj);
    }

    /**
     * Extracts expected type name from a field name or ID class name. Example: "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere" Example: "gest.gen.IDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
     * 
     * @param fieldName The field name
     * @param idClassName The ID class name (fully qualified)
     * @return The expected type name, or null if cannot be extracted
     */
    public static String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        if (fieldName != null && fieldName.startsWith("mID")) {
            return fieldName.substring(3);
        }
        String simpleClassName = ClassUtil.getSimpleName(idClassName);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2);
        }
        return null;
    }
}
