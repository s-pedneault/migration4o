# HTML Viewer Architecture

## Overview

Each exported data file gets a companion standalone `.html` viewer generated alongside it. These viewers are fully self-contained — they embed all data inline and require no server. They are only generated when the dedicated **HTML+JS** export output option is selected.

---

## File Generation Pipeline

### Entry point
`MigrationExportService.exportModulesSingleFormat()`:
1. Calls `exporter.setModuleNavData(modules, modulePaths, baseOutputPath)` **before** the export loop — pre-builds the full navigation tree from the known module list.
2. Sets `generateHtmlViewer` flag on `ExportEngine`.
3. Runs the export loop; `ExportEngine.generateHtmlViewerIfNeeded()` is called after each file is written — it embeds the correct nav JSON for that specific file.

### Generator

The single active generator is `JsViewerHtmlGenerator` (in `migration4o.util`), used with the dedicated **HTML+JS** export option.

**`JsViewerHtmlGenerator.writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson, String baseHref)`**
- Reads the `.js` export file (which contains `window.__m4o = {...}`)
- Escapes `</script` inside it to avoid injection
- Substitutes `__BASE_HREF__`, `__NAV_ITEMS__`, `__TITLE__`, `__ENTITY_NAME__`, `__EMBEDDED_JS_DATA__` in the template
- Writes the `.html` sibling file with the same base name
- **Deletes** the original `.js` file after embedding it

A convenience overload `writeViewerForJs(Path, DOSchemaClass)` calls through with `"[]"` and `"./"` as fallbacks.

### Navigation sidebar — built once, resolved per file via `<base href>`

**`ExportEngine.setModuleNavData(List<MigrationModule> modules, List<String> modulePaths, String baseOutputDir)`**
- Called **once** before the export loop starts.
- `modules` and `modulePaths` are parallel lists (same index = same module).
- Builds a `NavNode` tree where each leaf carries a **root-relative** href (e.g. `Activités/Intervention/Intervention.html`).
- Modules whose path has one segment go directly into `navTree`; those with multiple segments are grouped under their first path segment.
- After building the tree, serialises it once into `cachedNavJson` — this JSON string is the same for every file in the export.

**`ExportEngine.computeBaseHref(Path outputPath)`**
- Called per-file inside `generateHtmlViewerIfNeeded()`.
- Counts the depth of the file's directory relative to the db export root.
- Returns a `"../../"` style string (e.g. depth 2 → `"../../"`).
- This value fills the template's `<base href>` tag so the root-relative hrefs in `NAV_ITEMS` resolve correctly from any nested directory.

**No `current` marking in the JSON** — current-page detection is handled entirely in JavaScript by comparing each leaf's resolved URL to `location.href` (see sidebar behaviour below).

> **`HtmlNavPostProcessor` is no longer invoked** for JS exports. The class still exists but `MigrationExportService` no longer calls it for the JS HTML viewer flow.

---

## Template: js-viewer-template.html

Located at `src/main/resources/templates/js-viewer-template.html`.
Maven copies it to `classes/templates/` during `mvn clean compile` (output directory configured in `pom.xml`).

### Template placeholder tokens

| Token | Replaced with |
|---|---|
| `__BASE_HREF__` | Root-relative base path for this file (e.g. `../../`) — sets the `<base href>` tag so nav hrefs resolve from any depth |
| `__NAV_ITEMS__` | JSON array of nav nodes (e.g. `[{"label":"Activités","children":[...]}]`) — **same JSON for every file** |
| `__TITLE__` | HTML-escaped file base name (e.g. `Intervention`) |
| `__ENTITY_NAME__` | Human-readable entity name from `schemaClass.destinationName` |
| `__EMBEDDED_JS_DATA__` | Full JS file contents (the `window.__m4o = {...}` payload) |

### Navigation block in template

```html
<script>
    const NAV_ITEMS = __NAV_ITEMS__;
</script>
```

Placed **before** the main viewer `<script>`, so `NAV_ITEMS` is a global when the viewer script runs.

### Page structure

```
<header class="app-header">
  [☰ toggle btn]  [logo M]  [entity name h1]  [lang select]  [file badge]

<div class="app-layout">          ← flex row
  <nav class="nav-sidebar" id="navSidebar">
    <div class="nav-sidebar-header">Modules</div>
    <div class="nav-sidebar-scroll" id="navSidebarScroll">  ← rendered by JS from NAV_ITEMS
  </nav>
  <div class="app-body">
    <div class="panel-left">
      search-section (conditions)
      results-section (table + pagination)
    </div>
  </div>
</div>

<div class="detail-overlay">      ← modal slide-up for record detail
```

