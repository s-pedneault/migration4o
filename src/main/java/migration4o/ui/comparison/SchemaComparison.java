package migration4o.ui.comparison;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

import java.util.*;

/**
 * Represents the result of comparing two schemas.
 * Identifies classes and fields that exist in one schema but not the other,
 * and fields with different properties.
 */
public class SchemaComparison {

    private final DOSchema referenceSchema;
    private final DOSchema comparedSchema;
    private final String referenceLabel;
    private final String comparedLabel;

    private final List<ClassDifference> differences;
    private boolean showAllClasses = false;

    public SchemaComparison(DOSchema referenceSchema, String referenceLabel,
            DOSchema comparedSchema, String comparedLabel) {
        this.referenceSchema = referenceSchema;
        this.comparedSchema = comparedSchema;
        this.referenceLabel = referenceLabel;
        this.comparedLabel = comparedLabel;
        this.differences = new ArrayList<>();

        performComparison();
    }

    private void performComparison() {
        Map<String, DOSchemaClass> referenceClasses = buildClassMap(referenceSchema);
        Map<String, DOSchemaClass> comparedClasses = buildClassMap(comparedSchema);

        // Debug: check ParamConfig
        System.out.println("DEBUG Comparison: Reference has ParamConfig? "
                + referenceClasses.containsKey("gest.config.ParamConfig"));
        System.out.println("DEBUG Comparison: Compared has ParamConfig? "
                + comparedClasses.containsKey("gest.config.ParamConfig"));
        System.out.println("DEBUG Comparison: Reference class count: " + referenceClasses.size());
        System.out.println("DEBUG Comparison: Compared class count: " + comparedClasses.size());

        Set<String> allClassNames = new HashSet<>();
        allClassNames.addAll(referenceClasses.keySet());
        allClassNames.addAll(comparedClasses.keySet());

        for (String className : allClassNames) {
            DOSchemaClass refClass = referenceClasses.get(className);
            DOSchemaClass cmpClass = comparedClasses.get(className);

            ClassDifference diff = new ClassDifference(className, refClass, cmpClass);

            if (refClass != null && cmpClass != null) {
                // Both exist - compare fields
                compareFields(diff, refClass, cmpClass);
            }

            // Add if there are any differences, or if showing all classes
            if (diff.hasDifferences() || showAllClasses) {
                differences.add(diff);
            }
        }

        // Sort by class name
        differences.sort(Comparator.comparing(ClassDifference::getClassName));
    }

    private void compareFields(ClassDifference diff, DOSchemaClass refClass, DOSchemaClass cmpClass) {
        Map<String, DOSchemaField> refFields = buildFieldMap(refClass);
        Map<String, DOSchemaField> cmpFields = buildFieldMap(cmpClass);

        Set<String> allFieldNames = new HashSet<>();
        allFieldNames.addAll(refFields.keySet());
        allFieldNames.addAll(cmpFields.keySet());

        for (String fieldName : allFieldNames) {
            DOSchemaField refField = refFields.get(fieldName);
            DOSchemaField cmpField = cmpFields.get(fieldName);

            if (refField == null) {
                diff.addFieldOnlyInCompared(cmpField);
            } else if (cmpField == null) {
                diff.addFieldOnlyInReference(refField);
            } else {
                // Both exist - check for property differences
                FieldPropertyDifference propDiff = compareFieldProperties(refField, cmpField);
                if (propDiff.hasDifferences()) {
                    diff.addFieldWithDifferences(fieldName, propDiff);
                }
            }
        }
    }

    private FieldPropertyDifference compareFieldProperties(DOSchemaField refField, DOSchemaField cmpField) {
        FieldPropertyDifference diff = new FieldPropertyDifference();

        // Compare types with normalization
        String refType = normalizeType(refField.getType());
        String cmpType = normalizeType(cmpField.getType());
        if (!Objects.equals(refType, cmpType)) {
            diff.addDifference("type", refField.getType(), cmpField.getType());
        }

        if (refField.isCollection() != cmpField.isCollection()) {
            diff.addDifference("collection", refField.isCollection(), cmpField.isCollection());
        }

        // Compare childrenType with null/empty tolerance
        String refChildren = normalizeEmptyString(refField.getChildrenType());
        String cmpChildren = normalizeEmptyString(cmpField.getChildrenType());

        // Special case: if reference schema defines a proper type and database has
        // java.lang.Object,
        // don't consider it a difference (schema takes precedence)
        boolean isObjectPlaceholder = (refChildren != null && !refChildren.isEmpty() &&
                !refChildren.equals("java.lang.Object") &&
                "java.lang.Object".equals(cmpChildren));

        if (!Objects.equals(refChildren, cmpChildren) && !isObjectPlaceholder) {
            diff.addDifference("childrenType", refField.getChildrenType(), cmpField.getChildrenType());
        }

        return diff;
    }

