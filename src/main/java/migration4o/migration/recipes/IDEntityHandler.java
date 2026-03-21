package migration4o.migration.recipes;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.DatabaseUtil;

/**
 * Handles IDEntite-specific operations.
 * IDEntite objects are DB4O reference objects that contain an mID field.
 */
public class IDEntityHandler {

    /**
     * Extracts the mID field value from an IDEntite object.
     * The mID field represents the object's identifier.
     * 
     * @param container      The DB4O container
     * @param idEntiteObject The IDEntite object
     * @return The mID value, or null if not found or not accessible
     */
    public static Long extractMID(DODatabaseDelegate delegate, Object idEntiteObject) {
        if (idEntiteObject == null) {
            return null;
        }

        if (!(idEntiteObject instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) idEntiteObject;

        // Activate the object to ensure field values are loaded at all inheritance
        // levels
        // CRITICAL: Use max depth to handle deep inheritance hierarchies (e.g.,
        // IDEntite -> subclass -> subclass)
        try {
            delegate.activate(idEntiteObject, Integer.MAX_VALUE);
        } catch (StackOverflowError e) {
            // Stack overflow - try shallow activation as fallback
            try {
                delegate.activate(idEntiteObject, 10);
            } catch (Exception e2) {
                // Shallow activation also failed - try to proceed anyway
            }
        } catch (Exception e) {
            // Activation failed, try to proceed anyway
        }

        StoredClass storedClass = delegate.storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        // Find the mID field — must walk up the inheritance hierarchy because
        // DB4O's getStoredFields() only returns fields declared at that class level.
        // The mID field is typically declared on a base class (e.g., EntiteContientID),
        // not on concrete subclasses like IDCategRisque or IDUsagePrincipal.
        StoredField[] fields = DatabaseUtil.getAllFieldsIncludingAncestors(storedClass);
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                } catch (Exception e) {
                    // Failed to extract mID
                }
                break;
            }
        }

        return null;
    }

    /**
     * Checks if an IDEntite object should be skipped based on its mID value.
     * Typically, mID == -1 indicates an invalid or placeholder reference.
     * 
     * @param container      The DB4O container
     * @param idEntiteObject The IDEntite object
     * @return true if mID is -1, false otherwise
     */
    public static boolean shouldSkipMinusOne(DODatabaseDelegate delegate, Object idEntiteObject) {
        Long mID = extractMID(delegate, idEntiteObject);
        return mID != null && mID == -1;
    }

    /**
     * Checks if an mID value indicates a valid reference.
     * Valid references have mID > 0.
     * 
     * @param mID The mID value to check
     * @return true if valid (mID > 0), false otherwise
     */
    public static boolean isValidMID(Long mID) {
        return mID != null && mID > 0;
    }

    /**
     * Checks if an mID value indicates an invalid/placeholder reference.
     * Invalid references have mID == -1.
     * 
     * @param mID The mID value to check
     * @return true if invalid (mID == -1), false otherwise
     */
    public static boolean isInvalidMID(Long mID) {
        return mID != null && mID == -1;
    }
}