### Sidebar behaviour (JS IIFE at bottom of script)

- Reads `NAV_ITEMS` global; renders recursively via `renderNavItem(item, depth)`.
- Folder nodes (items with `children`) → collapsible `<button>` + `<div class="nav-group-children">`.
- Leaf nodes (items with `href`) → `<a class="nav-item-link">` tags.
- **Current-page detection is pure JS**: `isCurrentPage(href)` resolves `new URL(item.href, document.baseURI).href` and compares to `location.href`. No `current` flag is ever injected into the JSON.
- Groups containing the current page are auto-expanded on load via `hasCurrentDescendant(item)`.
- The current-page link gets class `nav-current` (blue highlight + left border).
- If `NAV_ITEMS` is empty, falls back to rendering `—` in the sidebar.
- Toggle button collapses/expands via `.collapsed` CSS class; state persisted via `localStorage` key `m4o_nav_collapsed`.

---

## MigrationModule → Nav mapping

`modules` and `modulePaths` are parallel lists maintained by `MigrationExportService`:
- `modules` comes from `DOModuleService.getInstance().getModules()` (loaded from `schema/migration-format.xml`).
- `modulePaths` is built by calling `ExportUtil.findModulePathByName(moduleName)` per module — produces slash-separated paths like `Activités/Intervention`.
- The first path segment (e.g. `Activités`) becomes the group label in the nav.
- The last path segment (e.g. `Intervention`) is used to derive the expected `.html` filename.

---

## IDEntite / Summary Feature

This feature enriches the HTML viewer with **human-readable record summaries** and **resolved reference labels**, replacing raw numeric IDs with meaningful text. It is active only during JS-format exports.

**⚠️ Performance bottleneck** — see notes below.

### What it does

1. **Record summary** (`_summary` attribute): When a `DOSchemaClass` has a `summary` template (e.g. `"[prenom] [nom] (dossier [numeroDossier])"`), `SummaryGenerator.generate()` evaluates it against each exported object and stores the result in the `_summary` attribute of that record's XML element. The HTML viewer picks it up in `buildRecord()` and stores it as `rec.summary`, displaying it in the **Summary** column (default visible) and as hover text in record detail.

2. **IDEntite scalar label**: When a field holds a non-embedded IDEntite reference (`embedContents=false`), its `mID` number is normally exported as-is. During JS export, `FieldExporter` calls `SummaryGenerator.resolveIDEntiteLabel()` to replace the raw `mID` column with the target entity's human-readable summary. The field tag is also renamed by stripping the `ID`/`Id` prefix (e.g. `IDPersonne` → `Personne`) so column headers read naturally.

3. **IDEntite structure label**: When an IDEntite is exported as an embedded structure (`embedContents=true`), `ObjectExporter` calls `SummaryGenerator.resolveIDEntiteLabel()` and, if a label is found, writes a flat `<element>label text</element>` instead of a nested structure.

### Data model

- **`DOSchemaClass.summary`**: A template string defined in `reference-schema.xml`. Syntax: literal text plus `[destinationName]` tokens. Tokens may be dot-paths one level deep (e.g. `[adresse.rue]`).
- **`DOSchemaClass.pointsTo`**: For IDEntite classes — the simple name of the target entity class (e.g. `Personne`). Used to narrow the scan when resolving references.
- **`DOSchemaClass.isIDEntite(DOSchema)`**: Returns `true` if the class descends from `gest.gen.IDEntite` (checks via `SchemaUtil.isDescendantOf`).

### Call chain

```
FieldExporter (scalar IDEntite field)
  └─ SummaryGenerator.resolveIDEntiteLabel(container, idEntiteObj, idEntiteClass, refSchema, dbSchema)
       ├─ ReferenceUtil.resolveIDEntiteReference(container, idEntiteObj, expectedType, dbSchema)
       │    ├─ activateObjectShallow → read mID from the IDEntite object
       │    └─ ReferenceUtil.findObjectByMID(container, mID, expectedType, dbSchema)   ← ⚠️ O(n) scan
       ├─ container.ext().getByID(targetObjectId) + activate
       └─ SummaryGenerator.generate(container, targetObj, targetSchemaClass, refSchema)
            └─ resolveToken() per [field] token → getStoredFieldValue per segment

ObjectExporter (root record or embedded IDEntite structure)
  ├─ SummaryGenerator.generate()          → _summary attribute (record summary)
  └─ SummaryGenerator.resolveIDEntiteLabel()  → flat label element (IDEntite structure)
```

