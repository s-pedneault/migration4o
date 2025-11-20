# DB4O Migration Pre-Analysis Plan for DataObjects API

## Problem Statement

In DB4O databases, objects are stored as multiple database entries due to inheritance:
- 1 entry for the leaf object itself  
- 1 entry for each ancestor class in the inheritance hierarchy

**Key Challenge**: When migrating based on a schema, we need to understand:
1. How many objects of each stored class will actually be migrated
2. How many objects will be left behind (data loss risk)
3. Which inheritance relationships affect migration coverage
4. Schema completeness assessment for migration planning

## DataObjects API Migration Architecture

### New Migration Subpackage Structure

```
dataobjects/
├── api/
│   ├── migration/
│   │   ├── DOPreAnalysis.java           // Main pre-analysis interface
│   │   ├── DOInheritanceMapping.java    // Inheritance relationship mapping
│   │   ├── DOMigrationCoverage.java     // Coverage analysis results  
│   │   ├── DOSchemaGap.java             // Schema gaps and recommendations
│   │   └── DOMigrationPlan.java         // Complete migration plan
│   └── ...existing APIs...
├── impl/
│   ├── migration/
│   │   ├── DOPreAnalysisImpl.java
│   │   ├── DOInheritanceMappingImpl.java
│   │   ├── DOMigrationCoverageImpl.java
│   │   ├── DOSchemaGapImpl.java
│   │   └── DOMigrationPlanImpl.java
│   └── ...existing implementations...
```

## Proposed Solution

### Phase 1: Enhanced Object ID Tracking System

#### 1.1 Create Inheritance-Aware Object Tracker

```java
/**
 * Tracks object export status across inheritance hierarchies
 */
public class InheritanceExportTracker {
    
    // Core tracking structures
    private Map<Long, Set<String>> objectIdToClasses;           // ObjectID -> Set of class names it represents
    private Map<String, Set<Long>> classToObjectIds;           // ClassName -> Set of ObjectIDs for that class
    private Set<Long> exportedObjectIds;                       // All ObjectIDs that have been exported
    private Map<String, Integer> exportedCountByClass;         // ClassName -> count of exported objects
    
    // Inheritance context
    private InheritanceHierarchy hierarchy;
    private ExtObjectContainer extDb;
    
    /**
     * Initialize tracker with inheritance relationships
     */
    public void initialize(ExtObjectContainer extDb, InheritanceHierarchy hierarchy);
    
    /**
     * Build the mapping between object IDs and all classes they represent
     */
    private void buildObjectIdToClassMapping();
    
    /**
     * Mark a leaf object as exported, which automatically marks all its inheritance ancestors
     */
    public InheritanceExportResult markLeafObjectExported(long objectId, String leafClassName);
    
    /**
     * Get export statistics for all classes
     */
    public Map<String, ClassExportStatistics> getExportStatistics();
    
    /**
     * Check if ALL objects in the database have been exported
     */
    public boolean isCompleteExportAchieved();
}
```

#### 1.2 Define Export Result Data Structures

```java
/**
 * Result of marking a leaf object as exported
 */
public class InheritanceExportResult {
    private long leafObjectId;
    private String leafClassName;
    private List<String> affectedClasses;              // All classes marked as having +1 export
    private List<Long> affectedObjectIds;              // All ObjectIDs marked as exported
    private boolean wasAlreadyExported;                // True if this object was already exported
    private Map<String, Integer> classExportCounts;   // Updated export counts per class
}

/**
 * Export statistics for a single class
 */
public class ClassExportStatistics {
    private String className;
    private int totalObjectsInDatabase;
    private int objectsExported;
    private int objectsRemaining;
    private double exportPercentage;
    private boolean isFullyExported;
    private boolean isLeafClass;
    private List<String> ancestorClasses;
}
```

### Phase 2: Integration with Existing Export Pipeline

#### 2.1 Modify DataExtractor to Use Tracker

```java
// In DataExtractor.reconstructCompleteObjects()
public static List<CompleteObject> reconstructCompleteObjects(
        String leafClassName,
        ExtObjectContainer extDb,
        InheritanceHierarchy hierarchy,
        ProgressCallback callback,
        InheritanceExportTracker exportTracker,  // NEW PARAMETER
        ui.assistant.AssistantSession session) throws Exception {
    
    // ... existing logic ...
    
    for (long objectID : objectIDs) {
        // Check if already exported
        if (exportTracker.isObjectExported(objectID)) {
            skipCount++;
            continue;
        }
        
        try {
            Object leafObject = extDb.getByID(objectID);
            if (leafObject != null) {
                CompleteObject completeObj = new CompleteObject(leafObject, leafClassName);
                mergeInheritanceFields(completeObj, leafObject, extDb, inheritanceChain, session);
                completeObjects.add(completeObj);
                
                // Mark this object and all its inheritance ancestors as exported
                InheritanceExportResult result = exportTracker.markLeafObjectExported(objectID, leafClassName);
                
                // Update progress tracking with inheritance awareness
                updateProgressWithInheritanceTracking(result, callback, session);
                
                successCount++;
            }
        } catch (Exception e) {
            // ... existing error handling ...
        }
    }
    
    // Log comprehensive export statistics
    logExportStatistics(exportTracker, leafClassName, callback);
}
```

