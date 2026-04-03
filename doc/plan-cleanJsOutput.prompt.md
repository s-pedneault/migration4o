# Plan: Clean JS Output + _preview + Unified Rendering

## TL;DR
Rewrite `StructuredWriterJS` to produce JS-native data instead of XML-shaped JSON. Add `openArray`/`closeArray` to the writer API, strip all 35+ unwrapping patterns from `sidebar-nav.js`, promote `_preview` to a first-class reserved property alongside `_summary`, and unify the fragmented rendering code paths (auto-discovery AND layout-driven) into a `renderValue()` dispatch tree with three rendering contexts (`detail`, `embedded`, `tabular`). XML untouched. Streaming preserved.

## Problem
`StructuredWriterJS` mimics XML conventions, producing:
- **Forced arrays**: `"nom":["value"]` instead of `"nom":"value"` (13 JS unwrap sites)
- **@attributes wrapper**: `{"@attributes":{"_id":"123"},"#text":"Faible"}` instead of `{_id:"123",_label:"Faible"}` (20 JS sites)
- **Class-name wrappers**: `{"Fichier":[{...}]}` instead of `{_class:"Fichier",...}` (6 JS sites)

Additionally, `sidebar-nav.js` has grown **two parallel rendering systems** (auto-discovery mode and layout-driven mode), each with their own unwrapping/access logic, and within auto-discovery there are 4+ sub-paths: `renderFieldRow`, `renderNodeSection`, `renderInlineIdEntiteSection`, `renderCollectionSection`. The same type of embedded object renders inconsistently depending on which path reaches it.

## Target Data Contract

- Scalar field: `nom: "value"`
- IDEntite reference (non-embedded): `idCategorieRisque: {_id: "12568653", _label: "Faible"}`
- IDEntite reference (embedded): `idFichier: {_class: "Fichier", _id: "51790381", _summary: "290 Mont Echo.jpg", _preview: "<img src='...' />", nom: "290 Mont Echo.jpg", ...}`
- Nested object (non-IDEntite): `adresse: {_class: "Adresse", numeroCivique: "123", ...}`
- Collection (real array): `listePieceJointe: [{_class: "PieceJointe", ...}, {...}]`
- Root record: `{_class: "DossierAdresse", _id: "42", _summary: "123 Rue Principale", _preview: "<img src='...' />", nom: "...", ...}`

Reserved property conventions:
- `_class` — type discriminator (replaces class-name wrapper keys)
- `_id` — object/reference ID (replaces `@attributes._id` / `@attributes.id`)
- `_summary` — human-readable label (replaces `@attributes._summary`)
- `_preview` — optional inline preview HTML (replaces `@attributes._preview`)
- `_label` — display text when content has attributes (replaces `#text`)
- Collections are real JS arrays — no single-element wrapping
- Scalars are direct values, not arrays

## Constraints
- **XML must not change** — `StructuredWriterXML` untouched; XSD validation still passes
- **Streaming preserved** — write to disk incrementally, no in-memory buffering
- **Single-pass replacement** — no dual-format coexistence in sidebar-nav.js
- **FieldExporter/ObjectExporter shared** — changes backward-compatible via API defaults

---

## Phase 1: Evolve StructuredWriterAPI (interface + element model)

### 1.1 Add openArray/closeArray to StructuredWriterAPI interface
- `default void openArray(StructuredWriterElementWithStructure element)` → delegates to `openStructure(element)`
- `default void closeArray(StructuredWriterElementWithStructure element)` → delegates to `closeStructure(element)`
- XML gets these defaults automatically — zero XML impact
- **File**: `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterAPI.java`

