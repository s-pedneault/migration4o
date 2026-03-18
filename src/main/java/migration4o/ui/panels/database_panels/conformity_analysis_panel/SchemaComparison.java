package migration4o.ui.panels.database_panels.conformity_analysis_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.comparison.ClassDifference;
import migration4o.models.schema.comparison.FieldPropertyDifference;

import java.util.*;

/**
 * Represents the result of comparing two schemas. Identifies classes and fields that exist in one schema but not the other, and fields with different properties.
 */
public class SchemaComparison {

    private final DOSchema referenceSchema;
    private final DOSchema comparedSchema;
    private final String referenceLabel;
    private final String comparedLabel;

    private final List<ClassDifference> differences;
    private boolean showAllClasses = false;

    public SchemaComparison(DOSchema referenceSchema, String referenceLabel, DOSchema comparedSchema, String comparedLabel) {
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
                // Virtual query fields (source starts with '@') are schema-only by
                // design and should not be treated as missing anomalies.
                if (!isVirtualQueryField(refField)) {
                    diff.addFieldOnlyInReference(refField);
                }
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
        String refType = normalizeType(refField.attributes.type);
        String cmpType = normalizeType(cmpField.attributes.type);
        if (!Objects.equals(refType, cmpType)) {
            diff.addDifference("type", refField.attributes.type, cmpField.attributes.type);
        }

        // If reference marks this as collection but compared schema does not,
        // treat reference as authoritative (more precise metadata) and do not flag.
        boolean referenceMorePreciseCollection = refField.attributes.isCollection && !cmpField.attributes.isCollection;
        if (refField.attributes.isCollection != cmpField.attributes.isCollection && !referenceMorePreciseCollection) {
            diff.addDifference("collection", refField.attributes.isCollection, cmpField.attributes.isCollection);
        }

        // Compare childrenType with null/empty tolerance
        String refChildren = normalizeEmptyString(refField.attributes.childrenType);
        String cmpChildren = normalizeEmptyString(cmpField.attributes.childrenType);

        // If reference has more precise children type metadata, prefer reference and
        // do not flag:
        // - compared is missing/empty
        // - compared is java.lang.Object placeholder
        boolean referenceHasSpecificChildrenType = refChildren != null && !"java.lang.Object".equals(normalizeType(refChildren));
        boolean comparedMissingChildrenType = cmpChildren == null;
        boolean comparedIsObjectPlaceholder = "java.lang.Object".equals(normalizeType(cmpChildren));
        boolean referenceMorePreciseChildrenType = referenceHasSpecificChildrenType && (comparedMissingChildrenType || comparedIsObjectPlaceholder);

        if (!Objects.equals(normalizeType(refChildren), normalizeType(cmpChildren)) && !referenceMorePreciseChildrenType) {
            diff.addDifference("childrenType", refField.attributes.childrenType, cmpField.attributes.childrenType);
        }

        return diff;
    }

    /**
     * Normalizes type names to handle common equivalences: - "String" and "java.lang.String" are the same - "Integer" and "java.lang.Integer" are the same - etc.
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
                String key = schemaClass.attributes.source;

                // Debug ParamConfig specifically
                if ("ParamConfig".equals(schemaClass.attributes.destinationName)) {
                    String shortName1 = schemaClass.attributes.source != null && schemaClass.attributes.source.contains(".") ? schemaClass.attributes.source.substring(schemaClass.attributes.source.lastIndexOf('.') + 1) : schemaClass.attributes.source;
                    System.out.println("DEBUG buildClassMap: Found ParamConfig - source='" + key + "', dest='" + shortName1 + "'");
                }

                // Skip classes with null or empty source name
                if (key == null || key.trim().isEmpty()) {
                    String shortName2 = schemaClass.attributes.source != null && schemaClass.attributes.source.contains(".") ? schemaClass.attributes.source.substring(schemaClass.attributes.source.lastIndexOf('.') + 1) : schemaClass.attributes.source;
                    System.out.println("WARNING: Skipping class with null/empty source name: " + shortName2);
                    continue;
                }

                if (map.containsKey(key)) {
                    String shortName3 = map.get(key).attributes.source != null && map.get(key).attributes.source.contains(".") ? map.get(key).attributes.source.substring(map.get(key).attributes.source.lastIndexOf('.') + 1) : map.get(key).attributes.source;
                    String shortName4 = schemaClass.attributes.source != null && schemaClass.attributes.source.contains(".") ? schemaClass.attributes.source.substring(schemaClass.attributes.source.lastIndexOf('.') + 1) : schemaClass.attributes.source;
                    System.out.println("WARNING: Duplicate class key '" + key + "' - overwriting " + shortName3 + " with " + shortName4);
                }
                map.put(key, schemaClass);
            }
        }
        return map;
    }

    private Map<String, DOSchemaField> buildFieldMap(DOSchemaClass schemaClass) {
        Map<String, DOSchemaField> map = new HashMap<>();
        if (schemaClass.fields != null) {
            for (DOSchemaField field : schemaClass.fields) {
                map.put(field.attributes.source, field);
            }
        }
        return map;
    }

    private boolean isVirtualQueryField(DOSchemaField field) {
        return field != null && field.attributes.source != null && field.attributes.source.startsWith("@");
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

    public boolean isShowAllClasses() {
        return showAllClasses;
    }

    public void setShowAllClasses(boolean showAllClasses) {
        this.showAllClasses = showAllClasses;
        differences.clear();
        performComparison();
    }
}
