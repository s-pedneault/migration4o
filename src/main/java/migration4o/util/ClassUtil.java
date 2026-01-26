package migration4o.util;

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
     * @return the simple name (e.g., "MyClass")
     */
    public static String getSimpleName(String absoluteName) {
        if (absoluteName.contains(".")) {
            return absoluteName.substring(absoluteName.lastIndexOf('.') + 1);
        }
        return absoluteName;
    }

}
