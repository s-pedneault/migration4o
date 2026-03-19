package migration4o.schema.processors;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.analysis.DOSchemaDataLossAnomaly;
import migration4o.models.schema.analysis.DOSchemaSharedEmbeddedAnomaly;
import migration4o.models.schema.analysis.DOSchemaSharedNotExportedAnomaly;
import migration4o.models.schema.analysis.DOSchemaShouldBeEmbeddedAnomaly;
import migration4o.models.schema.analysis.DOSchemaShouldNotBeExportedAnomaly;
import migration4o.util.ModuleUtil;
import migration4o.util.SchemaUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Schema processor that validates embedContents configuration based on reference counts and module membership. Implements validation rules from schema/guides/schema-validation-rules.md
 * 
 * This detector processes fields whose types are descendants of Entite or IDEntite, applying specific validation rules based on whether the target class has multiple references (shared) or single reference (composition).
 */
public class DOEmbeddingDetector {

    // Track which shared classes we've already reported to avoid duplicate
    // anomalies
    private static Set<String> reportedSharedNotExported = new HashSet<>();

    // Track which common field definitions we've already warned about for data loss
    private static Set<String> reportedCommonFieldDataLoss = new HashSet<>();

    /**
     * Validates embedContents configuration for all fields in the schema. Generates specific anomaly types for configuration issues.
     * 
     * @param schema The schema to validate
     */
    public static void detectEmbeddingAnomalies(DOSchema schema) {
        if (schema == null || schema.getClasses() == null) {
            return;
        }

        // Clear tracking sets for new detection run
        reportedSharedNotExported.clear();
        reportedCommonFieldDataLoss.clear();

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
        if (!field.attributes.isCollection && field.attributes.type != null) {
            DOSchemaClass typeClass = schema.findClassByName(field.attributes.type);
            if (typeClass != null) {
                processFieldType(schema, containingClass, field, typeClass);
            }
        }

        // Check collection fields
        if (field.attributes.isCollection && field.attributes.childrenType != null) {
            DOSchemaClass childrenClass = schema.findClassByName(field.attributes.childrenType);
            if (childrenClass != null) {
                processCollectionType(schema, containingClass, field, childrenClass);
            }
        }
    }

    /**
     * Process a non-collection field type.
     */
    private static void processFieldType(DOSchema schema, DOSchemaClass containingClass, DOSchemaField field, DOSchemaClass typeClass) {

        // CRITICAL DATA LOSS CHECK: If embedContents=false but type is not an IDEntite,
        // the field will not be exported at all (data loss!)
        if (!field.attributes.embedContents && !typeClass.isIDEntite()) {
            // Check if this is a common field reference
            if (field.isSharedField()) {
                // Only report once per common field definition
                if (reportedCommonFieldDataLoss.contains(field.attributes.definitionId)) {
                    return; // Already reported for this common field
                }
                reportedCommonFieldDataLoss.add(field.attributes.definitionId);

                // Generate warning for the common field definition
                String explanation = String.format("CRITICAL DATA LOSS: Common field '%s' (definitionId='%s') has embedContents=false " + "but type '%s' is NOT an IDEntite. All uses of this common field will NOT be exported " + "(neither as ID nor as embedded object), resulting in complete data loss! " + "Fix: Set embedContents=true on the common field definition.", field.attributes.source, field.attributes.definitionId, field.attributes.type);
                schema.anomalies.add(new DOSchemaDataLossAnomaly(containingClass, field, field.attributes.type, explanation));
            } else {
                // Regular field (not a common field reference)
                String containingModule = ModuleUtil.findModuleForClass(containingClass);
                String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                String explanation = String.format("CRITICAL DATA LOSS: Field '%s.%s'%s has embedContents=false " + "but type '%s' is NOT an IDEntite. This field will NOT be exported " + "(neither as ID nor as embedded object), resulting in complete data loss for this field! " + "Fix: Set embedContents=true to export the field as an embedded object.", containingClass.attributes.source, field.attributes.source, moduleInfo, field.attributes.type);
                schema.anomalies.add(new DOSchemaDataLossAnomaly(containingClass, field, field.attributes.type, explanation));
            }
            return; // Don't process further since this is a critical issue
        }

        // If type is a descendant of IDEntite, use PROCEDURE 1
        if (typeClass.isIDEntite()) {
            processIDEntiteType(schema, containingClass, field, typeClass);
        }
        // If type is a descendant of Entite (but not IDEntite), use PROCEDURE 2
        else if (typeClass.isEntite()) {
            processEntiteType(schema, containingClass, field, typeClass);
        }
    }

