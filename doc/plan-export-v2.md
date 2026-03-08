# Plan v2: Export Format Separation via FormatHandler + ExportContext

## Architecture Overview

Three new concepts work together:

1. **`ExportFormat`** — enum identifying each format (XML, HTML, JSON, EXCEL) by name only. Each `FormatHandler` implementation declares its own extension and display name.

2. **`ExportContext`** — single mutable object tracking the current position in the export tree (module → class → object → field), plus all format-neutral shared state: statistics, reference tracker, and `allowedObjectIds`. Public fields, no getters except for derived computations. References `ExportOperation` for immutable configuration.

3. **`FormatHandler`** — abstract class with **eight hooks**. The engine calls all hooks unconditionally; handlers override only what they need. Two hooks (`observeObject`, `observeField`) serve as schema-observation callbacks for XSD registration — all other handlers leave them as no-ops. One hook (`onReferencedClasses`) has a **default implementation** that replaces the current `ReferencedClassesExporter` entirely. Format handlers own their writer and per-format dedup set; everything else is shared through `ExportContext`.

### Loop structure (class-to-class)

```
for each handler → handler.init(context)

for each module (recursive):
    context.pushModule(module)
    for each class config:
        context.setClass(schemaClass, config)
        for each handler:
            handler.writer = handler.createWriter(filePath)
            handler.open(context)
            for each object:
                context.pushObject(obj, objectId)
                    handler.observeObject(context)        // XSD: addClass (no-op for others)
                    if !handler.onObject(context):        // content: write opening element
                        for each field:
                            context.setField(field, value)
                            handler.observeField(context) // XSD: addField (no-op for others)
                            if !handler.onField(context): // content: write field (default pipeline)
                                [pipeline writes field]
                            context.clearField()
                context.popObject()
            handler.close(context)                        // finish file; HTML viewer; XSD per-class
        context.clearClass()
    for each child module: recurse
    context.popModule()

for each handler → handler.onReferencedClasses(context)  // default impl included
for each handler → handler.done(context)                  // XML: unreached objects + XSD + validation

combine and return context.statistics
```

---

## Step 1 — Create `ExportFormat` enum

**Add** `src/main/java/migration4o/migration/ExportFormat.java`.

Four values: `XML`, `HTML`, `JSON`, `EXCEL`. No fields — the enum is a pure identifier. Extension and display name are declared by each `FormatHandler` subclass (see Step 3).

**Retire `StructuredWriterProvider`** — it is currently used only by `ExportOperation.getStructuredWriterAPI()`, which is being removed. Each `FormatHandler` subclass will instantiate its own `StructuredWriterAPI` implementation directly inside `createWriter()`.

---

## Step 2 — Create `ExportContext`

**Add** `src/main/java/migration4o/migration/format/ExportContext.java`.

All fields public. No getters except computed methods.

### Fields

```java
// Immutable reference — set in constructor
public final ExportOperation operation

// Set once before export starts
public Path basePath

// Module level
public final Deque<DOSchemaModule> moduleChain  // bottom = outermost, top = current

// Class level
public DOSchemaClass schemaClass
public ClassExportConfig exportConfig

// Object level
public final List<ObjectFrame> objectChain      // last = current, previous = parent
public Set<Long> allowedObjectIds               // null = allow all; set for unreached-objects pass

// Field level
public DOSchemaField field
public Object fieldValue

// Format-neutral shared state
public ExportStatistics statistics
public ReferencedClassTracker referencedClassTracker
```

`statistics` and `referencedClassTracker` are shared across all handlers — they track reach and class discovery, which are format-independent. Only `FormatHandler.exportedIds` (the per-format dedup set) is per-handler.

### ObjectFrame — public static inner class

```java
public final Object obj
public final long objectId
```

### Push/pop/set/clear methods

| Method | What it does |
|--------|-------------|
| `pushModule(DOSchemaModule)` | Push to `moduleChain` |
| `popModule()` | Pop from `moduleChain` |
| `setClass(DOSchemaClass, ClassExportConfig)` | Set `schemaClass` and `exportConfig` |
| `clearClass()` | Null both |
| `pushObject(Object, long objectId)` | Append `ObjectFrame` to `objectChain` |
| `popObject()` | Remove last element |
| `setField(DOSchemaField, Object)` | Set `field` and `fieldValue` |
| `clearField()` | Null both |

