# Data Export

Decision points, criteria, and processing steps when migrating data from a DB4O database to structured output (XML/JSON/Excel).

## Export Orchestration

### Entry Point
- `MigrationExportService.exportModules()` coordinates the full export pipeline
- Creates `ExportEngine` with both schemas (reference + database) and the database path
- Initializes shared tracking (`ExportEngine.initializeSharedTracking()`) so all modules share:
  - `exportedObjectIds` — global deduplication set
  - `sharedXSDBuilder` — single comprehensive XSD
  - `exportedXMLFiles` — list of generated files

### Module Iteration
- For each `MigrationModule`, call `ExportEngine.exportModuleStructured()`
- Modules are hierarchical (parent/child) → `ExportEngine.exportModuleRecursive()` walks tree
- Each module creates a directory; each class within it gets its own output file

### Per-Class Export
- `ExportEngine.exportClassToFile()` handles one class:
  1. Look up `DOSchemaClass` in both reference and database schemas
  2. Skip silently if not found in either schema
  3. Create `ObjectExporter` + `FieldExporter` for this file
  4. Write file header (root structure, metadata)
  5. Iterate `dbSchemaClass.objectIds`, call `ObjectExporter.exportObjectRecursively()` for each
  6. Close file

## Decision Point 1: Should This Object Be Exported?

### Object Limit
- `ExportOperation.maxObjectsPerClass` → if set, stop after N objects per class → `ExportEngine.exportClassToFile()`

### Deduplication
- `ObjectIdTracker.shouldExport()` checks `exportedObjectIds` set:
  - **Root objects** (directly from class `objectIds`): always pass — never checked for duplicates
  - **Embedded objects** (`isEmbedded = true`): always pass — value objects may appear multiple times
  - **Referenced objects**: checked; skip if already in the set
- This is the primary loop-prevention mechanism

### Activation Failure
- `ObjectActivator.getAndActivate()` returns null if object cannot be retrieved → silently skipped

### Export Criteria Filtering
- `ExportCriteriaFilter.shouldExport()` applies criteria from `ClassExportConfig`
- **Only applies to root objects** of the exported class (not embedded, not referenced)
- Uses `exportConfig.matchesAllCriteria(container, genericObj)` to filter
- Objects that don't match → skipped, counted as "filtered"

## Decision Point 2: Does This Object Have Exportable Fields?

- Before writing any XML tags for an object, a **dry run** counts exportable fields → `GenericObjectExporter.countFieldsToExport()` → `FieldExporter.countFieldsToExport()`
- Goes through the same skip logic as real export but writes nothing
- If count is **0** → the object's XML element is **not written at all** (no empty tags)
- The object is still counted as "reached/processed" in statistics

## Decision Point 3: Per-Field Export Decisions

For each field returned by `DatabaseUtil.getAllFieldsIncludingAncestors()`:

### 3a. Schema Lookup
- Field matched to `DOSchemaField` via `DatabaseUtil.findSchemaFieldByNameIncludingAncestors()` → searches current class and ancestor classes in reference schema
- Destination name: `schemaField.destinationName` (or `sourceFieldName` if no schema match)

### 3b. Export Flag
- `schemaField.isExported == false` → **skip** (field disabled in reference schema)

### 3c. Skip Conditions (`skipWhen`)
- `ValueUtil.shouldSkipField(value, schemaField, schema, userSelectedSkipOptions)` evaluates:
  - **User-selected skip**: if user chose this field in skip options → skip
  - **skipWhen keywords** (comma-separated in `DOSchemaField.skipWhen`):
    - `NULL` — value is null
    - `ZERO` — numeric value equals 0
    - `MINUS_ONE` — numeric value equals -1
    - `EMPTY_STRING` — string is null or empty after trim
    - `EMPTY_COLLECTION` — collection/array is null or empty
    - `FALSE` — boolean is false
    - `DEFAULT` — uses `ValueUtil.isEmpty()` legacy logic
  - Any matching condition → **skip**

### 3d. Null Values
- If value is null and not skipped by 3c → export as empty element (`<fieldName/>`)

### 3e. Field Type Routing

The field value is routed to the appropriate handler:

| Condition | Handler | Notes |
|-----------|---------|-------|
| `schemaField.isCollection == true` | `exportSchemaCollectionField()` | Schema says it's a collection, even if DB4O type isn't `Collection` |
| `instanceof Collection` (not GenericObject) | `exportCollectionField()` | Real Java collection |
| `instanceof byte[]` | `exportByteArrayField()` | Binary → Base64 |
| `isArray()` (non-byte) | `exportArrayField()` | Array → iterate items |
| All other values | `exportRegularField()` | Primitive or object reference |

All collection-like handlers delegate to `exportCollectionLikeField()` for unified processing.

## Decision Point 4: Regular Field — Primitive vs. Reference

`FieldExporter.exportRegularField()`:

### Class-Typed Field
- If `schemaField.type` is `java.lang.Class` or `Class`:
  - Extract class name from value (handle GenericObject wrapping)
  - Apply skip conditions and formatting
  - Export as string → **done**

### Persistent Object Reference (`getID(fieldValue) > 0`)
- Apply skip conditions → may skip
- Identify class via `SchemaUtil.findClassByName()`