### 1.2 Add openArray/closeArray to StructuredWriter façade
- Add public `openArray(String name)` / `openArray(String name, Map<String,String> attrs)` and `closeArray(String name)`
- **Extract shared private method** `openElement(String name, Map attrs, boolean isArray)` from current `openStructure()` logic (L84-89): creates `StructuredWriterElementWithStructure`, calls `pushElement()`, sets `element.isArray`, routes to `api.openArray()` or `api.openStructure()`, writes `element.prefix`
- **Extract shared private method** `closeElement(String name, boolean isArray)` from current `closeStructure()` logic (L92-137): includes the mismatch-recovery stack search; routes to `api.closeArray()` or `api.closeStructure()` based on element's `isArray` flag
- Both `openStructure` and `openArray` become thin wrappers calling `openElement`, same for close
- **File**: `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriter.java`

### 1.3 Add isArray flag to StructuredWriterElement
- `public boolean isArray = false` on the base class (alongside existing `hasWrittenChild`, `openChildArrayName`, etc.)
- Justification: array lifecycle is identical to structure (open/children/close), so a subclass adds no value — a flag is sufficient.
- **File**: `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterElement.java`

### 1.4 Verify XML unchanged
- Build + export with XML format → diff output against pre-change baseline
- XSD validation must still pass

---

## Phase 2: Rewrite StructuredWriterJS

### 2.1 New output logic

Replace the `beginValue()`/`openChildArray()`/`closeOpenChildArray()` mechanism (L88-122) with explicit array/object context tracking.

Current mechanism: every child element with the same name inside a parent is auto-grouped into an array (`"name":[item1, item2]`) — this is the root cause of forced arrays. The new writer uses the explicit `isArray` flag from Phase 1 to decide.

Element state (simplified):
- `isArray` (from Phase 1) — is this element an array context?
- `hasWrittenChild` — for comma placement between siblings

All output cases enumerated:

| Method | Parent is array | Has attributes | Output |
|--------|----------------|----------------|--------|
| `openStructure` | no | no | `"name":{` |
| `openStructure` | no | yes | `"name":{_id:"x",_summary:"y",_preview:"...",` |
| `openStructure` | yes | no | `{_class:"Name",` |
| `openStructure` | yes | yes | `{_class:"Name",_id:"x",_summary:"y",_preview:"...",` |
| `openArray` | no | — | `"name":[` |
| `openArray` | yes | — | `[` (rare: nested array) |
| `addContent` | no | no | `"name":"text"` |
| `addContent` | no | yes | `"name":{_id:"x",_label:"text"}` |
| `addContent` | yes | no | `"text"` |
| `addContent` | yes | yes | `{_id:"x",_label:"text"}` |
| `add` (no content) | no | no | `"name":null` |
| `add` (no content) | no | yes | `"name":{_id:"x"}` |
| `add` (no content) | yes | no | `null` |
| `add` (no content) | yes | yes | `{_id:"x"}` |
| `closeStructure` | — | — | `}` |
| `closeArray` | — | — | `]` |

Comma rule: prepend `,` if `parent.hasWrittenChild == true`, then set `parent.hasWrittenChild = true`.

### 2.2 Break output methods into clear private helpers
- `writeKey(element)` — writes `"name":` or nothing if parent is array
- `writeComma(element)` — handles comma before element based on parent.hasWrittenChild
- `writeInlineAttributes(attrs)` — writes reserved properties in fixed order: `_id`, `_summary`, `_preview`, then remaining `_*` keys. Reuses existing `appendQuoted()` and `escapeJson()`.
- `writeOpenObject(element)` — composes: comma + key + `{` + `_class` (if array parent) + attrs
- `writeOpenArray(element)` — composes: comma + key + `[`
- `writeScalar(element, content)` — composes: comma + key + quoted content (or `{attrs + _label}`)

### 2.3 Remove dead state usage
- `openChildArrayName` / `openChildArrayHasElements` are no longer read/written by JS writer methods. Fields remain on base class `StructuredWriterElement` for JSON writer (`StructuredWriterJSON.java`) compatibility.

### 2.4 Mismatch recovery
- `closeArray()` in `StructuredWriterJS` reuses same stack-mismatch logic via shared `closeElement()` from Phase 1.2.

