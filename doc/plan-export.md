# Plan: Export Format Separation via FormatHandler + ExportContext

## Summary

Introduce two new concepts:

1. **`ExportContext`** — a single mutable object tracking the current position in the export tree (module → class → object → field). Public fields, no getters. References `ExportOperation` for all shared config/state.

2. **`FormatHandler`** — abstract class with 6 lifecycle hooks. The base class owns default element-writing behavior using strongly-typed context fields. Each format (XML, JS, JSON, EXCEL) subclasses and overrides only the hooks it needs. No loose maps, no format checks in the pipeline.

The export loop is inverted to **class-to-class**: for each class, all format handlers write their output before moving to the next class. Each handler owns its own writer, dedup set, statistics.

---

## Step 1 — Create `ExportFormat` enum

Add `src/main/java/migration4o/migration/ExportFormat.java`.

Four values: `XML`, `JS`, `JSON`, `EXCEL`. Each carries:
- `extension` — file extension (`.xml`, `.js`, `.json`, `.xlsx`)
- `displayName` — for UI labels

Include `static ExportFormat fromString(String name)` factory for backward compatibility with `StructuredWriterProvider` string-based lookups.

---

## Step 2 — Create `ExportContext`

Add `src/main/java/migration4o/migration/format/ExportContext.java`.

All fields **public**. No getters except for derived computations.

### Fields

```
// Stable reference — set in constructor, never changes
public final ExportOperation operation

// Set once before export starts
public Path basePath

// Module level
public final Deque<DOSchemaModule> moduleChain  // bottom = outermost, top = current

// Class level
public DOSchemaClass schemaClass
public ClassExportConfig exportConfig

// Object level
public final List<ObjectFrame> objectChain  // last = current, previous = parent

// Value level (current field being exported)
public DOSchemaField field
public Object fieldValue
```

### ObjectFrame — public static inner class, public fields

```
public final Object obj
public final long objectId
```

### Push/pop/set/clear methods

| Method | Arguments | Justification |
|--------|-----------|---------------|
| `pushModule(DOSchemaModule)` | 1 | Path is computed from `basePath` + chain. Depth is `moduleChain.size()`. |
| `popModule()` | 0 | |
| `setClass(DOSchemaClass, ClassExportConfig)` | 2 | `schemaClass` provides `destinationName`, `summary`, `source`, `isIDEntite()`. `ClassExportConfig` provides `getDestinationFileName()` and criteria. `dbSchemaClass` is not stored — it's only needed by the object loop (for `objectIds`) and XSD registration, passed directly where needed. Output file path is not stored — derived by each handler as `modulePath().resolve(config.getDestinationFileName() + handler.format.extension)`. |
| `clearClass()` | 0 | |
| `pushObject(Object, long objectId)` | 2 | Root = `objectChain.isEmpty()`. Parent = `objectChain.get(size-2).objectId`. Embedded is not stored — it's a pipeline-internal concern (only used by `ObjectIdTracker` and `ExportCriteriaFilter` for dedup/criteria gating, never checked by any format handler). |
| `popObject()` | 0 | |
| `setValue(DOSchemaField, Object value)` | 2 | Field names (`destinationName`, `source`), `embedContents`, and all other metadata come from `DOSchemaField`. |
| `clearValue()` | 0 | |

### Computed methods (derived from state, not stored)

| Method | Returns | Logic |
|--------|---------|-------|
| `modulePath()` | `Path` | Starts from `basePath`, resolves `ModulePathUtil.moduleId(m)` for each module in `moduleChain` bottom-to-top |
| `parentObjectId()` | `Long` | `objectChain.size() < 2 ? null : objectChain.get(objectChain.size() - 2).objectId` |
| `isRootObject()` | `boolean` | `objectChain.size() == 1` |
| `currentObject()` | `ObjectFrame` | `objectChain.get(objectChain.size() - 1)` |

---

## Step 3 — Create `FormatHandler` abstract class

Add `src/main/java/migration4o/migration/format/FormatHandler.java`.

### Public fields

```
public final ExportFormat format
public StructuredWriter writer
public Set<Long> exportedObjectIds = new HashSet<>()
public ReferencedClassTracker referencedClassTracker
public ExportStatistics statistics
```

### Abstract method

```
abstract StructuredWriter createWriter(Path filePath)
```

