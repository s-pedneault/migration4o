package migration4o.schema.processors;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-processor that detects and adds missing references to schema classes.
 * Specifically handles indirect references through IDEntite collections.
 * 
 * For example, if a field has childrenType="IDProgramme" and IDProgramme has
 * pointsTo="Programme", this detector will add a reference to Programme.
 */
public class DOReferenceDetector {

    /**
     * Scans all classes in the schema and adds missing references.
     * This includes indirect references through IDEntite collections where
     * the collection's childrenType points to an IDEntite that has a pointsTo
     * attribute.
     * 
     * @param schema The schema to process
     */
    public static void detectAndAddReferences(DOSchema schema) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        // Build a map of class names to classes for quick lookup
        Map<String, DOSchemaClass> classMap = buildClassMap(schema);

        // First pass: infer pointsTo for IDEntite subclasses that don't have it set
        inferPointsTo(schema, classMap);

        // For each class, collect all references to it
        Map<String, List<DOSchemaReference>> referencesMap = new HashMap<>();

        // Scan all classes and their fields
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.fields == null) {
                continue;
            }

            for (DOSchemaField field : schemaClass.fields) {
                // 1. Check direct field type references (non-primitive, non-collection)
                if (field.type != null && !field.isCollection) {
                    DOSchemaClass typeClass = findClass(classMap, field.type);
                    if (typeClass != null && isEntityType(field.type)) {
                        // Direct reference to an entity class
                        addReference(referencesMap, typeClass.source,
                                schemaClass.source, field.source);
                    }
                }

                // 2. Check collection children type references
                if (field.childrenType != null) {
                    DOSchemaClass childrenClass = findClass(classMap, field.childrenType);

                    if (childrenClass != null) {
                        if (childrenClass.isIDEntite(schema)) {
                            // Indirect reference through IDEntite with pointsTo
                            String pointsTo = childrenClass.pointsTo;
                            if (pointsTo != null && !pointsTo.isEmpty()) {
                                DOSchemaClass targetClass = findClass(classMap, pointsTo);
                                if (targetClass != null) {
                                    addReference(referencesMap, targetClass.source,
                                            schemaClass.source, field.source);
                                }
                            }
                        } else if (isEntityType(field.childrenType)) {
                            // Direct reference to entity class in collection
                            addReference(referencesMap, childrenClass.source,
                                    schemaClass.source, field.source);
                        }
                    }
                }
            }
        }

        // Now update each class's schemaReferences array with the new references
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            List<DOSchemaReference> newRefs = referencesMap.get(schemaClass.source);
            if (newRefs != null && !newRefs.isEmpty()) {
                // Merge with existing references
                List<DOSchemaReference> allRefs = new ArrayList<>();

                // Add existing references
                if (schemaClass.schemaReferences != null) {
                    for (DOSchemaReference ref : schemaClass.schemaReferences) {
                        allRefs.add(ref);
                    }
                }

                // Add new references (avoid duplicates)
                for (DOSchemaReference newRef : newRefs) {
                    if (!containsReference(allRefs, newRef)) {
                        allRefs.add(newRef);
                    }
                }

                // Update the class
                schemaClass.schemaReferences = allRefs.toArray(new DOSchemaReference[0]);
            }
        }
    }

    /**
     * Check if a type represents an entity class (not a primitive or built-in
     * type).
     */
    private static boolean isEntityType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }

        String lower = typeName.toLowerCase();

        // Filter out primitive types
        if (lower.equals("string") || lower.equals("int") || lower.equals("integer") ||
                lower.equals("long") || lower.equals("double") || lower.equals("float") ||
                lower.equals("boolean") || lower.equals("bool") || lower.equals("date") ||
                lower.equals("datetime") || lower.equals("byte") || lower.equals("short") ||
                lower.equals("char") || lower.equals("character")) {
            return false;
        }

        // Filter out common Java built-in types
        if (typeName.startsWith("java.lang.") && !typeName.equals("java.lang.Class")) {
            return false;
        }

        // Filter out collection types (these are containers, not entities)
        if (typeName.startsWith("java.util.") || typeName.startsWith("gen.util.")) {
            return false;
        }

        // Filter out java.awt types except Color which is referenced as entity
        if (typeName.startsWith("java.awt.")) {
            return typeName.equals("java.awt.Color");
        }

        // Anything else is considered an entity type
        return true;
    }

    /**
     * Build a map of class names (both source and destinationName) to classes.
     */
    private static Map<String, DOSchemaClass> buildClassMap(DOSchema schema) {
        Map<String, DOSchemaClass> map = new HashMap<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            map.put(schemaClass.source, schemaClass);
            if (schemaClass.destinationName != null) {
                map.put(schemaClass.destinationName, schemaClass);
            }
        }
        return map;
    }

    /**
     * Find a class by name (checks both full name and short name).
     */
    private static DOSchemaClass findClass(Map<String, DOSchemaClass> classMap, String name) {
        if (name == null) {
            return null;
        }

        // Try full name first
        DOSchemaClass found = classMap.get(name);
        if (found != null) {
            return found;
        }

        // Try short name
        String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        return classMap.get(shortName);
    }

    /**
     * Add a reference to the map.
     */
    private static void addReference(Map<String, List<DOSchemaReference>> referencesMap,
            String targetClassName, String sourceClassName, String fieldName) {
        List<DOSchemaReference> refs = referencesMap.computeIfAbsent(targetClassName, k -> new ArrayList<>());
        refs.add(new DOSchemaReference(sourceClassName, fieldName));
    }

    /**
     * Check if a reference already exists in the list.
     */
    private static boolean containsReference(List<DOSchemaReference> refs, DOSchemaReference newRef) {
        for (DOSchemaReference ref : refs) {
            if (ref.className.equals(newRef.className) && ref.fieldName.equals(newRef.fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Infer pointsTo for IDEntite subclasses based on naming convention.
     * For example: gest.cours.IDProgramme → gest.cours.Programme
     */
    private static void inferPointsTo(DOSchema schema, Map<String, DOSchemaClass> classMap) {
        for (DOSchemaClass cls : schema.getClasses()) {
            // Only process IDEntite subclasses that don't already have pointsTo set
            if (cls.isIDEntite(schema) && (cls.pointsTo == null || cls.pointsTo.isEmpty())) {
                // Try to infer from source name: gest.cours.IDProgramme → gest.cours.Programme
                String sourceName = cls.source;
                if (sourceName.contains(".ID")) {
                    // Replace .IDXxx with .Xxx
                    String inferredTarget = sourceName.replaceFirst("\\.ID([A-Z])", ".$1");

                    // Check if this target class exists
                    if (findClass(classMap, inferredTarget) != null) {
                        cls.pointsTo = inferredTarget;
                    }
                }
            }
        }
    }
}