### 2.5 initialize / onDocumentComplete unchanged
- `initialize()`: write `window.__m4o={` (L26-27)
- `onDocumentComplete()`: write `;\n` (L35-36)
- `includeCollectionSizeMetadata()`: still returns `false` (L30-32)

**File**: `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterJS.java` (full rewrite)

---

## Phase 3: Update callers to use openArray for collections

### 3.1 FieldExporter collection paths

**`exportCollectionLikeField()` (L295-369):**
- L314: `xmlWriter.openStructure(fieldName, ...)` → `xmlWriter.openArray(fieldName, ...)`
- L369: `xmlWriter.closeStructure(fieldName)` → `xmlWriter.closeArray(fieldName)`
- Note: individual items inside the collection are NOT wrapped in "entry" elements — they're objects rendered by ObjectExporter directly, which correctly uses `openStructure`. No change needed for items.

**`exportMapField()` (L406-439):**
- L422: `xmlWriter.openStructure(fieldName, ...)` → `xmlWriter.openArray(fieldName, ...)`
- L438: `xmlWriter.closeStructure(fieldName)` → `xmlWriter.closeArray(fieldName)`
- L427/L437: The inner `<entry>` wrappers stay as `openStructure`/`closeStructure` — each map entry is an object, not an array.

**`exportStandaloneCollectionItems()` (L513-587):**
- L517: `xmlWriter.openStructure("items", null)` → `xmlWriter.openArray("items", null)` (Map case)
- L529: `xmlWriter.closeStructure("items")` → `xmlWriter.closeArray("items")`
- L567: `xmlWriter.openStructure("items", null)` → `xmlWriter.openArray("items", null)` (Collection case)
- L578: `xmlWriter.closeStructure("items")` → `xmlWriter.closeArray("items")`
- L521/L528 and L572/L577: Inner `<entry>` wrappers stay as structures.

**File**: `src/main/java/migration4o/migration/FieldExporter.java`

### 3.2 No changes needed (verified)
- **HtmlFormatHandler** (L226, L261): `openStructure` for objects — correct, these are objects not arrays
- **ObjectExporter**: only calls `closeStructure` (L131) matching handler's open — correct
- These files remain unchanged.

---

## Phase 4: Simplify sidebar-nav.js data access

*This phase updates all data access patterns to work with the clean format. No rendering logic changes yet.*

### 4.1 Remove all unwrapping patterns

**`@attributes` access (20 sites):**
- `flattenValue()` (L696): replace `value['@attributes']` with direct `._id`, `._summary`, `._preview` checks; skip `_class`
- `buildRecord()` (L732-734): `raw._id` instead of `raw['@attributes'].id`; `raw._summary` instead of `attrs._summary`; `raw._preview` instead of `attrs._preview`
- `mergeAttributes()` (L1483-1492): **delete entirely** — no more attribute objects to merge
- `getObjectEntries()` (L1494-1514): rewrite — no `@attributes`/`#text` extraction; iterate all keys, skip `_*`-prefixed ones for primitives, classify rest normally
- `renderInlineIdEntiteSection()` (L1978-1992): access `._id`, `._summary`, `._preview` directly — but this function is deleted in Phase 5
- `expandWrapperCollectionItems()` (L1853-1897): **delete entirely** — no class-name wrappers to expand
- `renderLayoutNode()` section case (~L2683): `_sData['@attributes']['_preview']` → `_sData._preview`
- `flattenRow()` (L1793-1828): replace `value['@attributes']` extraction with `._id`, `._summary`, `._preview` direct reads

**`#text` access (20 sites):**
- Replace all `value['#text']` reads with `value._label`
- `flattenValue()` (L699-700): `value._label` instead of `value['#text']`
- `getObjectEntries()` (L1504): `value._label` instead of `value['#text']`
- `renderInlineIdEntiteSection()` (L1978-1980): `value._label` instead of `value['#text']` (deleted in Phase 5)
- `flattenRow()` (L1821-1822): `value._label`