    /**
     * Normalizes type names to handle common equivalences:
     * - "String" and "java.lang.String" are the same
     * - "Integer" and "java.lang.Integer" are the same
     * - etc.
     */
    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }

        // Map of short names to fully qualified names
        switch (type) {
            case "String":
                return "java.lang.String";
            case "Integer":
                return "java.lang.Integer";
            case "Long":
                return "java.lang.Long";
            case "Double":
                return "java.lang.Double";
            case "Float":
                return "java.lang.Float";
            case "Boolean":
                return "java.lang.Boolean";
            case "Byte":
                return "java.lang.Byte";
            case "Short":
                return "java.lang.Short";
            case "Character":
                return "java.lang.Character";
            case "Date":
                return "java.util.Date";
            default:
                return type;
        }
    }

    /**
     * Normalizes empty strings and null to be equivalent.
     */
    private String normalizeEmptyString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private Map<String, DOSchemaClass> buildClassMap(DOSchema schema) {
        Map<String, DOSchemaClass> map = new HashMap<>();
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                String key = schemaClass.getSourceName();

                // Debug ParamConfig specifically
                if ("ParamConfig".equals(schemaClass.getShortName())) {
                    System.out.println("DEBUG buildClassMap: Found ParamConfig - source='" + key + "', dest='" +
                            schemaClass.getShortName() + "'");
                }

                // Skip classes with null or empty source name
                if (key == null || key.trim().isEmpty()) {
                    System.out.println(
                            "WARNING: Skipping class with null/empty source name: " + schemaClass.getShortName());
                    continue;
                }

                if (map.containsKey(key)) {
                    System.out.println("WARNING: Duplicate class key '" + key + "' - overwriting " +
                            map.get(key).getShortName() + " with " + schemaClass.getShortName());
                }
                map.put(key, schemaClass);
            }
        }
        return map;
    }

    private Map<String, DOSchemaField> buildFieldMap(DOSchemaClass schemaClass) {
        Map<String, DOSchemaField> map = new HashMap<>();
        if (schemaClass.getFields() != null) {
            for (DOSchemaField field : schemaClass.getFields()) {
                map.put(field.getSource(), field);
            }
        }
        return map;
    }

    public List<ClassDifference> getDifferences() {
        return differences;
    }

    public DOSchema getReferenceSchema() {
        return referenceSchema;
    }

    public DOSchema getComparedSchema() {
        return comparedSchema;
    }

    public String getReferenceLabel() {
        return referenceLabel;
    }

    public String getComparedLabel() {
        return comparedLabel;
    }

    public void setShowAllClasses(boolean showAllClasses) {
        this.showAllClasses = showAllClasses;
        differences.clear();
        performComparison();
    }

    /**
     * Represents differences for a single class.
     */
    public static class ClassDifference {
        private final String className;
        private final DOSchemaClass referenceClass;
        private final DOSchemaClass comparedClass;
        private final List<DOSchemaField> fieldsOnlyInReference = new ArrayList<>();
        private final List<DOSchemaField> fieldsOnlyInCompared = new ArrayList<>();
        private final Map<String, FieldPropertyDifference> fieldsWithDifferences = new HashMap<>();

        public ClassDifference(String className, DOSchemaClass referenceClass, DOSchemaClass comparedClass) {
            this.className = className;
            this.referenceClass = referenceClass;
            this.comparedClass = comparedClass;
        }

        public void addFieldOnlyInReference(DOSchemaField field) {
            fieldsOnlyInReference.add(field);
        }

        public void addFieldOnlyInCompared(DOSchemaField field) {
            fieldsOnlyInCompared.add(field);
        }

        public void addFieldWithDifferences(String fieldName, FieldPropertyDifference diff) {
            fieldsWithDifferences.put(fieldName, diff);
        }

        public boolean hasDifferences() {
            return referenceClass == null || comparedClass == null ||
                    !fieldsOnlyInReference.isEmpty() ||
                    !fieldsOnlyInCompared.isEmpty() ||
                    !fieldsWithDifferences.isEmpty();
        }

        public boolean isOnlyInReference() {
            return referenceClass != null && comparedClass == null;
        }

        public boolean isOnlyInCompared() {
            return referenceClass == null && comparedClass != null;
        }

        public String getClassName() {
            return className;
        }

        public DOSchemaClass getReferenceClass() {
            return referenceClass;
        }

        public DOSchemaClass getComparedClass() {
            return comparedClass;
        }

        public List<DOSchemaField> getFieldsOnlyInReference() {
            return fieldsOnlyInReference;
        }

        public List<DOSchemaField> getFieldsOnlyInCompared() {
            return fieldsOnlyInCompared;
        }

        public Map<String, FieldPropertyDifference> getFieldsWithDifferences() {
            return fieldsWithDifferences;
        }
    }

    /**
     * Represents property differences for a single field.
     */
    public static class FieldPropertyDifference {
        private final Map<String, PropertyDiff> differences = new HashMap<>();

        public void addDifference(String property, Object referenceValue, Object comparedValue) {
            differences.put(property, new PropertyDiff(referenceValue, comparedValue));
        }

        public boolean hasDifferences() {
            return !differences.isEmpty();
        }

        public Map<String, PropertyDiff> getDifferences() {
            return differences;
        }

        public static class PropertyDiff {
            private final Object referenceValue;
            private final Object comparedValue;

            public PropertyDiff(Object referenceValue, Object comparedValue) {
                this.referenceValue = referenceValue;
                this.comparedValue = comparedValue;
            }

            public Object getReferenceValue() {
                return referenceValue;
            }

            public Object getComparedValue() {
                return comparedValue;
            }
        }
    }
}