### Computed methods

| Method | Returns | Logic |
|--------|---------|-------|
| `modulePath()` | `Path` | `basePath` + `moduleChain` bottom-to-top via `ModulePathUtil.moduleId(m)` |
| `moduleDisplayName()` | `String` | Join `moduleChain` names with `/`, bottom-to-top |
| `isRootObject()` | `boolean` | `objectChain.size() == 1` |
| `currentObject()` | `ObjectFrame` | `objectChain.get(objectChain.size() - 1)` |
| `parentObjectId()` | `Long` | `objectChain.size() < 2 ? null : objectChain.get(size-2).objectId` |

---

## Step 3 — Create `FormatHandler` abstract class

**Add** `src/main/java/migration4o/migration/format/FormatHandler.java`.

### Public fields

```java
public final ExportFormat format
public StructuredWriter writer
public final Set<Long> exportedIds = new HashSet<>()
```

No `ExportStatistics` or `ReferencedClassTracker` here — those are shared in `ExportContext`.

### Abstract methods

```java
// Each implementation declares its own output file extension, e.g. ".xml", ".html", ".json", ".xlsx"
public abstract String extension()

// Each implementation declares its own display name for UI labels, e.g. "XML", "HTML", "JSON", "Excel"
public abstract String displayName()

// Each implementation creates its own writer, instantiating the appropriate StructuredWriterAPI directly
protected abstract StructuredWriter createWriter(Path filePath) throws IOException
```

`extension()` and `displayName()` are defined by the handler, not the enum. This means a future handler can introduce a new format — or a variant of an existing one — without touching `ExportFormat`. `StructuredWriterProvider` is not used.

### Eight hooks

All take only `ExportContext`. All have default implementations (no-op or standard behavior). Handlers override only what they need.

---

**`init(ExportContext ctx)`** — called once before any module is processed.
Default: no-op.

---

**`open(ExportContext ctx)`** — called at the start of each class data file, after `writer` has been set by the engine. Default:
```java
writer.openStructure("export");
writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()));
writer.openStructure("objects");
```

---

**`observeObject(ExportContext ctx)`** — called once per object, before `onObject`. **Not for content writing** — for schema observation only. Default: no-op. `XmlFormatHandler` overrides to call `xsdBuilder.addClass(ctx.schemaClass)`.

This decouples XSD registration from the content-writing pipeline. The engine always calls this unconditionally; handlers that don't need it pay zero cost.

---

**`onObject(ExportContext ctx) → boolean`** — called after `observeObject`, before field export. Returns `true` if the handler has fully written this object (skip field loop); `false` to proceed with the default field pipeline. Default:
```java
Map<String,String> attrs = null;
if (ctx.operation.exportNativeIds) {
    attrs = Map.of("id", String.valueOf(ctx.currentObject().objectId));
}
writer.openStructure(ctx.schemaClass.destinationName, attrs);
return false;  // engine exports fields and then closes the structure
```
When `onObject` returns `false`, the engine calls `writer.closeStructure(ctx.schemaClass.destinationName)` after the field loop.

---

**`observeField(ExportContext ctx)`** — called once per schema field, before `onField`. **Not for content writing** — for schema observation only. Default: no-op. `XmlFormatHandler` overrides to call `xsdBuilder.addField(ctx.schemaClass, ctx.field)`.

---

**`onField(ExportContext ctx) → boolean`** — called after `observeField`, before the default field pipeline. Returns `true` if the handler has fully written this field (skip default); `false` to let the pipeline handle it. Default: `return false`.

---

**`close(ExportContext ctx)`** — called after the data file is fully written and the writer flushed. Default: no-op.

---

**`onReferencedClasses(ExportContext ctx)`** — called once per handler after all modules finish, before `done`. **Has a default implementation** that replaces `ReferencedClassesExporter`:

```java
public void onReferencedClasses(ExportContext ctx) throws Exception {
    if (ctx.referencedClassTracker == null) return;
    Set<String> toExport = ctx.referencedClassTracker.getReferencedClasses();
    if (toExport.isEmpty()) return;

    Path referencedPath = ctx.modulePath().getParent().resolve("Referenced");
    Files.createDirectories(referencedPath);

    // Disable tracking during this pass to prevent infinite recursion
    ReferencedClassTracker saved = ctx.referencedClassTracker;
    ctx.referencedClassTracker = null;
    try {
        for (String className : toExport) {
            if (saved.isReferencedClassExported(className)) continue;
            DOSchemaClass schemaClass = ctx.operation.referenceSchema.findClassByName(className);
            DOSchemaClass dbSchemaClass = ctx.operation.databaseSchema.findClassByName(className);
            if (schemaClass == null || dbSchemaClass == null) continue;

            ctx.setClass(schemaClass, null);
            Path filePath = referencedPath.resolve(
                schemaClass.destinationName + extension());
            this.writer = createWriter(filePath);
            this.open(ctx);
            new ObjectExportLoop(ctx, this).run(dbSchemaClass);
            this.close(ctx);
            ctx.clearClass();
            saved.markReferencedClassAsExported(className);
        }
    } finally {
        ctx.referencedClassTracker = saved;
    }
}
```

Handlers can override if they need different behavior for referenced classes. The recursive use of `open/close` ensures that format-specific post-processing (HTML viewer generation, XSD registration) happens automatically for referenced-class files too.

---

**`done(ExportContext ctx)`** — called after `onReferencedClasses`, once per handler, for final tasks. Default: no-op.

---

### Static factory

```java
public static List<FormatHandler> create(List<ExportFormat> formats, boolean generateXsd)
```

Returns handler instances for the selected formats. `generateXsd` is passed to `XmlFormatHandler`. There is no longer a separate `generateHtmlViewer` flag — HTML is its own format (`ExportFormat.HTML`), selected alongside or instead of others.

---

## Step 4 — Create `XmlFormatHandler`

**Add** `src/main/java/migration4o/migration/format/XmlFormatHandler.java`.

```java
public String extension()    { return ".xml"; }
public String displayName()  { return "XML"; }
```

### Own state

```java
private final XSDBuilder xsdBuilder
private final Set<String> exportedXMLFiles = new HashSet<>()
private final boolean generateXsd
```

### Overrides 6 hooks

**`init(ctx)`**: Calls `xsdBuilder.startExportRoot()`. Absorbs `ExportOperation.initializeSharedTracking()` XSD-builder setup.

**`observeObject(ctx)`**: `if (ctx.schemaClass != null) xsdBuilder.addClass(ctx.schemaClass)`. Also calls `xsdBuilder.addTopLevelObject(ctx.schemaClass.destinationName, dbSchemaClass)` — where `dbSchemaClass` is retrieved as `ctx.operation.databaseSchema.findClassByName(ctx.schemaClass.source)`. This is called once per object but `XSDBuilder` registration is idempotent.

**`observeField(ctx)`**: `if (ctx.field != null) xsdBuilder.addField(ctx.schemaClass, ctx.field)`. Absorbs `FieldExporter.java:198`.

**`open(ctx)`**: Computes relative schema path via `ModulePathUtil.getSchemaLocationForXml(filePath, ctx.basePath)`. Calls `writer.openRootStructure("export", schemaLocation)`. Then writes metadata and opens `"objects"` as in the default. Absorbs the 3-way branch in `ClassFileExporter.java L81–90`.

**`close(ctx)`**:
- Closes `"objects"` and `"export"` structures and flushes writer.
- Tracks `filePath` in `exportedXMLFiles`.

**`done(ctx)`**: Three tasks in order:

1. **Unreached objects** — computes reached IDs from `ctx.statistics.exportedObjectIds` (the reach map), subtracts from all DB object IDs in `ctx.operation.databaseSchema`. If non-empty:
   - Constructs path `basePath / <dbFolder> / _Migration / Extra.xml`.
   - Sets `ctx.allowedObjectIds = unreachedIds`.
   - Creates writer, calls `open(ctx)` with a temporary null schemaClass (special metadata path), runs object loop via `ObjectExportLoop`, calls `close(ctx)`.
   - Clears `ctx.allowedObjectIds`.
   - Absorbs `ExportEngine.exportUnreachedObjects()` and `MigrationExportService.java L99–106`.

2. **Comprehensive XSD** — if `generateXsd`: writes `xsdBuilder` to `_Migration/Schema.xsd`. Absorbs `MigrationExportService.java L110–118`.

3. **XML validation** — if `generateXsd && !exportedXMLFiles.isEmpty()`: calls `XMLValidator.validateMultiple(...)` and reports results via `ctx.operation.monitor`. Absorbs `MigrationExportService.java L127–164`.

---

