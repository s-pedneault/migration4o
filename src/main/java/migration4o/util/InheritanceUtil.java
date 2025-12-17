package migration4o.util;

import migration4o.models.database.*;
import migration4o.models.schema.*;
import java.util.*;

/**
 * Utility class for inheritance-related operations.
 * Provides helper methods for class lookups and relationship building.
 */
public class InheritanceUtil {

    /**
     * Create a fast lookup map from class names to DODatabaseClass objects.
     * This avoids repeated linear searches through class arrays.
     */
    public static Map<String, DODatabaseClass> createDatabaseClassMap(DODatabaseClass[] classes) {
        Map<String, DODatabaseClass> classMap = new HashMap<>();
        for (DODatabaseClass dbClass : classes) {
            if (dbClass.getAbsoluteName() != null) {
                classMap.put(dbClass.getAbsoluteName(), dbClass);
            }
        }
        return classMap;
    }

    /**
     * Create a fast lookup map from class names to DOSchemaClass objects.
     */
    public static Map<String, DOSchemaClass> createSchemaClassMap(DOSchema schema) {
        Map<String, DOSchemaClass> classMap = new HashMap<>();
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass.getAbsoluteName() != null) {
                    classMap.put(schemaClass.getAbsoluteName(), schemaClass);
                }
            }
        }
        return classMap;
    }

    /**
     * Find a schema class by absolute name using the lookup map.
     * More efficient than linear search.
     */
    public static DOSchemaClass findSchemaClass(Map<String, DOSchemaClass> schemaMap, String className) {
        return className != null ? schemaMap.get(className) : null;
    }

    /**
     * Find a database class by absolute name using the lookup map.
     * More efficient than linear search.
     */
    public static DODatabaseClass findDatabaseClass(Map<String, DODatabaseClass> databaseMap, String className) {
        return className != null ? databaseMap.get(className) : null;
    }

    /**
     * Check if a class name represents a root class (no meaningful superclass).
     */
    public static boolean isRootClass(String superClassName) {
        return superClassName == null ||
                superClassName.isEmpty() ||
                superClassName.equals("java.lang.Object");
    }

    /**
     * Get the superclass name from either schema or database class.
     * Prioritizes schema information over database information.
     */
    public static String getSuperClassName(DOSchemaClass schemaClass, DODatabaseClass databaseClass) {
        // Try schema first
        if (schemaClass != null) {
            String schemaSuper = schemaClass.getSuperClassAbsoluteName();
            if (schemaSuper != null && !schemaSuper.isEmpty()) {
                return schemaSuper;
            }
        }

        // Fall back to database class
        if (databaseClass != null) {
            return databaseClass.getSuperClassAbsoluteName();
        }

        return null;
    }

    /**
     * Build the complete inheritance chain for a class using efficient lookups.
     * Returns class names from immediate parent to root.
     */
    public static List<String> buildInheritanceChain(String className,
            Map<String, DOSchemaClass> schemaMap,
            Map<String, DODatabaseClass> databaseMap) {

        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>(); // Prevent infinite loops

        String currentClass = className;
        while (currentClass != null && !visited.contains(currentClass)) {
            visited.add(currentClass);

            // Find superclass using both schema and database information
            DOSchemaClass schemaClass = findSchemaClass(schemaMap, currentClass);
            DODatabaseClass databaseClass = findDatabaseClass(databaseMap, currentClass);
            String superClassName = getSuperClassName(schemaClass, databaseClass);

            if (isRootClass(superClassName)) {
                break; // Reached the top of the hierarchy
            }

            chain.add(superClassName);
            currentClass = superClassName;
        }

        return chain;
    }

    /**
     * Find all direct subclasses of a given parent class.
     */
    public static List<DODatabaseClass> findDirectSubclasses(DODatabaseClass parentClass,
            DODatabaseClass[] allClasses, Map<String, DOSchemaClass> schemaMap) {

        List<DODatabaseClass> directSubclasses = new ArrayList<>();
        String parentName = parentClass.getAbsoluteName();

        for (DODatabaseClass candidateClass : allClasses) {
            if (!candidateClass.equals(parentClass)) {
                DOSchemaClass schemaClass = findSchemaClass(schemaMap, candidateClass.getAbsoluteName());
                String superClassName = getSuperClassName(schemaClass, candidateClass);

                if (parentName.equals(superClassName)) {
                    directSubclasses.add(candidateClass);
                }
            }
        }

        return directSubclasses;
    }

    /**
     * Find all subclasses (direct and indirect) of a given class recursively.
     */
    public static Set<DODatabaseClass> findAllSubclasses(DODatabaseClass parentClass,
            DODatabaseClass[] allClasses, Map<String, DOSchemaClass> schemaMap) {

        Set<DODatabaseClass> allSubclasses = new HashSet<>();
        Set<DODatabaseClass> visited = new HashSet<>();

        findAllSubclassesRecursive(parentClass, allClasses, schemaMap, allSubclasses, visited);

        return allSubclasses;
    }

    /**
     * Recursive helper method to find all subclasses.
     */
    private static void findAllSubclassesRecursive(DODatabaseClass parentClass,
            DODatabaseClass[] allClasses, Map<String, DOSchemaClass> schemaMap,
            Set<DODatabaseClass> allSubclasses, Set<DODatabaseClass> visited) {

        if (visited.contains(parentClass)) {
            return; // Prevent infinite recursion
        }
        visited.add(parentClass);

        List<DODatabaseClass> directSubclasses = findDirectSubclasses(parentClass, allClasses, schemaMap);
        for (DODatabaseClass subclass : directSubclasses) {
            allSubclasses.add(subclass);
            // Recursively find subclasses of this subclass
            findAllSubclassesRecursive(subclass, allClasses, schemaMap, allSubclasses, visited);
        }
    }
}