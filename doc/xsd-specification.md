.3 # XSD Schema Generation — Specification & Design Decisions

This document describes every design decision and mapping rule in the XSD schema generator (`migration4o.migration.xsd` package). Each criterion is justified with the rationale that drove the choice and references the relevant implementation.

---

## 1. Document Structure

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 1.1 | **Root element name** | `<xs:element name="export">` wrapping the entire file content | Every exported XML file is wrapped in `<export>…</export>`. Using a fixed root name provides a single entry point for validation and parsing. |
| 1.2 | **Root element structure** | `<xs:sequence>` containing `<metadata>` then `<objects>` | The XML files always write metadata first (via `StructuredWriterUtil.metadata()`), then open `<objects>`. A sequence enforces this exact order. |
| 1.3 | **Root `<objects>` content model** | `<xs:choice minOccurs="0" maxOccurs="unbounded">` with `<xs:element ref="…"/>` for each top-level class | Inside `<objects>`, any exported class may appear in any order and any number of times. `xs:choice` with `maxOccurs="unbounded"` allows exactly this — a mix of heterogeneous elements repeated freely. |
| 1.4 | **Top-level element refs are sorted** | Alphabetically sorted by destination name | Deterministic output — makes diffs and reviews predictable regardless of export order. |
| 1.5 | **Schema annotation** | `<xs:annotation><xs:appinfo>Migration4o - par Gestion Technologies</xs:appinfo></xs:annotation>` | Identifies the generator in the XSD itself for traceability. |
| 1.6 | **XML-XSD binding** | Each XML file carries `xsi:noNamespaceSchemaLocation` pointing to `_Migration/Schema.xsd` (relative path) | No XML namespace is used because DB4O export data has no namespace. The relative path is computed from the XML file's directory so each file finds the shared schema. |
| 1.7 | **Single shared XSD** | One `Schema.xsd` generated at `_Migration/Schema.xsd` covering all modules | All modules export into the same type universe (shared classes, shared IDEntite references). A single XSD avoids duplication and cross-module type conflicts. |

---

## 2. Metadata Type

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 2.1 | **Named complexType** | `<xs:complexType name="Metadata">` with a `<xs:sequence>` of 6 elements | Defined as a named type (not inline) so it can be referenced by the root element via `type="Metadata"`. |
| 2.2 | **Element order** | `generator → provider → module → type → objects → date` | Must match the write order in `StructuredWriterUtil.metadata()` exactly, since `xs:sequence` enforces order. This order mirrors the logical progression: who generated it → for which module → what data → when. |
| 2.3 | **All fields optional** | Every element has `minOccurs="0"` | Metadata fields are written conditionally (`if (metadata.generator != null)`). The `Extra.xml` file has no metadata at all (null schemaClass). All must be optional to accommodate this. |
| 2.4 | **All fields as `xs:string`** | Type is `xs:string` for all 6 elements | Metadata values are always plain text. Even `date` is written via `Date.toString()` which is not ISO format, so `xs:string` is the only safe choice. |

---

## 3. Class Type Definitions

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 3.1 | **Named complexType + global element** | Top-level classes get both `<xs:complexType name="X">` and `<xs:element name="X" type="X"/>` | The complexType is reusable when other fields reference this class. The global element is needed for `<xs:element ref="X"/>` in the root `<xs:choice>`. Separating them follows XSD best practice (the "Venetian Blind" pattern). |
| 3.2 | **Referenced-only types** | Get `<xs:complexType name="X">` only (no global element) | Types discovered transitively through field references are never root elements in `<objects>`. Writing only the complexType avoids polluting the global element namespace. |
| 3.3 | **Class title as XML comment** | `<!-- Titre du type -->` before the complexType | Provides human-readable context in the XSD file for developers reviewing the schema manually. |
| 3.4 | **Class description as `xs:documentation`** | `<xs:annotation><xs:documentation xml:lang="fr">…</xs:documentation></xs:annotation>` inside the complexType | Standard XSD mechanism for documentation. `xml:lang="fr"` reflects that class descriptions are in French. |
| 3.5 | **Alphabetical class ordering** | Classes sorted by `destinationName` before writing | Deterministic output for diffing and review. |
| 3.6 | **Field source: full schema (own + inherited)** | `getAllExportedFieldsIncludingAncestors()` walks the inheritance chain root-first, merging exported fields | DB4O stores fields including inherited ones. The XML output exports all fields from ancestor classes, so the XSD must declare them all. Child fields override ancestor fields with the same `destinationName`. |
| 3.7 | **Authority is the reference schema** | During registration, classes are resolved against the reference schema (not the database schema) | The reference schema is the authoritative source for export field definitions (destination names, types, export flags, skip conditions). The database schema only reflects what exists — the reference schema controls what gets exported and how. |

