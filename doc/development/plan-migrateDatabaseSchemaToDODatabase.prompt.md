# Plan: Migrate Database Schema from DOSchema* to DODatabase*

## TL;DR
Replace all database-derived DOSchema* objects with DODatabase* objects. DODatabaseLoader already builds DODatabase/DODatabaseClass/DODatabaseField with `schemaClass`/`schemaField` links back to the reference schema. The migration switches from DODatabaseReader → DODatabaseLoader and from reading objectIds on DOSchemaClass to reading them from DODatabaseClass.objects. For behavioral queries (isDescendantOf, isExported, pointsTo, etc.), database classes delegate to their linked schema counterpart — no reimplementation at the database level.

---

## Decisions

- **No reimplementation of schema behavior on DODatabase**. For `isDescendantOf`, `isIDEntite`, `isExported`, `pointsTo`, etc., always defer to `dbClass.schemaClass` / `dbField.schemaField`. The reference schema is the authority on structure and behavior.
- **Database viewer**: New `DatabaseStructurePanel` — SchemaEditorPanel stays reference-only.
- **Schema comparison**: New cross-type comparison logic (DOSchema vs DODatabase).
- **DODatabaseReader retirement**: Keep but mark @Deprecated until stabilization.
- **Deduplication**: Modify `DOObjectDeduplicator` to work on `DODatabase` (objectIds live in `DODatabaseClass.objects`).
- **enrichPointsToFromReferenceSchema**: Remove — `dbClass.schemaClass` provides direct access to reference schema's `pointsTo`.
- **reachedObjectIds**: Never populated — delete entirely. Export tracking uses `ExportStatistics.exportedObjectIdsSet`.
- **Missing classes warning**: When a database class has no matching reference schema class (`dbClass.schemaClass == null`), warn the user so the schema can be fixed. Silently skipping risks data loss.
- **Coexistence during development**: Both `context.database` and `context.databaseSchema` exist temporarily. Phase 4 removes the old path — by the end, only `DODatabase` remains.

---

## Current State
- **DODatabaseReader** reads DB4O → produces `DOSchema` with `DOSchemaClass[]` carrying `objectIds`, `uniqueObjectIds`, `reachedObjectIds`
- **DODatabaseLoader** (already exists) reads DB4O → produces `DODatabase` with `DODatabaseClass[]`, each linked to `DOSchemaClass schemaClass`
- Both schemas are `DOSchema` type — consumers can't distinguish reference from database
- `DODatabaseContext.databaseSchema` and `ExportRequest.databaseSchema` store database-derived DOSchema

## Target State
- **DODatabaseLoader** is sole database reader; DODatabaseReader is @Deprecated
- `DODatabaseContext.database` holds `DODatabase` (not `DOSchema`)
- `ExportRequest.database` holds `DODatabase`
- `DOSchemaClass` loses `objectIds`, `uniqueObjectIds`, `reachedObjectIds`
- All database-specific data accessed via `DODatabaseClass` / `DODatabaseClass.objects`
- All behavioral queries (type classification, export flags, pointsTo) accessed via `dbClass.schemaClass`
- `context.databaseSchema` is gone — no dual representation

---

## File-by-File Inventory

### REFERENCE SCHEMA ONLY (no changes needed)

