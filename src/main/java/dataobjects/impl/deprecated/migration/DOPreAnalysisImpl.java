package dataobjects.impl.deprecated.migration;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.deprecated.migration.*;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import java.util.*;

/**
 * Implementation of the migration pre-analysis functionality.
 */
public class DOPreAnalysisImpl implements DOPreAnalysis {

    private final DOEngine engine;
    private DOInheritanceMapping inheritanceMapping;
    private DOMigrationCoverage coverage;
    private DOLostObjectAnalysis lostObjectAnalysis;
    private DOSchemaGap[] schemaGaps;
    private DOMigrationPlan migrationPlan;

    public DOPreAnalysisImpl(DOEngine engine) {
        this.engine = engine;
        performAnalysis();
    }

    private void performAnalysis() {
        System.out.println("Performing migration pre-analysis...");

        // Phase 1: Analyze inheritance relationships
        this.inheritanceMapping = new DOInheritanceMappingImpl(engine.getSchema(), engine.getDatabase());

        // Phase 2: Analyze migration coverage
        this.coverage = new DOMigrationCoverageImpl(engine, inheritanceMapping);

        // Phase 3: Analyze lost objects using ID-based approach
        this.lostObjectAnalysis = new DOLostObjectAnalysisImpl(engine, inheritanceMapping);

        // Phase 4: Identify schema gaps
        this.schemaGaps = analyzeSchemaGaps();

        // Phase 5: Generate migration plan
        this.migrationPlan = new DOMigrationPlanImpl(engine, inheritanceMapping, coverage, schemaGaps);

        System.out.println("Migration pre-analysis complete.");
    }

    @Override
    public DOInheritanceMapping getInheritanceMapping() {
        return inheritanceMapping;
    }

    @Override
    public DOMigrationCoverage getCoverage() {
        return coverage;
    }

    @Override
    public DOLostObjectAnalysis getLostObjectAnalysis() {
        return lostObjectAnalysis;
    }

    @Override
    public DOSchemaGap[] getSchemaGaps() {
        return schemaGaps;
    }

    @Override
    public DOMigrationPlan generateMigrationPlan() {
        return migrationPlan;
    }

    @Override
    public void printAnalysisReport() {
        System.out.println("\n========================================");
        System.out.println("MIGRATION PRE-ANALYSIS REPORT");
        System.out.println("========================================");

        printInheritanceAnalysis();
        printCoverageAnalysis();
        printLostObjectAnalysis();
        printSchemaGapAnalysis();
        printMigrationPlan();

        System.out.println("========================================");
    }

    @Override
    public DOClassAnalysis getClassAnalysis(DOClass clazz) {
        return new DOClassAnalysisImpl(clazz, inheritanceMapping, coverage, schemaGaps);
    }

    private DOSchemaGap[] analyzeSchemaGaps() {
        List<DOSchemaGap> gaps = new ArrayList<>();

        // Analyze unresolved fields
        DOUnresolvedField[] unresolvedFields = coverage.getUnresolvedFields();
        for (DOUnresolvedField unresolvedField : unresolvedFields) {
            gaps.add(new DOSchemaGapImpl(
                    DOGapType.UNRESOLVED_FIELD,
                    unresolvedField.getContainingClass(),
                    unresolvedField.getField(),
                    String.format("Field '%s' has unresolved content type: %s",
                            unresolvedField.getField().getName(),
                            unresolvedField.getUnresolvedReason()),
                    DOSeverityLevel.MEDIUM));
        }

        // Analyze polymorphic storage conflicts
        DOPolymorphicStorage[] polymorphicStorages = coverage.getPolymorphicStoragePoints();
        for (DOPolymorphicStorage storage : polymorphicStorages) {
            if (storage.affectsMigration()) {
                gaps.add(new DOSchemaGapImpl(
                        DOGapType.POLYMORPHIC_CONFLICT,
                        storage.getStorageClass(),
                        null,
                        String.format("Class '%s' stores %d polymorphic object types",
                                storage.getStorageClass().getShortName(),
                                storage.getStoredObjectTypes().length),
                        DOSeverityLevel.HIGH));
            }
        }

        // Analyze inheritance gaps
        analyzeInheritanceGaps(gaps);

        return gaps.toArray(new DOSchemaGap[0]);
    }

    private void analyzeInheritanceGaps(List<DOSchemaGap> gaps) {
        // Check for missing schema mappings for database classes
        Set<String> schemaClassNames = new HashSet<>();
        for (DOClass schemaClass : engine.getSchema().getClasses()) {
            schemaClassNames.add(schemaClass.getAbsoluteName());
        }

        for (DOClass dbClass : engine.getDatabase().getClasses()) {
            if (!schemaClassNames.contains(dbClass.getAbsoluteName())) {
                gaps.add(new DOSchemaGapImpl(
                        DOGapType.MISSING_SCHEMA_CLASS,
                        dbClass,
                        null,
                        String.format("Database class '%s' has no schema mapping",
                                dbClass.getAbsoluteName()),
                        DOSeverityLevel.HIGH));
            }
        }
    }

    private void printInheritanceAnalysis() {
        System.out.println("\n--- INHERITANCE ANALYSIS ---");
        DOClass[] rootClasses = inheritanceMapping.getRootClasses();
        System.out.printf("Root classes found: %d%n", rootClasses.length);

        for (DOClass rootClass : rootClasses) {
            printInheritanceTree(rootClass, 0);
        }
    }