---

## 4. Field Content Model — `xs:all` vs `xs:sequence`

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 4.1 | **`xs:all` for class fields** | Fields are wrapped in `<xs:all>` (not `<xs:sequence>`) | DB4O `StoredField` iteration order is not guaranteed and may differ per object or activation depth. Fields in the XML output can appear in any order. `xs:all` permits unordered elements, matching the actual export behavior. |
| 4.2 | **`maxOccurs="1"` on all field elements within `xs:all`** | Every `<xs:element>` inside `<xs:all>` gets `maxOccurs="1"` (explicit or via `xs:all` default) | The XSD `xs:all` compositor requires each child element to appear at most once. Each field of a class is written exactly once per object, so this constraint is naturally satisfied. |
| 4.3 | **`minOccurs="0"` on all field elements** | Every field element gets `minOccurs="0"` | Fields may be omitted due to `skipWhen` conditions (NULL, DEFAULT, MINUS_ONE, EMPTY_COLLECTION), user-selected exclusions, or simply being null. No field is ever guaranteed to be present. |
| 4.4 | **Alphabetical field ordering** | Fields sorted by `destinationName` within each class | With `xs:all`, element order in the schema is irrelevant for validation. Alphabetical sort provides deterministic, reviewable XSD output. |
| 4.5 | **Classes with no exported fields** | Fallback to `<xs:sequence><xs:any minOccurs="0" maxOccurs="unbounded" processContents="skip"/></xs:sequence>` | Some classes have `isExported=false` on all fields, or the class is discovered but has no field registrations. The wildcard `xs:any` with `processContents="skip"` accepts any content without validation, preventing false negatives on valid XML. |
| 4.6 | **`unknown` catch-all element** | `<xs:element name="unknown" type="xs:anyType" minOccurs="0"/>` inside every `xs:all` | When a database field has no schema mapping (`schemaField == null`), the export writes it under the element name `"unknown"`. This dedicated slot absorbs those unmapped fields without breaking validation. |

---

## 5. Primitive Field Type Mapping