| File | Justification |
|---|---|
| `schema/DOSchemaService.java` | Loads/stores reference-schema.xml only |
| `schema/DOReferenceSchemaReader.java` | Reads reference-schema.xml |
| `schema/processors/DOReferenceDetector.java` | Post-processes reference schema only |
| `application/ApplicationService.java` | Initializes reference schema at startup |
| `migration/xsd/XSD*.java` (4 files) | XSD generation from reference schema |
| `migration/recipes/FieldValueMapper.java` | Value mapping from reference schema |
| `migration/recipes/VirtualFieldQueryEngine.java` | Virtual field queries use reference schema |
| `migration/recipes/ExportCriteriaFilter.java` | Criteria filtering from reference schema |
| `migration/recipes/IDReferenceExporter.java` | ID reference export rules |
| `migration/recipes/IDReferenceDetector.java` | ID pattern detection from reference schema |
| `migration/recipes/IDClassResolver.java` | Entity type resolution |
| `migration/recipes/GenericObjectExporter.java` | Field export rules |
| `migration/FieldExporter.java` | All lookups use `operation.referenceSchema` |
| `migration/ObjectExporter.java` | Class lookup uses `request.referenceSchema` |
| `migration/SummaryGenerator.java` | Summary from reference schema |
| `migration/tasks/ModuleExporter.java` | Module tree from reference schema |
| `migration/tasks/NavTreeBuilder.java` | Navigation tree |
| `migration/tasks/ModulePathUtil.java` | Module path utility |
| `util/ValueUtil.java` | Skip evaluation |
| `util/TypeUtil.java` | Type checking |
| `util/SchemaUtil.java` | Skip options, shared fields |
| `util/CollectionTypeUtil.java` | Collection type detection |
| `util/ModuleUtil.java` | Module navigation |
| `models/schema/DOSchema*.java` | Model definitions (unchanged) |
| `models/schema/analysis/*.java` | Anomaly types (reference only) |
| `ui/panels/reference_schema_panels/**` (most) | Reference schema editor UI |
| `ui/common/FieldSelectorPanel.java` | Field selection from reference schema |
| `ui/common/renderers/SchemaTypeRenderer.java` | Type rendering |

### DATABASE SCHEMA → MUST MIGRATE TO DODatabase*

#### Phase 1: Core Database Infrastructure

**1.1 `database/DODatabaseContext.java`** — Change field type
- Current: `public DOSchema databaseSchema;`
- After: `public DODatabase database;`
- Also update `closeDatabase()` which nullifies `databaseSchema`

**1.2 `database/DODatabaseService.java`** — Switch to DODatabaseLoader
- Current: calls `DODatabaseReader.readDatabaseAsSchema()` → stores in `context.databaseSchema`
- After: calls `DODatabaseLoader.load(container, referenceSchema)` → stores in `context.database`
- Remove `enrichPointsToFromReferenceSchema()` — `dbClass.schemaClass` provides direct access to reference schema `pointsTo`

**1.3 `database/processors/DOObjectDeduplicator.java`** — Adapt for DODatabase
- Current: takes `DOSchema`, iterates `DOSchemaClass[]`, deduplicates `objectIds`/`uniqueObjectIds`
- After: takes `DODatabase`, iterates `DODatabaseClass[]`, deduplicates `DODatabaseClass.objects.objectIds` / `.uniqueObjectIds`
- Walk parent chain via `DODatabaseClassAttributes.parentClassName` + `DODatabase.findClassByName()`

**1.4 Mark @Deprecated**:
- `database/DODatabaseReader.java`
- `database/processors/DOClassConverter.java`
- `database/processors/DOClassesConverter.java`
- `database/processors/DOFieldConverter.java`
- `database/processors/DOFieldsConverter.java`

#### Phase 2: Export Engine Migration

**2.1 `migration/ExportRequest.java`** — Change field type
- Current: `public DOSchema databaseSchema;`
- After: `public DODatabase database;`

**2.2 `migration/tasks/ObjectExportLoop.java`** — Use DODatabaseClass
- Current: receives `DOSchemaClass dbSchemaClass`, reads `dbSchemaClass.objectIds`
- After: receives `DODatabaseClass dbClass`, reads `dbClass.objects.objectIds`, uses `dbClass.attributes.source`

**2.3 `migration/tasks/ExportModuleLoop.java`** — Locate DODatabaseClass for each module class
- Look up `DODatabaseClass` from `request.database.findClassByName(className)`
- Reference DOSchemaClass accessed via `dbClass.schemaClass` or direct reference schema lookup
- **WARNING**: When `dbClass` is null (class in reference schema but not in database), log warning — no objects to export, but not an error
- **WARNING**: When `dbClass.schemaClass` is null (class in database but not in reference schema), trigger user-visible warning about potential data loss — schema may need updating

