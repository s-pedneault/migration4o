package migration4o.schema.processors;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaSharedEmbeddedAnomaly;
import migration4o.models.schema.DOSchemaSharedNotExportedAnomaly;
import migration4o.models.schema.DOSchemaShouldBeEmbeddedAnomaly;
import migration4o.models.schema.DOSchemaShouldNotBeExportedAnomaly;
import migration4o.util.ModuleUtil;

/**
 * Schema processor that validates embedContents configuration based on
 * reference counts and module membership.
 * Implements validation rules from schema/guides/schema-validation-rules.md
 * 
 * This detector processes fields whose types are descendants of Entite or
 * IDEntite, applying specific validation rules based on whether the target
 * class has multiple references (shared) or single reference (composition).
 */
public class DOEmbeddingDetector {

    /**
     * Validates embedContents configuration for all fields in the schema.
     * Generates specific anomaly types for configuration issues.
     * 
     * @param schema The schema to validate
     */
    public static void detectEmbeddingAnomalies(DOSchema schema) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.fields == null) {
                continue;
            }

            for (DOSchemaField field : schemaClass.fields) {
                validateFieldEmbedding(schema, schemaClass, field);
            }
        }
    }

    /**
     * Validates a single field's embedContents configuration.
     */
    private static void validateFieldEmbedding(DOSchema schema, DOSchemaClass containingClass, DOSchemaField field) {
        // Check non-collection fields
        if (!field.isCollection && field.type != null) {
            DOSchemaClass typeClass = schema.findClassByName(field.type);
            if (typeClass != null) {
                processFieldType(schema, containingClass, field, typeClass);
            }
        }

        // Check collection fields
        if (field.isCollection && field.childrenType != null) {
            DOSchemaClass childrenClass = schema.findClassByName(field.childrenType);
            if (childrenClass != null) {
                processCollectionType(schema, containingClass, field, childrenClass);
            }
        }
    }

    /**
     * Process a non-collection field type.
     */
    private static void processFieldType(DOSchema schema, DOSchemaClass containingClass,
            DOSchemaField field, DOSchemaClass typeClass) {

        // If type is a descendant of IDEntite, use PROCEDURE 1
        if (typeClass.isIDEntite(schema)) {
            processIDEntiteType(schema, containingClass, field, typeClass);
        }
        // If type is a descendant of Entite (but not IDEntite), use PROCEDURE 2
        else if (typeClass.isEntite(schema)) {
            processEntiteType(schema, containingClass, field, typeClass);
        }
    }

    /**
     * Process a collection field's children type.
     */
    private static void processCollectionType(DOSchema schema, DOSchemaClass containingClass,
            DOSchemaField field, DOSchemaClass childrenClass) {

        // If childrenType is a descendant of IDEntite, use PROCEDURE 1
        if (childrenClass.isIDEntite(schema)) {
            processIDEntiteType(schema, containingClass, field, childrenClass);
        }
        // If childrenType is a descendant of Entite (but not IDEntite), use PROCEDURE 2
        else if (childrenClass.isEntite(schema)) {
            processEntiteType(schema, containingClass, field, childrenClass);
        }
    }

    /**
     * PROCEDURE 1: Process a type that is a descendant of IDEntite.
     * 1. Lookup the IDEntite-type definition in the schema
     * 2. Lookup the concrete class mentioned in its pointsTo attribute
     * 3. Process that concrete class with PROCEDURE 2
     */
    private static void processIDEntiteType(DOSchema schema, DOSchemaClass containingClass,
            DOSchemaField field, DOSchemaClass idEntiteClass) {

        // Lookup the concrete class mentioned in pointsTo attribute
        if (idEntiteClass.pointsTo == null || idEntiteClass.pointsTo.isEmpty()) {
            return;
        }

        DOSchemaClass concreteClass = schema.findClassByName(idEntiteClass.pointsTo);
        if (concreteClass == null) {
            return;
        }

        // Process the concrete class with PROCEDURE 2
        processEntiteType(schema, containingClass, field, concreteClass);
    }

    /**
     * PROCEDURE 2: Process a type that is a descendant of Entite.
     * Validates based on reference count and module membership.
     */
    private static void processEntiteType(DOSchema schema, DOSchemaClass containingClass,
            DOSchemaField field, DOSchemaClass concreteClass) {

        if (concreteClass.schemaReferences == null) {
            return;
        }

        int referenceCount = concreteClass.schemaReferences.length;
        boolean isListedInModule = ModuleUtil.isClassListedInAnyModule(schema, concreteClass);

        // If concrete class has MORE than one reference (shared object)
        if (referenceCount > 1) {
            // Generate DOSchemaSharedEmbeddedAnomaly if embedContents=true
            if (field.embedContents) {
                String explanation = String.format(
                        "Field '%s.%s' has embedContents=true but points to class '%s' " +
                                "which has %d references (shared object). Should be embedContents=false to avoid duplication.",
                        containingClass.source, field.source, concreteClass.source, referenceCount);
                schema.anomalies.add(new DOSchemaSharedEmbeddedAnomaly(containingClass, field, explanation));
            }

            // Generate DOSchemaSharedNotExportedAnomaly if NOT listed in any module
            if (!isListedInModule) {
                String explanation = String.format(
                        "Class '%s' is referenced by %d fields but is NOT listed in any module. " +
                                "Shared objects with multiple references should be listed in a module for proper export.",
                        concreteClass.source, referenceCount);
                schema.anomalies.add(new DOSchemaSharedNotExportedAnomaly(containingClass, field, explanation));
            }
        }

        // If concrete class has exactly one reference (single-use object)
        if (referenceCount == 1) {
            // Generate DOSchemaShouldBeEmbeddedAnomaly if embedContents=false
            if (!field.embedContents) {
                String explanation = String.format(
                        "Field '%s.%s' has embedContents=false but points to class '%s' " +
                                "which has only 1 reference (single-use object). Should be embedContents=true for efficiency.",
                        containingClass.source, field.source, concreteClass.source);
                schema.anomalies.add(new DOSchemaShouldBeEmbeddedAnomaly(containingClass, field, explanation));
            }

            // Generate DOSchemaShouldNotBeExportedAnomaly if IS listed in any module
            if (isListedInModule) {
                String explanation = String.format(
                        "Class '%s' has only 1 reference but IS listed in a module. " +
                                "Single-use objects should be embedded rather than exported separately.",
                        concreteClass.source);
                schema.anomalies.add(new DOSchemaShouldNotBeExportedAnomaly(containingClass, field, explanation));
            }
        }
    }
}