#### 2.2 Update Migration Progress Tracking

```java
/**
 * Update all migration tracking systems with inheritance-aware data
 */
private static void updateProgressWithInheritanceTracking(
        InheritanceExportResult result, 
        ProgressCallback callback, 
        ui.assistant.AssistantSession session) {
    
    // Update per-class statistics for ALL affected classes
    for (String className : result.getAffectedClasses()) {
        // Update DODatabaseClass counts
        DODatabaseClass dbClass = findDatabaseClass(className);
        if (dbClass != null) {
            dbClass.increaseMigratedObjectCount(1);
        }
        
        // Update session tracking
        if (className.equals(result.getLeafClassName())) {
            session.trackDirectMigration(className, 1);
        } else {
            session.trackIndirectMigration(className, 1);
        }
        
        // Update MigrationClassResult
        MigrationClassResult classResult = session.getOrCreateClassResult(className, true);
        if (className.equals(result.getLeafClassName())) {
            classResult.incrementDirectlyMigrated(1);
        } else {
            classResult.incrementIndirectlyMigrated(1);
        }
    }
}
```

### Phase 3: Export Engine Integration

#### 3.1 Pre-Export Planning Phase

```java
/**
 * Before starting any export, analyze inheritance impact
 */
public class ExportPlanner {
    
    /**
     * Analyze what classes will be affected by exporting specific leaf classes
     */
    public ExportPlan createExportPlan(
            Set<String> targetLeafClasses, 
            InheritanceHierarchy hierarchy,
            ExtObjectContainer extDb) {
        
        ExportPlan plan = new ExportPlan();
        
        for (String leafClass : targetLeafClasses) {
            // Get all objects for this leaf class
            long[] objectIds = getObjectIdsForClass(leafClass, extDb);
            
            // For each object, determine inheritance impact
            List<String> inheritanceChain = hierarchy.getInheritanceChain(leafClass);
            
            plan.addLeafClassImpact(leafClass, objectIds.length, inheritanceChain);
        }
        
        return plan;
    }
}

/**
 * Export plan with inheritance impact analysis
 */
public class ExportPlan {
    private Map<String, Integer> totalExportsByClass;    // Class -> Total objects that will be marked as exported
    private Map<String, Integer> directExportsByClass;   // Class -> Objects directly exported from this class
    private Map<String, Integer> indirectExportsByClass; // Class -> Objects exported via inheritance
    private Set<String> allAffectedClasses;
    private long totalUniqueObjects;                     // Actual distinct objects being exported
    private long totalInheritanceMarks;                  // Total inheritance marks (will be > totalUniqueObjects)
    
    public boolean wouldAchieveCompleteExport();
    public Map<String, Double> getExportCoveragePerClass();
    public void printExportImpactSummary();
}
```

#### 3.2 Export Validation Phase

```java
/**
 * After export completion, validate inheritance tracking
 */
public class ExportValidator {
    
    /**
     * Verify that all inheritance relationships were properly tracked
     */
    public ValidationResult validateInheritanceExport(
            InheritanceExportTracker tracker,
            InheritanceHierarchy hierarchy,
            ExtObjectContainer extDb) {
        
        ValidationResult result = new ValidationResult();
        
        // Check 1: Verify all leaf objects are accounted for
        for (String leafClass : hierarchy.getLeafClasses()) {
            StoredClass sc = findStoredClass(leafClass, extDb);
            if (sc != null) {
                long[] allIds = sc.getIDs();
                ClassExportStatistics stats = tracker.getExportStatistics().get(leafClass);
                
                if (stats.getObjectsExported() != allIds.length) {
                    result.addError(leafClass, "Missing leaf objects: expected " + allIds.length + 
                                   ", exported " + stats.getObjectsExported());
                }
            }
        }
        
        // Check 2: Verify inheritance consistency
        for (String className : hierarchy.getAllClasses()) {
            List<String> children = hierarchy.getChildren(className);
            if (children != null && !children.isEmpty()) {
                // Parent class export count should >= sum of children counts
                validateParentChildExportConsistency(className, children, tracker, result);
            }
        }
        
        return result;
    }
}
```