| # | Criterion | Current Implementation | XSD Type | Justification |
|---|-----------|----------------------|----------|---------------|
| 5.1 | **String** | `java.lang.String` / `string` | `xs:string` | Direct mapping, no transformation. |
| 5.2 | **Integer** | `java.lang.Integer` / `int` | `xs:int` | Numeric value exported via `toString()`. `xs:int` validates the integer range. |
| 5.3 | **Long** | `java.lang.Long` / `long` | `xs:long` | Same rationale as integer, wider range. |
| 5.4 | **Double** | `java.lang.Double` / `double` | `xs:double` | IEEE 754 double. `xs:double` matches Java's `Double.toString()` output. |
| 5.5 | **Float** | `java.lang.Float` / `float` | `xs:float` | IEEE 754 single. `xs:float` matches Java's `Float.toString()` output. |
| 5.6 | **Byte** | `java.lang.Byte` / `byte` | `xs:byte` | Single-byte numeric. |
| 5.7 | **Short** | `java.lang.Short` / `short` | `xs:short` | 16-bit numeric. |
| 5.8 | **Boolean** | `java.lang.Boolean` / `boolean` | **`xs:string`** (not `xs:boolean`) | **Critical decision**: DB4O data may contain non-standard boolean representations (e.g. stored as `INT`, `"true"` as string, or platform-specific formats). Java `Boolean.toString()` outputs `"true"`/`"false"`, but DB4O values after `toString()` may not match `xs:boolean`'s allowed values (`true`/`false`/`1`/`0`). Using `xs:string` prevents validation failures on edge-case data. |
| 5.9 | **Date** | `java.util.Date` / `date` | **`xs:string`** (not `xs:date`/`xs:dateTime`) | **Critical decision**: The export calls `Date.toString()` which produces `"Thu Mar 15 10:30:00 EST 2026"` — not ISO 8601. `xs:date` and `xs:dateTime` require ISO format. Using `xs:string` is the only safe choice until a date formatter is implemented. |
| 5.10 | **Object** | `java.lang.Object` / `Object` / `object` | `xs:anyType` | The runtime type is unknown at schema time. `xs:anyType` is the XSD universal base type, accepting any content. |
| 5.11 | **Class** | `java.lang.Class` / `Class` | `xs:string` | `java.lang.Class` fields are exported as the fully-qualified class name string (e.g. `"java.lang.String"`). The export strips the `"class "` prefix from `toString()`. |
| 5.12 | **byte[]** | `byte[]` | `xs:string` (in field writer) / `xs:base64Binary` (in type mapper) | **Inconsistency note**: `XSDTypeMapper.getXSDType("byte[]")` returns `xs:base64Binary`, but `XSDFieldWriter.writeFieldElement()` overrides this to `xs:string` for byte[] fields. The export encodes byte arrays as Base64 strings via `Base64.getEncoder().encodeToString()`. Both `xs:string` and `xs:base64Binary` accept the encoded output, but the intent differs. |
| 5.13 | **Unknown/null type** | Field with null or empty `type` | `xs:anyType` | Safety fallback — no type information available, accept anything. |
| 5.14 | **Array component types** | `int[]`, `String[]`, etc. (except `byte[]`) | Mapped by stripping `[]` from type name and applying regular mapping | Arrays are structurally exported as collections. The component type determines the XSD type of each item element. `byte[]` is excluded because it's treated as a single Base64 value, not an array of items. |
| 5.15 | **Fields with `valueMap`** | Schema field has `<valueMap>` with `<mapping from="…" to="…"/>` | `xs:string` | ValueMap transforms raw database values to display strings (e.g. `1 → "1JOUR"`). The output is always a mapped string, regardless of the original field type. |

---

## 6. Collection & Array Fields

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 6.1 | **Wrapper element** | `<xs:element name="fieldName" minOccurs="0">…</xs:element>` containing an anonymous `<xs:complexType>` | Collections are written as `<fieldName size="3"><Item/><Item/><Item/></fieldName>`. The outer wrapper element matches the field's destination name, containing the child items. |
| 6.2 | **Inner content model** | `<xs:sequence>` containing one `<xs:element>` with `maxOccurs="unbounded"` | Items can repeat. `xs:sequence` is used (not `xs:all`) because `xs:all` does not allow `maxOccurs="unbounded"` child elements. |
| 6.3 | **`size` attribute** | `<xs:attribute name="size" type="xs:int"/>` on the wrapper's complexType | The export writes a `size="N"` attribute on collection wrappers (when `includeCollectionSizeMetadata()` returns true, which is the default). This is informational metadata for consumers. Type is `xs:int` because it's always a non-negative integer count. |
| 6.4 | **Primitive-type items** | Item element uses the XSD-mapped primitive type, element name = field destination name | For collections of primitives (e.g. `int[]`), each item is a direct value element. The item element name repeats the field name since there's no class name to use. |
| 6.5 | **Complex-type items** | Item element name = child class `destinationName`, type = child class `destinationName` | For collections of entities, each item is written as `<ClassName>…</ClassName>`. The item element references the complex type defined elsewhere in the XSD. The referenced type is registered in `context.referencedTypes` for transitive discovery. |
| 6.6 | **IDEntite collection with `embedContents=true`** | Item type resolved via `pointsTo` on the child class: uses the pointed-to entity class, not the IDEntite wrapper | When a collection holds IDEntite references with embedding enabled, the export resolves the reference and writes the target entity inline. The XSD must reference the target entity type (e.g. `TypeActivite`), not the IDEntite wrapper (e.g. `IDTypeActivite`). |
| 6.7 | **IDEntite collection with `embedContents=false`** | ID-reference pattern: item element uses the IDEntite class name and type | When embedding is off, the export writes synthetic ID wrapper objects (via `IDReferenceExporter`) with just the `mID` field. The XSD references the IDEntite class type. |
| 6.8 | **Polymorphic collections** | `<xs:any minOccurs="0" maxOccurs="unbounded" processContents="lax"/>` | When a collection's child type has subclasses in the reference schema, the runtime items may use any subclass element name. `xs:any` with `processContents="lax"` accepts any element but validates it against the XSD if a matching declaration exists. This handles DB4O polymorphism where a `Vector<Rapport>` may contain `SousRapport` instances. |
| 6.9 | **Unknown child type** | Item type falls back to `xs:anyType` with a warning | If `childrenType` references a class not found in the reference schema, we emit a console warning and use `xs:anyType` to avoid blocking validation. |
| 6.10 | **Array fields** | Treated identically to collections (same wrapper + sequence structure) | The export converts arrays to `List` and processes them through the same `exportCollectionLikeField()` path. The XSD structure must match. |

