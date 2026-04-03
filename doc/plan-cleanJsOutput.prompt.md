# Plan: Clean JS Output from StructuredWriter

## TL;DR
Rewrite `StructuredWriterJS` to produce JS-native data instead of XML-shaped JSON. Add `openArray`/`closeArray` to `StructuredWriterAPI` (XML gets default delegation — zero impact). Strip all 35+ unwrapping patterns from `sidebar-nav.js`. Streaming preserved. XML untouched.

## Problem
`StructuredWriterJS` mimics XML conventions, producing:
- **Forced arrays**: `"nom":["value"]` instead of `"nom":"value"` (11 JS unwrap sites)
- **@attributes wrapper**: `{"@attributes":{"_id":"123"},"#text":"Faible"}` instead of `{_id:"123",_label:"Faible"}` (15 JS sites)
- **Class-name wrappers**: `{"Fichier":[{...}]}` instead of `{_class:"Fichier",...}` (9 JS sites)

Every new viewer feature must replicate all three unwrapping steps at every data access point, making the JS fragile and the data shape unpredictable from reading Java code alone.

## Target Data Contract

```js
// Scalar field:
nom: "value"

// IDEntite reference (non-embedded):
idCategorieRisque: {_id: "12568653", _label: "Faible"}

// IDEntite reference (embedded):
idFichier: {_class: "Fichier", _id: "51790381", _label: "290 Mont Echo.jpg",
            nom: "290 Mont Echo.jpg", dateAjout: "1900-01-01T00:00:00", ...}

// Nested object (non-IDEntite):
adresse: {_class: "Adresse", numeroCivique: "123", ...}

// Collection (real array):
listePieceJointe: [{_class: "PieceJointe", ...}, {...}]

// Root record:
{_class: "DossierAdresse", _id: "42", _summary: "123 Rue Principale", nom: "...", ...}
```

Reserved property conventions:
- `_class`: type discriminator (replaces class-name wrapper keys)
- `_id`: object/reference ID (replaces `@attributes._id` / `@attributes.id`)
- `_summary`: human-readable label on objects (replaces `@attributes._summary`)
- `_label`: display text when content has attributes (replaces `#text`)
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
- **Extract shared private method** `openElement(String name, Map attrs, boolean isArray)` to avoid duplicating the push/write/pop logic from `openStructure`/`closeStructure`. Both `openStructure` and `openArray` call this shared method.
- Same for close: shared `closeElement(String name, boolean isArray)` that routes to `api.closeArray` or `api.closeStructure` based on the element's `isArray` flag.
- Sets `element.isArray = true` before calling `api.openArray(element)`
- **File**: `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriter.java`

### 1.3 Add isArray flag to StructuredWriterElement
- `public boolean isArray = false` on the base class
- Justification: array lifecycle is identical to structure (open/children/close), so a subclass adds no value — a flag is sufficient. Document this in a comment.
- **File**: `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterElement.java`

### 1.4 Verify XML unchanged
- Build + export with XML format → diff output against pre-change baseline
- XSD validation must still pass all 107 files

---

## Phase 2: Rewrite StructuredWriterJS

### 2.1 New output logic

Replace the `beginValue`/`openChildArray`/`closeOpenChildArray` mechanism with explicit array/object context tracking.

Element state (simplified):
- `isArray` (from Phase 1) — is this element an array context?
- `hasWrittenChild` — for comma placement between siblings

All 8 output cases enumerated:

| Method | Parent is array | Has attributes | Output |
|--------|----------------|----------------|--------|
| `openStructure` | no | no | `"name":{` |
| `openStructure` | no | yes | `"name":{_id:"x",_summary:"y",` |
| `openStructure` | yes | no | `{_class:"Name",` |
| `openStructure` | yes | yes | `{_class:"Name",_id:"x",_summary:"y",` |
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
- `writeComma(element)` — handles comma before element
- `writeInlineAttributes(attrs)` — writes `_id:"x",_summary:"y"` pairs
- `writeOpenObject(element)` — composes: comma + key + `{` + `_class` (if array parent) + attrs
- `writeOpenArray(element)` — composes: comma + key + `[`
- `writeScalar(element, content)` — composes: comma + key + quoted content (or `{attrs + _label}`)

### 2.3 Remove dead state
- `openChildArrayName` / `openChildArrayHasElements` no longer used by JS writer (kept on element base class for JSON writer compatibility)

### 2.4 Mismatch recovery
- `closeArray()` reuses the same stack-mismatch recovery logic that `closeStructure()` has. In Phase 1.2, the shared `closeElement()` method handles this for both.

### 2.5 initialize / onDocumentComplete unchanged
- `initialize()`: write `window.__m4o={`
- `onDocumentComplete()`: write `;\n`

**File**: `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterJS.java` (~207 lines, full rewrite)

---

## Phase 3: Update callers to use openArray for collections

