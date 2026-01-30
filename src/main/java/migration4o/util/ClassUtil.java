package migration4o.util;

import com.db4o.reflect.generic.GenericObject;

public class ClassUtil {
    /**
     * Extracts the package name from an absolute class name.
     *
     * @param absoluteName the absolute class name (e.g., "com.example.MyClass")
     * @return the package name or "(default package)" if no package
     */
    public static String getPackageName(String absoluteName) {
        int lastDot = absoluteName.lastIndexOf('.');
        if (lastDot > 0) {
            return absoluteName.substring(0, lastDot);
        }
        return "(default package)";
    }

    /**
     * Extracts the simple name from an absolute class name.
     * 
     * @param absoluteName the absolute class name (e.g., "com.example.MyClass")
     * @return the simple name (e.g., "MyClass"), or "Unknown" if absoluteName is
     *         null
     */
    public static String getSimpleName(String absoluteName) {
        if (absoluteName == null) {
            return "?";
        }
        if (absoluteName.contains(".")) {
            return absoluteName.substring(absoluteName.lastIndexOf('.') + 1);
        }
        return absoluteName;
    }

    /**
     * Gets the class name of an object, handling GenericObjects specially.
     */
    public static String getClassName(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            try {
                if (genericObj.getGenericClass() != null) {
                    return genericObj.getGenericClass().getName();
                }
            } catch (Exception e) {
                // Fall back to regular class name
            }
        }
        return obj.getClass().getName();
    }

}