---

## 7. Complex (Non-Primitive, Non-Collection) Fields

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 7.1 | **Non-embedded IDEntite reference** (`embedContents=false`, `pointsTo` present) | `<xs:element name="fieldName" type="xs:long"/>` | When embedding is off, the export writes only the `mID` value as a scalar long. The reference target is exported separately as a top-level object. `xs:long` matches the `mID` type. |
| 7.2 | **Embedded IDEntite reference** (`embedContents=true`, `pointsTo` present) | Wrapped `<xs:any>` element | The export resolves the IDEntite to its target entity and writes it inline. However, if resolution fails, the IDEntite wrapper itself may be written. Both the target type and the IDEntite type are registered as `referencedTypes`. The `xs:any` wrapper accommodates either outcome. |
| 7.3 | **Non-IDEntite complex field, no subclasses** | `<fieldName><ChildType>…</ChildType></fieldName>` — wrapper element containing a single typed child | Wrapped typed element: the field is written as a wrapper containing one child element named after the type. This matches the export pattern where `xmlWriter.openStructure(fieldName)` wraps the recursive `exportObject()` output. |
| 7.4 | **Non-IDEntite complex field with subclasses** | Wrapped `<xs:any>` element | Same polymorphism issue as collections — the runtime type may be a subclass. `xs:any` with `processContents="lax"` accommodates this. |
| 7.5 | **Complex field, non-exported type** (`migrate=false`) | `xs:anyType` simple element | If the referenced class has `isExported=false`, we cannot generate a type definition for it. Using `xs:anyType` allows the XML to pass validation without a concrete type. |
| 7.6 | **Complex field, type not in schema** | `xs:anyType` simple element | For types not found in the reference schema at all, we fall back to `xs:anyType`. This is a safety net for edge cases in the DB4O data. |
| 7.7 | **Primitive mapped to `embedContents` (java.lang.Class)** | `xs:string` simple element | Despite `isPrimitiveType()` returning true, when `embedContents=true` and the field type is `java.lang.Class`, the export writes a plain class name string. The XSD outputs `xs:string`. |

---

## 8. Wrapper Element Patterns

The XSD uses three distinct wrapper patterns for complex/embedded fields:

| # | Pattern | XSD Structure | Used When | Justification |
|---|---------|--------------|-----------|---------------|
| 8.1 | **Wrapped typed element** | `<xs:element name="field"><xs:complexType><xs:sequence><xs:element name="Type" type="Type" minOccurs="0"/></xs:sequence></xs:complexType></xs:element>` | Non-IDEntite complex fields without subclasses | The export writes `<field><Type>…</Type></field>`. The inner element is the class's destination name. One specific child element is expected. |
| 8.2 | **Wrapped any element** | `<xs:element name="field"><xs:complexType><xs:sequence><xs:any minOccurs="0" processContents="lax"/></xs:sequence></xs:complexType></xs:element>` | Polymorphic fields, embedded IDEntite, unknown embedded types | When the concrete child element name is unpredictable (polymorphism, fallback), `xs:any` accepts any single child element. `processContents="lax"` means "validate if you can, skip if you can't". |
| 8.3 | **Wrapped text element** | `<xs:element name="field" type="xs:string"/>` | `java.lang.Class` with `embedContents=true` | No wrapper needed — the export writes a plain string value directly inside the element. |

