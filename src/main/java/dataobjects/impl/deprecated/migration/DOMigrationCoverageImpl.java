package dataobjects.impl.deprecated.migration;

import dataobjects.api.deprecated.migration.*;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.database.DODatabase;
import dataobjects.util.CollectionTypeUtil;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of migration coverage analysis.
 */
public class DOMigrationCoverageImpl implements DOMigrationCoverage {

    private final DOInheritanceMapping inheritanceMapping;
    private final DOSchema schema;
    private final DODatabase database;

    // Analysis results
    private final List<DOUnresolvedField> unresolvedFields;
    private final List<DOPolymorphicStorage> polymorphicStoragePoints;
    private final Map<DOClass, DOCoverageStatus> coverageStatusMap;
    private final Map<DOClass, DOClassCoverage> classCoverageMap;

    public DOMigrationCoverageImpl(DOEngine engine, DOInheritanceMapping inheritanceMapping) {
        this.inheritanceMapping = inheritanceMapping;
        this.schema = engine.getSchema();
        this.database = engine.getDatabase();

        this.unresolvedFields = new ArrayList<>();
        this.polymorphicStoragePoints = new ArrayList<>();
        this.coverageStatusMap = new HashMap<>();
        this.classCoverageMap = new HashMap<>();

        performCoverageAnalysis();
    }

    private void performCoverageAnalysis() {
        System.out.println("Analyzing migration coverage...");

        // Analyze each schema class
        for (DOClass schemaClass : schema.getClasses()) {
            analyzeClassCoverage(schemaClass);
        }

        // Analyze polymorphic storage points
        analyzePolymorphicStorage();

        System.out.println("Coverage analysis complete.");
    }

    private void analyzeClassCoverage(DOClass schemaClass) {
        int totalFields = 0;
        int resolvedFields = 0;
        List<DOUnresolvedField> classUnresolvedFields = new ArrayList<>();

        // Analyze each field in the schema class
        for (DOField field : schemaClass.getFields()) {
            totalFields++;

            if (isFieldResolved(schemaClass, field)) {
                resolvedFields++;
            } else {
                DOUnresolvedField unresolvedField = createUnresolvedField(schemaClass, field);
                classUnresolvedFields.add(unresolvedField);
                unresolvedFields.add(unresolvedField);
            }
        }

        // Calculate coverage percentage
        double coveragePercentage = totalFields > 0 ? (double) resolvedFields / totalFields : 1.0;

        // Determine coverage status
        DOCoverageStatus status = determineCoverageStatus(coveragePercentage, classUnresolvedFields.size());
        coverageStatusMap.put(schemaClass, status);

        // Create class coverage stats
        DOClassCoverage classCoverage = new DOClassCoverageImpl(
                schemaClass, status, coveragePercentage, resolvedFields, totalFields,
                getObjectCount(schemaClass));
        classCoverageMap.put(schemaClass, classCoverage);
    }

    private boolean isFieldResolved(DOClass schemaClass, DOField field) {
        // Check if this is a collection field with unresolved content type
        if (CollectionTypeUtil.isCollectionType(field.getTypeName())) {
            String contentType = field.getContentTypeName();
            return isContentTypeResolved(contentType);
        }

        // For non-collection fields, check if the type is resolved
        return isContentTypeResolved(field.getTypeName());
    }

    private boolean isContentTypeResolved(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }

