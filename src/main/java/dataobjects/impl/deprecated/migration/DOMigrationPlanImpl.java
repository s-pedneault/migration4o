package dataobjects.impl.deprecated.migration;

import dataobjects.api.deprecated.migration.*;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.DOClass;
import java.util.*;

/**
 * Implementation of migration plan generation.
 */
public class DOMigrationPlanImpl implements DOMigrationPlan {

    private final DOEngine engine;
    private final DOInheritanceMapping inheritanceMapping;
    private final DOMigrationCoverage coverage;
    private final DOSchemaGap[] schemaGaps;

    private final List<DOExportStrategy> exportStrategies;
    private final List<DODataLossRisk> dataLossRisks;
    private final List<DORecommendation> recommendations;

    public DOMigrationPlanImpl(DOEngine engine, DOInheritanceMapping inheritanceMapping,
            DOMigrationCoverage coverage, DOSchemaGap[] schemaGaps) {
        this.engine = engine;
        this.inheritanceMapping = inheritanceMapping;
        this.coverage = coverage;
        this.schemaGaps = schemaGaps;

        this.exportStrategies = new ArrayList<>();
        this.dataLossRisks = new ArrayList<>();
        this.recommendations = new ArrayList<>();

        generatePlan();
    }

    private void generatePlan() {
        System.out.println("Generating migration plan...");

        generateExportStrategies();
        assessDataLossRisks();
        generateRecommendations();

        System.out.println("Migration plan generation complete.");
    }

    private void generateExportStrategies() {
        for (DOClass clazz : engine.getSchema().getClasses()) {
            DOStrategyType strategyType = determineStrategyType(clazz);
            String description = generateStrategyDescription(clazz, strategyType);
            double successRate = calculateSuccessRate(clazz, strategyType);
            String[] prerequisites = generatePrerequisites(clazz, strategyType);

            exportStrategies.add(new DOExportStrategyImpl(
                    clazz, strategyType, description, successRate, prerequisites));
        }
    }

    private DOStrategyType determineStrategyType(DOClass clazz) {
        DOCoverageStatus status = coverage.getCoverageStatus(clazz);

        if (status == DOCoverageStatus.NO_COVERAGE) {
            return DOStrategyType.SKIP_CLASS;
        }

        if (inheritanceMapping.isPolymorphic(clazz)) {
            return DOStrategyType.POLYMORPHIC_EXPORT;
        }

        if (status == DOCoverageStatus.CUSTOM_RESOLVER_NEEDED) {
            return DOStrategyType.CUSTOM_RESOLVER;
        }

        if (inheritanceMapping.getParent(clazz) != null) {
            return DOStrategyType.INHERITANCE_EXPORT;
        }

        return DOStrategyType.DIRECT_EXPORT;
    }

    private String generateStrategyDescription(DOClass clazz, DOStrategyType strategyType) {
        switch (strategyType) {
            case DIRECT_EXPORT:
                return "Export objects directly from " + clazz.getShortName();
            case INHERITANCE_EXPORT:
                return "Export through inheritance hierarchy from " + clazz.getShortName();
            case POLYMORPHIC_EXPORT:
                return "Handle polymorphic storage for " + clazz.getShortName();
            case CUSTOM_RESOLVER:
                return "Requires custom migration logic for " + clazz.getShortName();
            case SKIP_CLASS:
                return "Skip migration of " + clazz.getShortName() + " due to insufficient coverage";
            default:
                return "Standard export for " + clazz.getShortName();
        }
    }

    private double calculateSuccessRate(DOClass clazz, DOStrategyType strategyType) {
        DOCoverageStatus status = coverage.getCoverageStatus(clazz);

        switch (strategyType) {
            case DIRECT_EXPORT:
                return status == DOCoverageStatus.FULL_COVERAGE ? 0.95 : 0.8;
            case INHERITANCE_EXPORT:
                return 0.85;
            case POLYMORPHIC_EXPORT:
                return 0.7;
            case CUSTOM_RESOLVER:
                return 0.6;
            case SKIP_CLASS:
                return 0.0;
            default:
                return 0.75;
        }
    }

    private String[] generatePrerequisites(DOClass clazz, DOStrategyType strategyType) {
        List<String> prerequisites = new ArrayList<>();

        switch (strategyType) {
            case POLYMORPHIC_EXPORT:
                prerequisites.add("Verify inheritance mapping");
                prerequisites.add("Test polymorphic object handling");
                break;
            case CUSTOM_RESOLVER:
                prerequisites.add("Implement custom field resolvers");
                prerequisites.add("Test custom migration logic");
                break;
            case INHERITANCE_EXPORT:
                prerequisites.add("Validate inheritance relationships");
                break;
        }

        return prerequisites.toArray(new String[0]);
    }