## Step 5 — Create `HtmlFormatHandler`

**Add** `src/main/java/migration4o/migration/format/HtmlFormatHandler.java`.

This handler generates self-contained `.html` files — one per class — with the data embedded inline as a `<script>` block. There is no intermediate `.js` file written to disk and no deletion step. The JS data format (`StructuredWriterJS`) is used internally as the serialization layer, but the output file is always a complete HTML document.

```java
public String extension()    { return ".html"; }
public String displayName()  { return "HTML"; }
```

### Own state

```java
private String cachedNavJson = "[]"
private final Map<String,Long> idEntiteTargetCache = new HashMap<>()
private final Map<Long,String> idEntiteSummaryCache = new HashMap<>()
```

No `generateHtmlViewer` flag — this handler IS the HTML viewer format. If the user selects HTML, they always get self-contained HTML files.

### How the writer works

`createWriter(filePath)` creates a `StructuredWriter` backed by an in-memory `StringWriter` using `StructuredWriterJS`. The pipeline writes the JS data object into this buffer during the object loop. No file is touched yet.

`close(ctx)` reads the accumulated buffer content, wraps it in the full HTML template (nav sidebar, scripts, styles — currently generated by `JsViewerHtmlGenerator`), and writes the single `.html` file to `filePath`. The buffer is then discarded.

This eliminates the write-then-inject-then-delete JS file cycle entirely.

### Overrides 4 hooks

**`init(ctx)`**: Builds nav tree from `ctx.operation.referenceSchema` modules via `NavTreeBuilder`. Serializes to JSON and caches in `cachedNavJson`. Absorbs `MigrationExportService.java L74–76`.

**`onObject(ctx) → boolean`**:
- If `ctx.schemaClass.isIDEntite(ctx.operation.databaseSchema)`: attempts `SummaryGenerator.resolveIDEntiteLabel(container, obj, ctx.schemaClass, ...)` using own caches. If label resolved: writes `writer.elementWithContent(stripIdPrefix(ctx.schemaClass.destinationName), null, label, false)`, returns `true`. If null: falls through.
- Builds attributes: `id` if `ctx.operation.exportNativeIds`, `_summary` if schemaClass has summary template.
- Calls `writer.openStructure(ctx.schemaClass.destinationName, attrs)`, returns `false`.
- Absorbs `ObjectExporter.java L170–200`.

**`onField(ctx) → boolean`**: Checks `ctx.field != null && !ctx.field.embedContents` and whether the target class is IDEntite. If so: resolves label via `SummaryGenerator.resolveIDEntiteLabel(...)` using own caches. If label found: writes `writer.elementWithContent(stripIdPrefix(ctx.field.destinationName), null, label, false)`, returns `true`. Otherwise returns `false`. Absorbs `FieldExporter.java:514`.

**`close(ctx)`**: Reads the in-memory JS data from the `StringWriter` buffer. Computes `baseHref` from `ctx.moduleChain.size()` (one `../` per level, `"./"` at depth 0). Computes `layoutJson` from `ctx.exportConfig`. Calls `HtmlViewerGenerator.write(filePath, ctx.schemaClass, cachedNavJson, dataScript, baseHref, layoutJson)` which assembles and writes the complete `.html` file. Absorbs `HtmlViewerTask.java` JS branch and the current write-then-inject flow. `JsViewerHtmlGenerator` is either refactored into `HtmlViewerGenerator` or kept as a private utility called by this handler.

---

## Step 6 — Create `JsonFormatHandler` and `ExcelFormatHandler`

### `JsonFormatHandler`

**Add** `src/main/java/migration4o/migration/format/JsonFormatHandler.java`.

```java
public String extension()    { return ".json"; }
public String displayName()  { return "JSON"; }
```

`createWriter(filePath)` uses `StructuredWriterJSON`. All hooks use base-class defaults unless JSON-specific behavior is identified during implementation (e.g. different root element structure, metadata serialization). The class exists as a dedicated implementation so any future specifics have a clear home.

---

### `ExcelFormatHandler`

**Add** `src/main/java/migration4o/migration/format/ExcelFormatHandler.java`.

```java
public String extension()    { return ".xlsx"; }
public String displayName()  { return "Excel"; }
```

`createWriter(filePath)` uses `StructuredWriterExcel`. Overrides one hook:

**`init(ctx)`**: Sets `ctx.operation.exportNativeIds = true` — EXCEL always includes the native ID column. Absorbs `ExportOperation.shouldExportNativeIdsForCurrentFormat()` EXCEL branch.

---

## Step 7 — Refactor `ObjectExporter` and `FieldExporter`

This is the key wiring change. Both classes now receive `(ExportContext, FormatHandler)` instead of `(ExportOperation, StructuredWriter, XSDBuilder)`. The writer is accessed via `handler.writer`; XSD observation is via hook calls; operation config is via `context.operation`.

### `ObjectExporter`

**New constructor**: `ObjectExporter(ExportContext context, FormatHandler handler)`

**Remove fields**: `StructuredWriter xmlWriter`, `XSDBuilder xsdBuilder`. Access `handler.writer` directly.

**`exportObject(long objectId, boolean isEmbedded)` — replaces the 10-parameter `exportObjectRecursively`**:

```java
public void exportObject(long objectId, boolean isEmbedded) throws IOException {
    // 1. Check ctx.allowedObjectIds (null = allow all)
    // 2. Dedup check via ObjectIdTracker(handler.exportedIds, ctx.statistics, ...)
    // 3. Activate object
    // 4. Export criteria filter (uses ctx.operation.applyExportCriteriaFilters, ctx.exportConfig)
    // 5. Resolve schemaClass via SchemaElementMapper
    // 6. Dry-run field count
    // 7. ctx.pushObject(obj, objectId)
    //        handler.observeObject(ctx)          ← XSD addClass, no-op for non-XML
    //        boolean handled = handler.onObject(ctx)
    //        if !handled && fieldsToExport > 0:
    //            fieldExporter.exportAllFields(objectId)
    //        if !handled: handler.writer.closeStructure(...)
    //    ctx.popObject()
    // 8. Record ctx.statistics
}
```

The parent-object ID, root-object flag, and field context come from `ctx.objectChain` — the parameter list collapses to two arguments. `isEmbedded` is passed to `ObjectIdTracker.shouldExport()` locally — not stored on context (pipeline-internal concern).

`FieldExporter` is constructed inside the `ObjectExporter` constructor:
```java
this.fieldExporter = new FieldExporter(context, handler);
```

The circular reference via `operation.objectExporter` is removed. `FieldExporter` holds a direct reference back to the `ObjectExporter` for recursive embedded-object export (same pattern as today, but via the field rather than via the operation).

### `FieldExporter`

**New constructor**: `FieldExporter(ExportContext context, FormatHandler handler)`

**Remove fields**: `StructuredWriter xmlWriter`, `XSDBuilder xsdBuilder`. Use `handler.writer` for writing.

**`exportAllFields(long parentObjectId)` — simplified signature** (object info is in `ctx.currentObject()`):

For each `StoredField`:
```java
DOSchemaField schemaField = /* lookup */;
Object fieldValue = /* read */;

context.setField(schemaField, fieldValue);
handler.observeField(context);              // XSD addField — no-op for non-XML
boolean handled = handler.onField(context); // JS IDEntite override, etc.
if (!handled) {
    // default pipeline: null / collection / array / scalar handling
    // (unchanged logic from current FieldExporter)
}
context.clearField();
```