### Performance bottleneck — `ReferenceUtil.findObjectByMID()`

`findObjectByMID()` resolves an IDEntite reference by scanning **all objects of the expected entity type** in the database, activating each one and comparing its `mID` field:

```java
for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
    if (!schemaClass.isEntite(databaseSchema)) continue;
    for (long objectId : schemaClass.objectIds) {
        Object obj = container.ext().getByID(objectId);
        activateObjectShallow(container, obj, objectId);
        Long objMID = extractMIDField(container, obj);
        if (mID.equals(objMID)) return objectId;
    }
}
```

**Cost per IDEntite reference field**: O(n) object activations, where n = number of objects in the target entity class. With large tables and many fields per record this compounds heavily.

**Known improvement opportunities**:
- **Build a `mID → objectId` index** once per entity class (e.g. `Map<Long, Long>` keyed by mID), populated on first resolution for that class, reused for all subsequent lookups.
- **Cache resolved labels** by `(idEntiteObjectId → label)` so the same IDEntite value encountered in multiple records is only resolved once.
- The index and label cache could be held in `ExportEngine` for the lifetime of a single export run, then discarded.

### UI — Summary Editor

`SummaryEditorDialog` (in `migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs`) allows editing the `summary` template for any schema class. IDEntite classes are highlighted differently in the dialog (their summaries define the labels shown when other records reference them).

---

## Output Directory Structure

```
output/
  <db-folder>/                    ← e.g. 54060/
    _Migration/
      Extra.html
      Schema.xsd
    Activités/
      Intervention/
        Intervention.html         ← leaf viewer (nav shows all modules)
        CodeAppel.html
      Prévention/
        Prevention.html
    Matériel/
      Bornes et points d'eau/
        BorneIncendie.html
        Paramètres/
          AnomBorne.html
    ...
```

HTML files replace the `.js` export files (JS is embedded then deleted). The nav sidebar lists every module with relative hrefs from each file's location.

---

## Build System

**Maven outputs directly to `classes/`** — configured in `pom.xml`:
```xml
<outputDirectory>${project.basedir}/classes</outputDirectory>
```
`run-ui.sh` uses `-cp "classes:lib/*:..."`. Therefore **`mvn clean compile` is the only step needed** to build and deploy. Never manually copy files to `classes/`.

---

## Template Caching

`JsViewerHtmlGenerator` caches the loaded template string in a `private static volatile String cachedTemplate` field (double-checked locking). The cache is never invalidated within a JVM run — restart the application to pick up template changes.

---

## Adding a New Template Token

1. Add placeholder (e.g. `__MY_TOKEN__`) anywhere in `js-viewer-template.html`
2. In `JsViewerHtmlGenerator.writeViewerForJs()`, chain `.replace("__MY_TOKEN__", value)` onto the template substitution
3. Run `mvn clean compile` — Maven copies the updated template to `classes/`

---

## Key Files

| File | Role |
|---|---|
| `src/main/resources/templates/js-viewer-template.html` | Viewer template (source of truth) |
| `classes/templates/js-viewer-template.html` | Runtime copy — generated by Maven, do not edit directly |
| `migration4o/util/JsViewerHtmlGenerator.java` | Writes the HTML viewer; accepts `navItemsJson` and `baseHref` as 3rd/4th parameters |
| `migration4o/migration/ExportEngine.java` | `setModuleNavData()` builds nav tree + `cachedNavJson` once; `computeBaseHref()` computes per-file depth string; `generateHtmlViewerIfNeeded()` wires them |
| `migration4o/migration/MigrationExportService.java` | Calls `setModuleNavData()` before export loop; no post-processing step |
| `migration4o/util/HtmlNavPostProcessor.java` | Legacy post-processor — no longer called for JS viewer flow |
| `migration4o/migration/SummaryGenerator.java` | Generates `_summary` strings from template; resolves IDEntite labels |
| `migration4o/util/ReferenceUtil.java` | `resolveIDEntiteReference()` / `findObjectByMID()` — mID linear scan (performance bottleneck) |
| `migration4o/migration/ObjectExporter.java` | Embeds `_summary` attribute and resolves IDEntite structures during JS export |
| `migration4o/migration/FieldExporter.java` | Resolves IDEntite scalar fields to human-readable labels during JS export |