    private void assessDataLossRisks() {
        // Risk from unresolved fields
        DOUnresolvedField[] unresolvedFields = coverage.getUnresolvedFields();
        if (unresolvedFields.length > 0) {
            dataLossRisks.add(new DODataLossRiskImpl(
                    null, // Affects multiple classes
                    DORiskLevel.HIGH,
                    String.format("%d unresolved fields may cause data loss", unresolvedFields.length),
                    unresolvedFields.length * 100, // Estimated objects at risk
                    new String[] {
                            "Resolve field type mappings",
                            "Implement custom field resolvers",
                            "Accept partial data migration"
                    },
                    false));
        }

        // Risk from schema gaps
        long criticalGaps = Arrays.stream(schemaGaps)
                .filter(gap -> gap.getSeverity() == DOSeverityLevel.CRITICAL)
                .count();

        if (criticalGaps > 0) {
            dataLossRisks.add(new DODataLossRiskImpl(
                    null,
                    DORiskLevel.CRITICAL,
                    String.format("%d critical schema gaps identified", criticalGaps),
                    criticalGaps * 500, // Estimated objects at risk
                    new String[] {
                            "Resolve all critical schema gaps",
                            "Update schema definitions",
                            "Do not proceed with migration"
                    },
                    false));
        }

        // Risk from low coverage
        if (coverage.getOverallCoveragePercentage() < 0.5) {
            dataLossRisks.add(new DODataLossRiskImpl(
                    null,
                    DORiskLevel.HIGH,
                    String.format("Low overall coverage: %.1f%%", coverage.getOverallCoveragePercentage() * 100),
                    1000, // Estimated objects at risk
                    new String[] {
                            "Improve schema coverage",
                            "Add missing class mappings",
                            "Consider partial migration"
                    },
                    coverage.getOverallCoveragePercentage() >= 0.3));
        }
    }

    private void generateRecommendations() {
        // Recommendations based on schema gaps
        Map<DOGapType, Integer> gapCounts = new HashMap<>();
        for (DOSchemaGap gap : schemaGaps) {
            gapCounts.merge(gap.getGapType(), 1, Integer::sum);
        }

        for (Map.Entry<DOGapType, Integer> entry : gapCounts.entrySet()) {
            DOGapType gapType = entry.getKey();
            int count = entry.getValue();

            recommendations.add(new DORecommendationImpl(
                    getRecommendationTypeForGap(gapType),
                    DOSeverityLevel.HIGH,
                    String.format("Address %d %s issues", count, gapType.getName().toLowerCase()),
                    getActionItemsForGap(gapType),
                    String.format("Will resolve %d migration issues", count)));
        }

        // Coverage improvement recommendations
        if (coverage.getOverallCoveragePercentage() < 0.8) {
            recommendations.add(new DORecommendationImpl(
                    DORecommendationType.SCHEMA_IMPROVEMENT,
                    DOSeverityLevel.MEDIUM,
                    "Improve overall schema coverage to at least 80%",
                    new String[] {
                            "Review and map unresolved fields",
                            "Add missing class definitions",
                            "Update collection content types"
                    },
                    "Will significantly improve migration success rate"));
        }
    }

    private DORecommendationType getRecommendationTypeForGap(DOGapType gapType) {
        switch (gapType) {
            case UNRESOLVED_FIELD:
                return DORecommendationType.FIELD_MAPPING;
            case MISSING_SCHEMA_CLASS:
                return DORecommendationType.SCHEMA_IMPROVEMENT;
            case POLYMORPHIC_CONFLICT:
                return DORecommendationType.INHERITANCE_MAPPING;
            default:
                return DORecommendationType.SCHEMA_IMPROVEMENT;
        }
    }

    private String[] getActionItemsForGap(DOGapType gapType) {
        switch (gapType) {
            case UNRESOLVED_FIELD:
                return new String[] {
                        "Update schema field type definitions",
                        "Add explicit collection content types",
                        "Implement custom field resolvers"
                };
            case MISSING_SCHEMA_CLASS:
                return new String[] {
                        "Add missing class mappings to schema",
                        "Review database class hierarchy",
                        "Decide on migration inclusion"
                };
            case POLYMORPHIC_CONFLICT:
                return new String[] {
                        "Review inheritance mapping strategy",
                        "Test polymorphic object migration",
                        "Consider type-specific handling"
                };
            default:
                return new String[] { "Review and address manually" };
        }
    }

    @Override
    public DOExportStrategy[] getExportStrategies() {
        return exportStrategies.toArray(new DOExportStrategy[0]);
    }

    @Override
    public DODataLossRisk[] getDataLossRisks() {
        return dataLossRisks.toArray(new DODataLossRisk[0]);
    }

    @Override
    public DORecommendation[] getRecommendations() {
        return recommendations.toArray(new DORecommendation[0]);
    }