### Phase 4: Implementation Checklist

#### 4.1 Core Implementation Tasks

- [ ] **Create InheritanceExportTracker class**
  - [ ] Object ID to class mapping logic
  - [ ] Export marking with inheritance propagation
  - [ ] Statistics calculation and reporting
  - [ ] Integration with existing InheritanceHierarchy

- [ ] **Modify DataExtractor**
  - [ ] Add InheritanceExportTracker parameter
  - [ ] Implement export checking before processing
  - [ ] Add inheritance-aware progress updates
  - [ ] Enhanced logging for inheritance tracking

- [ ] **Update Migration Tracking Systems**
  - [ ] Modify DODatabaseClass to handle inheritance counts
  - [ ] Update AssistantSession with direct/indirect tracking
  - [ ] Enhance MigrationClassResult for inheritance scenarios
  - [ ] Update DOEngineMonitoring for comprehensive tracking

- [ ] **Create Export Planning System**
  - [ ] ExportPlanner for pre-export analysis
  - [ ] ExportPlan for impact assessment
  - [ ] Integration with existing MigrationService

#### 4.2 Integration Points

- [ ] **Modify MigrationService.performInheritanceAwareFullDatabaseDump()**
  - [ ] Initialize InheritanceExportTracker
  - [ ] Pass tracker to DataExtractor calls
  - [ ] Generate final export report with inheritance statistics

- [ ] **Update exportModuleToExcel()**
  - [ ] Use inheritance tracker for module-specific exports
  - [ ] Ensure proper inheritance handling in module boundaries

- [ ] **Enhance Progress Reporting**
  - [ ] Update progress callbacks to show inheritance impact
  - [ ] Display both direct and indirect export counts
  - [ ] Show completion percentage accounting for inheritance

#### 4.3 Testing Strategy

- [ ] **Unit Tests**
  - [ ] InheritanceExportTracker functionality
  - [ ] Export result calculations
  - [ ] Statistics accuracy

- [ ] **Integration Tests**
  - [ ] Full export with inheritance tracking
  - [ ] Module export with inheritance
  - [ ] Edge cases (orphaned objects, complex hierarchies)

- [ ] **Validation Tests**
  - [ ] Compare old vs new export counts
  - [ ] Verify no objects are missed
  - [ ] Confirm inheritance relationships are properly tracked

## Benefits of This Approach

### 1. Complete Migration Guarantee
- **No missed objects**: Every object in inheritance hierarchy is accounted for
- **Accurate progress**: Real-time tracking of inheritance impact
- **Validation**: Post-export verification ensures completeness

### 2. Enhanced Analytics
- **Inheritance impact**: See how many ancestor objects are affected by leaf exports
- **Class coverage**: Understand export coverage per class in inheritance hierarchy
- **Direct vs Indirect**: Distinguish between objects exported directly vs via inheritance

### 3. Performance Optimization
- **Skip already exported**: Avoid re-processing objects already exported via inheritance
- **Batch tracking**: Efficient bulk updates for inheritance hierarchies
- **Memory efficient**: Smart data structures for large object sets

### 4. Backward Compatibility
- **Non-breaking**: Existing export logic continues to work
- **Optional enhancement**: Can be enabled/disabled per export operation
- **Gradual rollout**: Can be implemented incrementally

## Implementation Timeline

### Week 1: Core Infrastructure
- Create InheritanceExportTracker
- Define data structures and interfaces
- Basic unit tests

### Week 2: DataExtractor Integration
- Modify reconstruction logic
- Add inheritance-aware export marking
- Update progress tracking

### Week 3: Migration Service Integration
- Update MigrationService methods
- Add export planning capabilities
- Enhanced reporting

### Week 4: Testing & Validation
- Comprehensive testing
- Performance validation
- Documentation and examples

## Risk Mitigation

### Performance Concerns
- **Lazy loading**: Build inheritance mappings on-demand
- **Memory management**: Use efficient data structures for large datasets
- **Batch processing**: Process inheritance updates in batches

### Complexity Management
- **Modular design**: Keep tracker separate from existing logic
- **Clear interfaces**: Well-defined APIs for integration
- **Comprehensive logging**: Detailed debug information for troubleshooting

### Data Integrity
- **Validation hooks**: Multiple checkpoints to verify correctness
- **Rollback capability**: Ability to revert to simple export if needed
- **Audit trail**: Complete log of all inheritance marking decisions

---

This plan provides a comprehensive solution for handling DB4O's inheritance-based object storage while maintaining compatibility with your existing sophisticated migration infrastructure.
