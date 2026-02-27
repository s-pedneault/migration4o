# Reachability Tracking

How the export engine records which DB4O objects have been reached (visited) during export, and how the UI computes unreached counts.

## Core Concept

An object is "reached" when the export engine encounters it — whether or not it ends up in the XML output. An object can be reached but skipped (filtered, empty, duplicate). Unreached objects are those the engine never visited during the entire export.

## Data Flow Overview

```
Export Engine                    Statistics Layer              UI Layer
─────────────                    ────────────────              ────────
ObjectExporter                   ExportStatistics              MigrationServiceCallback
FieldExporter          ──────►   .exportedObjectIdsSet    ──►  .handleExportCompleted()
ExportCriteriaFilter             Map<className, Set<Long>>         │
                                        │                          ▼
                                        │ .setExportInfo()    MigrationStructurePanel
                                        ▼                     .onExportCompleted()
                                 .exportedObjectIds                │
                                 Map<className, List<Long>>        ▼
                                                              MainWindow
                                                              .notifyExportCompleted()
                                                                   │
                                                                   ▼
                                                              MigrationCoveragePanel
                                                              .updateExportedCounts()
                                                                   │
                                                                   ▼
                                                              ObjectExportTrackingIndex
                                                              .markReachedAll()
                                                                   │
                                                                   ▼
                                                              DOSchemaClass
                                                              .reachedObjectIds
```

## Recording Layer: ExportStatistics

**Class**: `migration4o.migration.monitoring.ExportStatistics`

Two methods record reach into the same map (`exportedObjectIdsSet`):

| Method | What it records | When used |
|--------|----------------|-----------|
| `recordClassExport(DOSchemaClass, long)` | Adds to `exportedObjectIdsSet` + increments `exportedClassCounts` + notifies monitor | Object successfully exported with XML output |
| `recordReachedOnly(String/DOSchemaClass, long)` | Adds to `exportedObjectIdsSet` only | Object reached but not counted as an export (skipped, filtered, IDEntite wrapper, collection wrapper) |

Both methods add to the same `exportedObjectIdsSet: Map<String, Set<Long>>`, keyed by source class name. The `Set<Long>` deduplicates naturally.

## Who Calls recordClassExport

One call site:

| Location | Context |
|----------|---------|
| `ObjectExporter.exportObjectRecursively()` line 162 | After successfully processing an object (with or without XML output), when `schemaClass != null` |

If `schemaClass == null` (object has no reference schema entry), falls back to `recordReachedOnly(className, objectId)` at line 164.

## Who Calls recordReachedOnly

All call sites and their purpose:

### ObjectExporter

| Line | Context |
|------|---------|
| 164 | Object exported but has no DOSchemaClass in reference schema |

### FieldExporter.exportAllFields()

| Line | Context |
|------|---------|
| — | Collection wrappers via `markCollectionWrapperReached()` (see below) |

### FieldExporter.exportFieldValue() — the "regular field" path

| Line | Context |
|------|---------|
| ~445 | Persistent object reference skipped by `shouldSkipField()` — wrapper still reached |
| ~460 | IDEntite reference skipped by `shouldSkipMinusOne()` + `skipWhen` contains MINUS_ONE |
| ~478 | Non-embedded IDEntite — exported as scalar mID, wrapper object marked reached but not traversed |
| ~508 | Object reference skipped because target has 0 exportable fields |
| ~921 | IDEntite encountered inside `exportFieldValue()` — IDEntite wrapper marked reached before resolving to target entity |

### FieldExporter.markCollectionWrapperReached()

| Line | Context |
|------|---------|
| ~1030 | Collection wrapper object (e.g., VectChampPerso GenericObject) marked reached; its *contents* are exported separately as collection items |

Called from `exportAllFields()` before entering:
- `exportSchemaCollectionField()` (schema-flagged collections — step 5d)
- `exportCollectionField()` (Java Collection instances — step 5e)
- `exportSchemaCollectionField()` via ancestry safety net (step 5f)

### FieldExporter.recordRelationshipSkippedIfPersistent()

| Line | Context |
|------|---------|
| ~1010 | Helper that marks any persistent child object as reached when its relationship is skipped |

### ExportCriteriaFilter.shouldExport()

| Line | Context |
|------|---------|
| ~55 | Root object filtered out by export criteria — still marked as reached |

## What Does NOT Record Reach

### IDReferenceExporter.exportAsIDReference()