        // Content type is unresolved if it's java.lang.Object or unknown
        return !contentType.equals("java.lang.Object") &&
                !contentType.equals("unknown") &&
                !contentType.equals("Object");
    }

    private DOUnresolvedField createUnresolvedField(DOClass containingClass, DOField field) {
        String collectionType = CollectionTypeUtil.isCollectionType(field.getTypeName()) ? field.getTypeName() : null;
        String contentType = field.getContentTypeName();
        String reason = determineUnresolvedReason(field, contentType);

        return new DOUnresolvedFieldImpl(containingClass, field, collectionType, contentType, reason);
    }

    private String determineUnresolvedReason(DOField field, String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "Content type is null or empty";
        }
        if (contentType.equals("java.lang.Object")) {
            return "Content type is generic Object";
        }
        if (contentType.equals("unknown")) {
            return "Content type is unknown";
        }
        return "Content type could not be resolved: " + contentType;
    }

    private DOCoverageStatus determineCoverageStatus(double coveragePercentage, int unresolvedCount) {
        if (coveragePercentage >= 1.0) {
            return DOCoverageStatus.FULL_COVERAGE;
        } else if (coveragePercentage >= 0.8) {
            return DOCoverageStatus.PARTIAL_COVERAGE;
        } else if (coveragePercentage > 0.0) {
            return DOCoverageStatus.PARTIAL_COVERAGE;
        } else {
            return DOCoverageStatus.NO_COVERAGE;
        }
    }

    private void analyzePolymorphicStorage() {
        System.out.println("Analyzing polymorphic storage patterns...");

        // Analyze both schema and database classes for polymorphic storage
        Set<DOClass> allClasses = new HashSet<>();
        for (DOClass clazz : schema.getClasses()) {
            allClasses.add(clazz);
        }
        for (DOClass clazz : database.getClasses()) {
            allClasses.add(clazz);
        }

        for (DOClass clazz : allClasses) {
            analyzePolymorphicStorageForClass(clazz);
        }

        System.out.printf("Found %d polymorphic storage points%n", polymorphicStoragePoints.size());
    }

    private void analyzePolymorphicStorageForClass(DOClass clazz) {
        // Check if this class has child classes (inheritance hierarchy)
        DOClass[] children = inheritanceMapping.getChildren(clazz);
        DOClass[] descendants = inheritanceMapping.getDescendants(clazz);

        if (children.length == 0 && descendants.length == 0) {
            return; // No inheritance, no polymorphic storage
        }

        // Get object count for this class
        long objectCount = getObjectCount(clazz);

        if (objectCount == 0) {
            return; // No objects stored, no polymorphic concern
        }

        // Build detailed polymorphic storage information
        List<PolymorphicStorageDetail> storageDetails = new ArrayList<>();

        // Check what types are actually stored in this class
        for (DOClass descendant : descendants) {
            long descendantObjectCount = getObjectCount(descendant);
            if (descendantObjectCount > 0) {
                // This descendant type has objects that might be stored in the parent class
                storageDetails.add(new PolymorphicStorageDetail(
                        descendant,
                        descendantObjectCount,
                        getInheritancePath(descendant, clazz),
                        isSchemaClassMapped(descendant)));
            }
        }

        // Also check if the class itself stores objects
        if (objectCount > 0) {
            storageDetails.add(new PolymorphicStorageDetail(
                    clazz,
                    objectCount,
                    new DOClass[] { clazz }, // Self-storage
                    isSchemaClassMapped(clazz)));
        }

        if (!storageDetails.isEmpty()) {
            polymorphicStoragePoints.add(new DOPolymorphicStorageImpl(
                    clazz,
                    descendants,
                    objectCount,
                    true,
                    storageDetails));
        }
    }

    private DOClass[] getInheritancePath(DOClass descendant, DOClass ancestor) {
        List<DOClass> path = new ArrayList<>();
        DOClass current = descendant;

        while (current != null && !current.equals(ancestor)) {
            path.add(current);
            current = inheritanceMapping.getParent(current);
        }

        if (current != null) {
            path.add(ancestor);
        }

        return path.toArray(new DOClass[0]);
    }

    private boolean isSchemaClassMapped(DOClass clazz) {
        for (DOClass schemaClass : schema.getClasses()) {
            if (schemaClass.getAbsoluteName().equals(clazz.getAbsoluteName())) {
                return true;
            }
        }
        return false;
    }

    // Helper class to store detailed polymorphic storage information
    private static class PolymorphicStorageDetail {
        public final DOClass storedType;
        public final long objectCount;
        public final DOClass[] inheritancePath;
        public final boolean hasSchemaMaping;

        public PolymorphicStorageDetail(DOClass storedType, long objectCount,
                DOClass[] inheritancePath, boolean hasSchemaMapping) {
            this.storedType = storedType;
            this.objectCount = objectCount;
            this.inheritancePath = inheritancePath;
            this.hasSchemaMaping = hasSchemaMapping;
        }
    }

    private long getObjectCount(DOClass clazz) {
        // Look for the corresponding database class
        for (DOClass dbClass : database.getClasses()) {
            if (dbClass.getAbsoluteName().equals(clazz.getAbsoluteName()) &&
                    dbClass instanceof dataobjects.api.models.database.DODatabaseClass) {
                return ((dataobjects.api.models.database.DODatabaseClass) dbClass).getTotalObjectCount();
            }
        }
        // Default value if not found
        return 100;
    }

    @Override
    public DOCoverageStatus getCoverageStatus(DOClass clazz) {
        return coverageStatusMap.getOrDefault(clazz, DOCoverageStatus.NO_COVERAGE);
    }

    @Override
    public DOUnresolvedField[] getUnresolvedFields() {
        return unresolvedFields.toArray(new DOUnresolvedField[0]);
    }

    @Override
    public DOPolymorphicStorage[] getPolymorphicStoragePoints() {
        return polymorphicStoragePoints.toArray(new DOPolymorphicStorage[0]);
    }

    @Override
    public double getOverallCoveragePercentage() {
        if (classCoverageMap.isEmpty()) {
            return 0.0;
        }

        double totalCoverage = 0.0;
        for (DOClassCoverage coverage : classCoverageMap.values()) {
            totalCoverage += coverage.getCoveragePercentage();
        }

        return totalCoverage / classCoverageMap.size();
    }

    @Override
    public DOClassCoverage[] getClassCoverageStats() {
        return classCoverageMap.values().toArray(new DOClassCoverage[0]);
    }

    @Override
    public boolean hasSufficientCoverage() {
        return getOverallCoveragePercentage() >= 0.8;
    }

    // Implementation classes for data structures
    private static class DOUnresolvedFieldImpl implements DOUnresolvedField {
        private final DOClass containingClass;
        private final DOField field;
        private final String collectionType;
        private final String contentType;
        private final String unresolvedReason;

        public DOUnresolvedFieldImpl(DOClass containingClass, DOField field,
                String collectionType, String contentType, String unresolvedReason) {
            this.containingClass = containingClass;
            this.field = field;
            this.collectionType = collectionType;
            this.contentType = contentType;
            this.unresolvedReason = unresolvedReason;
        }

        @Override
        public DOClass getContainingClass() {
            return containingClass;
        }

        @Override
        public DOField getField() {
            return field;
        }

        @Override
        public String getCollectionType() {
            return collectionType;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getUnresolvedReason() {
            return unresolvedReason;
        }
    }

    private static class DOPolymorphicStorageImpl implements DOPolymorphicStorage {
        private final DOClass storageClass;
        private final DOClass[] storedObjectTypes;
        private final long objectCount;
        private final boolean affectsMigration;
        private final List<PolymorphicStorageDetail> storageDetails;

        public DOPolymorphicStorageImpl(DOClass storageClass, DOClass[] storedObjectTypes,
                long objectCount, boolean affectsMigration,
                List<PolymorphicStorageDetail> storageDetails) {
            this.storageClass = storageClass;
            this.storedObjectTypes = storedObjectTypes;
            this.objectCount = objectCount;
            this.affectsMigration = affectsMigration;
            this.storageDetails = storageDetails != null ? storageDetails : new ArrayList<>();
        }

        @Override
        public DOClass getStorageClass() {
            return storageClass;
        }

        @Override
        public DOClass[] getStoredObjectTypes() {
            return storedObjectTypes;
        }

        @Override
        public long getObjectCount() {
            return objectCount;
        }

        @Override
        public boolean affectsMigration() {
            return affectsMigration;
        }

        @Override
        public String getDetailedExplanation() {
            StringBuilder explanation = new StringBuilder();
            explanation.append("Polymorphic Conflict in class: ").append(storageClass.getAbsoluteName()).append("\n");
            explanation.append("This class stores ").append(storageDetails.size())
                    .append(" different object types:\n\n");

            for (PolymorphicStorageDetail detail : storageDetails) {
                explanation.append("  - Type: ").append(detail.storedType.getAbsoluteName()).append("\n");
                explanation.append("    Objects: ").append(detail.objectCount).append("\n");
                explanation.append("    Schema Mapped: ").append(detail.hasSchemaMaping ? "Yes" : "No").append("\n");
                explanation.append("    Inheritance Path: ");
                for (int i = 0; i < detail.inheritancePath.length; i++) {
                    if (i > 0)
                        explanation.append(" -> ");
                    explanation.append(detail.inheritancePath[i].getShortName());
                }
                explanation.append("\n");

                // Add migration impact
                if (!detail.hasSchemaMaping) {
                    explanation.append("    MIGRATION IMPACT: This type is not mapped in schema - data may be lost!\n");
                } else if (detail.inheritancePath.length > 2) {
                    explanation.append("    MIGRATION IMPACT: Deep inheritance may require special handling\n");
                }
                explanation.append("\n");
            }

            explanation.append("RECOMMENDATIONS:\n");
            boolean hasUnmappedTypes = storageDetails.stream().anyMatch(d -> !d.hasSchemaMaping);
            boolean hasDeepInheritance = storageDetails.stream().anyMatch(d -> d.inheritancePath.length > 2);

            if (hasUnmappedTypes) {
                explanation.append("  - Add schema mappings for unmapped types to prevent data loss\n");
            }
            if (hasDeepInheritance) {
                explanation.append("  - Review inheritance hierarchy for migration complexity\n");
            }
            explanation.append("  - Consider exporting polymorphic data with type information\n");
            explanation.append("  - Test migration with sample data to verify type preservation\n");

            return explanation.toString();
        }

        @Override
        public DOPolymorphicTypeInfo[] getTypeBreakdown() {
            return storageDetails.stream()
                    .map(detail -> new DOPolymorphicTypeInfoImpl(detail))
                    .toArray(DOPolymorphicTypeInfo[]::new);
        }
    }

    private static class DOClassCoverageImpl implements DOClassCoverage {
        private final DOClass clazz;
        private final DOCoverageStatus status;
        private final double coveragePercentage;
        private final int mappedFieldCount;
        private final int totalFieldCount;
        private final long objectCount;

        public DOClassCoverageImpl(DOClass clazz, DOCoverageStatus status, double coveragePercentage,
                int mappedFieldCount, int totalFieldCount, long objectCount) {
            this.clazz = clazz;
            this.status = status;
            this.coveragePercentage = coveragePercentage;
            this.mappedFieldCount = mappedFieldCount;
            this.totalFieldCount = totalFieldCount;
            this.objectCount = objectCount;
        }

        @Override
        public DOClass getClazz() {
            return clazz;
        }

        @Override
        public DOCoverageStatus getStatus() {
            return status;
        }

        @Override
        public double getCoveragePercentage() {
            return coveragePercentage;
        }

        @Override
        public int getMappedFieldCount() {
            return mappedFieldCount;
        }

        @Override
        public int getTotalFieldCount() {
            return totalFieldCount;
        }

        @Override
        public long getObjectCount() {
            return objectCount;
        }
    }

    private static class DOPolymorphicTypeInfoImpl implements DOPolymorphicTypeInfo {
        private final PolymorphicStorageDetail detail;

        public DOPolymorphicTypeInfoImpl(PolymorphicStorageDetail detail) {
            this.detail = detail;
        }

        @Override
        public DOClass getStoredType() {
            return detail.storedType;
        }

        @Override
        public long getObjectCount() {
            return detail.objectCount;
        }

        @Override
        public DOClass[] getInheritancePath() {
            return detail.inheritancePath;
        }

        @Override
        public boolean hasSchemaMapping() {
            return detail.hasSchemaMaping;
        }

        @Override
        public int getInheritanceDepth() {
            return detail.inheritancePath.length - 1; // Subtract 1 for the storage class itself
        }

        @Override
        public String getMigrationImpact() {
            StringBuilder impact = new StringBuilder();

            if (!detail.hasSchemaMaping) {
                impact.append("HIGH RISK: Type not mapped in schema - data loss likely. ");
            }

            if (detail.inheritancePath.length > 2) {
                impact.append("MEDIUM RISK: Deep inheritance (").append(getInheritanceDepth())
                        .append(" levels) - complex migration required. ");
            }

            if (detail.objectCount > 1000) {
                impact.append("VOLUME CONCERN: Large number of objects (").append(detail.objectCount)
                        .append(") - performance impact during migration. ");
            }

            if (impact.length() == 0) {
                impact.append("LOW RISK: Type properly mapped with shallow inheritance.");
            }

            return impact.toString().trim();
        }
    }
}
