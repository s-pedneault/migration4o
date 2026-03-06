package migration4o.schema.processors;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.schema.modules.DOModuleService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Post-processing step that determines optimal embedding strategy for entity
 * fields. Makes a GLOBAL decision per entity based on reference pattern.
 * 
 * Must be run after DOReferenceDetector has populated all field references.
 * 
 * Embedding Strategy: - If entity is referenced ONLY by fields in a SINGLE
 * PACKAGE OR MODULE: Set embedContents=true on ALL those fields Rationale: Safe
 * to embed - all classes in same package/module are typically exported together
 * - If entity is referenced by fields in MULTIPLE PACKAGES AND MODULES: Set
 * embedContents=false on ALL fields Rationale: Cannot embed - other
 * packages/modules exporting separately will need access to this entity
 * 
 * Example: BornePer referenced by gest.borne.Borne, gest.borne.ModeleBorne, and
 * gest.borne.ParamMaintBorne → All in package gest.borne → embedContents=true
 * for ALL 5 fields → When exporting the gest.borne package, all BornePer
 * entities are contained within
 */
public class DOEmbeddingCoordinator {

    private final DOSchema referenceSchema;
    private final Map<String, List<FieldReference>> entityReferences;

    /**
     * Represents a field that references an entity class
     */
    private static class FieldReference {
        final DOSchemaClass containingClass;
        final DOSchemaField field;

        FieldReference(DOSchemaClass containingClass, DOSchemaField field) {
            this.containingClass = containingClass;
            this.field = field;
        }

        String getPackageName() {
            String className = containingClass.source;
            int lastDot = className.lastIndexOf('.');
            return lastDot > 0 ? className.substring(0, lastDot) : "";
        }

        String getClassName() {
            return containingClass.source;
        }
    }

    public DOEmbeddingCoordinator(DOSchema referenceSchema) {
        this.referenceSchema = referenceSchema;
        this.entityReferences = new HashMap<>();
    }

    /**
     * Analyzes the reference schema and sets embedding strategy for all entity
     * classes. Global decision per entity: embedContents=true ONLY if ALL
     * references are in ONE package.
     */
    public void coordinateEmbedding() {
        System.out.println("\n=== DOEmbeddingCoordinator: Analyzing embedding strategy ===");

        // Step 1: Build map of entity classes to all fields that reference them
        buildReferenceMap();

        // Step 2: For each entity, make global decision based on how many
        // packages
        // reference it
        int embeddedEntities = 0;
        int notEmbeddedEntities = 0;
        int totalFieldsEmbedded = 0;
        int totalFieldsNotEmbedded = 0;

        for (Map.Entry<String, List<FieldReference>> entry : entityReferences.entrySet()) {
            String entityClass = entry.getKey();
            List<FieldReference> allReferences = entry.getValue();

            if (allReferences.isEmpty()) {
                continue;
            }

            // Count how many DIFFERENT packages reference this entity
            Set<String> referencingPackages = allReferences.stream().map(FieldReference::getPackageName).collect(Collectors.toSet());

            boolean allowEmbed = false;
            String groupingReason = "";

            if (referencingPackages.size() == 1) {
                // All in same package
                allowEmbed = true;
                groupingReason = "same package (" + referencingPackages.iterator().next() + ")";
            } else if (DOModuleService.getInstance().hasModules()) {
                // Check if all references are in the same module
                Set<String> referencingModules = new HashSet<>();
                for (FieldReference ref : allReferences) {
                    String moduleName = findModuleForClass(ref.getClassName());
                    if (moduleName != null) {
                        referencingModules.add(moduleName);
                    }
                }

                if (referencingModules.size() == 1) {
                    allowEmbed = true;
                    groupingReason = "same module (" + referencingModules.iterator().next() + ")";
                }
            }

            if (allowEmbed) {
                // ALL references are within ONE package - safe to embed
                // Set embedContents=true on ALL fields
                for (FieldReference ref : allReferences) {
                    ref.field.embedContents = true;
                }
                embeddedEntities++;
                totalFieldsEmbedded += allReferences.size();

                Set<String> classNames = allReferences.stream().map(FieldReference::getClassName).collect(Collectors.toSet());
                System.out.println("✓ EMBED: " + entityClass + " → " + allReferences.size() + " field(s) in " + groupingReason + " (" + classNames.size() + " class(es))");
                if (allReferences.size() > 1) {
                    for (FieldReference ref : allReferences) {
                        System.out.println("    - " + ref.getClassName() + "." + ref.field.source);
                    }
                }
            } else {
                // Referenced by MULTIPLE packages/modules - must NOT embed
                // Set embedContents=false on ALL fields
                for (FieldReference ref : allReferences) {
                    ref.field.embedContents = false;
                }
                notEmbeddedEntities++;
                totalFieldsNotEmbedded += allReferences.size();

                System.out.println("⊗ NO EMBED: " + entityClass + " → " + allReferences.size() + " field(s) across " + referencingPackages.size() + " packages");
                if (referencingPackages.size() <= 5) {
                    System.out.println("    Packages: " + referencingPackages);
                }
            }
        }

        System.out.println("\n=== Embedding Strategy Summary ===");
        System.out.println("Entities embedded (all refs in 1 package/module): " + embeddedEntities + " (" + totalFieldsEmbedded + " fields)");
        System.out.println("Entities NOT embedded (multi-package/module):     " + notEmbeddedEntities + " (" + totalFieldsNotEmbedded + " fields)");
        System.out.println("Total entities analyzed:                          " + (embeddedEntities + notEmbeddedEntities));
    }