- Creates a **synthetic** ID wrapper (e.g., `<IDCompartiment><mID>12345</mID></IDCompartiment>`)
- Does NOT call `recordReachedOnly` for the synthetic wrapper (it's not a real DB4O object)
- DOES call `objectExporter.exportObjectRecursively()` on the target entity, which records reach via `recordClassExport` inside ObjectExporter

### IDEntiteResolver

- Does NOT directly record reach
- The IDEntite wrapper reach is recorded by `FieldExporter.exportFieldValue()` at line ~921 *before* calling the resolver
- The resolved target entity reach is recorded by `ObjectExporter.exportObjectRecursively()` when the resolver's callback invokes it

### Virtual field query results

- Results from `executeVirtualFieldQuery()` are passed to `exportCollectionLikeField()` which calls either `exportFieldValue()` or `IDReferenceExporter.exportAsIDReference()` per item
- Each item records reach through those respective paths
- The virtual field itself has no wrapper object to mark as reached

## Aggregation: setExportInfo

**Class**: `ExportStatistics.setExportInfo()`

Copies `exportedObjectIdsSet` (HashMap of Sets) into `exportedObjectIds` (HashMap of Lists) for downstream consumption. Called at the end of each module's export.

## Propagation Chain

After export completes (SwingWorker.done()):

1. **`MigrationServiceCallback.handleExportCompleted(result)`**
   - Calls `MainWindow.showMigrationResults(result)` — shows the results tab
   - Invokes the `resultCallback.onExportCompleted(result)`

2. **`MigrationStructurePanel.onExportCompleted(result)`** (the callback)
   - Calls `MainWindow.notifyExportCompleted(result)`

3. **`MainWindow.notifyExportCompleted(ExportStatistics)`**
   - Extracts `result.exportedClassCounts` and `result.exportedObjectIds`
   - Calls `MigrationCoveragePanel.updateExportedCounts()`

4. **`MigrationCoveragePanel.updateExportedCounts()`**
   - Flattens ALL object IDs from ALL classes into a single `Set<Long>`
   - Calls `trackingIndex.markReachedAll(allExportedIds)`

## Tracking Index: ObjectExportTrackingIndex

**Class**: `migration4o.database.reach.ObjectExportTrackingIndex`

Built from the **database schema** (not reference schema). Indexes all known object IDs.

### Key data structures

| Field | Type | Purpose |
|-------|------|---------|
| `classToAllIds` | `Map<String, Set<Long>>` | All object IDs per class (including inherited) |
| `classToUniqueIds` | `Map<String, Set<Long>>` | Leaf-only IDs per class (from `uniqueObjectIds`) |
| `idToClasses` | `Map<Long, Set<String>>` | Object ID → all classes in hierarchy |
| `idToLeafClass` | `Map<Long, String>` | Object ID → most-specific (leaf) class |
| `reachedIds` | `Set<Long>` | All reached object IDs (flat) |
| `reachedIdsByClass` | `Map<String, Set<Long>>` | Reached IDs grouped by class |

### markReachedAll(Collection<Long>)

For each object ID:
1. Add to `reachedIds` (global set)
2. `resolveClassesForObjectId(objectId)` — walks `parentClassName` chain from leaf to root
3. Add ID to `reachedIdsByClass` for every class in the hierarchy
4. After all IDs processed: `syncSchemaReachedArrays()` — copies `reachedIdsByClass` into `DOSchemaClass.reachedObjectIds` long arrays

### resolveClassesForObjectId(long)

1. Check `idToClasses` map (built during `rebuild()`)
2. If not found, look up `idToLeafClass` and walk `parentClassName` chain upward
3. Returns set of all class names in hierarchy

### getUnreachedByLeafClass()

For each entry in `idToLeafClass`:
- If object ID is NOT in `reachedIds` → add to unreached list for that leaf class

This is what the "Unreached Explorer" UI displays.

## Reset Before Export

**Must happen before every export** to prevent accumulation from prior runs.

1. `MigrationServiceCallback.exportModulesAsync()` calls `resetReachedValuesInCoveragePanel()`
2. → `MainWindow.resetCoverageReachedValues()`
3. → `MigrationCoveragePanel.resetReachedValues()`
4. → `ObjectExportTrackingIndex.resetReached()`
   - Clears `reachedIds` and `reachedIdsByClass`
   - Calls `syncSchemaReachedArrays()` (sets all `DOSchemaClass.reachedObjectIds` to null)

## Key Observation: Class Name Resolution

The `exportedObjectIdsSet` in ExportStatistics is keyed by **source class name** (e.g., `gest.champPerso.ChampPerso`). But `markReachedAll()` in the tracking index receives a **flat set of object IDs** (class names are discarded during the flattening in `updateExportedCounts`). The tracking index resolves each ID back to its class hierarchy using `idToClasses` / `idToLeafClass`.

This means: if an object ID exists in `exportedObjectIdsSet` under any class name, it will be marked as reached regardless. The class-name keying in ExportStatistics is for export counts; reachability depends only on the object ID being present.

## Situations Where Objects Remain Unreached

An object stays unreached if the export engine never encounters it via any of these paths:

1. **Not a root object** of any exported class
2. **Not referenced** by any field of any exported object
3. **Not matched** by any virtual field criteria query
4. **Parent not exported** — if the parent object was itself unreached, its children are never visited
5. **Field disabled** — `isExported=false` in schema prevents field traversal; the direct field value (if persistent) IS marked reached via `recordRelationshipSkippedIfPersistent()`, but its nested children are never visited
6. **Collection field disabled** — if a collection field has `isExported=false`, the wrapper object is marked reached, but individual collection items are never extracted or visited

Note: Objects that ARE reached but skipped (duplicate, filtered, empty) are still marked in `exportedObjectIdsSet` and therefore count as reached.

## Safety Nets for Reach Recording on Errors

Three safety nets ensure objects are marked reached even when errors occur during export:

1. **ObjectExporter catch block**: If `exportObjectRecursively()` throws after activation, the object is still marked via `recordReachedOnly(className, objectId)` in the catch handler
2. **FieldExporter per-field catch block**: If a field's export throws in `exportAllFields()`, the field value is checked for persistence and marked reached if it has a valid DB4O ID
3. **Collection item catch block**: If an individual item in `exportCollectionLikeField()` throws during export, the item is marked reached if persistent, and processing continues with the remaining items