**2.4 `migration/tasks/ExportSelectionAdvisor.java`** — Use DODatabase
- Current: stores `DOSchema databaseSchema`, calls `databaseSchema.findClassByName()`, reads `.objectIds`
- After: stores `DODatabase database`, calls `database.findClassByName()`, reads `.objects.objectIds`
- Reference schema usage (schemaReferences, sharedFields, type classification) stays on DOSchema

**2.5 `migration/tasks/ExportPreSelection.java`** — Pass DODatabase

**2.6 `migration/format/ExportCurrentState.java`** — Update request type reference

**2.7 `migration/format/XmlFormatHandler.java`** — collectUnreachedIds()
- Iterate `request.database.getClasses()`, read `dbClass.objects.uniqueObjectIds` / `.objectIds`
- Use `dbClass.schemaClass` to get `destinationName` for XML output

**2.8 `migration/format/HtmlFormatHandler.java`** — Verify/update for similar objectId access

**2.9 `migration/monitoring/ExportStatistics.java`** — Verify all callers pass reference schema classes

#### Phase 3: UI Migration

**3.1 `ui/main/MainWindow.java`** — Central database holder
- Change `private DOSchema currentDatabaseSchema` → `private DODatabase currentDatabase`
- Change `DatabaseSession.databaseSchema` → `DatabaseSession.database` (DODatabase)
- Pass DODatabase to new DatabaseStructurePanel instead of SchemaEditorPanel

**3.2 `ui/panels/reference_schema_panels/migration_structure_panel/ExportOptions.java`** — Build ExportRequest
- `request.database = dbContext.database;`

**3.3 `ui/panels/reference_schema_panels/migration_structure_panel/MigrationStructurePanelUtil.java`** — CRITICAL
- Remove the objectIds copy from database→reference schema (lines 67-74)
- When building ClassNode, look up DODatabaseClass from DODatabase and pass it alongside the reference DOSchemaClass
- Remove fallback to database schema for classes not in reference schema
- **ADD WARNING**: When DODatabaseClass exists but `dbClass.schemaClass == null`, display a warning to user (e.g. "Class X exists in database but has no schema definition — data may be lost during export"). This protects against data loss from schema/database discrepancies.

**3.4 `ui/panels/reference_schema_panels/migration_structure_panel/MigrationServiceCallback.java`** — Export trigger
- Pass DODatabase from context to export request

**3.5 `models/ui/ClassNode.java`** — Display counts from DODatabase
- Add optional `DODatabaseClass dbClass` field
- Use `dbClass.objects.uniqueObjectIds` for `getObjectCount()`
- Use `dbClass.objects.uniqueObjectIds` for `exportConfig.countMatchingObjects()`
- Continue using reference `DOSchemaClass.attributes.destinationName` for display label

**3.6 `ui/panels/database_panels/conformity_analysis_panel/SchemaComparison.java`** — New cross-type comparison
- Compare DOSchema (reference) vs DODatabase (database)
- Match classes by source name: `DOSchemaClass.attributes.source` ↔ `DODatabaseClass.attributes.source`
- Compare fields by source name: `type`, `isCollection`, `childrenType`

**3.7 `ui/panels/database_panels/conformity_analysis_panel/SchemaComparisonPanel.java`** — Updated comparison display
- Read object counts from DODatabaseClass.objects
- For field detail table: access `isExported` via `databaseField.schemaField.attributes.isExported` (not from DODatabaseFieldAttributes which lacks it)

**3.8 `ui/panels/database_panels/conformity_analysis_panel/SynchronizedTreePanel.java`** — May need DODatabaseClass for counts

**3.9 `ui/panels/database_panels/multi_database_comparison_panel/MultiDatabaseComparisonPanel.java`** — Use DODatabase
- Read `DODatabaseClass.objects.uniqueObjectIds` for counts
- For `isDescendantOf()` calls: defer to `dbClass.schemaClass.isDescendantOf()` — **do not reimplement**
- Handle null `schemaClass` gracefully (skip class if no schema link)

