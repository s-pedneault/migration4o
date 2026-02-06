package migration4o.migration.recipes;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Recipe for detecting when fields should export as ID references instead of
 * embedded entities.
 * Determines if a collection or field should use ID reference pattern based on
 * schema configuration.
 */
public class IDReferenceDetector {

    /**
     * Result of ID reference detection.
     */
    public static class DetectionResult {
        public final boolean shouldExportAsIDReferences;
        public final DOSchemaClass idClass;

        public DetectionResult(boolean shouldExportAsIDReferences, DOSchemaClass idClass) {
            this.shouldExportAsIDReferences = shouldExportAsIDReferences;
            this.idClass = idClass;
        }
    }

    /**
     * Checks if a field should export its contents as ID references.
     * 
     * @param schemaField     The field being exported
     * @param referenceSchema The reference schema containing ID class definitions
     * @return DetectionResult with shouldExportAsIDReferences flag and idClass if
     *         found
     */
    public static DetectionResult detectIDReference(DOSchemaField schemaField, DOSchema referenceSchema) {
        if (schemaField == null) {
            return new DetectionResult(false, null);
        }

        // Check if this field should NOT embed contents and has a childrenType
        if (!schemaField.embedContents && schemaField.childrenType != null) {
            // Find the corresponding ID class for this entity type
            DOSchemaClass idClass = IDClassResolver.findIDClass(schemaField.childrenType, referenceSchema);
            if (idClass != null) {
                return new DetectionResult(true, idClass);
            }
        }

        return new DetectionResult(false, null);
    }
}