    @Override
    public boolean isReadyForMigration() {
        // Migration is ready if:
        // 1. No critical risks
        // 2. Sufficient coverage
        // 3. No critical schema gaps

        boolean hasCriticalRisk = dataLossRisks.stream()
                .anyMatch(risk -> risk.getRiskLevel() == DORiskLevel.CRITICAL);

        boolean hasCriticalGap = Arrays.stream(schemaGaps)
                .anyMatch(gap -> gap.getSeverity() == DOSeverityLevel.CRITICAL);

        return !hasCriticalRisk && !hasCriticalGap && coverage.hasSufficientCoverage();
    }

    @Override
    public DORiskLevel getOverallRiskLevel() {
        // Determine highest risk level
        for (DODataLossRisk risk : dataLossRisks) {
            if (risk.getRiskLevel() == DORiskLevel.CRITICAL) {
                return DORiskLevel.CRITICAL;
            }
        }

        for (DODataLossRisk risk : dataLossRisks) {
            if (risk.getRiskLevel() == DORiskLevel.HIGH) {
                return DORiskLevel.HIGH;
            }
        }

        if (coverage.getOverallCoveragePercentage() < 0.8) {
            return DORiskLevel.MEDIUM;
        }

        return DORiskLevel.LOW;
    }

    @Override
    public long getEstimatedMigrationTime() {
        // Estimate based on number of classes and complexity
        int classCount = engine.getSchema().getClasses().length;
        int complexityMultiplier = 1;

        // Increase time for inheritance and polymorphic classes
        for (DOClass clazz : engine.getSchema().getClasses()) {
            if (inheritanceMapping.isPolymorphic(clazz)) {
                complexityMultiplier += 2;
            } else if (inheritanceMapping.getParent(clazz) != null) {
                complexityMultiplier += 1;
            }
        }

        // Base time: 30 seconds per class, multiplied by complexity
        return classCount * 30000L * complexityMultiplier;
    }

    @Override
    public String getPlanSummary() {
        return String.format("Migration plan: %s risk, %.1f%% coverage, %d strategies, %s",
                getOverallRiskLevel().getName(),
                coverage.getOverallCoveragePercentage() * 100,
                exportStrategies.size(),
                isReadyForMigration() ? "ready" : "not ready");
    }

    // Implementation classes for data structures
    private static class DOExportStrategyImpl implements DOExportStrategy {
        private final DOClass targetClass;
        private final DOStrategyType strategyType;
        private final String description;
        private final double successRate;
        private final String[] prerequisites;

        public DOExportStrategyImpl(DOClass targetClass, DOStrategyType strategyType,
                String description, double successRate, String[] prerequisites) {
            this.targetClass = targetClass;
            this.strategyType = strategyType;
            this.description = description;
            this.successRate = successRate;
            this.prerequisites = prerequisites;
        }

        @Override
        public DOClass getTargetClass() {
            return targetClass;
        }

        @Override
        public DOStrategyType getStrategyType() {
            return strategyType;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public double getSuccessRate() {
            return successRate;
        }

        @Override
        public String[] getPrerequisites() {
            return prerequisites;
        }
    }

    private static class DODataLossRiskImpl implements DODataLossRisk {
        private final DOClass affectedClass;
        private final DORiskLevel riskLevel;
        private final String description;
        private final long objectsAtRisk;
        private final String[] mitigationStrategies;
        private final boolean acceptableRisk;

        public DODataLossRiskImpl(DOClass affectedClass, DORiskLevel riskLevel,
                String description, long objectsAtRisk,
                String[] mitigationStrategies, boolean acceptableRisk) {
            this.affectedClass = affectedClass;
            this.riskLevel = riskLevel;
            this.description = description;
            this.objectsAtRisk = objectsAtRisk;
            this.mitigationStrategies = mitigationStrategies;
            this.acceptableRisk = acceptableRisk;
        }

        @Override
        public DOClass getAffectedClass() {
            return affectedClass;
        }

        @Override
        public DORiskLevel getRiskLevel() {
            return riskLevel;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public long getObjectsAtRisk() {
            return objectsAtRisk;
        }

        @Override
        public String[] getMitigationStrategies() {
            return mitigationStrategies;
        }

        @Override
        public boolean isAcceptableRisk() {
            return acceptableRisk;
        }
    }

    private static class DORecommendationImpl implements DORecommendation {
        private final DORecommendationType type;
        private final DOSeverityLevel priority;
        private final String description;
        private final String[] actionItems;
        private final String expectedImpact;

        public DORecommendationImpl(DORecommendationType type, DOSeverityLevel priority,
                String description, String[] actionItems, String expectedImpact) {
            this.type = type;
            this.priority = priority;
            this.description = description;
            this.actionItems = actionItems;
            this.expectedImpact = expectedImpact;
        }

        @Override
        public DORecommendationType getType() {
            return type;
        }

        @Override
        public DOSeverityLevel getPriority() {
            return priority;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String[] getActionItems() {
            return actionItems;
        }

        @Override
        public String getExpectedImpact() {
            return expectedImpact;
        }
    }
}
