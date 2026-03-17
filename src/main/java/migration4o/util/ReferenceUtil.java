package migration4o.util;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Utility class for resolving object references, particularly IDEntite
 * patterns.
 */
public class ReferenceUtil {

    /**
     * Determines which object ID should be exported for an IDEntite reference.
     * If embedContents is false, returns the IDEntite object ID itself. If
     * embedContents is true, attempts to resolve to the target object. Returns
     * null if resolution fails (caller must skip the export).
     * 
     * @param container Database container
     * @param idEntiteObj The IDEntite object
     * @param idClassName Class name of the IDEntite object
     * @param schemaField Schema field definition (may be null)
     * @param databaseSchema The database schema containing all classes
     * @return The object ID to export, or null if embedContents resolution
     * failed
     */
    public static Long resolveIDEntiteForExport(ExtObjectContainer container, Object idEntiteObj, String idClassName, DOSchemaField schemaField, DOSchema databaseSchema) {
        long idEntiteId = container.ext().getID(idEntiteObj);

        // Check if we should embed the target object's contents
        boolean embedContents = schemaField != null && schemaField.embedContents;

        if (!embedContents) {
            // Export the IDEntite object itself
            return idEntiteId;
        }

        // embedContents=true: try to resolve to the target object
        String fieldName = schemaField != null ? schemaField.destinationName : null;
        String expectedType = extractExpectedTypeFromFieldName(fieldName, idClassName);

        // Resolve the reference to find the target object
        Long targetObjectId = resolveIDEntiteReference(container, idEntiteObj, expectedType, databaseSchema);

        if (targetObjectId == null) {
            System.err.println("[WARN] IDEntite resolution failed for field '" + fieldName + "' (" + idClassName + ", objectId=" + idEntiteId + ") - skipping unresolvable reference");
            return null;
        }

        return targetObjectId;
    }

    /**
     * Resolves an IDEntite reference to find the target object ID.
     * 
     * @param container Database container
     * @param idEntiteObj The IDEntite object to resolve
     * @param expectedType Expected type name (simple or absolute class name) -
     * can be null
     * @param databaseSchema The database schema containing all classes
     * @return The object ID of the target object, or null if not found
     */
    public static Long resolveIDEntiteReference(ExtObjectContainer container, Object idEntiteObj, String expectedType, DOSchema databaseSchema) {
        try {
            long idEntiteId = container.ext().getID(idEntiteObj);

            // Activate the IDEntite object to read its mID
            ObjectResolverUtil.activateObjectShallow(container, idEntiteObj, idEntiteId);
            Long mID = extractMIDField(container, idEntiteObj);

            if (mID == null) {
                return null;
            }

            // Search for the target object with matching mID
            return findObjectByMID(container, mID, expectedType, databaseSchema);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds an object by its mID field value.
     * 
     * @param container Database container
     * @param mID The mID value to search for
     * @param expectedType Expected type name (simple or absolute class name) -
     * can be null
     * @param databaseSchema The database schema containing all classes
     * @return The object ID of the matching object, or null if not found
     */
    public static Long findObjectByMID(ExtObjectContainer container, Long mID, String expectedType, DOSchema databaseSchema) {
        if (mID == null || databaseSchema == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (!schemaClass.isEntite(databaseSchema)) {
                continue;
            }

            String fullClassName = schemaClass.source;

            // Only search in classes that match the expected type (if
            // specified)
            if (expectedType != null && !fullClassName.equals(expectedType)) {
                // Also try matching by simple name
                String simpleClassName = ClassUtil.getSimpleName(fullClassName);
                if (!simpleClassName.equals(expectedType)) {
                    continue;
                }
            }

            long[] objectIds = schemaClass.objectIds;
            if (objectIds != null) {
                for (long objectId : objectIds) {
                    try {
                        Object obj = container.ext().getByID(objectId);
                        if (obj != null) {
                            ObjectResolverUtil.activateObjectShallow(container, obj, objectId);
                            Long objMID = extractMIDField(container, obj);

                            if (mID.equals(objMID)) {
                                return objectId;
                            }
                        }
                    } catch (Exception e) {
                        // Skip objects that can't be processed
                    }
                }
            }
        }

        return null;
    }

    /**
     * Extracts the mID field value from a GenericObject.
     * 
     * @param container Database container
     * @param obj The object (must be a GenericObject)
     * @return The mID value as Long, or null if not found
     */
    public static Long extractMIDField(ExtObjectContainer container, Object obj) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        StoredField[] fields = storedClass.getStoredFields();
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                } catch (Exception e) {
                    // Field access failed
                }
            }
        }
        return null;
    }

    /**
     * Extracts expected type name from a field name or ID class name. Example:
     * "mIDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere" Example:
     * "gest.gen.IDTypeAssistanceParticuliere" -> "TypeAssistanceParticuliere"
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