**Single-element array unwrapping (13 sites):**
- Delete all `if (Array.isArray(x) && x.length === 1) x = x[0]` patterns at:
  - `unwrapEmbeddedValue()` (L1462-1481): **delete function entirely** — no wrapper objects to unwrap
  - `renderInlineIdEntiteSection()` (L2009): deleted with function
  - `renderLayoutNode()` section case (~L2676-2686): simplify
  - `renderLayoutNode()` field case (~L2723-2752): simplify
  - `classifyFieldEntry()` (L1624-1672): simplify — single-element arrays no longer need special handling

**Class-name wrapper detection (6 sites):**
- Delete all `/^[A-Z]/.test(key)` wrapper detection blocks
- Delete `unwrapEmbeddedValue()` entirely (L1462-1481) — no wrappers to unwrap
- `renderLayoutNode()` section (~L2680-2686): remove wrapper detection, use `._preview` directly
- `expandWrapperCollectionItems()`: already deleted above

### 4.2 Rewrite `classifyFieldEntry()` (L1624-1672)
Simplified logic (no unwrapping needed):
- `null/undefined` → `{type: 'primitive', value: ''}`
- `Array.isArray(value)` → `{type: 'collection', value}` (all arrays are now real collections)
- `typeof value === 'object'` → check `isReferenceOnly(value)` → if true, `{type: 'reference'}`, else `{type: 'object'}`
- otherwise → `{type: 'primitive', value}`
- Delete calls to `unwrapEmbeddedValue()` and `isLikelyCollectionField()` — no longer needed

### 4.3 Rewrite `getObjectEntries()` (L1494-1514)
New logic:
- Iterate `Object.entries(value)`
- Skip `_`-prefixed keys (`_class`, `_id`, `_summary`, `_preview`, `_label`)
- For each remaining key, call simplified `classifyFieldEntry(key, val)`
- No `@attributes` extraction, no `#text` extraction

### 4.4 Rewrite `flattenValue()` (L685-710) and `flattenRow()` (L1793-1828)
- Remove `@attributes` handling — reserved properties (`_id`, `_summary`, `_preview`) accessed directly
- Remove `#text` handling — use `_label`
- Remove class-name wrapper detection
- `flattenRow()`: directly flatten `._id`, `._summary` as row values (no `@attributes` indirection)

### 4.5 Update `renderLayoutNode()` (L2647-3215) data access
This is a 570-line function with its own complete rendering logic. Data access patterns that need updating:
- **Section case** (`ref` resolution): replace `_sData['@attributes']['_preview']` with `_sData._preview`; remove class-wrapper detection (`/^[A-Z]/.test(key)` + single-element unwrap)
- **Field case**: replace `Array.isArray(val) && val.length === 1` unwrap with direct value access; replace `@attributes._id` checks with `._id`
- **Table case**: replace `@attributes` access in column/cell rendering; replace `#text` with `._label`
- All `resolveFieldValue()` call sites: no change needed (it just navigates the data tree)

**File**: `src/main/resources/templates/sidebar-nav.js`

---

## Phase 5: Unified Rendering in sidebar-nav.js

*Depends on Phase 4 (clean data format in place)*

The clean data format makes objects self-describing via `_class`, `_id`, `_label`, `_preview`. However, the **same object** can appear in three visual contexts and must render differently while flowing through one code path. We use a rendering context parameter.

### 5.0 Rendering Context Model

Three named contexts:

| Context | When used | Output style |
|---|---|---|
| `'detail'` | Root record; deeply expanded embedded object | Full field grid, `_preview` as hero image, collapsible nested sections, tabs, pagination |
| `'embedded'` | Object nested inside a `'detail'` or another `'embedded'` section | Compact header: `_summary` + thumbnail `_preview` + optional link, fields in collapsible `<details>` |
| `'tabular'` | An item inside a collection table cell | Summary label as link + mini-thumbnail only; no fields, no recursion |