---

## 9. Transitive Type Discovery

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 9.1 | **Iterative discovery loop** | After writing registered classes, `writeDiscoveredReferencedTypes()` runs a `while(foundNewTypes)` loop | Field writers register types in `context.referencedTypes` as they encounter them. These types may themselves reference further types. The iterative loop continues until no new types are added — guaranteeing the type graph is fully resolved. |
| 9.2 | **Reference schema lookup** | Discovered types are looked up by `destinationName` in the reference schema | The `referencedTypes` set stores destination names. The reference schema is the authority for field definitions of transitively discovered types. |
| 9.3 | **Non-exported referenced types** | Warning printed, type skipped (added to `writtenTypes` to prevent re-processing) | If a referenced type has `migrate=false`, it cannot be fully defined. A warning alerts the developer that the XSD may be incomplete. The type is not written but is marked as processed to avoid infinite loops. |
| 9.4 | **Missing referenced types** | Error printed, summary at end | If a referenced type is not found in the reference schema at all, it's a schema gap. The error message guides the developer to either add the class or change `embedContents` to `false` on the referencing field. |
| 9.5 | **Duplicate prevention** | `writtenTypes` set tracks all written type definitions | Ensures each complexType is written exactly once, even if referenced from multiple fields. |

---

## 10. Inheritance Handling

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 10.1 | **Flattened field inheritance** | `getAllExportedFieldsIncludingAncestors()` walks the parent chain and merges fields | XSD does not automatically inherit fields from parent types (that would require `xs:extension`). Instead, each class's complexType contains all fields including inherited ones. This flat model matches the export behavior where `DatabaseUtil.getAllFieldsIncludingAncestors()` iterates all stored fields. |
| 10.2 | **Child overrides parent** | Root-first merge: child field with same `destinationName` replaces ancestor's field | If a child class redefines a field (different type, different export settings), the child definition wins. The merge processes ancestors root-first and children last, so child entries overwrite. |
| 10.3 | **Subclass detection for polymorphism** | `hasAnySubclass()` checks if any class in the schema has `parentClassName` equal to the target class's source name | Used to decide between typed elements and `xs:any` wildcards. If the type *could* be subclassed, the XSD must accommodate element names it hasn't predicted. |

---

## 11. Top-Level Object Registration

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 11.1 | **Registration at `open()` time** | `XmlFormatHandler.open()` registers the configured class as a top-level object *before* any objects are activated | DB4O polymorphism means `observeObject()` only sees concrete subclass names (e.g. `SousRapport`), not the base class from the module config (e.g. `Rapport`). Registering at `open()` ensures the base class is always in the XSD root choice. |
| 11.2 | **Registration at `observeObject()` time** | Each exported object also registers its concrete runtime class | Catches subclasses that appear at runtime but aren't the declared module class. Both the base and subclass end up as top-level elements. |
| 11.3 | **Idempotent registration** | `XSDBuilder.addClass()` and `addTopLevelObject()` are idempotent (map-based) | Called once per object, thousands of times per export. Must not accumulate duplicates. |

---

## 12. XSD Composition & Validation

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 12.1 | **`processContents="lax"`** | Used on `xs:any` for polymorphic fields and embedded IDEntite references | `lax` means: "validate the element if its declaration is found in the schema, skip validation otherwise." This is the right balance — known types are validated, unknown subclasses pass through without error. |
| 12.2 | **`processContents="skip"`** | Used on `xs:any` for classes with zero exported fields | `skip` means: "do not validate at all." Used only when the class has no field definitions, so there's nothing meaningful to validate. |
| 12.3 | **Post-export validation** | `XMLValidator.validateMultiple()` validates all exported XML files against the generated XSD | Catches XSD/XML mismatches before the user consumes the data. Uses `javax.xml.validation.Validator` with a custom error handler for detailed line/column error reporting. |
| 12.4 | **XML escaping in XSD** | `XSDTypeMapper.escapeXml()` escapes `&`, `<`, `>`, `"`, `'` in documentation text | Class descriptions may contain special characters. Escaping prevents broken XSD syntax. |