**3.10 `ui/panels/database_panels/database_export_panel/DatabaseExportPanel.java`** — Context access

**3.11 `ui/panels/database_panels/cost_panel/CostPanel.java`** — Use DODatabase

**3.12 `ui/panels/database_panels/database_export_panel/SeedQueryDialog.java`** — Verify schema access

**3.13 NEW: `ui/panels/database_panels/DatabaseStructurePanel.java`** — Create
- Read-only tree view of DODatabase structure
- Show DODatabaseClass nodes: source, parentClassName, instanceCount, field count, objectIds count
- Show DODatabaseField children: source, type, isArray, isCollection, childrenType
- Show schema link status: `dbClass.schemaClass != null` indicator per class

#### Phase 4: Cleanup

**4.1 `models/schema/DOSchemaClass.java`** — Remove database-specific fields
- Remove `public long[] objectIds;`
- Remove `public long[] uniqueObjectIds;`
- Remove `public long[] reachedObjectIds;`

**4.2 `models/schema/DOSchema.java`** — Remove DODatabaseSchema interface

**4.3 `util/ReferenceUtil.java`** — Update objectIds access to use DODatabase

**4.4 `util/DatabaseUtil.java`** — Verify (reference schema only — no change expected)

**4.5 `util/JsViewerHtmlGenerator.java`** — Verify (reference schema only — no change expected)

**4.6 `models/schema/comparison/ClassDifference.java`** — Update for cross-type comparison

**4.7 Remove `context.databaseSchema`** from DODatabaseContext — sole field is now `database`

**4.8 Remove `request.databaseSchema`** from ExportRequest — sole field is now `database`

**4.9 Remove old `@Deprecated` DOObjectDeduplicator method** (DOSchema-based overload)

---

## Steps

### Phase 1: Core Database Infrastructure (*all sequential*)

1. **Modify `DOObjectDeduplicator`** — Add new overload accepting `DODatabase`, working on `DODatabaseClass.objects.objectIds/.uniqueObjectIds`. Walk parent chain via `DODatabase.findClassByName(parentClassName)`. Keep old DOSchema method marked @Deprecated for coexistence.
   - File: `database/processors/DOObjectDeduplicator.java`

2. **Update `DODatabaseContext`** — Add `public DODatabase database` field. Keep `databaseSchema` temporarily. Update `closeDatabase()` to null both.
   - File: `database/DODatabaseContext.java`

3. **Update `DODatabaseService.openDatabase()`** — Call `DODatabaseLoader.load(container, referenceSchema)` → store in `context.database` → call deduplicator on DODatabase. Also still call DODatabaseReader to populate `context.databaseSchema` (coexistence). Remove `enrichPointsToFromReferenceSchema()` (defer to `dbClass.schemaClass` for pointsTo).
   - File: `database/DODatabaseService.java`

4. **Mark @Deprecated**: `DODatabaseReader`, `DOClassConverter`, `DOClassesConverter`, `DOFieldConverter`, `DOFieldsConverter`

5. **Build + verify**: `mvn clean compile`. Run `./run-ui.sh local/46058/BackupAuto.premligne.bak.nozip --repeat-export`. Confirm `context.database` populated and database opens without errors.

### Phase 2: Export Engine Migration (*depends on Phase 1*)

6. **Update `ExportRequest`** — Add `public DODatabase database` field. Keep `databaseSchema` temporarily.
   - File: `migration/ExportRequest.java`

7. **Update `ExportSelectionAdvisor`** — Accept `DODatabase` instead of `DOSchema databaseSchema`. Use `database.findClassByName()` → `.objects.objectIds`. Reference schema usage unchanged.
   - File: `migration/tasks/ExportSelectionAdvisor.java`