#### If IDEntite:
1. Check `skipWhen` for `MINUS_ONE` + `IDEntityHandler.shouldSkipMinusOne()` → skip if mID is -1
2. **`embedContents = false`** (default): extract mID → export as scalar value → **done**, target not traversed
3. **`embedContents = true`**: resolve via `ReferenceObjectExporter.resolveAndExport()` → find target entity → recursively export it
4. If non-embedded IDEntite and `pointsTo` is set → register target class in `ReferencedClassTracker`

#### If Regular Object:
1. Count fields to export on the target object (dry run) → if 0, skip (don't write empty wrapper)
2. Write wrapper element → recurse into `exportFieldValue()` → `ObjectExporter.exportObjectRecursively()`

### Primitive Value (`getID(fieldValue) <= 0`)
- Apply skip conditions
- Apply value mapping → `FieldValueMapper.applyMapping()`
- Apply formatting → `ValueUtil.formatFieldValue()`
- Export as text content

## Decision Point 5: Collection Item Processing

`exportCollectionLikeField()`:

- Empty collection + skip conditions met → skip
- Empty collection + no skip → export with `size="0"` attribute
- Non-empty collection:
  1. Check `IDReferenceDetector.detectIDReference()`:
     - If field has `embedContents = false` + `childrenType` set + matching ID class exists → export each item as ID reference via `IDReferenceExporter`
  2. Otherwise → export each item via `exportFieldValue()` (recurse)

## Decision Point 6: Virtual Fields

`FieldExporter.exportVirtualFields()`:

- Iterates `DOSchemaField[]` of the current class
- Only processes fields where `source.startsWith("@")` and `isExported == true` and `criterias` is non-empty
- Executes criteria-based query → `executeVirtualFieldQuery()`:
  1. Preloads all objects of target class (cached in `preloadedObjectsByClass`)
  2. For each criterion, extracts match value from current object
  3. Filters target objects by comparing `criterion.with` field value using `criterion.operator`
  4. Combines multiple criteria with `criteriasOperator` (AND/OR)
- Results exported as collection via `exportCollectionLikeField()`

## Decision Point 7: Embedding vs. Referencing

The `embedContents` flag on `DOSchemaField` controls whether an object is inlined or deduplicated:

| Object Type | `embedContents = false` | `embedContents = true` |
|-------------|------------------------|----------------------|
| **IDEntite** | Export mID as scalar value | Resolve to target entity, export inline |
| **Regular object** | N/A (always embedded) | Always embedded |

- Regular (non-IDEntite) objects are **always embedded inline** regardless of `embedContents`
- Only IDEntite references respect the `embedContents` distinction

## Decision Point 8: Referenced Class Export

After all modules are exported:
- `ReferencedClassTracker` contains classes discovered during export that were not in any module
- `ExportEngine.exportReferencedClasses()` exports them into a virtual "Referenced" module
- Each referenced class is exported like a normal class (full object iteration)
- **No further reference tracking** during this phase (prevents infinite cascading)

## Output Formats

Controlled by `ExportOperation.outputFormat`:

| Format | Writer | Extension | Native IDs | Collection Size Metadata |
|--------|--------|-----------|------------|------------------------|
| XML | `StructuredWriterXML` | `.xml` | Optional | Yes (attribute) |
| JSON | JSON writer | `.json` | Optional | Depends on writer |
| EXCEL | Excel writer | `.xlsx` | Always on | Depends on writer |

- Native DB4O object IDs exported as `id` attribute when enabled → `ExportOperation.exportNativeIds`
- Excel format always includes native IDs regardless of setting

## XSD Generation

- When using XML format and shared tracking:
  - `XSDBuilder` accumulates all exported class/field structures
  - `ExportEngine.writeComprehensiveXSD()` writes a single `schema.xsd` at the database output root
  - Each XML file references it via relative path (`../../schema.xsd`, etc.)
- Post-export: all XML files validated against the XSD → `XMLValidator.validateMultiple()`

## Export History

- Successful exports saved to `local/.export-history.properties` → `ExportHistory.saveExport()`
- Stores: export type, target name, output path, class names, module names, maxObjectsPerClass, exportNativeIds, outputFormat
- Used by `MigrationExportService.repeatLastExport()` to replay identical export

## Complete Export Decision Flowchart

```
Module → Class → objectIds[]
  │
  ├─ maxObjectsPerClass limit? → stop after N
  │
  └─ for each objectId:
       │
       ├─ ObjectIdTracker: duplicate? (root/embedded bypass) → skip
       ├─ ObjectActivator: can activate? → skip if null
       ├─ ExportCriteriaFilter: matches criteria? → skip if root & no match
       ├─ countFieldsToExport: any exportable fields? → skip if 0
       │
       └─ for each field:
            │
            ├─ schema isExported=false? → skip
            ├─ skipWhen matches value? → skip
            ├─ user-selected skip? → skip
            │
            ├─ null → empty element
            ├─ collection → extract items → recurse each (with IDReference detection)
            ├─ byte[] → Base64 string
            ├─ array → iterate → recurse each
            ├─ Class → class name string
            ├─ IDEntite + embedContents=false → mID scalar
            ├─ IDEntite + embedContents=true → resolve target → recurse
            ├─ IDEntite + mID=-1 + skipWhen MINUS_ONE → skip
            ├─ regular object + 0 fields → skip
            └─ regular object → wrapper element → recurse
       │
       └─ virtual fields (@source):
            └─ criteria query → matching objects → export as collection
```
