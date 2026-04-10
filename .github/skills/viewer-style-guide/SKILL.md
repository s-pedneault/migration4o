---
name: viewer-style-guide
description: Refine visual components in the JS viewer HTML output, update sample data or layout coverage in the style guide, and regenerate doc/style-guide.html. Use this skill when asked to fix rendering, tweak styles, adjust layout, update the style guide, or add new data scenarios to the style-guide generator.
---

# Viewer Style Guide

## Purpose

`doc/style-guide.html` is a **self-contained HTML viewer** — identical in structure to every real export page — populated with crafted synthetic data that exercises every rendering path. It exists so we can refine viewer components in isolation without needing a database export.

---

## Key files

| File | Role |
|---|---|
| `src/main/java/migration4o/util/StyleGuideGenerator.java` | Builds all sample data and layout JSON, calls `writeRawViewer` |
| `src/main/java/migration4o/util/JsViewerHtmlGenerator.java` | `writeRawViewer()` — assembles the HTML from template + JS modules + injected JSON |
| `src/main/resources/templates/class-viewer-template.html` | HTML shell with `__PLACEHOLDER__` tokens |
| `src/main/resources/templates/sidebar.css` | All viewer CSS (injected via `__SIDEBAR_CSS__`) |
| `src/main/resources/templates/scripts/viewer-*.js` | JS modules concatenated into `__SIDEBAR_NAV_JS__` |
| `doc/style-guide.html` | **Generated output** — never edit by hand |

---

## Regenerate the style guide

After changing any of the above source files:

```bash
mvn clean compile && java -cp classes:lib/* migration4o.util.StyleGuideGenerator
```

Then open (or refresh) `doc/style-guide.html` in the browser.

> **Always recompile before running** — `javac` output lands in `classes/`, which is the classpath used above.

---

## What the style guide covers

`StyleGuideGenerator` builds **3 synthetic records** of entity type `StyleGuideRecord`:

| Record | ID | Purpose |
|---|---|---|
| Alice Tremblay | 1001 | Full data: every field type present, IDEntite with nav-link → dark-blue button, cross-refs → tabbed back-ref section |
| Bâtiment B | 1002 | Sparse data: nulls, empty string, empty collection, absent embedded object |
| Point B-03 | 1003 | IDEntite regression: `typeContact` column in collection table must render as dark-blue link buttons (not yellow badges), cross-refs → single-group back-ref section |

### Layout nodes exercised

| Node type | Where in layout |
|---|---|
| `section` (non-collapsible) | "Informations générales" |
| `field` — all scalar types | Inside sections and columns |
| `field` with format specs | `bool:Oui,Non`, `date:yyyy-MM-dd`, `longdate:yyyy-MM-dd HH:mm`, `num:#,##0.0 %`, `num:#,##0.0 m` |
| `field` with IDEntite ref | `typeSysteme` (with nav href → link; record 3 uses an absent href → plain text) |
| `field` with array value | `tags` — routed through `renderValue` |
| `divider` (full + small) | Between info and columns, and before tables |
| `columns` (2-column) | Counts/dates column pair |
| `section` (collapsible + ref) | `adresse` — small object renders inline |
| `tabs` / `tab` | "Données spatiales" → two tabs: coordonnées + dimensions |
| `table` with `columnTitles` | `contacts` — includes IDEntite `typeContact` column (link regression) |
| `table` (auto columns) | `interventions` — includes IDEntite `auteur` column |

### Schema/nav JSON injected

- **`SCHEMA_FIELDS`** — titles, IDEntite flags, `pointsTo` for all fields (consumed by `viewer-schema.js`)
- **`NAV_ITEMS`** — 4 nav leaves: `style-guide.html`, `TypeSysteme.html`, `TypeContact.html`, `Auteur.html`
- **`DETAIL_LAYOUT`** — hand-crafted JSON matching the layout node list above
- **`CLASS_LAYOUTS`** — `{}` (no custom popup layouts; exercises automatic popup rendering)
- **`CROSS_REFS`** — record 1001 → tabbed 2-entity back-refs; record 1003 → 2-entry single-entity back-refs

---

## Debug overlay mode

