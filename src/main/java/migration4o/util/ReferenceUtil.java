package migration4o.util;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseDelegate;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for resolving object references, particularly IDEntite patterns. All multi-delegate lookups are delegated to {@link DODatabase}.
 */
public class ReferenceUtil {

    /**
     * Resolves an IDEntite reference for export. When the target entity class is exportable (migrate=true), resolves to the target entity so the full entity structure is exported. When the target is not exportable (e.g. DSI2003 code tables with migrate=false), returns the IDEntite object itself so its own fields and valueMaps are exported.
     * 
     * @param delegate Database delegate that owns the IDEntite wrapper object
     * @param idEntiteObj The IDEntite object
     * @param idClassName Class name of the IDEntite object
     * @param schemaField Schema field definition (may be null)
     * @param database The database (DODatabase) containing all classes across all delegates
     * @return The resolved reference (objectId + owning delegate)
     */
    public static ResolvedReference resolveIDEntiteForExport(DODatabaseDelegate delegate, Object idEntiteObj, String idClassName, DOSchemaField schemaField, DODatabase database) {
        long idEntiteId = delegate.getID(idEntiteObj);

        // Look up the IDEntite schema class to check if the target entity is exportable
        if (schemaField != null && schemaField.schema != null) {
            DOSchemaClass idEntiteClass = schemaField.schema.findClassByName(idClassName);
            if (idEntiteClass != null) {
                DOSchemaClass targetClass = idEntiteClass.getPointsToClass();
                if (targetClass != null && targetClass.attributes.migrate) {
                    // Target entity is exportable — resolve to it
                    ResolvedReference resolved = resolveIDEntiteReference(delegate, idEntiteObj, targetClass, database);
                    if (resolved != null) {
                        return resolved;
                    }
                    // Resolution failed — fall back to IDEntite itself
                }
            }
        }

        // Target entity not exportable or not resolvable — return IDEntite itself
        return new ResolvedReference(idEntiteId, delegate);
    }

    /**
     * Resolves an IDEntite reference to find the target object.
     * Extracts the mID from the wrapper, then uses {@link DODatabase#findObjectByMID}
     * with the target entity class to route to the correct delegate.
     * 
     * @param delegate          Database delegate that owns the IDEntite wrapper object
     * @param idEntiteObj       The IDEntite object to resolve
     * @param targetEntityClass The target Entite schema class (drives type filter and delegate routing)
     * @param database          The database (DODatabase) containing all classes across all delegates
     * @return The resolved reference (objectId + owning delegate), or null if not found
     */
    public static ResolvedReference resolveIDEntiteReference(DODatabaseDelegate delegate, Object idEntiteObj, DOSchemaClass targetEntityClass, DODatabase database) {
        try {
            // Activate the IDEntite object to read its mID (uses the wrapper's own delegate)
            Long mID = IDEntityHandler.extractMID(delegate, idEntiteObj);

            if (mID == null) {
                return null;
            }

            // Search for the target object with matching mID, routed to the correct delegate
            return database.findObjectByMID(mID, targetEntityClass);
        } catch (Exception e) {
            return null;
        }
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
