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

**`JsViewerHtmlGenerator.writeViewerForJs(Path jsPath, DOSchemaClass schemaClass, String navItemsJson)`**
- Reads the `.js` export file (which contains `window.__m4o = {...}`)
- Escapes `</script` inside it to avoid injection
- Substitutes `__NAV_ITEMS__`, `__TITLE__`, `__ENTITY_NAME__`, `__EMBEDDED_JS_DATA__` in the template
- Writes the `.html` sibling file with the same base name
- **Deletes** the original `.js` file after embedding it

A convenience overload `writeViewerForJs(Path, DOSchemaClass)` calls through with `"[]"` as fallback nav.

### Navigation sidebar — built at generation time

**`ExportEngine.setModuleNavData(List<MigrationModule> modules, List<String> modulePaths, String baseOutputDir)`**
- Called once before export starts.
- `modules` and `modulePaths` are parallel lists (same index = same module).
- For each module, records its `name` (label) and expected main HTML path: `<dbBasePath>/<modulePath>/<leafSegment>.html`.
- Modules whose path has one segment go into `navRoots`; those with multiple segments are grouped by their first path segment into `navGroups` (`LinkedHashMap`, insertion order preserved).

**`ExportEngine.buildNavJsonForFile(Path currentFile)`**
- Called per-file inside `generateHtmlViewerIfNeeded()`.
- Serialises `navRoots` as top-level leaf nodes and `navGroups` as group objects with `children`.
- Each leaf gets an `href` relative from `currentFile.getParent()` to the module's target HTML file.
- A leaf is marked `"current": true` when `currentFile` is inside that module's folder (`currentFile.startsWith(target.getParent())`).

> **`HtmlNavPostProcessor` is no longer invoked** for JS exports. The class still exists but `MigrationExportService` no longer calls it for the JS HTML viewer flow.

---

## Template: js-viewer-template.html

Located at `src/main/resources/templates/js-viewer-template.html`.
Maven copies it to `classes/templates/` during `mvn clean compile` (output directory configured in `pom.xml`).

### Template placeholder tokens

| Token | Replaced with |
|---|---|
| `__NAV_ITEMS__` | JSON array of nav nodes (e.g. `[{"label":"Activités","children":[...]}]`) |
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
- Groups containing the current page (`current: true` on a descendant) are auto-expanded on load.
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
| `migration4o/util/JsViewerHtmlGenerator.java` | Writes the HTML viewer; accepts `navItemsJson` 3rd parameter |
| `migration4o/migration/ExportEngine.java` | `setModuleNavData()` builds nav tree; `buildNavJsonForFile()` computes per-file JSON; `generateHtmlViewerIfNeeded()` wires them |
| `migration4o/migration/MigrationExportService.java` | Calls `setModuleNavData()` before export loop; no post-processing step |
| `migration4o/util/HtmlNavPostProcessor.java` | Legacy post-processor — no longer called for JS viewer flow |