`doc/style-guide.html` has a **○ Debug** toggle button fixed at the bottom-right of the page. Clicking it adds the `sg-debug` class to `<body>`, which activates:

- A colored **outline** on every rendered structure
- A **corner badge** (top-right of each box) showing the CSS class name

| Color | Structures |
|---|---|
| Blue (`#1d4ed8`) | `detail-section` |
| Green (`#15803d` / `#16a34a`) | `layout-columns`, `layout-column` |
| Purple (`#7c3aed`, `#6d28d9`, `#a855f7`) | `layout-tabs`, `tab-bar`, `tab-panel` |
| Orange / Amber (`#c2410c`, `#b45309`, `#d97706`) | `field-group`, `field-pair`, `field-columns-2/3` |
| Red (`#be123c`) | `field-row` |
| Teal / Cyan (`#0f766e`, `#0e7490`, `#0369a1`) | `back-ref-section`, `back-ref-list`, `back-ref-row` |
| Gray (`#475569`) | `detail-scroll` |
| Gold (`#92400e`) | `hr.layout-divider` (outline only — void element, no badge) |
| Dark teal (`#0f766e`) | `collection-table` |

The debug overlay is injected as a post-processing step in `StyleGuideGenerator.postProcessDebugMode()` and does **not** affect any production JS or CSS. The `sidebar.css` and all `viewer-*.js` files are untouched.

When reporting a rendering issue, enable Debug mode, screenshot the area, and reference the badge label (e.g. "the `tab-panel` containing the contacts table").

---

## Workflow: refining a component

1. **Read the relevant JS module** using the `viewer-js-modules` skill to locate the exact function.
2. **Edit the `.js` source file** under `src/main/resources/templates/scripts/`.
3. If you need new data scenarios (e.g. to cover a newly found edge case), also update `StyleGuideGenerator.java`.
4. **Rebuild and regenerate**:
   ```bash
   mvn clean compile && java -cp classes:lib/* migration4o.util.StyleGuideGenerator
   ```
5. **Refresh the browser** on `doc/style-guide.html` and verify all previously working scenarios still render correctly.
6. If CSS is involved, edit `sidebar.css` (no compile step needed for CSS — but still regenerate so the new CSS is embedded).

> **Do not edit `doc/style-guide.html` directly.** It is overwritten on every regeneration.

---

## Adding new scenarios to the generator

Add or modify methods inside `StyleGuideGenerator.java`:

| What to change | Where |
|---|---|
| New data record | Add a `recordN()` method; include it in `buildDataJson()` array |
| New layout node | Add to `buildDetailLayout()` return array |
| New schema field title or IDEntite mapping | Add entry to `buildSchemaFields()` |
| New nav page for cross-linking | Add entry to `buildNavJson()` |
| New cross-ref entries | Add to `buildCrossRefs()` |
| New CLASS_LAYOUTS entry | Change `buildClassLayouts()` from `"{}"` to a proper JSON object |

The `s()`, `b()`, `n()`, `l()`, `kv()`, `ks()`, `obj()`, `arr()` helpers are the micro-DSL for building JSON — use them instead of string concatenation to keep the code readable.

---

## Template placeholder reference

| Placeholder | Replaced with |
|---|---|
| `__SIDEBAR_CSS__` | Full content of `sidebar.css` |
| `__SIDEBAR_NAV_JS__` | Concatenated JS from all 12 `viewer-*.js` modules |
| `__EXPORT_LANGUAGE__` | `"fr"` by default |
| `__BASE_HREF__` | `"./"` (style guide is in `doc/`) |
| `__NAV_ITEMS__` | `NAV_ITEMS` array JSON |
| `__DETAIL_LAYOUT__` | `DETAIL_LAYOUT` array JSON |
| `__CLASS_LAYOUTS__` | `CLASS_LAYOUTS` object JSON |
| `__SCHEMA_FIELDS__` | `SCHEMA_FIELDS` array JSON |
| `__DEFAULT_COLUMNS__` | `"null"` |
| `__TITLE__` | Browser tab title |
| `__ENTITY_NAME__` | Entity name shown in the header |
| `__EMBEDDED_JS_DATA__` | `window.__m4o = {...};` data script |
| `null/*XREF*/` | `CROSS_REFS` JSON object |
