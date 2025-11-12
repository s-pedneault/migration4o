# HTML Report Restructuring Plan

## Current Report Issues

The existing reachability report shows:
- Module-based navigation
- Schema class structures
- Field drill-down
- Database statistics (instance counts)

**Problems:**
1. Doesn't show REACHABILITY status (reached vs unreached)
2. No distinction between objects that ARE reachable vs those that AREN'T
3. Shows all objects without reachability context
4. Doesn't explain DB4O's exploded storage
5. No diagnostics about unique objects vs duplicates

---

## New Report Structure

### Top Section: Executive Summary

```
┌─────────────────────────────────────────────────┐
│  🔍 Database Reachability Analysis              │
│                                                 │
│  Total Unique Objects: 2,338,163                │
│  ✅ Reached Objects: 2,078,041                  │
│  ❌ Unreached Objects: 260,122                  │
│                                                 │
│  Total Database Entries: 6,262,180              │
│  Storage Inflation Factor: 2.67x                │
│                                                 │
│  ℹ️  Note: DB4O stores objects in multiple      │
│     inheritance tables (exploded storage)       │
└─────────────────────────────────────────────────┘
```

### Section 1: Reached Objects (Module Organization)

**Purpose:** Show objects that WILL be migrated, organized by module

```
📦 Modules (Reachable Objects Only)

├─ 📁 Module: PremiereLigne
│  ├─ 📄 Class: Dossier
│  │  └─ ✅ Reached: 12,543 objects
│  │  ├─ 🔗 Field: mEmploye → Employe
│  │  ├─ 🔗 Field: mNumero → String
│  │  └─ 📦 Collection: mDocuments (Vector<Document>)
│  │     └─ Contains: 45,231 Document objects
│  │
│  └─ 📄 Class: Document
│     └─ ✅ Reached: 45,231 objects
│     ├─ 🔗 Field: mTitre → String
│     └─ 🔗 Field: mDateCreation → Date
│
├─ 📁 Module: Gestion
│  └─ ... (similar structure)
```

**Features:**
- ✅ Only shows objects with `isReachable() == true`
- ✅ Organized by module structure (as per recipe)
- ✅ Shows exact counts of reached objects
- ✅ Click-to-expand to see field details
- ✅ Shows reference chains

### Section 2: Unreached Objects (By Class)

**Purpose:** Show objects that WILL NOT be migrated, grouped by reason

```
⚠️  Unreached Objects (Will Not Be Migrated)

🔍 Total Unreached: 260,122 unique objects

📊 By Class:

├─ gest.employe.EmployeArchive
│  ❌ Unreached: 65,432 objects
│  ❓ Possible Reason: Orphaned - no references from active modules
│  🔎 Show Details ▼
│
├─ gest.ancien.DossierAncien
│  ❌ Unreached: 42,891 objects
│  ❓ Possible Reason: Legacy data - not in active schema
│  🔎 Show Details ▼
│
└─ ... (more classes)

📋 Reasons for Unreachability:
  • Orphaned: Not referenced from any module root
  • Legacy: Class not in active schema
  • Intermediate: Stored only in non-leaf class tables
  • Isolated: Part of disconnected object graph
```

**Features:**
- ❌ Only shows objects with `isReachable() == false`
- 📊 Grouped by class
- 🔍 Shows unique count (not duplicates)
- ❓ Explains why unreachable
- 🔎 Can drill down to see object IDs

### Section 3: Database Diagnostics

**Purpose:** Explain DB4O's storage and help understand the numbers

```
📊 Database Storage Diagnostics

DB4O "Exploded" Storage Model:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
In DB4O, each object is stored in MULTIPLE tables:
  Example: LeafClass extends MiddleClass extends BaseClass
  
  Object #12345 appears in:
    • LeafClass table      ✓
    • MiddleClass table    ✓  (duplicate)
    • BaseClass table      ✓  (duplicate)
  
  Result: 1 object = 3 database entries

Your Database:
  • Total entries: 6,262,180
  • Unique objects: 2,338,163
  • Storage inflation: 2.67x

Reachability Overlap:
  • Objects marked as BOTH reached & unreached: 1,966,804
  • Why? Same object in leaf table (reached) AND parent table (unreached)
  • This is EXPECTED and correct!

Top Classes with Duplicates:
  1. gest.gen.Entite: 1,993,720 entries (inheritance base)
  2. gest.gen.IDEntite: 1,090,765 entries (inheritance base)
  3. ... (more classes)
```

### Section 4: Reachability Algorithm

**Purpose:** Explain HOW objects are determined to be reached

