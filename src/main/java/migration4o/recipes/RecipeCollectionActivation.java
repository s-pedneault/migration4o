package migration4o.recipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.util.ObjectResolverUtil;
import migration4o.util.ValueUtil;

/**
 * Unified recipe for extracting collection items from DB4O objects.
 * Provides a single, consistent approach for all collection processing across
 * the application.
 */
public class RecipeCollectionActivation {

    /**
     * Extracts and activates collection items from a DB4O persistent object.
     * Activates the collection object first before extracting items.
     * 
     * NOTE: This is rarely needed in export workflow since objects are already
     * activated at max depth. Kept for compatibility with legacy code.
     * 
     * @param container     The DB4O container
     * @param collectionObj The collection object to extract from
     * @return Collection of items, or null if extraction fails
     */
    public static Collection<?> getItems(ExtObjectContainer container, Object collectionObj) {
        // Activate the collection object to access its fields
        long collectionId = container.ext().getID(collectionObj);
        if (collectionId > 0) {
            ObjectResolverUtil.activateObjectShallow(container, collectionObj, collectionId);
        }

        return extractCollectionItems(container, collectionObj);
    }

    /**
     * Extracts collection items from a DB4O object using a unified approach.
     * This method handles all collection extraction scenarios:
     * 1. Real Java Collection instances (post-activation)
     * 2. GenericObject proxies with DB4O translator fields
     * 3. Arrays
     * 
     * CRITICAL: This is the ONLY method that should be used for collection
     * extraction
     * to ensure consistent behavior across tracer, export, and other components.
     * 
     * @param container     The DB4O container
     * @param collectionObj The collection object to extract from
     * @return Collection of items, or null if extraction fails
     */
    private static Collection<?> extractCollectionItems(ExtObjectContainer container, Object collectionObj) {
        if (collectionObj == null) {
            return null;
        }

        // Handle byte arrays separately - they're arrays but not collections of objects
        if (collectionObj.getClass().isArray()) {
            if (collectionObj instanceof byte[]) {
                // Byte arrays should not be treated as collections of objects
                return null;
            }
            // For other array types, convert to list
            return ValueUtil.arrayToList(collectionObj);
        }

        // APPROACH 1: If it's already a real Java Collection (after activation), use it
        // directly
        // This is the most common case after max-depth activation
        if (collectionObj instanceof Collection) {
            return (Collection<?>) collectionObj;
        }

        // APPROACH 2: If it's a GenericObject proxy, extract from DB4O translator
        // fields
        if (collectionObj instanceof GenericObject) {
            return extractFromGenericObject(container, (GenericObject) collectionObj);
        }

        // Unknown type - cannot extract
        return null;
    }

    /**
     * Extracts collection items from a GenericObject using DB4O translator fields.
     * DB4O stores collections using internal translator fields like
     * "com.db4o.config.TCollection".
     * This method traverses the entire class hierarchy to find these translator
     * fields.
     * 
     * @param container  The DB4O container
     * @param genericObj The GenericObject representing a collection
     * @return Collection of items, or null if extraction fails
     */
    private static Collection<?> extractFromGenericObject(ExtObjectContainer container, GenericObject genericObj) {
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        // CRITICAL: Traverse the ENTIRE class hierarchy (including ancestors)
        // DB4O translator fields may be defined in parent classes
        StoredClass currentClass = storedClass;
        while (currentClass != null) {
            StoredField[] fields = currentClass.getStoredFields();

            for (StoredField field : fields) {
                String fieldName = field.getName();

                // DB4O translators start with "com.db4o.config.T"
                // TCollection is used for Vector and other collection types
                if (fieldName.startsWith("com.db4o.config.T")) {
                    try {
                        Object value = field.get(genericObj);
                        if (value != null && value.getClass().isArray()) {
                            // This is the collection data array - extract all non-null items
                            List<Object> list = new ArrayList<>();
                            int length = java.lang.reflect.Array.getLength(value);

                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(value, i);
                                if (item != null) {
                                    list.add(item);
                                }
                            }

                            return list;
                        }
                    } catch (Exception e) {
                        // Failed to extract from this translator field, try next one
                    }
                }
            }

            // Move to parent class to check for inherited translator fields
            currentClass = currentClass.getParentStoredClass();
        }

        return null;
    }

}