    /**
     * Process a collection field's children type.
     */
    private static void processCollectionType(DOSchema schema, DOSchemaClass containingClass, DOSchemaField field, DOSchemaClass childrenClass) {

        // CRITICAL DATA LOSS CHECK: If embedContents=false but childrenType is not an
        // IDEntite,
        // the collection will not be exported at all (data loss!)
        if (!field.attributes.embedContents && !childrenClass.isIDEntite()) {
            // Check if this is a common field reference
            if (field.isSharedField()) {
                // Only report once per common field definition
                if (reportedCommonFieldDataLoss.contains(field.attributes.definitionId)) {
                    return; // Already reported for this common field
                }
                reportedCommonFieldDataLoss.add(field.attributes.definitionId);

                // Generate warning for the common field definition
                String explanation = String.format("CRITICAL DATA LOSS: Common field '%s' (definitionId='%s') is a collection with embedContents=false " + "but childrenType '%s' is NOT an IDEntite. All uses of this common field will NOT be exported " + "(neither as IDs nor as embedded objects), resulting in complete data loss! " + "Fix: Set embedContents=true on the common field definition.", field.attributes.source, field.attributes.definitionId, field.attributes.childrenType);
                schema.anomalies.add(new DOSchemaDataLossAnomaly(containingClass, field, field.attributes.childrenType, explanation));
            } else {
                // Regular field (not a common field reference)
                String containingModule = ModuleUtil.findModuleForClass(containingClass);
                String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                String explanation = String.format("CRITICAL DATA LOSS: Field '%s.%s'%s is a collection with embedContents=false " + "but childrenType '%s' is NOT an IDEntite. This collection will NOT be exported " + "(neither as IDs nor as embedded objects), resulting in complete data loss for this field! " + "Fix: Set embedContents=true to export the collection items as embedded objects.", containingClass.attributes.source, field.attributes.source, moduleInfo, field.attributes.childrenType);
                schema.anomalies.add(new DOSchemaDataLossAnomaly(containingClass, field, field.attributes.childrenType, explanation));
            }
            return; // Don't process further since this is a critical issue
        }

        // If childrenType is a descendant of IDEntite, use PROCEDURE 1
        if (childrenClass.isIDEntite()) {
            processIDEntiteType(schema, containingClass, field, childrenClass);
        }
        // If childrenType is a descendant of Entite (but not IDEntite), use PROCEDURE 2
        else if (childrenClass.isEntite()) {
            processEntiteType(schema, containingClass, field, childrenClass);
        }
    }

    /**
     * PROCEDURE 1: Process a type that is a descendant of IDEntite. 1. Lookup the IDEntite-type definition in the schema 2. Lookup the concrete class mentioned in its pointsTo attribute 3. Process that concrete class with PROCEDURE 2
     */
    private static void processIDEntiteType(DOSchema schema, DOSchemaClass containingClass, DOSchemaField field, DOSchemaClass idEntiteClass) {

        // Lookup the concrete class mentioned in pointsTo attribute
        if (idEntiteClass.attributes.pointsTo == null || idEntiteClass.attributes.pointsTo.isEmpty()) {
            return;
        }

        DOSchemaClass concreteClass = schema.findClassByName(idEntiteClass.attributes.pointsTo);
        if (concreteClass == null) {
            return;
        }

        // Process the concrete class with PROCEDURE 2
        processEntiteType(schema, containingClass, field, concreteClass);
    }