Each format creates its own writer wrapping the appropriate `StructuredWriterAPI` implementation.

### Six hooks — all take only `ExportContext`

**`init(ExportContext context)`** — called once before export. Default: no-op.

**`open(ExportContext context)`** — called at start of each data file. Default:
```java
writer.openStructure("export");
writer.metadata(context.schemaClass.getMetadata(...));
writer.openStructure("objects");
```

**`onObject(ExportContext context)` → `boolean`** — called after `context.pushObject()`, before field export. Responsible for writing the object element opening. Default implementation:
```java
String elementName = context.schemaClass != null
    ? context.schemaClass.destinationName
    : ClassUtil.getSimpleName(className);
Map<String, String> attrs = null;
if (context.operation.exportNativeIds) {
    attrs = new LinkedHashMap<>();
    attrs.put("id", String.valueOf(context.currentObject().objectId));
}
writer.openStructure(elementName, attrs);
return false;  // proceed to field export
```
This reads `exportNativeIds` from `context.operation` and `objectId` from `context.currentObject()` — all strongly typed. No loose attribute map flows between pipeline and handler.

**`onField(ExportContext context)` → `boolean`** — called after `context.setValue()`, before default reference/recursive handling. Default: `return false` (pipeline handles the field normally).

**`close(ExportContext context)`** — called after data file is fully written. Default: no-op.

**`done(ExportContext context)`** — called after all modules are exported. Default: no-op.

### Static factory

```java
static List<FormatHandler> createHandlers(List<ExportFormat> formats,
    boolean generateHtmlViewer, boolean generateXsd)
```

Returns the appropriate handler instances for the selected formats.

---

## Step 4 — Create `XmlFormatHandler`

Add `src/main/java/migration4o/migration/format/XmlFormatHandler.java`.

### Own state

`XSDBuilder sharedXSDBuilder`, `Set<String> exportedXMLFiles`, `boolean generateXsd`, `boolean generateHtmlViewer`.

### Overrides 3 hooks

**`open(context)`**: Computes schema location by relativizing the output file path against `context.basePath` to locate `_Migration/Schema.xsd`. Calls `writer.openRootStructure("export", schemaLocation)` instead of `openStructure`. Then writes metadata and opens `"objects"` as usual. Absorbs the 3-way branch from `ClassFileExporter.java L81-90`, `ExportEngine.java L314`, `ExportEngine.java L417`.

**`close(context)`**: Computes file path from `context.modulePath()` + `context.exportConfig.getDestinationFileName()` + `.xml`. Adds to `exportedXMLFiles`. Registers class in `sharedXSDBuilder`. Writes per-class XSD if no shared builder. Generates XML HTML viewer if `generateHtmlViewer`. Absorbs `ModuleExporter.java L106`, `ClassXsdWriter.java L36`, `HtmlViewerTask.java` XML branch.

**`done(context)`**: Exports unreached objects. Writes comprehensive XSD via `sharedXSDBuilder`. Validates all `exportedXMLFiles` against the XSD. Absorbs `MigrationExportService.java L99, L110, L129`.

---

## Step 5 — Create `JsFormatHandler`

Add `src/main/java/migration4o/migration/format/JsFormatHandler.java`.

### Own state

`String cachedNavJson`, `Map<String,Long> idEntiteTargetCache`, `Map<Long,String> idEntiteSummaryCache`.

### Overrides 4 hooks

**`init(context)`**: Builds nav tree from `context.operation.referenceSchema` modules. Serializes to JSON and caches in `cachedNavJson`. Absorbs `MigrationExportService.java L74`.

**`onObject(context)` → `boolean`**: Reads `context.schemaClass.summary` — if present, calls `SummaryGenerator.generate(context.operation.container, context.currentObject().obj, context.schemaClass, context.operation.referenceSchema)`. Reads `context.schemaClass.isIDEntite(context.operation.databaseSchema)` — if true, resolves IDEntite label via `SummaryGenerator.resolveIDEntiteLabel(...)` using `context.currentObject().obj`, `context.schemaClass`, `context.operation.referenceSchema`, `context.operation.databaseSchema`, and own caches. If label resolves: writes flat `writer.elementWithContent(stripIdPrefix(context.schemaClass.destinationName), null, label, false)`, returns `true` (fully handled, skip fields). If label is null: falls through to write the opening element with summary attribute, returns `false`. For non-IDEntite objects: builds attributes with `id` (if `context.operation.exportNativeIds`) and `_summary`, calls `writer.openStructure(context.schemaClass.destinationName, attrs)`, returns `false`. Absorbs `ObjectExporter.java L170-200`.