8. **Update `ExportPreSelection`** — Pass `request.database` to ExportSelectionAdvisor.
   - File: `migration/tasks/ExportPreSelection.java`

9. **Update `ObjectExportLoop`** — Change `run(DOSchemaClass)` → `run(DODatabaseClass)`. Read `dbClass.objects.objectIds`, use `dbClass.attributes.source` for preselection key.
   - File: `migration/tasks/ObjectExportLoop.java`

10. **Update `ExportModuleLoop`** — Resolve `DODatabaseClass` from `request.database.findClassByName(className)`. Pass to `ObjectExportLoop.run()`. **Add warning when `dbClass.schemaClass == null`** (database class not in reference schema → potential data loss). *Depends on step 9.*
    - File: `migration/tasks/ExportModuleLoop.java`

11. **Update `XmlFormatHandler.collectUnreachedIds()`** — Iterate `request.database.getClasses()`. Read `.objects.uniqueObjectIds/.objectIds`. Use `dbClass.schemaClass` for destinationName.
    - File: `migration/format/XmlFormatHandler.java`

12. **Verify `HtmlFormatHandler`** — Confirm reference-schema-only access. No changes expected.
    - File: `migration/format/HtmlFormatHandler.java`

13. **Verify `ExportStatistics`, `FieldExporter`, `ObjectExporter`** — Confirm all callers pass reference schema classes. No changes expected.

14. **Build + verify**: `mvn clean compile`. Run `./run-ui.sh local/46058/BackupAuto.premligne.bak.nozip --repeat-export`. Confirm identical XML output.

### Phase 3: UI Migration (*depends on Phase 2*)

**Batch A: Core infrastructure (sequential)**

15. **Update `MainWindow`** — `DODatabase currentDatabase`, `DatabaseSession.database`, new accessors. Pass DODatabase to DatabaseStructurePanel. Update `openDatabaseFile()`.
    - File: `ui/main/MainWindow.java`

16. **Update `ExportOptions.toExportRequest()`** — Set `request.database = dbContext.database`. *Depends on 15.*
    - File: `ExportOptions.java`

17. **Update `MigrationServiceCallback`** + **`DatabaseExportPanel`** — Access `dbContext.database`.
    - Files: `MigrationServiceCallback.java`, `DatabaseExportPanel.java`

**Batch B: Count display (depends on 15)**

18. **Update `ClassNode`** — Add optional `DODatabaseClass dbClass` field. Use `dbClass.objects.uniqueObjectIds` for counts. Reference DOSchemaClass for labels.
    - File: `models/ui/ClassNode.java`

19. **Rewrite `MigrationStructurePanelUtil`** — Remove objectIds copy (lines 67-74). Look up DODatabaseClass from DODatabase when building ClassNode. Remove fallback to database schema. **Add user-visible warning when DODatabaseClass has no schema link** (`dbClass.schemaClass == null`) to flag potential data loss from schema/database discrepancies.
    - File: `MigrationStructurePanelUtil.java`

**Batch C: Comparison panels (depends on 15)**

20. **Rewrite `SchemaComparison`** — Cross-type comparison: `compare(DOSchema, DODatabase)`. Match by source name. Compare `type`, `isCollection`, `childrenType`.
    - Files: `SchemaComparison.java`, `ClassDifference.java`

21. **Update `SchemaComparisonPanel`** + `SynchronizedTreePanel` — Read counts from DODatabaseClass.objects. For field `isExported` display: use `databaseField.schemaField.attributes.isExported` (delegate to schema, no reimplementation).
    - Files: `SchemaComparisonPanel.java`, `SynchronizedTreePanel.java`

22. **Update `MultiDatabaseComparisonPanel`** — Store DODatabase per database. Use `dbClass.objects.uniqueObjectIds` for counts. For `isDescendantOf()`: delegate to `dbClass.schemaClass.isDescendantOf()` — skip class if `schemaClass` is null.
    - File: `MultiDatabaseComparisonPanel.java`

**Batch D: Other panels (parallel with B and C)**

