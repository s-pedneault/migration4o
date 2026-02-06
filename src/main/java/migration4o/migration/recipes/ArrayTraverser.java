package migration4o.migration.recipes;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for traversing and extracting items from arrays using reflection.
 * Provides type-safe array operations without knowing the array's component
 * type.
 */
public class ArrayTraverser {

    /**
     * Gets the length of an array using reflection.
     * 
     * @param array The array object
     * @return The length of the array, or 0 if not an array
     */
    public static int getLength(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return 0;
        }
        return Array.getLength(array);
    }

    /**
     * Gets an item from an array at the specified index using reflection.
     * 
     * @param array The array object
     * @param index The index to get
     * @return The item at the specified index, or null if invalid
     */
    public static Object getItem(Object array, int index) {
        if (array == null || !array.getClass().isArray()) {
            return null;
        }

        int length = Array.getLength(array);
        if (index < 0 || index >= length) {
            return null;
        }

        return Array.get(array, index);
    }

    /**
     * Converts an array to a List using reflection.
     * Filters out null items by default.
     * 
     * @param array The array object
     * @return List of non-null items, or empty list if not an array
     */
    public static List<Object> toList(Object array) {
        return toList(array, true);
    }

    /**
     * Converts an array to a List using reflection.
     * 
     * @param array       The array object
     * @param filterNulls Whether to filter out null items
     * @return List of items, or empty list if not an array
     */
    public static List<Object> toList(Object array, boolean filterNulls) {
        List<Object> list = new ArrayList<>();

        if (array == null || !array.getClass().isArray()) {
            return list;
        }

        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            if (!filterNulls || item != null) {
                list.add(item);
            }
        }

        return list;
    }

    /**
     * Checks if an object is an array.
     * 
     * @param obj The object to check
     * @return true if the object is an array, false otherwise
     */
    public static boolean isArray(Object obj) {
        return obj != null && obj.getClass().isArray();
    }
}