**`onField(context)` → `boolean`**: Checks `context.field.embedContents == false` and whether the target class is an IDEntite. If so: resolves label via `SummaryGenerator.resolveIDEntiteLabel(...)` using `context.fieldValue`, the target schema class, and own caches. Writes `writer.elementWithContent(stripIdPrefix(context.field.destinationName), null, label, false)`. Returns `true`. Otherwise returns `false`. Absorbs `FieldExporter.java L514`.

**`close(context)`**: Computes file path from `context.modulePath()` + `context.exportConfig.getDestinationFileName()` + `.js`. Computes baseHref from `context.moduleChain.size()`. Generates HTML viewer via `JsViewerHtmlGenerator.writeViewerForJs(filePath, context.schemaClass, cachedNavJson, baseHref, layoutJson)`. Absorbs `HtmlViewerTask.java` JS branch.

---

## Step 6 — Create `DefaultFormatHandler`

Add `src/main/java/migration4o/migration/format/DefaultFormatHandler.java`.

For JSON and EXCEL.

### Overrides 1 hook

**`init(context)`**: If `format == ExportFormat.EXCEL`, sets `context.operation.exportNativeIds = true`. Absorbs `ExportOperation.java L143`.

All other hooks use base class defaults.

---

## Step 7 — Clean up `ExportOperation`

In `ExportOperation.java`:

**Remove** (moved to format handlers): `sharedXSDBuilder`, `xsdBuilder`, `exportedXMLFiles`, `generateHtmlViewer`, `navTree`, `cachedNavJson`, `idEntiteTargetCache`, `idEntiteSummaryCache`, `exportedObjectIds`, `referencedClassTracker`, `statistics`, `objectExporter`, `exportConfig`.

**Remove** (moved to `ExportContext`): `moduleStack`.

**Remove** (moved to `ExportFormat` enum or `FormatHandler`): `outputFormat` (String), `xmlWriter`.

**Remove methods**: `isXMLFormat()`, `shouldExportNativeIdsForCurrentFormat()`, `getOutputFileExtension()`, `getStructuredWriterAPI()`.

**Add**: `List<FormatHandler> formatHandlers`, `ExportContext context`.

**Keep**: `referenceSchema`, `databaseSchema`, `databasePath`, `dbContext`, `container`, `baseOutputPath`, `monitor`, `maxObjectsPerClass`, `exportNativeIds`, `applyUserSelectedFieldExclusions`, `applySkipWhenConditions`, `applyExportCriteriaFilters`, `skipObjectsWithoutExportableFields`, `classNames`, `saveToHistory`, `useSharedTracking`, `selectedSkipUserOptions`, `availableSkipUserOptions`, `outputOptions`.

---

## Step 8 — Invert the export loop (class-to-class)

**Current** (in `MigrationExportService`):
```
for each format:
    create ExportEngine
    for each module:
        for each class: export
```

**New:**
```
create List<FormatHandler> handlers from selected output options
create ExportContext(operation)
context.basePath = computeBasePath()

for each handler: handler.init(context)

for each module:
    context.pushModule(module)
    for each class config in module:
        resolve schemaClass, dbSchemaClass
        context.setClass(schemaClass, config)
        for each handler:
            Path filePath = context.modulePath().resolve(
                config.getDestinationFileName() + handler.format.extension)
            handler.writer = handler.createWriter(filePath)
            handler.open(context)
            objectLoop(context, handler, dbSchemaClass)
            handler.close(context)
        context.clearClass()
    for each child module: recurse
    context.popModule()

for each handler: handler.done(context)
combine results from all handlers
```

Each class is fully exported to all selected formats before moving to the next class.

---

## Step 9 — Update pipeline classes

**ExportEngine.java**: Becomes the orchestrator of the class-to-class loop above. Accepts `List<FormatHandler>` at construction. No format awareness. Calls `pushModule/popModule`, `setClass/clearClass` on context, then loops over handlers calling `open/close`. Delegates object iteration to `ObjectExportLoop`.