    /**
     * Build a map of entity classes to all fields that reference them
     */
    private void buildReferenceMap() {
        for (DOSchemaClass schemaClass : referenceSchema.getClasses()) {
            for (DOSchemaField field : schemaClass.fields) {
                String fieldType = field.type;
                String childrenType = field.childrenType;

                // Check if field references an entity (non-primitive type in
                // schema)
                if (fieldType != null && !fieldType.isEmpty()) {
                    if (isEntityType(fieldType)) {
                        addReference(fieldType, schemaClass, field);
                    }
                }

                // Check children type for collections
                if (childrenType != null && !childrenType.isEmpty()) {
                    if (isEntityType(childrenType)) {
                        addReference(childrenType, schemaClass, field);
                    }
                }
            }
        }
    }

    /**
     * Check if a type represents an entity class (exists in schema and not
     * primitive)
     */
    private boolean isEntityType(String typeName) {
        // Skip primitive types
        if (isPrimitive(typeName)) {
            return false;
        }

        // Check if it's a class in the reference schema
        return referenceSchema.findClassByName(typeName) != null;
    }

    /**
     * Check if a type is a primitive type
     */
    private boolean isPrimitive(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }

        String lower = typeName.toLowerCase();
        return lower.equals("string") || lower.equals("int") || lower.equals("integer") || lower.equals("long") || lower.equals("double") || lower.equals("float") || lower.equals("boolean") || lower.equals("bool") || lower.equals("date") || lower.equals("datetime") || lower.equals("byte") || lower.equals("short") || lower.equals("char") || lower.equals("character");
    }

    /**
     * Add a reference from a field to an entity class
     */
    private void addReference(String entityClass, DOSchemaClass containingClass, DOSchemaField field) {
        entityReferences.computeIfAbsent(entityClass, k -> new ArrayList<>()).add(new FieldReference(containingClass, field));
    }

    /**
     * Find which module contains the given class by recursively searching
     * through modules and their children
     */
    private String findModuleForClass(String className) {
        List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();
        if (modules == null || modules.isEmpty()) {
            return null;
        }

        for (DOSchemaModule module : modules) {
            String result = findModuleForClassRecursive(module, className);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Recursively search a module and its children for a class
     */
    private String findModuleForClassRecursive(DOSchemaModule module, String className) {
        // Check if this module contains the class
        boolean hasClass = module.classConfigs.stream().anyMatch(c -> c.getClassName().equals(className));
        if (hasClass) {
            return module.name;
        }

        // Check child modules recursively
        for (DOSchemaModule child : module.children) {
            String result = findModuleForClassRecursive(child, className);
            if (result != null) {
                return result;
            }
        }

        return null;
    }
}