    private void printCoverageAnalysis() {
        System.out.println("\n--- COVERAGE ANALYSIS ---");
        System.out.printf("Overall coverage: %.1f%%%n", coverage.getOverallCoveragePercentage() * 100);
        System.out.printf("Sufficient coverage: %s%n", coverage.hasSufficientCoverage() ? "YES" : "NO");

        DOUnresolvedField[] unresolvedFields = coverage.getUnresolvedFields();
        if (unresolvedFields.length > 0) {
            System.out.printf("Unresolved fields: %d%n", unresolvedFields.length);
        }
    }

    private void printLostObjectAnalysis() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LOST OBJECT ANALYSIS (EXACT Reachability Tracking)");
        System.out.println("=".repeat(60));

        final DOLostObjectAnalysis lostAnalysis = getLostObjectAnalysis();
        System.out.printf("📊 Total UNIQUE objects in database: %d%n", lostAnalysis.getTotalObjectCount());
        System.out.printf("✅ Objects REACHABLE from module roots: %d (%.2f%%)%n",
                lostAnalysis.getTotalObjectCount() - lostAnalysis.getTotalLostObjectCount(),
                (100.0 * (lostAnalysis.getTotalObjectCount() - lostAnalysis.getTotalLostObjectCount()))
                        / lostAnalysis.getTotalObjectCount());
        System.out.printf("❌ Objects UNREACHABLE: %d (%.2f%%)%n",
                lostAnalysis.getTotalLostObjectCount(),
                lostAnalysis.getLostObjectPercentage());

        if (lostAnalysis.hasDataLossRisk()) {
            System.out.println("\n⚠️  DATA LOSS RISK DETECTED!");
            System.out.println("\nObjects unreachable from module roots will NOT be migrated.");
            System.out.println("These objects exist in the database but are not referenced through");
            System.out.println("any module's object graph traversal starting from leaf class instances.");

            System.out.println("\nREACHABILITY ALGORITHM:");
            System.out.println("1. Start with ALL leaf class objects (end classes with no subclasses)");
            System.out.println("2. For each leaf object:");
            System.out.println("   - Mark object as REACHED for all classes in its inheritance chain");
            System.out.println("   - Recursively follow ALL field references");
            System.out.println("   - Recursively process ALL collection contents");
            System.out.println("3. Unreachable = Objects never encountered during traversal");
            System.out.println("\nThis is EXACT tracking, not statistical estimation.");

        } else {
            System.out.println("✅ No data loss risk - all objects are reachable from module roots!");
            System.out.println("Every object can be reached through graph traversal from leaf class instances.");
        }
    }

    private void printSchemaGapAnalysis() {
        System.out.println("\n--- SCHEMA GAP ANALYSIS ---");
        if (schemaGaps.length == 0) {
            System.out.println("No schema gaps identified.");
        } else {
            System.out.printf("Schema gaps found: %d%n", schemaGaps.length);
            for (DOSchemaGap gap : schemaGaps) {
                System.out.printf("  - %s: %s [%s]%n",
                        gap.getGapType().getName(),
                        gap.getDescription(),
                        gap.getSeverity().getName());
            }
        }
    }

    private void printMigrationPlan() {
        System.out.println("\n--- MIGRATION PLAN ---");
        System.out.printf("Ready for migration: %s%n", migrationPlan.isReadyForMigration() ? "YES" : "NO");
        System.out.printf("Overall risk level: %s%n", migrationPlan.getOverallRiskLevel().getName());

        DORecommendation[] recommendations = migrationPlan.getRecommendations();
        if (recommendations.length > 0) {
            System.out.printf("Recommendations: %d%n", recommendations.length);
            for (DORecommendation rec : recommendations) {
                System.out.printf("  - [%s] %s%n", rec.getPriority().getName(), rec.getDescription());
            }
        }
    }

    private void printInheritanceTree(DOClass clazz, int depth) {
        String indent = "  ".repeat(depth);
        System.out.printf("%s%s%n", indent, clazz.getShortName());

        DOClass[] children = inheritanceMapping.getChildren(clazz);
        for (DOClass child : children) {
            printInheritanceTree(child, depth + 1);
        }
    }

    // Implementation class for schema gaps
    private static class DOSchemaGapImpl implements DOSchemaGap {
        private final DOGapType gapType;
        private final DOClass sourceClass;
        private final DOField sourceField;
        private final String description;
        private final DOSeverityLevel severity;

        public DOSchemaGapImpl(DOGapType gapType, DOClass sourceClass, DOField sourceField,
                String description, DOSeverityLevel severity) {
            this.gapType = gapType;
            this.sourceClass = sourceClass;
            this.sourceField = sourceField;
            this.description = description;
            this.severity = severity;
        }

        @Override
        public DOGapType getGapType() {
            return gapType;
        }

        @Override
        public DOClass getSourceClass() {
            return sourceClass;
        }

        @Override
        public DOField getSourceField() {
            return sourceField;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public DOSeverityLevel getSeverity() {
            return severity;
        }

        @Override
        public String[] getRecommendations() {
            switch (gapType) {
                case UNRESOLVED_FIELD:
                    return new String[] {
                            "Add explicit type mapping to schema",
                            "Implement custom field resolver",
                            "Update collection content type definition"
                    };
                case MISSING_SCHEMA_CLASS:
                    return new String[] {
                            "Add class mapping to schema",
                            "Create custom migration logic",
                            "Consider excluding from migration"
                    };
                case POLYMORPHIC_CONFLICT:
                    return new String[] {
                            "Review inheritance mapping strategy",
                            "Consider separate migration for each type",
                            "Implement polymorphic resolver"
                    };
                default:
                    return new String[] { "Review and address manually" };
            }
        }

        @Override
        public boolean causesDataLoss() {
            return severity == DOSeverityLevel.HIGH || severity == DOSeverityLevel.CRITICAL;
        }
    }
}
