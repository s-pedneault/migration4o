package migration4o.migration.monitoring;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks classes referenced during export that were not in the original export
 * request.
 * When an object refers to another object whose class is not in the export set,
 * that class is dynamically added to ensure referential integrity.
 * 
 * Referenced classes that don't belong to any module in the export structure
 * are collected into a virtual "Referenced" module.
 */
public class ReferencedClassTracker {
    // Classes that are part of the original export request (organized by module)
    private final Map<String, Set<String>> moduleClasses = new HashMap<>();

    // Classes discovered during export that weren't in the original request
    private final Set<String> referencedClasses = new HashSet<>();

    // Track which referenced classes have been exported (to avoid duplicates)
    private final Set<String> exportedReferencedClasses = new HashSet<>();

    /**
     * Registers a module and its classes as part of the export request.
     * 
     * @param moduleName the module name
     * @param classNames the classes in this module
     */
    public void registerModule(String moduleName, Set<String> classNames) {
        moduleClasses.put(moduleName, new HashSet<>(classNames));
    }

    /**
     * Checks if a class is in the original export request.
     * 
     * @param className the full class name (e.g., "gest.prev.TypePrev")
     * @return true if this class is in any of the registered modules
     */
    public boolean isInExportRequest(String className) {
        for (Set<String> classes : moduleClasses.values()) {
            if (classes.contains(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers a referenced class that was discovered during export.
     * Only registers if the class is not already in the export request.
     * 
     * @param className the full class name (e.g., "gest.prev.TypePrev")
     */
    public void registerReferencedClass(String className) {
        if (!isInExportRequest(className)) {
            referencedClasses.add(className);
        }
    }

    /**
     * Gets all referenced classes that need to be exported.
     * 
     * @return set of class names
     */
    public Set<String> getReferencedClasses() {
        return new HashSet<>(referencedClasses);
    }

    /**
     * Marks a referenced class as exported (to avoid duplicate exports).
     * 
     * @param className the class name
     */
    public void markReferencedClassAsExported(String className) {
        exportedReferencedClasses.add(className);
    }

    /**
     * Checks if a referenced class has already been exported.
     * 
     * @param className the class name
     * @return true if already exported
     */
    public boolean isReferencedClassExported(String className) {
        return exportedReferencedClasses.contains(className);
    }

    /**
     * Finds which module a class belongs to.
     * 
     * @param className the full class name
     * @return the module name, or null if not in any module
     */
    public String findModuleForClass(String className) {
        for (Map.Entry<String, Set<String>> entry : moduleClasses.entrySet()) {
            if (entry.getValue().contains(className)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the count of referenced classes discovered so far.
     * 
     * @return number of referenced classes
     */
    public int getReferencedClassCount() {
        return referencedClasses.size();
    }

    /**
     * Resets the tracker for a new export operation.
     * NOTE: Does NOT clear moduleClasses - those are registered once at the start
     * of the entire export and should persist across all class exports.
     */
    public void reset() {
        // Do NOT clear moduleClasses - they're registered once for the whole export!
        referencedClasses.clear();
        exportedReferencedClasses.clear();
    }
}
