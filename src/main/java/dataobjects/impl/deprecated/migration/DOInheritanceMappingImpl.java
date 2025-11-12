package dataobjects.impl.deprecated.migration;

import dataobjects.api.deprecated.migration.*;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.DOClass;
import java.util.*;

/**
 * Implementation of inheritance mapping analysis.
 */
public class DOInheritanceMappingImpl implements DOInheritanceMapping {

    private final DOSchema schema;
    private final DODatabase database;
    private final Map<DOClass, DOClass> parentMap;
    private final Map<DOClass, Set<DOClass>> childrenMap;
    private final Set<DOClass> rootClasses;

    public DOInheritanceMappingImpl(DOSchema schema, DODatabase database) {
        this.schema = schema;
        this.database = database;
        this.parentMap = new HashMap<>();
        this.childrenMap = new HashMap<>();
        this.rootClasses = new HashSet<>();

        analyzeInheritance();
    }

    private void analyzeInheritance() {
        System.out.println("Analyzing inheritance relationships...");

        // First pass: Build parent-child relationships from schema
        Map<String, DOClass> classMap = new HashMap<>();
        for (DOClass clazz : schema.getClasses()) {
            classMap.put(clazz.getAbsoluteName(), clazz);
        }

        // Add database classes to the map
        for (DOClass clazz : database.getClasses()) {
            if (!classMap.containsKey(clazz.getAbsoluteName())) {
                classMap.put(clazz.getAbsoluteName(), clazz);
            }
        }

        // Build inheritance relationships
        for (DOClass clazz : classMap.values()) {
            String superClassName = clazz.getSuperClassAbsoluteName();

            if (superClassName != null && !superClassName.trim().isEmpty() &&
                    !superClassName.equals("java.lang.Object")) {

                DOClass parentClass = classMap.get(superClassName);
                if (parentClass != null) {
                    // Set parent relationship
                    parentMap.put(clazz, parentClass);

                    // Add to children set
                    childrenMap.computeIfAbsent(parentClass, k -> new HashSet<>()).add(clazz);
                } else {
                    // Parent class not found in schema/database
                    System.out.printf("Warning: Parent class '%s' not found for '%s'%n",
                            superClassName, clazz.getAbsoluteName());
                }
            }
        }

        // Identify root classes (classes with no parent or parent is Object)
        for (DOClass clazz : classMap.values()) {
            if (!parentMap.containsKey(clazz)) {
                rootClasses.add(clazz);
            }
            // Ensure all classes have an entry in childrenMap
            childrenMap.computeIfAbsent(clazz, k -> new HashSet<>());
        }

        System.out.printf("Inheritance analysis complete: %d root classes, %d total classes%n",
                rootClasses.size(), classMap.size());
    }

    @Override
    public DOClass[] getRootClasses() {
        return rootClasses.toArray(new DOClass[0]);
    }

    @Override
    public DOClass[] getAncestors(DOClass clazz) {
        List<DOClass> ancestors = new ArrayList<>();
        DOClass current = getParent(clazz);
        while (current != null) {
            ancestors.add(current);
            current = getParent(current);
        }
        return ancestors.toArray(new DOClass[0]);
    }

    @Override
    public DOClass[] getDescendants(DOClass clazz) {
        Set<DOClass> descendants = new HashSet<>();
        collectDescendants(clazz, descendants);
        return descendants.toArray(new DOClass[0]);
    }

    @Override
    public DOClass[] getLeafClasses(DOClass clazz) {
        Set<DOClass> leafClasses = new HashSet<>();
        collectLeafClasses(clazz, leafClasses);
        return leafClasses.toArray(new DOClass[0]);
    }

    @Override
    public boolean isPolymorphic(DOClass clazz) {
        // A class is polymorphic if it has descendants and stores objects
        DOClass[] children = getChildren(clazz);
        if (children.length == 0) {
            return false;
        }

        // Check if this class actually stores objects (has object count > 0)
        if (clazz instanceof dataobjects.api.models.database.DODatabaseClass) {
            dataobjects.api.models.database.DODatabaseClass dbClass = (dataobjects.api.models.database.DODatabaseClass) clazz;
            return dbClass.getTotalObjectCount() > 0;
        }

        // If not a database class, assume it's polymorphic if it has children
        return true;
    }

    @Override
    public int getInheritanceDepth(DOClass clazz) {
        return getAncestors(clazz).length;
    }

    @Override
    public DOClass getParent(DOClass clazz) {
        return parentMap.get(clazz);
    }

    @Override
    public DOClass[] getChildren(DOClass clazz) {
        Set<DOClass> children = childrenMap.get(clazz);
        return children != null ? children.toArray(new DOClass[0]) : new DOClass[0];
    }

    private void collectDescendants(DOClass clazz, Set<DOClass> descendants) {
        for (DOClass child : getChildren(clazz)) {
            if (!descendants.contains(child)) {
                descendants.add(child);
                collectDescendants(child, descendants);
            }
        }
    }

    private void collectLeafClasses(DOClass clazz, Set<DOClass> leafClasses) {
        DOClass[] children = getChildren(clazz);
        if (children.length == 0) {
            leafClasses.add(clazz);
        } else {
            for (DOClass child : children) {
                collectLeafClasses(child, leafClasses);
            }
        }
    }
}