---

## 13. Schema Registration During Export

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 13.1 | **Live observation model** | `XmlFormatHandler` overrides `observeObject()` and `observeField()` hooks to populate `XSDBuilder` during export | The XSD is built incrementally as the export discovers actual classes and fields. This ensures the XSD covers exactly what was exported — no more, no less. |
| 13.2 | **Field registration uses reference schema** | `XSDContext.registerField()` looks up the field in the reference schema, not the database schema | Export uses reference schema field definitions (destination name, type, embedContents). The XSD must match these definitions, not the raw database structure. |
| 13.3 | **Only exported fields registered** | `registerField()` checks `refField.isExported` before storing | Fields with `isExported=false` are never written to XML. They must be excluded from the XSD to avoid declaring elements that never appear. |
| 13.4 | **ID-reference class observation** | `observeReferencedClass()` hook registers IDEntite wrapper classes and all their fields | When `IDReferenceExporter` writes a synthetic `IDFoo` wrapper, the wrapper class must appear in the XSD with its `mID` field. This hook ensures that. |

---

## 14. Known Limitations & Improvement Opportunities

| # | Area | Current State | Potential Improvement |
|---|------|--------------|----------------------|
| 14.1 | **`byte[]` type inconsistency** | `XSDFieldWriter` hardcodes `xs:string` for byte[] fields, but `XSDTypeMapper` returns `xs:base64Binary` | Align on `xs:base64Binary` — the export does produce valid Base64 content, and `xs:base64Binary` is semantically richer. Requires verifying that the Java XML validator handles `xs:base64Binary` correctly with Base64-encoded text. |
| 14.2 | **Boolean as `xs:string`** | All booleans mapped to `xs:string` | Could use `xs:boolean` if DB4O data is verified to always produce `"true"`/`"false"`. Requires a data audit across all databases. |
| 14.3 | **Date as `xs:string`** | All dates mapped to `xs:string` | Could use `xs:dateTime` if a date formatter is added to produce ISO 8601 output. Would require changes in `FieldExporter` to format dates before writing. |
| 14.4 | **No `xs:extension` for inheritance** | Fields are flattened into each complexType | Using `xs:extension` would reduce redundancy and make the inheritance hierarchy visible in the XSD. However, it would require `xs:sequence` (XSD restriction: `xs:all` cannot be used with `xs:extension`), which conflicts with unordered field export. |
| 14.5 | **No `xs:enumeration` for valueMaps** | ValueMap fields use `xs:string` | Could generate `xs:simpleType` restrictions with `xs:enumeration` for each mapped value. This would provide tighter validation but requires ensuring all possible runtime values are covered in the map. |
| 14.6 | **`skippedBecause` attribute not in XSD** | The export may add `skippedBecause="…"` attributes on elements when skip conditions are bypassed | These attributes are not declared in the XSD. They pass validation because `xs:all`/elements don't restrict attributes by default, but declaring them with `<xs:anyAttribute processContents="skip"/>` would be more correct. |
| 14.7 | **Polymorphic field validation gap** | Fields with subclasses use `xs:any processContents="lax"` | If all concrete subclass types have been written into the XSD, a `xs:choice` listing them would provide stricter validation. The challenge is knowing *which* subclasses will appear at runtime. |
| 14.8 | **Extra.xml has no metadata** | The extra-objects file writes `<export><objects>…` without `<metadata>` | The XSD declares `<metadata>` with `minOccurs="0"`, so this validates. But the root `<export>` type is defined inline (not named), so this is coupled. |
| 14.9 | **Virtual fields** | Virtual fields (source starts with `@`) are exported as collections but may not be registered in XSD if their `observeField()` runs after the field writer | Virtual fields call `observeField()` after export. Their types are registered, but timing could cause gaps if the XSD is written concurrently (currently not an issue since XSD is written after all exports complete). |
| 14.10 | **`xs:all` maxOccurs limitation** | `xs:all` is restricted to `maxOccurs="1"` on children in XSD 1.0 | This is correct for our use case (each field appears at most once per object). However, if future requirements introduce repeated same-name fields per object, `xs:sequence` would be needed. XSD 1.1 relaxes this restriction. |
