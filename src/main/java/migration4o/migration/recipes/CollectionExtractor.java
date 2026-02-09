package migration4o.migration.recipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.util.ObjectResolverUtil;

/**
 * Extracts collection items from DB4O persistent objects.
 * Handles DB4O's internal collection storage format (TCollection translators).
 */
public class CollectionExtractor {

    /**
     * Extracts collection items from a DB4O persistent object.
     * DB4O stores collections using translator fields like
     * "com.db4o.config.TCollection".
     * This method traverses the class hierarchy to find and extract the collection
     * data.
     * 
     * @param container     The DB4O container
     * @param collectionObj The collection object to extract from
     * @return Collection of items, or null if extraction fails
     */
    public static Collection<?> extractItems(ExtObjectContainer container, Object collectionObj) {
        // Handle byte arrays separately - they're arrays but not collections
        if (collectionObj != null && collectionObj.getClass().isArray()) {
            if (collectionObj instanceof byte[]) {
                // Byte arrays should not be treated as collections of objects
                // They should be handled as primitive data (e.g., base64 encoded)
                System.err.println("WARNING: Attempting to extract collection items from byte array. " +
                        "This field may be incorrectly marked as collection=true in the schema.");
                return null;
            }
        }

        // If it's already a Java Collection (e.g., Vector), just return it
        if (collectionObj instanceof Collection) {
            return (Collection<?>) collectionObj;
        }

        if (!(collectionObj instanceof GenericObject)) {
            // Warn about unhandled collection type - this could indicate a data extraction
            // issue
            if (collectionObj != null) {
                String typeName = collectionObj.getClass().getName();
                System.err.println("WARNING: Collection field is not a GenericObject or Collection, type=" +
                        typeName + ". Unable to extract items.");
            }
            return null;
        }

        GenericObject genericObj = (GenericObject) collectionObj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        // Traverse the entire class hierarchy to find collection data
        // DB4O stores Vector data in translator fields like
        // "com.db4o.config.TCollection"
        StoredClass currentClass = storedClass;
        while (currentClass != null) {
            StoredField[] fields = currentClass.getStoredFields();

            for (StoredField field : fields) {
                String fieldName = field.getName();

                // DB4O translators start with "com.db4o.config.T"
                // TCollection is used for Vector and other collections
                if (fieldName.startsWith("com.db4o.config.T")) {
                    try {
                        Object value = field.get(genericObj);
                        if (value != null && value.getClass().isArray()) {
                            // This is the collection data array
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
                        // Failed to extract from DB4O translator field
                    }
                }
            }

            currentClass = currentClass.getParentStoredClass();
        }

        return null;
    }

    /**
     * Extracts and activates collection items from a DB4O persistent object.
     * Same as extractItems but also activates the collection object first.
     * 
     * @param container     The DB4O container
     * @param collectionObj The collection object to extract from
     * @return Collection of items, or null if extraction fails
     */
    public static Collection<?> extractAndActivate(ExtObjectContainer container, Object collectionObj) {
        // Activate the collection object to access its fields
        long collectionId = container.ext().getID(collectionObj);
        if (collectionId > 0) {
            ObjectResolverUtil.activateObject(container, collectionObj, collectionId);
        }

        return extractItems(container, collectionObj);
    }
}