**MigrationExportService.java**: Creates format handlers via `FormatHandler.createHandlers(formats, generateHtmlViewer, generateXsd)`. Creates `ExportContext`. Passes both to `ExportEngine`. No format-specific `if` statements remain.

**ModuleExporter.java**: Calls `context.pushModule(module)` / `context.popModule()`. No `isXMLFormat()` guards. No XSD path computation. No file tracking. Those responsibilities are now in format handlers' `close` hooks.

**ClassFileExporter.java**: Simplified — per handler, creates writer, calls `handler.open(context)`, runs object loop, calls `handler.close(context)`. No 3-way root element branch. No format checks.

**ObjectExporter.java**: Receives the current `FormatHandler` (for writer and hooks). Calls `context.pushObject(obj, objectId)`. Calls `boolean handled = handler.onObject(context)`. If `!handled`, exports fields. Calls `context.popObject()`. The 10-parameter method becomes `pushObject` (2 args) + context reads. `isEmbedded` is passed only to `ObjectIdTracker.shouldExport()` as a local parameter — not stored on context.

**FieldExporter.java**: Calls `context.setValue(schemaField, value)`. Calls `boolean handled = handler.onField(context)`. If `!handled`, proceeds with default mID/recursive export. Calls `context.clearValue()`.

**ObjectExportLoop.java**: Receives `FormatHandler` to use `handler.exportedObjectIds` for dedup and `handler.statistics` for recording. Receives `dbSchemaClass` directly (for `objectIds` array). Not from context.

**ReferencedClassesExporter.java**: Loops over handlers. For each handler, exports referenced classes using that handler's writer and dedup set. No `isXMLFormat()` guard.

---

## Step 10 — Retire or internalize task classes

**Delete `HtmlViewerTask.java`** — logic absorbed by `JsFormatHandler.close()` and `XmlFormatHandler.close()`.

**Internalize `ClassXsdWriter.java`** — becomes a utility called from `XmlFormatHandler.close()`. No longer checks format itself.

**Internalize `ModulePathUtil.getSchemaLocationForXml()`** — moves into `XmlFormatHandler`. `ModulePathUtil.moduleId()` stays as a shared utility (used by `ExportContext.modulePath()`).

**Keep `NavTreeBuilder.java`** — called from `JsFormatHandler.init()`.

---

## Step 11 — Update `ExportOutputOption`

In `ExportOutputOption.java`:

`toWriterFormat()` returns `ExportFormat` enum instead of `String`. Add `createFormatHandlers(List<String> options)` → `List<FormatHandler>` that maps UI option strings to handler instances with appropriate flags (`generateHtmlViewer`, `generateXsd`).

---

## Verification

1. `mvn clean compile` — zero errors
2. `./run-ui.sh` — export with multiple formats selected, verify each class appears in all formats before the next class starts
3. XML: XSD validation passes, per-class and comprehensive XSD files generated
4. HTML+JS: HTML viewer loads with sidebar navigation
5. JSON/EXCEL: output files match pre-refactoring baseline
6. Compare export statistics (object counts, class counts) before and after

---

## Decisions

- **No `Map<String,String>` flows through the system** — each format handler builds its own attributes from strongly-typed context fields (`context.currentObject().objectId`, `context.operation.exportNativeIds`, `context.schemaClass.summary`)
- **Base `FormatHandler.onObject()` owns default element writing** — not a no-op; it writes the standard element opening. Subclasses replace it entirely when they need different output
- **`pushObject(Object, long)` — 2 args** — root = empty chain; parent = previous chain element; embedded is pipeline-internal (passed to `ObjectIdTracker` only)
- **`pushModule(DOSchemaModule)` — 1 arg** — path derived from `basePath` + chain; depth = `moduleChain.size()`
- **`setValue(DOSchemaField, Object)` — 2 args** — field names, embed flags, all metadata from `DOSchemaField`
- **`setClass(DOSchemaClass, ClassExportConfig)` — 2 args** — `dbSchemaClass` passed directly to object loop; output path derived per handler from `context.modulePath() + config.getDestinationFileName() + handler.format.extension`
- **Class-to-class loop** — each class exported to all formats before moving on; each handler owns its own writer, dedup set, referenced class tracker, statistics
- **`StructuredWriterAPI` pattern untouched** — serialization (how to write XML/JS/JSON) remains separate from format handler logic (what to write and when)