    /**
     * PROCEDURE 2: Process a type that is a descendant of Entite. Validates based on whether it's a superclass, reference count, and module membership.
     */
    private static void processEntiteType(DOSchema schema, DOSchemaClass containingClass, DOSchemaField field, DOSchemaClass concreteClass) {

        if (concreteClass.schemaReferences == null) {
            return;
        }

        // Check if this class is actually a superclass of other Entite-type classes
        boolean isSuperclass = concreteClass.hasSubclasses();

        if (isSuperclass) {
            // If concrete class is a superclass, only warn about embedding
            if (field.attributes.embedContents) {
                String containingModule = ModuleUtil.findModuleForClass(containingClass);
                String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                String explanation = String.format("Field '%s.%s'%s has embedContents=true but points to class '%s' " + "which is a superclass of other Entite-type classes. Should be embedContents=false to avoid issues.", containingClass.attributes.source, field.attributes.source, moduleInfo, concreteClass.attributes.source);
                schema.anomalies.add(new DOSchemaSharedEmbeddedAnomaly(containingClass, field, explanation));
            }
        } else {
            // Not a superclass - proceed with reference count validation
            int referenceCount = concreteClass.schemaReferences.length;
            boolean isListedInModule = ModuleUtil.isClassListedInAnyModule(concreteClass);

            if (referenceCount > 1) {
                // If concrete class has MORE than one reference (shared object)
                if (field.attributes.embedContents) {
                    String containingModule = ModuleUtil.findModuleForClass(containingClass);
                    String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                    String explanation = String.format("Field '%s.%s'%s has embedContents=true but points to class '%s' " + "which has %d references (shared object). Should be embedContents=false to avoid duplication.", containingClass.attributes.source, field.attributes.source, moduleInfo, concreteClass.attributes.source, referenceCount);
                    schema.anomalies.add(new DOSchemaSharedEmbeddedAnomaly(containingClass, field, explanation));
                }

                // Generate DOSchemaSharedNotExportedAnomaly if NOT listed in any module
                // Only report once per class to avoid duplicate warnings
                if (!isListedInModule) {// } && !reportedSharedNotExported.contains(concreteClass.attributes.source)) {
                    reportedSharedNotExported.add(concreteClass.attributes.source);

                    String containingModule = ModuleUtil.findModuleForClass(containingClass);
                    String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                    String explanation = String.format("Class '%s' is referenced by %d fields but is NOT listed in any module. " + "Shared objects with multiple references should be listed in a module for proper export. " + "First detected on field '%s.%s'%s.", concreteClass.attributes.source, referenceCount, containingClass.attributes.source, field.attributes.source, moduleInfo);
                    schema.anomalies.add(new DOSchemaSharedNotExportedAnomaly(containingClass, field, explanation));
                }
            } else if (referenceCount == 1) {
                // If concrete class has exactly one reference (single-use object)
                // BUT: If the class is listed in a module, it's intended to be exported
                // as a top-level entity, so don't warn about embedding
                if (!isListedInModule && !field.attributes.embedContents) {
                    String containingModule = ModuleUtil.findModuleForClass(containingClass);
                    String moduleInfo = containingModule != null ? " (in module " + containingModule + ")" : "";

                    String explanation = String.format("Field '%s.%s'%s has embedContents=false but points to class '%s' " + "which has only 1 reference (single-use object). Should be embedContents=true for efficiency.", containingClass.attributes.source, field.attributes.source, moduleInfo, concreteClass.attributes.source);
                    schema.anomalies.add(new DOSchemaShouldBeEmbeddedAnomaly(containingClass, field, explanation));
                }
            }
        }
    }
}