```
🔄 Reachability Algorithm

How We Determine Reachability:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Step 1: Start with Leaf Classes
  • Identify all "end" classes (no subclasses)
  • These are the entry points for traversal

Step 2: Process Each Leaf Object
  • Load object from database
  • Mark as REACHED for ALL classes in inheritance chain
  • Process all fields recursively

Step 3: Recursive Field Processing
  • For direct references: Follow and mark as reached
  • For collections: Mark each contained object as reached
  • Continue recursively until all references explored

Step 4: Result
  • Reached: Any object encountered during traversal
  • Unreached: Objects never encountered

This is EXACT tracking - not statistical estimation!
```

---

## Implementation Plan

### Phase 1: Update Data Collection ✓ DONE
- ✅ `DOObjectResolverImpl` now tracks exact reachability
- ✅ `DOObjectReachabilityTracker` provides exact data
- ✅ Each `DODatabaseObject` has `isReachable()` status

### Phase 2: Create New Report Sections

#### 2.1: Update ReachabilityReportGenerator
```java
private void generateCompleteReport() {
    writeHeader();
    writeExecutiveSummary();        // NEW
    writeReachedObjectsSection();   // UPDATED
    writeUnreachedObjectsSection(); // NEW
    writeDiagnosticsSection();      // NEW
    writeAlgorithmSection();        // NEW
    writeStyles();
    writeJavaScript();
}
```

#### 2.2: Data Structures Needed
```java
// For Section 1: Reached objects by module
class ReachedModuleData {
    String moduleName;
    Map<String, ReachedClassData> classes;
}

class ReachedClassData {
    String className;
    long reachedObjectCount;      // EXACT count, not percentage
    List<FieldData> fields;
}

// For Section 2: Unreached objects by class
class UnreachedClassData {
    String className;
    long unreachedObjectCount;    // EXACT count, not percentage
    String reason;
    List<Long> sampleObjectIds;   // First 100 for drill-down
}

// For Section 3: Diagnostics
class StorageDiagnostics {
    long totalDatabaseEntries;          // Total entries in all tables
    long uniqueObjectCount;             // Actual unique objects
    long reachedObjectCount;            // Objects marked as reached
    long unreachedObjectCount;          // Objects marked as unreached
    long bothReachedAndUnreachedCount;  // Overlap due to inheritance
    double storageInflationFactor;      // totalEntries / uniqueObjects
    Map<String, Long> topDuplicateClasses;  // Class → entry count
}
```

### Phase 3: Update JavaScript for Interactivity

#### Features Needed:
1. **Module Drill-Down** - Click to expand reached objects
2. **Unreached Drill-Down** - Click to see unreached object IDs
3. **Filters:**
   - Show only reached
   - Show only unreached
   - Show by module
   - Show by class
4. **Search:**
   - Search by class name
   - Search by object ID
5. **Export:**
   - Export unreached object IDs as CSV
   - Export reachability summary

### Phase 4: Styling Updates

#### Visual Design:
- ✅ Green indicators for reached objects
- ❌ Red indicators for unreached objects  
- ℹ️  Blue info boxes for diagnostics
- 📊 Charts/graphs for statistics
- 🔍 Expandable sections
- 💡 Tooltips explaining concepts

---

## Benefits of New Structure

### 1. Clear Separation
- Reached objects: "These WILL migrate" ✅
- Unreached objects: "These WILL NOT migrate" ❌
- No confusion!

### 2. Actionable Information
- See exactly which objects are at risk
- Understand WHY they're unreachable
- Can drill down to investigate specific objects

### 3. Educational
- Explains DB4O's storage model
- Shows how reachability works
- Helps validate the algorithm

### 4. Diagnostic Tool
- Can verify algorithm correctness
- Can identify data issues
- Can investigate specific objects

---

## Next Steps

1. **Implement Executive Summary Section**
   - Show total unique object count (EXACT number)
   - Show reached object count (EXACT number)
   - Show unreached object count (EXACT number)
   - Show total database entries
   - Show storage inflation factor
   - NO PERCENTAGES - only exact counts

2. **Update Reached Objects Section**
   - Filter to only show `isReachable() == true`
   - Keep module organization
   - Show EXACT object counts per class
   - NO PERCENTAGES

3. **Create Unreached Objects Section**
   - Group by class
   - Show EXACT unreached object count per class
   - Show reasons
   - Allow drill-down
   - NO PERCENTAGES

4. **Add Diagnostics Section**
   - Show total entries (EXACT)
   - Show unique objects (EXACT)
   - Show overlap count (EXACT)
   - List top duplicate classes with counts
   - NO PERCENTAGES - only exact numbers

5. **Update Styling**
   - Green/red indicators
   - Better visual hierarchy
   - Clearer sections
   - Emphasize EXACT counts

Should we start with the executive summary and work our way down?
