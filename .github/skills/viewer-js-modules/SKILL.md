---
name: viewer-js-modules
description: Understand, navigate, or modify the modular JS source for the HTML viewer (sidebar nav + data viewer). Use this skill before editing any file in templates/scripts/, or when tracing a JS behaviour across modules.
---

# HTML Viewer — JS Module Structure

The viewer JS is split into **12 source files** under `src/main/resources/templates/scripts/`. At build/export time `JsViewerHtmlGenerator.loadSidebarNavJs()` concatenates them in order into one `<script>` block embedded in every HTML file.

> **Key constraint**: HTML files are opened locally (no server), so no `<script src="…">` links work. The concatenation happens in Java; the individual `.js` files are the editable source of truth.

---

## Module order and responsibilities

| # | File | Lines | Responsibility |
|---|---|---|---|
| 1 | `nav-sidebar.js` | ~440 | **Complete standalone IIFE** — navigation sidebar, tree rendering, keyboard nav, search filter, collapse persistence |
| 2 | `viewer-state.js` | ~115 | **Opens the data-viewer IIFE** — guard (`conditionsContainer` check), all shared `let`/`const` state, DOM element refs, `I18N` table, `OPERATORS`/`OPERATOR_LABELS`/`NO_VALUE_OPS` |
| 3 | `viewer-schema.js` | ~82 | `t()`, `esc()`, `normalizeSchemaPath`, schema field indexing (`schemaTitleByPath`/`Name`), `schemaTitleForPath`, IDEntite field set, `pointsToByPath`, nav href map |
| 4 | `viewer-data.js` | ~202 | `appendField`, `normalizeFieldPath`, `flattenValue`, `pickBest`, `summarize`, `buildRecord`, `collectFromNamedArray`, `getRootPayload`, `parsePayload`, `guessType`, `buildDiscoveredFields`, `getFieldType`, `buildFieldOptions` |
| 5 | `viewer-fieldpicker.js` | ~247 | `_buildTreeLevel`, `buildFieldTree`, `_countLeaves`, `_filterTree`, `createFieldPicker` (returns `{ element, getValue, setValue, refresh, destroy }`) |
| 6 | `viewer-search.js` | ~145 | `buildOperatorOptions`, `updateValueInput`, `addCondition`, `getConditions`, `matchCondition`, `ensureSearchColumnsVisible`, `applySearch` |
| 7 | `viewer-results.js` | ~148 | `getPageSize`, `getColumnLabel`, `getColumnValue`, `getVisibleColumns`, `renderColumnsMenu`, `renderColumnHeader`, `renderResultsHead`, `renderResults` |
| 8 | `viewer-formatting.js` | ~235 | `DATE_LOCALES`, `fmtDate`, `renderBoolValue`, `fmtValue`, `isNarrowField`, `isReferenceOnly`, `classifyFieldEntry`, `getObjectEntries`, `humanizeFieldName`, `formatSectionTitle`, `sectionTitleAttr`, `displayFieldLabel`, field classification helpers (`classifyPrimitiveBucket`, `extractCamelPrefix`, `groupByPrefix`, `fieldImportanceScore`, `sortPrimitiveEntries`), `refLinkBtn`, `extractPreviewSrc`, `renderPreview` |
| 9 | `viewer-rendering.js` | ~547 | `renderFieldRow`, `renderColumnGroup`, `renderPrimitiveGroup`, `appendRowValue`, `flattenRow`, `computeCollectionTable`, `renderCollectionTableBody`, `extractLayoutFields`, `renderCollectionSection`, `renderValue`, `renderReferenceRow`, `renderObjectSection` |
| 10 | `viewer-detail.js` | ~281 | `openDetailOverlay`, `closeDetailOverlay`, `selectRecord`, `findCurrentRecordIndex`, `updateDetailNav`, `navigateDetail`, `renderDetail`, `renderBackRefSection`, `renderBackRefRow` |
| 11 | `viewer-layout.js` | ~418 | `unwrapClassWrapper`, `resolveFieldValue`, `formatDatePattern`, `fmtValueWithFormat`, `renderLayoutDetail`, `renderLayoutNode`, `layoutStyleFont`, `renderLayoutChildren`, `bindTabEvents` |
| 12 | `viewer-wire.js` | ~179 | `applyLanguage`, `initialize`, all `addEventListener` wiring, **closes the data-viewer IIFE** |

---

## IIFE structure

```
nav-sidebar.js           ← (function(){ ... })();   [self-contained]

viewer-state.js          ← (function(){
viewer-schema.js             // functions sharing the closure
viewer-data.js               // …
...
viewer-wire.js               initialize(); })();    [closes IIFE]
```

All data-viewer functions can call each other freely — they share the same closure scope.

---

## Where things live (quick lookup)

| What you need | Where to look |
|---|---|
| Change i18n strings (French/English) | `viewer-state.js` — `I18N` object |
| Change operator labels or add an operator | `viewer-state.js` — `OPERATORS` / `OPERATOR_LABELS`; also `viewer-search.js` — `matchCondition` |
| Schema field title resolution | `viewer-schema.js` — `schemaTitleForPath` |
| Cross-page deep-link logic | `viewer-schema.js` — `pointsToByPath` + `navHrefByDestName` |
| Summary/field flattening from raw payload | `viewer-data.js` — `flattenValue`, `buildRecord` |
| Field picker tree | `viewer-fieldpicker.js` — `createFieldPicker` |
| Search/filter logic | `viewer-search.js` |
| Results table columns / pagination | `viewer-results.js` |
| Date/bool/number formatting | `viewer-formatting.js` — `fmtDate`, `fmtValue`, `fmtValueWithFormat` |
| Humanizing field names | `viewer-formatting.js` — `humanizeFieldName` |
| Reference link rendering | `viewer-formatting.js` — `refLinkBtn` |
| `renderPreview` (hero / thumb) | `viewer-formatting.js` — `renderPreview` |
| Collection table generation | `viewer-rendering.js` — `renderCollectionSection` |
| Object section layout (embedded/detail) | `viewer-rendering.js` — `renderObjectSection` |
| Detail overlay open/close/navigate | `viewer-detail.js` |
| Back-reference ("Références") section | `viewer-detail.js` — `renderBackRefSection` |
| Layout-driven rendering (CLASS_LAYOUTS) | `viewer-layout.js` — `renderLayoutNode` |
| Tab widget binding | `viewer-layout.js` — `bindTabEvents` |
| Event wiring / boot | `viewer-wire.js` — `initialize` |

---

## Java integration

`JsViewerHtmlGenerator.java` (in `migration4o.util`):
- `SIDEBAR_NAV_JS_RESOURCES` — ordered `String[]` of classpath resource paths; **add new files here** in position order.
- `loadSidebarNavJs()` — concatenates all files; result is cached in `cachedSidebarNavJs`.
- The placeholder `__SIDEBAR_NAV_JS__` in both HTML templates is replaced with this concatenated string.

To add a new module file:
1. Create `src/main/resources/templates/scripts/my-module.js`
2. Add its classpath path to `SIDEBAR_NAV_JS_RESOURCES` at the correct position.

---

## Notes

- The original `sidebar-nav.js` (parent of `scripts/`) is no longer used at runtime. It can be kept as a backup reference or deleted.
- Functions in one viewer module **may call functions defined in later modules** because they are all resolved at the same IIFE scope; ordering only matters for variable/const declarations, not function declarations.
- The `__EXPORT_LANGUAGE__` token in `viewer-state.js` is substituted by Java before embedding.