### 3.1 FieldExporter collection paths (~6 call sites)
- `exportCollectionLikeField()` (~L388/390): `xmlWriter.openStructure(fieldName)` → `xmlWriter.openArray(fieldName)`, `xmlWriter.closeStructure(fieldName)` → `xmlWriter.closeArray(fieldName)`
- `exportMapField()` (~L523/525): same pattern for map wrappers
- `exportStandaloneCollectionItems()` (~L1005/1025): same pattern
- **File**: `src/main/java/migration4o/migration/FieldExporter.java`

### 3.2 No changes needed (verified)
- **HtmlFormatHandler**: uses `openStructure` for objects, `elementWithContent` for IDEntite refs — correct
- **ObjectExporter**: `closeStructure` calls match object opens — correct
- These files remain unchanged

---

## Phase 4: Simplify sidebar-nav.js

### 4.1 Remove all unwrapping patterns
- Delete 11 `if (Array.isArray(x) && x.length === 1) x = x[0]` sites
- Replace 15 `cellVal['#text']` accesses with `._label`
- Replace 11 `['@attributes']` accesses with direct `._id`, `._summary`
- Delete 9 class-name wrapper detection blocks (`wrapKeys`, `/^[A-Z]/`)
- Delete `expandWrapperCollectionItems()` entirely (collections are flat arrays)

### 4.2 Update data access patterns
- `buildRecord()`: `rec.id` from `raw._id` instead of `raw['@attributes'].id`; `rec.summary` from `raw._summary`
- `renderLayoutNode()`: direct property access in table cells and field rendering
- `renderInlineIdEntiteSection()`: check `val._id` + `val._label`
- `resolveFieldValue()`: simple dot traversal, no wrapper unwrapping needed
- Collection/table detection: `Array.isArray(items)` — already an array, no unwrapping

### 4.3 Update IDEntite detection
- Current: checks `idEntiteFieldSet` + data shape (`#text` + `@attributes`)
- New: checks `idEntiteFieldSet` + `val._id` (cleaner, single property check)
- `pointsToByPath` / `navHrefByDestName` navigation links remain unchanged

**File**: `src/main/resources/templates/sidebar-nav.js`

---

## Verification

1. **Build**: `mvn clean compile` — must succeed
2. **XML validation**: Export to XML → all 107 files PASS XSD validation (output must be byte-identical to pre-change)
3. **Data shape validation**: Python script extracts `window.__m4o` from generated HTML and verifies:
   - No `@attributes` keys anywhere in the data
   - No `#text` keys anywhere in the data
   - Collections are real arrays (not single-element wrapped objects)
   - `_class`, `_id`, `_summary`, `_label` properties present where expected
4. **Data completeness check**: Count total fields/values in old output vs new output to confirm nothing was dropped (**no silent data loss**)
5. **Visual verification**: Browse generated HTML pages:
   - Detail views render correctly
   - Collection tables display properly
   - IDEntite cross-page links work
   - Embedded IDEntite toggle triangles appear
   - Summary/search functionality works

---

## Files Modified

**API layer (Phase 1)**:
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterAPI.java` — add default openArray/closeArray
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriter.java` — add public façade + shared openElement/closeElement
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterElement.java` — add isArray flag

**JS writer (Phase 2)**:
- `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterJS.java` — full rewrite

**Callers (Phase 3)**:
- `src/main/java/migration4o/migration/FieldExporter.java` — openArray for collections (~6 sites)

**JS viewer (Phase 4)**:
- `src/main/resources/templates/sidebar-nav.js` — remove ~35 unwrapping sites, simplify data access

**Unchanged (must NOT be modified)**:
- `src/main/java/migration4o/util/tools/structuredwriter/formats/StructuredWriterXML.java`
- `src/main/java/migration4o/migration/format/HtmlFormatHandler.java`
- `src/main/java/migration4o/migration/ObjectExporter.java`

## Decisions
- **isArray as flag, not subclass**: Array element lifecycle is identical to structure; flag is sufficient. Documented in code.
- **Shared façade methods**: `openElement`/`closeElement` private methods on StructuredWriter avoid duplicating push/write/pop logic between structure and array paths.
- **`_class` emitted on every object inside an array**: This replaces class-name wrapper detection. The JS can always check `obj._class` to know the type.
- **`_label` replaces `#text`**: For elements with both attributes and content text.
- **`openChildArrayName` mechanism removed from JS**: Explicit `openArray`/`closeArray` calls make the forced-array heuristic unnecessary. State fields kept on base class for JSON writer compatibility.
- **StructuredWriterJSON.java NOT updated**: It's not used by HTML export. Can be modernized later if needed.
- **Mismatch recovery shared**: Both `closeStructure` and `closeArray` use the same recovery logic via shared `closeElement`.
- **Data loss prevention**: Verification includes a numeric field/value count comparison between old and new output.