Context propagation rules:
- Root record → `'detail'`
- Child object of `'detail'` → `'embedded'`
- Child object of `'embedded'` → `'embedded'` (stays compact; doesn't cascade to `'detail'`)
- Collection items → `'tabular'` regardless of outer context
- Scalars → context-independent (`renderFieldRow`)
- `renderLayoutNode()` uses `'embedded'` for inline objects and `'tabular'` for table cells

### 5.1 Introduce `renderValue(label, value, ctx)` — single dispatch entry point

Detection rules (checked in order):
1. `typeof value !== 'object'` or null → `renderFieldRow(label, value)` — context-independent scalar
2. `Array.isArray(value)` → `renderCollectionSection(label, value, ctx)` — receives outer ctx; cells use `'tabular'`
3. `isReferenceOnly(value)` → `renderReferenceRow(label, value)` — context-independent link + optional thumbnail

   Helper `isReferenceOnly(val)`: returns `true` if `val._id` exists and all keys are `_`-prefixed (no `_class` check needed — a reference-only object has `{_id, _label, _preview?}` but no data fields)

4. Else → `renderObjectSection(label, value, ctx)` — the unified context-aware object renderer

### 5.2 Rewrite `renderNodeSection()` → `renderObjectSection(label, value, ctx)`

The universal object renderer. All three context variants share one function body with context-driven output differences:

**Shared logic (all contexts):**
- Extract reserved props: `value._id`, `value._summary`, `value._preview`, `value._class`
- Resolve cross-page link: `pointsToByPath[normalizeSchemaPath(label)]` → `navHrefByDestName[dest]` → `linkHref`
- Iterate non-`_*` keys via rewritten `getObjectEntries(value)` (Phase 4.3)
- Separate entries: primitives, references, objects, collections (using simplified `classifyFieldEntry()`)

**`'detail'` context:**
- `_preview` → full-width `<div class="detail-hero-preview">` at top
- `_id` → navigation link in section header
- Primitives → `renderPrimitiveGroup()` (multi-column grid with type buckets)
- Child objects → `renderValue(key, child, 'embedded')` (context downgrade)
- Collections → `renderValue(key, col, 'embedded')` → dispatches to `renderCollectionSection()`
- Tabs: if 2+ inner sub-sections, create tabbed UI (reuse existing tab pattern from L1874-1895)

**`'embedded'` context:**
- Renders as `<details>` with header: inline `_preview` thumbnail + `_summary` label (+ link if `_id` + `linkHref`)
- Inside `<details>`: `renderPrimitiveGroup()` + child objects (`renderValue(key, child, 'embedded')`) + collections
- Compact: same nesting structure as `'detail'` but with thumbnail instead of hero, auto-collapsed unless level ≤ 1

**`'tabular'` context:**
- Emit only: linked `_summary` (via `linkHref` if available, else plain text) + optional mini `_preview` thumbnail
- No fields, no `<details>`, no recursion — returns immediately

### 5.3 Delete `renderInlineIdEntiteSection()` (L1961-2038, ~78 lines)
- Reference-only IDEntite (non-embedded, `{_id, _label}`): now handled by `renderReferenceRow()` via `renderValue()` step 3
- Embedded IDEntite with fields: now handled by `renderObjectSection()` with `'embedded'` context (detects `_id` → link, `_preview` → thumbnail)
- **All 78 lines deleted.**

### 5.4 Delete helper functions made obsolete
- `unwrapEmbeddedValue()` (L1462-1481): deleted in Phase 4.1
- `mergeAttributes()` (L1483-1492): deleted in Phase 4.1
- `expandWrapperCollectionItems()` (L1853-1897): deleted in Phase 4.1
- `isLikelyCollectionField()` (~L1609): deleted — all arrays are real collections in clean format

### 5.5 New helper: `renderReferenceRow(label, value)`
For non-embedded IDEntite references (`{_id: "123", _label: "John Doe", _preview: "..."}`):
- Resolve cross-page link via `pointsToByPath` + `navHrefByDestName`
- Render as field row: label + clickable `_label` text (or `_id` fallback) + optional mini thumbnail from `_preview`
- Reuses existing `refLinkBtn()` helper for link styling

### 5.6 Update `renderCollectionSection()` (L1915-1957)
- Receives outer `ctx` (not used beyond passing `'tabular'` to cells)
- `computeCollectionTable()` simplified: no `expandWrapperCollectionItems()` call; `flattenRow()` uses clean data
- Table cell rendering: for cells containing objects, call `renderValue(colName, cellObj, 'tabular')` instead of manual `@attributes`/`#text` extraction
- Pagination and sorting logic unchanged

### 5.7 Update `renderDetail()` (L2232-2433) — auto-discovery mode
The existing `renderDetail()` hero-header logic (subtitle promotion, key-info extraction) stays largely intact. Changes:
- Replace `getObjectEntries()` + `idEntiteFieldSet`-based filtering with simplified classification:
  - Current: separate `idEntiteEntries` from `objectEntries` using `idEntiteFieldSet`
  - New: all objects go through `renderValue(key, value, 'embedded')` — the dispatcher handles reference-only vs embedded
- Replace `renderInlineIdEntiteSection()` calls with `renderValue(key, value, 'embedded')`
- Replace `renderNodeSection()` calls with `renderValue(key, value, 'detail')` at top level
- Hero `_preview` rendering: `rec.preview` → `rec._preview`; regex extraction pattern unchanged

### 5.8 Update `renderLayoutNode()` (L2647-3215) — layout-driven mode
This 570-line function stays as the layout engine but delegates object/collection rendering to unified functions:
- **Section case with `ref`**: after resolving `_sData`, call `renderValue(label, _sData, 'embedded')` for preview + expansion instead of manual `@attributes._preview` extraction
- **Field case with object value**: call `renderValue(label, scalarVal, 'embedded')` instead of inline IDEntite handling
- **Table case**: delegate cell rendering to `renderValue(colName, cellVal, 'tabular')` instead of manual flattening
- The layout node types (`section`, `columns`, `column`, `field`, `divider`, `tabs`, `tab`) and their structural rendering stay the same — only their data access patterns change (Phase 4.5) and their object/collection rendering delegates to `renderValue()`

### 5.9 Remove `idEntiteFieldSet` from render dispatch
- `idEntiteFieldSet` remains populated at init time (L593-605) — used for `pointsToByPath` link resolution
- But render path selection no longer uses it — data shape alone (`isReferenceOnly()`) determines link vs. full-object
- Remove from: `renderNodeSection` L2114-2115, `renderDetail` L2392-2395
- Removes the fragile "check schema set + check data shape" double-gate

**File**: `src/main/resources/templates/sidebar-nav.js`

---

## Verification

1. **Build**: `mvn clean compile` — must succeed
2. **XML validation**: Export to XML → all files PASS XSD validation (XML output must be byte-identical to pre-change)
3. **Data shape validation**: Extract `window.__m4o` from generated HTML and verify:
   - No `@attributes` keys anywhere
   - No `#text` keys anywhere
   - Collections are real arrays (not single-element wrapped objects)
   - `_class`, `_id`, `_summary`, `_preview`, `_label` present where expected
4. **Data completeness check**: Count total fields/values in old vs new output — no silent data loss
5. **Rendering unification check**: An embedded entity via IDEntite, a directly-embedded entity, and a collection item of the same class should produce visually consistent output (same field grid, link style, preview placement)
6. **`_preview` rendering across contexts**: Verify previews appear for:
   - `'detail'`: hero preview at top of root record
   - `'embedded'`: thumbnail next to summary in collapsible header
   - `'tabular'`: mini-thumbnail in collection table cell
7. **Layout-driven mode**: Verify layout pages render correctly with clean data format — sections, fields, tables, tabs all functional
8. **Visual regression**: Browse generated HTML pages:
   - Detail views render correctly in both auto-discovery and layout modes
   - Collection tables display properly with pagination
   - IDEntite cross-page links work
   - Summary column in results table works
   - Search/filter functionality works

---

## Files Modified

**API layer (Phase 1):**
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterAPI.java` — add default `openArray`/`closeArray`
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriter.java` — extract `openElement`/`closeElement` shared methods; add `openArray`/`closeArray` public façade
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterElement.java` — add `isArray` flag

**JS writer (Phase 2):**
- `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterJS.java` — full rewrite; `writeInlineAttributes()` emits `_id`, `_summary`, `_preview` in fixed order

**Callers (Phase 3):**
- `src/main/java/migration4o/migration/FieldExporter.java` — `openArray`/`closeArray` for collection wrappers at L314/369, L422/438, L517/529, L567/578

**JS viewer (Phases 4–5):**
- `src/main/resources/templates/sidebar-nav.js` — major changes:
  - **Deleted functions**: `unwrapEmbeddedValue()`, `mergeAttributes()`, `expandWrapperCollectionItems()`, `renderInlineIdEntiteSection()`, `isLikelyCollectionField()`
  - **Rewritten functions**: `classifyFieldEntry()`, `getObjectEntries()`, `flattenValue()`, `flattenRow()`, `buildRecord()`, `renderNodeSection()` (→ `renderObjectSection()`)
  - **New functions**: `renderValue()`, `renderReferenceRow()`, `isReferenceOnly()`
  - **Updated functions**: `renderDetail()`, `renderLayoutNode()`, `renderCollectionSection()`, `computeCollectionTable()`, `renderFieldRow()`
  - **Unchanged functions**: `renderPrimitiveGroup()`, `renderColumnGroup()`, `sortPrimitiveEntries()`, `fmtValue()`, `fmtValueWithFormat()`, `refLinkBtn()`, tab/event binding

**Unchanged (must NOT be modified):**
- `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterXML.java`
- `src/main/java/migration4o/migration/format/HtmlFormatHandler.java`
- `src/main/java/migration4o/migration/ObjectExporter.java`
- `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterJSON.java`

---

## Decisions
- **isArray as flag, not subclass**: Array element lifecycle is identical to structure; flag is sufficient.
- **Shared `openElement`/`closeElement` private methods on StructuredWriter**: Avoid duplicating push/write/pop/recovery logic.
- **`_class` emitted on every object inside an array**: Replaces class-name wrapper detection. JS can always check `obj._class`.
- **`_preview` is a first-class reserved property**: Promoted from `@attributes._preview` to top-level `_preview`, consistent with `_summary`. Emitted in fixed order: `_id`, `_summary`, `_preview`.
- **`_label` replaces `#text`**: For elements with both attributes and content text.
- **Rendering context (`detail`/`embedded`/`tabular`) replaces `level` integer**: Context propagation is explicit — no ambiguity about what depth means.
- **`renderValue()` is dispatch-only**: it never renders HTML itself; it classifies and routes to `renderFieldRow`, `renderReferenceRow`, `renderObjectSection`, or `renderCollectionSection`.
- **`renderObjectSection()` replaces three old functions**: `renderNodeSection()`, `renderInlineIdEntiteSection()`, and the IDEntite-handling branches inside `renderDetail()`. One function, three output templates keyed by `ctx`.
- **`renderLayoutNode()` delegates, not duplicates**: Layout engine keeps its structural logic (sections, columns, tabs) but delegates all object/collection/field rendering to `renderValue()` instead of reimplementing rendering inline.
- **`idEntiteFieldSet` removed from render dispatch**: Data shape (`isReferenceOnly()`) alone drives the decision. Set is kept for `pointsToByPath` link resolution only.
- **`openChildArrayName` mechanism removed from JS writer**: Explicit `openArray`/`closeArray` makes the auto-grouping heuristic unnecessary. Fields kept on base class for JSON writer compatibility.
- **StructuredWriterJSON.java NOT updated**: Not used by HTML export. Can be modernized later.
- **Data loss prevention**: Verification includes numeric field/value count comparison.