23. **Update `CostPanel`** — Receive DODatabase. Use DODatabaseClassAttributes.instanceCount.
    - File: `CostPanel.java`

24. **Update `SeedQueryDialog`** — Verify schema access, update if needed.
    - File: `SeedQueryDialog.java`

25. **Create `DatabaseStructurePanel`** — Read-only DODatabase tree view. Show schema link status per class/field. Replaces SchemaEditorPanel for database viewing.
    - New file: `ui/panels/database_panels/DatabaseStructurePanel.java`

26. **Update `ReferenceUtil`** — ObjectIds access (line ~112) to use DODatabase parameter.
    - File: `util/ReferenceUtil.java`

27. **Build + verify**: `mvn clean compile`. Run `./run-ui.sh local/46058/BackupAuto.premligne.bak.nozip --repeat-export`. Verify all panels.

### Phase 4: Cleanup (*depends on Phase 3*)

28. **Remove `DOSchema databaseSchema`** from `DODatabaseContext` and `ExportRequest`. Remove DODatabaseReader invocation from DODatabaseService.

29. **Remove database fields from `DOSchemaClass`** — Delete `objectIds`, `uniqueObjectIds`, `reachedObjectIds`.
    - File: `models/schema/DOSchemaClass.java`

30. **Clean up `DOSchema`** — Remove `implements DODatabaseSchema`. Evaluate if `DOReferenceSchema` still useful.
    - Files: `DOSchema.java`, `DOReferenceSchema.java`, `DODatabaseSchema.java`

31. **Remove old `@Deprecated` DOObjectDeduplicator method**.

32. **Final build + full integration test**: `mvn clean compile`. Run `./run-ui.sh local/46058/BackupAuto.premligne.bak.nozip --repeat-export`. Verify identical export. Confirm no dual representation remains.

---

## Relevant Files

### Must Modify (28 files)
- `src/main/java/migration4o/database/DODatabaseContext.java` — add `DODatabase database`, remove `DOSchema databaseSchema`
- `src/main/java/migration4o/database/DODatabaseService.java` — switch to DODatabaseLoader, remove enrichPointsTo
- `src/main/java/migration4o/database/processors/DOObjectDeduplicator.java` — DODatabase overload
- `src/main/java/migration4o/migration/ExportRequest.java` — `DODatabase database` field
- `src/main/java/migration4o/migration/tasks/ExportSelectionAdvisor.java` — use DODatabase for objectIds
- `src/main/java/migration4o/migration/tasks/ExportPreSelection.java` — pass DODatabase
- `src/main/java/migration4o/migration/tasks/ObjectExportLoop.java` — DODatabaseClass parameter
- `src/main/java/migration4o/migration/tasks/ExportModuleLoop.java` — resolve DODatabaseClass, add missing-schema warning
- `src/main/java/migration4o/migration/format/XmlFormatHandler.java` — collectUnreachedIds with DODatabase
- `src/main/java/migration4o/migration/format/HtmlFormatHandler.java` — verify (likely no change)
- `src/main/java/migration4o/migration/format/ExportCurrentState.java` — request type update
- `src/main/java/migration4o/migration/monitoring/ExportStatistics.java` — verify (likely no change)
- `src/main/java/migration4o/ui/main/MainWindow.java` — DODatabase field, session, accessors
- `src/main/java/migration4o/ui/panels/reference_schema_panels/migration_structure_panel/ExportOptions.java` — set request.database
- `src/main/java/migration4o/ui/panels/reference_schema_panels/migration_structure_panel/MigrationServiceCallback.java` — export flow
- `src/main/java/migration4o/ui/panels/reference_schema_panels/migration_structure_panel/MigrationStructurePanelUtil.java` — remove objectIds copy, add missing-schema warning
- `src/main/java/migration4o/ui/panels/database_panels/database_export_panel/DatabaseExportPanel.java` — context access
- `src/main/java/migration4o/ui/panels/database_panels/conformity_analysis_panel/SchemaComparison.java` — cross-type comparison
- `src/main/java/migration4o/ui/panels/database_panels/conformity_analysis_panel/SchemaComparisonPanel.java` — DODatabase counts, isExported via schemaField
- `src/main/java/migration4o/ui/panels/database_panels/conformity_analysis_panel/SynchronizedTreePanel.java` — verify
- `src/main/java/migration4o/ui/panels/database_panels/multi_database_comparison_panel/MultiDatabaseComparisonPanel.java` — DODatabase, delegate isDescendantOf to schemaClass
- `src/main/java/migration4o/ui/panels/database_panels/cost_panel/CostPanel.java` — DODatabase
- `src/main/java/migration4o/models/ui/ClassNode.java` — DODatabaseClass for counts
- `src/main/java/migration4o/models/schema/DOSchemaClass.java` — remove objectIds fields
- `src/main/java/migration4o/models/schema/DOSchema.java` — remove DODatabaseSchema interface
- `src/main/java/migration4o/models/schema/comparison/ClassDifference.java` — cross-type comparison
- `src/main/java/migration4o/util/ReferenceUtil.java` — objectIds access update