The format check at [FieldExporter.java:514](src/main/java/migration4o/migration/FieldExporter.java#L514) (`"JS".equalsIgnoreCase(operation.outputFormat)`) is removed entirely — `HtmlFormatHandler.onField()` absorbs that branch and returns `true`, preventing the default pipeline from running.

For recursive embedded-object export, `FieldExporter` calls `objectExporter.exportObject(objectId, isEmbedded)` — same instance, same `(context, handler)`.

---

## Step 8 — Update `ObjectExportLoop` and `ModuleExporter`

### `ObjectExportLoop`

**New constructor**: `ObjectExportLoop(ExportContext context, FormatHandler handler)`

Iterates `dbSchemaClass.objectIds`. For each ID: checks cancellation, `maxObjectsPerClass`, and calls `objectExporter.exportObject(objectId, false)`. Uses `handler.exportedIds` for dedup (no longer `operation.exportedObjectIds`). Uses `context.statistics` (no longer `operation.statistics`). Fires `context.operation.monitor` callbacks.

Reference propagation: after the loop, propagates discovered classes from the local tracker back to `context.referencedClassTracker`. Unchanged behavior, just sourced from context instead of operation.

### `ModuleExporter`

**New constructor**: `ModuleExporter(ExportContext context, ExportEngine engine)`

`exportModuleRecursive(module, basePath, depth)` replaces `exportModuleRecursive(module, basePath, depth)` — same signature externally, but internally:

- Replaces `operation.moduleStack.push/pop` → `context.pushModule(module)` / `context.popModule()`.
- No longer computes the file path or extension — that is done in `ExportEngine.exportClassToAllHandlers()`.
- No longer calls `ClassFileExporter` — instead calls `engine.exportClassToAllHandlers(schemaClass, dbSchemaClass, config)`.
- No longer has `operation.isXMLFormat()` guard or `operation.exportedXMLFiles` tracking.
- No longer references `operation.getOutputFileExtension()`.

---

## Step 9 — Rewrite `ExportEngine` as the class-to-class orchestrator

`ExportEngine` owns the class-to-class loop and calls into format handlers.

**Remove**: `exportModule()` (the `@Deprecated` legacy path) — **delete it**. No active callers.

**Remove**: `writeComprehensiveXSD()` — absorbed into `XmlFormatHandler.done()`.

**Remove**: `exportUnreachedObjects()` — absorbed into `XmlFormatHandler.done()`.

**Remove**: `exportReferencedClasses()` — absorbed into `FormatHandler.onReferencedClasses()`.

**Remove**: `setModuleNavData()` — absorbed into `HtmlFormatHandler.init()`.

**New `exportModules(List<DOSchemaModule>, List<String>)` — the main entry point**:

```java
public ExportStatistics exportModules(List<DOSchemaModule> modules,
    List<String> modulePaths) throws Exception {

    List<FormatHandler> handlers = operation.handlers;
    ExportContext context = operation.context;

    // Init
    for (FormatHandler h : handlers) h.init(context);

    // Module traversal
    for (int i = 0; i < modules.size(); i++) {
        new ModuleExporter(context, this).exportModuleRecursive(
            modules.get(i), context.basePath, 0);
    }

    // Referenced classes — each handler runs its own pass
    for (FormatHandler h : handlers) h.onReferencedClasses(context);

    // Done — unreached objects, XSD, validation (format-specific)
    for (FormatHandler h : handlers) h.done(context);

    return context.statistics;
}
```

**New `exportClassToAllHandlers(DOSchemaClass, DOSchemaClass, ClassExportConfig)` — called by `ModuleExporter`**:

```java
void exportClassToAllHandlers(DOSchemaClass schemaClass,
    DOSchemaClass dbSchemaClass, ClassExportConfig config) throws Exception {

    context.setClass(schemaClass, config);

    for (FormatHandler handler : operation.handlers) {
        Path filePath = context.modulePath().resolve(
            config.getDestinationFileName() + handler.extension());
        Files.createDirectories(filePath.getParent());

        handler.writer = handler.createWriter(filePath);
        handler.open(context);
        new ObjectExportLoop(context, handler).run(dbSchemaClass);
        handler.close(context);
    }

    context.clearClass();
}
```

This replaces `ClassFileExporter` entirely — **`ClassFileExporter.java` is deleted**.

---

## Step 10 — Update `MigrationExportService`

**Delete `exportModulesSingleFormat()`** — the per-format loop is gone.

**Rewrite `exportModules()`**:

```java
public ExportStatistics exportModules(..., List<String> outputOptions) throws Exception {
    List<ExportFormat> formats = ExportOutputOption.toFormats(outputOptions);
    boolean generateXsd = ExportOutputOption.generatesXsd(outputOptions);

    List<FormatHandler> handlers = FormatHandler.create(formats, generateXsd);

    ExportOperation operation = new ExportOperation();
    // ... set all config fields on operation ...

    ExportContext context = new ExportContext(operation);
    context.basePath = operation.getBaseOutputPath(baseOutputPath);
    context.statistics = new ExportStatistics(monitor);
    context.referencedClassTracker = new ReferencedClassTracker();
    ExportUtil.registerAllModuleClasses(modules, context.referencedClassTracker);

    operation.handlers = handlers;
    operation.context = context;

    ExportEngine engine = new ExportEngine(operation);
    ExportStatistics result = engine.exportModules(modules, modulePaths);

    if (operation.saveToHistory) {
        ExportHistory.save(...);
    }

    return result;
}
```

No format-specific `if` statements remain in `MigrationExportService`.

**`repeatLastExport()`** — unchanged in logic; calls `exportModules()` with persisted options.

---

## Step 11 — Clean up `ExportOperation`

### Remove

| Field / Method | Moved to |
|---------------|----------|
| `outputFormat` (String) | Replaced by `ExportFormat` enum on each handler |
| `outputOptions` | Replaced by `List<FormatHandler> handlers` |
| `generateHtmlViewer` | Handler own state |
| `xmlWriter` | `FormatHandler.writer` |
| `xsdBuilder` | `XmlFormatHandler` own state |
| `sharedXSDBuilder` | `XmlFormatHandler` own state |
| `exportedXMLFiles` | `XmlFormatHandler` own state |
| `exportedObjectIds` (Set<Long>) | `FormatHandler.exportedIds` |
| `allowedObjectIds` | `ExportContext.allowedObjectIds` |
| `statistics` | `ExportContext.statistics` |
| `exportConfig` | `ExportContext.exportConfig` |
| `referencedClassTracker` | `ExportContext.referencedClassTracker` |
| `moduleStack` | `ExportContext.moduleChain` |
| `idEntiteTargetCache` | `HtmlFormatHandler` own state |
| `idEntiteSummaryCache` | `HtmlFormatHandler` own state |
| `navTree` | `HtmlFormatHandler` own state |
| `cachedNavJson` | `HtmlFormatHandler` own state |
| `useSharedTracking` | Concept removed — always true in new design |
| `objectExporter` | Removed (circular-reference workaround, no longer needed) |
| `getStructuredWriterAPI()` | Removed |
| `getOutputFileExtension()` | Removed (`handler.extension()`) |
| `isXMLFormat()` | Removed (no format checks in pipeline) |
| `shouldExportNativeIdsForCurrentFormat()` | Removed (`ExcelFormatHandler.init()` handles EXCEL) |
| `initializeSharedTracking()` | Removed |
| `resetSharedTracking()` | Removed |

### Add

```java
public List<FormatHandler> handlers
public ExportContext context
```

### Keep unchanged

`referenceSchema`, `databaseSchema`, `databasePath`, `dbContext`, `container`, `baseOutputPath`, `monitor`, `maxObjectsPerClass`, `exportNativeIds`, `applyUserSelectedFieldExclusions`, `applySkipWhenConditions`, `applyExportCriteriaFilters`, `skipObjectsWithoutExportableFields`, `saveToHistory`, `availableSkipUserOptions`, `selectedSkipUserOptions`, `getDatabaseFolderName()`, `getBaseOutputPath()`.

---

## Step 12 — Update `ExportOutputOption`

Rename the `HTML_JS = "HTML + JS"` constant to `HTML = "HTML"`. Remove `generatesHtmlViewer()` — HTML is now an explicit format, not a flag on another format.

Replace `toWriterFormat(String)` with:

```java
public static List<ExportFormat> toFormats(List<String> options)
public static boolean generatesXsd(List<String> options)
```

Update `fromPersistedToken()` to map both the old `"HTML + JS"` and `"JS"` tokens to `HTML` for backward compatibility with persisted options. `normalize()`, `parsePersistedOptions()`, and `toPersistedOptions()` are otherwise unchanged.

---

## Step 13 — Retire and internalize

| Class | Disposition |
|-------|-------------|
| `ClassFileExporter.java` | **Delete** — absorbed into `ExportEngine.exportClassToAllHandlers()` |
| `HtmlViewerTask.java` | **Delete** — absorbed into `HtmlFormatHandler.close()` |
| `ClassXsdWriter.java` | **Delete** — XSD writing absorbed into `XmlFormatHandler.close()` and `done()` |
| `ReferencedClassesExporter.java` | **Delete** — replaced by `FormatHandler.onReferencedClasses()` default impl |
| `StructuredWriterProvider.java` | **Delete** — no remaining callers |
| `JsViewerHtmlGenerator.java` | **Refactor into `HtmlViewerGenerator`** — the write-to-file method is replaced by a method that accepts the pre-built data script string and writes a single self-contained `.html` file. The separate JS-file-generation path is removed. |
| `XmlViewerHtmlGenerator.java` | **Keep** — called directly from `XmlFormatHandler.close()` if an XML HTML viewer is ever added back; otherwise can be removed if that feature is dropped |
| `NavTreeBuilder.java` | **Keep** — called from `HtmlFormatHandler.init()` |
| `ModulePathUtil.java` | **Keep** — `moduleId()` used by `ExportContext.modulePath()`; `getSchemaLocationForXml()` moves into `XmlFormatHandler` as a private helper |

---

## Verification

1. `mvn clean compile` — zero errors
2. Multi-format export (XML + XSD and HTML selected simultaneously) — both sets of output files produced for each class before moving to the next class
3. XML: XSD validation passes; `_Migration/Schema.xsd` present; `_Migration/Extra.xml` present for databases with unreached objects; per-class structure correct
4. HTML: each class produces a single self-contained `.html` file; viewer loads with sidebar navigation and correct `baseHref` at all nesting depths; no intermediate `.js` files on disk
5. JSON and EXCEL: output files match pre-refactoring baseline; EXCEL includes native IDs column
6. Referenced classes: `Referenced/` subfolder present in all selected formats independently
7. Export statistics (object counts, class counts, warning counts) match pre-refactoring baseline
8. `repeatLastExport()` works with persisted options, including old `"HTML + JS"` tokens

---

## Key Decisions

**`observeObject` / `observeField` separate from `onObject` / `onField`** — schema observation (XSD registration) and content writing are orthogonal concerns. Separating them means the pipeline never checks the format for XSD purposes; `XmlFormatHandler` registers itself silently, and all other handlers pay zero cost (no-op).

**`onReferencedClasses` has a default implementation** — the referenced-class export pattern is common to all formats. The default handles it generically by reusing `open`, the object loop, and `close` — so format-specific post-processing (HTML viewer for JS, XSD registration for XML) happens automatically for referenced-class files without extra code.

**Shared `ExportStatistics` and `ReferencedClassTracker` in `ExportContext`** — reach analysis and class discovery are format-independent. Only the dedup set (`FormatHandler.exportedIds`) is per-format, because the same object can appear in multiple formats without being a "duplicate" in the cross-format sense. This also eliminates the need to combine per-format statistics at the end.

**`allowedObjectIds` on `ExportContext`** — the unreached-objects pass gates the object loop on a specific allowed set. Rather than adding a parameter to the object loop, the context holds it as a nullable field (null = allow all). `XmlFormatHandler.done()` sets it, runs the unreached-objects loop, then clears it. The rest of the pipeline checks `ctx.allowedObjectIds` unchanged.

**`XmlFormatHandler.done()` computes unreached objects internally** — it has access to `ctx.statistics.exportedObjectIds` (the reach map) and `ctx.operation.databaseSchema` (all known object IDs). This moves the unreached-objects computation from `MigrationExportService` into the handler that actually cares about it, keeping `MigrationExportService` format-agnostic.

**Extension and display name belong to the handler, not the enum** — `ExportFormat` is a pure identifier. Each handler declares `extension()` and `displayName()`, so a new handler variant (e.g. a compact JSON format) can be added without modifying the enum. The engine uses `handler.extension()` everywhere; no format-name string comparisons.

**`HtmlFormatHandler` fuses JS data and HTML viewer into one output file** — the current flow (write `.js`, generate `.html` referencing it, delete `.js`) is replaced by writing the JS data object to an in-memory buffer and embedding it inline when `close()` assembles the final HTML file. The output is a single self-contained `.html` per class. `JsViewerHtmlGenerator` is refactored into `HtmlViewerGenerator` to accept the pre-built data script string rather than a file path.

**JSON and EXCEL are separate handler classes** — they share no code beyond the `FormatHandler` base class. Each is the natural home for format-specific behavior discovered during implementation.

**`ClassFileExporter` is deleted** — its only job was to bridge `ModuleExporter` and the per-class write sequence. In the new design that sequence lives in `ExportEngine.exportClassToAllHandlers()`, which is one natural method rather than an extra class.

**`ExportEngine.exportModule()` (deprecated) is deleted** — no active callers; removing it eliminates dead code that would have required its own format-handler upgrade.

**`StructuredWriterProvider` is retired** — it was only used to select the writer API via a string name. Each handler now instantiates its API directly, making the dependency explicit and removing indirection.

**No loose attribute maps flow between pipeline and handler** — all attributes (`id`, `_summary`, `skippedBecause`) are assembled from strongly typed context fields inside the handler's own hook methods. The pipeline never builds or passes attribute maps.