### Must Create (1 file)
- `src/main/java/migration4o/ui/panels/database_panels/DatabaseStructurePanel.java` — new database viewer

### Mark @Deprecated (5 files)
- `src/main/java/migration4o/database/DODatabaseReader.java`
- `src/main/java/migration4o/database/processors/DOClassConverter.java`
- `src/main/java/migration4o/database/processors/DOClassesConverter.java`
- `src/main/java/migration4o/database/processors/DOFieldConverter.java`
- `src/main/java/migration4o/database/processors/DOFieldsConverter.java`

### Key Reference (unchanged, use as implementation templates)
- `src/main/java/migration4o/database/DODatabaseLoader.java` — pattern for DODatabase construction + schema linking
- `src/main/java/migration4o/database/DODatabase.java` — `findClassByName()`, `classes[]`
- `src/main/java/migration4o/database/DODatabaseClass.java` — `schemaClass`, `objects`, `attributes`, `setFields()`
- `src/main/java/migration4o/database/DODatabaseField.java` — `schemaField`, `attributes`
- `src/main/java/migration4o/database/DODatabaseClassObjects.java` — `objectIds`, `uniqueObjectIds`
- `src/main/java/migration4o/database/DODatabaseClassAttributes.java` — `source`, `parentClassName`, `instanceCount`

---

## Verification

1. `mvn clean compile` — must pass after each phase
2. Run `./run-ui.sh local/46058/BackupAuto.premligne.bak.nozip --repeat-export` — full integration test:
   - Database opens without errors (Phase 1)
   - Export produces identical XML output (Phase 2 — compare output files)
   - Missing-schema warnings appear for unlinked database classes (Phase 2-3)
   - Database structure panel displays correctly, shows schema link status (Phase 3)
   - Schema comparison panel works (Phase 3)
   - Cost panel shows correct instance counts (Phase 3)
   - MigrationStructurePanel tree shows correct object counts (Phase 3)
   - Multi-database comparison works with isDescendantOf delegating to schemaClass (Phase 3)
3. Verify DOSchemaClass no longer has objectIds fields (Phase 4)
4. Verify `context.databaseSchema` no longer exists — only `context.database` (Phase 4)

---

## Scope Boundaries
- **Included**: All database-derived DOSchema* → DODatabase* migration; new DatabaseStructurePanel; deduplicator adaptation; cross-type schema comparison; missing-schema user warnings for data loss prevention
- **Excluded**: Changes to reference schema loading (DOReferenceSchemaReader); reference schema XML format; DOSchemaModule; XSD generation; export engine restructuring beyond type substitution
- **NOT changing**: Reference-schema-only code paths; DB4O container management; DODatabaseLoader itself; DODatabaseMonitor callbacks
- **Principle**: No reimplementation of schema logic on DODatabase classes — always delegate to linked `schemaClass`/`schemaField`
