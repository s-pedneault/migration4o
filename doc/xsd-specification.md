# XSD Schema Generation — Specification & Design Decisions

This document describes every design decision and mapping rule in the XSD schema generator (`migration4o.migration.xsd` package). Each criterion is justified with the rationale that drove the choice and references the relevant implementation.

---

## 1. Document Structure

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 1.1 | **Root element name** | `<xs:element name="export">` wrapping the entire file content | Every exported XML file is wrapped in `<export>…</export>`. Using a fixed root name provides a single entry point for validation and parsing. |
| 1.2 | **Root element structure** | `<xs:sequence>` containing `<metadata>` then `<objects>` | The XML files always write metadata first (via `StructuredWriterUtil.metadata()`), then open `<objects>`. A sequence enforces this exact order. |
| 1.3 | **Root `<objects>` content model** | `<xs:choice minOccurs="0" maxOccurs="unbounded">` with `<xs:element ref="…"/>` for every exported class | Inside `<objects>`, any exported class may appear in any order and any number of times. The choice lists **all** classes with `migrate=true` from the reference schema — not just observed classes. |
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
| 2.3 | **All fields optional** | Every element has `minOccurs="0"` | Metadata fields are written conditionally (`if (metadata.generator != null)`). All must be optional to accommodate partial metadata. |
| 2.4 | **All fields as `xs:string`** | Type is `xs:string` for all 6 elements | Metadata values are always plain text. Even `date` is written via `Date.toString()` which is not ISO format, so `xs:string` is the only safe choice. |
| 2.5 | **Extra.xml metadata** | `Extra.xml` now includes full `<metadata>` with generator, provider, module (`_Migration`), type (`Extra`), and object count | All exported XML files consistently include metadata, making them self-describing. |

---

## 3. Class Type Definitions

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 3.1 | **Named complexType + global element** | Every exported class gets both `<xs:complexType name="X">` and `<xs:element name="X" type="X"/>` | The complexType is reusable when other fields reference this class. The global element is needed for `<xs:element ref="X"/>` in the root `<xs:choice>` and in polymorphic `xs:choice` groups. Follows the "Venetian Blind" XSD pattern. |
| 3.2 | **Full-schema generation** | All classes with `migrate=true` are written to the XSD, regardless of whether they were observed during export | The XSD is generated from the complete reference schema, not from runtime observations. This guarantees full schema coverage and deterministic output across runs. |
| 3.3 | **Class title in `xs:annotation`** | `<xs:annotation><xs:documentation xml:lang="fr">title</xs:documentation></xs:annotation>` inside the complexType | Standard XSD mechanism for documentation. Provides human-readable context with proper tooling support. |
| 3.4 | **Class description in `xs:annotation`** | Second `<xs:documentation xml:lang="fr">description</xs:documentation>` inside the same `xs:annotation` | Both title and description use `xml:lang="fr"` to reflect that schema documentation is in French. |
| 3.5 | **Alphabetical class ordering** | Classes sorted by `destinationName` before writing | Deterministic output for diffing and review. |
| 3.6 | **Field source depends on inheritance** | Classes with an exported parent use `getOwnExportedFields()` (own fields only); classes without use `getAllExportedFieldsIncludingAncestors()` (flattened) | When `xs:extension` is used, inherited fields come from the parent type — only own fields should appear. When no exported parent exists, all fields must be flattened into the type. |
| 3.7 | **Authority is the reference schema** | Classes and fields are resolved from the reference schema (not the database schema) | The reference schema is the authoritative source for export field definitions (destination names, types, export flags, skip conditions). The database schema only reflects what exists — the reference schema controls what gets exported and how. |
| 3.8 | **Non-exported class field exclusion** | Fields whose type resolves to a class with `migrate=false` are skipped from the XSD with a warning | A non-exported class has no complexType in the XSD. Any field referencing such a type would produce an unresolvable type reference. Fields are skipped at generation time with a warning log. This applies to direct field types, collection children types, and embedded IDEntite `pointsTo` targets. |

---

## 4. Field Content Model — `xs:sequence`

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 4.1 | **`xs:sequence` for class fields** | Fields are wrapped in `<xs:sequence>` | Fields in the XML output are sorted alphabetically by destination name, guaranteeing a deterministic order. `xs:sequence` enforces this order and enables `xs:extension` for inheritance. |
| 4.2 | **`maxOccurs="1"` on field elements** | Every `<xs:element>` inside `<xs:sequence>` gets `maxOccurs="1"` (explicit or default) | Each field of a class is written exactly once per object, so this constraint is naturally satisfied. |
| 4.3 | **`minOccurs="0"` on all field elements** | Every field element gets `minOccurs="0"` | Fields may be omitted due to `skipWhen` conditions (NULL, MINUS_ONE, EMPTY_COLLECTION), user-selected exclusions, or simply being null. No field is ever guaranteed to be present. |
| 4.4 | **Alphabetical field ordering** | Fields sorted by `destinationName` within each class | Both the XML output and XSD field declarations are sorted identically, ensuring validation correctness with `xs:sequence`. |
| 4.5 | **Classes with no exported fields** | Empty `<xs:sequence/>` | Empty classes get an empty complexType. No wildcards or fallbacks are used — the schema precisely represents the absence of fields. |
| 4.6 | **No `unknown` catch-all element** | Removed | Fields without a schema mapping are skipped during export. There is no `"unknown"` element name — all exported fields must have a valid schema mapping with a destination name. |

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
| 5.8 | **Boolean** | `java.lang.Boolean` / `boolean` | `xs:boolean` | Java `Boolean.toString()` outputs `"true"`/`"false"`, which are valid `xs:boolean` values. Strong typing enables boolean-aware consumers. |
| 5.9 | **Date** | `java.util.Date` / `date` | `xs:dateTime` | The export formats dates using `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")` producing ISO 8601 output. `xs:dateTime` provides proper temporal validation. |
| 5.10 | **Object** | `java.lang.Object` / `Object` / `object` | `xs:anyType` | The runtime type is unknown at schema time. `xs:anyType` is the XSD universal base type, accepting any content. |
| 5.11 | **Class** | `java.lang.Class` / `Class` | `xs:string` | `java.lang.Class` fields are exported as the fully-qualified class name string. |
| 5.12 | **byte[]** | `byte[]` | `xs:base64Binary` | The export encodes byte arrays as Base64 strings via `Base64.getEncoder().encodeToString()`. `xs:base64Binary` matches the encoding semantics. `XSDTypeMapper` handles the mapping consistently. |
| 5.13 | **Unknown/null type** | Field with null or empty `type` | **Skipped with warning** | `XSDFieldWriter` skips fields with null or empty types with a warning log. This can happen for inherited DB4O internal fields (e.g. `com.db4o.config.TCollection`). `XSDTypeMapper` still throws `IllegalArgumentException` for null/empty types as a safety net for unexpected callers. |
| 5.14 | **Array component types** | `int[]`, `String[]`, etc. (except `byte[]`) | Mapped by stripping `[]` from type name and applying regular mapping | Arrays are structurally exported as collections. The component type determines the XSD type of each item element. `byte[]` is excluded because it's treated as a single Base64 value, not an array of items. |
| 5.15 | **Fields with `valueMap`** | Schema field has `<valueMap>` with `<mapping from="…" to="…"/>` | Inline `xs:simpleType` with `xs:enumeration` facets | ValueMap transforms raw database values to display strings. The XSD lists all possible output values as `xs:enumeration` facets for strict validation. Values are sorted and deduplicated for deterministic output. |
| 5.16 | **Object-typed fields with `valueMap`** | Field type is `object` and has a `<valueMap>` | `xs:anyType` (no enumeration) | Object-typed fields can hold any value at runtime (dates, numbers, strings). The valueMap is a best-effort transformation, not an exhaustive constraint — unmapped values pass through unchanged. Using `xs:enumeration` would reject valid values not covered by the map. |

---

## 6. Collection & Array Fields

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 6.1 | **Wrapper element** | `<xs:element name="fieldName" minOccurs="0">…</xs:element>` containing an anonymous `<xs:complexType>` | Collections are written as `<fieldName size="3"><Item/><Item/><Item/></fieldName>`. The outer wrapper element matches the field's destination name, containing the child items. |
| 6.2 | **Inner content model** | `<xs:sequence>` or `<xs:choice>` depending on polymorphism | Monomorphic collections use `<xs:sequence>` with `maxOccurs="unbounded"`. Polymorphic collections use `<xs:choice>` listing base + all exported descendants. |
| 6.3 | **`size` attribute** | `<xs:attribute name="size" type="xs:int"/>` on the wrapper's complexType | The export writes a `size="N"` attribute on collection wrappers. Type is `xs:int` because it's always a non-negative integer count. |
| 6.4 | **Primitive-type items** | Item element uses the XSD-mapped primitive type, element name = field destination name | For collections of primitives (e.g. `int[]`), each item is a direct value element. The item element name repeats the field name since there's no class name to use. |
| 6.5 | **Complex-type items** | Item element name = child class `destinationName`, type = child class `destinationName` | For collections of entities, each item is written as `<ClassName>…</ClassName>`. The item element references the complex type defined elsewhere in the XSD. |
| 6.6 | **IDEntite collection with `embedContents=true`** | Item type resolved via `pointsTo` on the child class: uses the pointed-to entity class | When a collection holds IDEntite references with embedding enabled, the export resolves the reference and writes the target entity inline. The XSD references the target entity type. |
| 6.7 | **IDEntite collection with `embedContents=false`** | ID-reference pattern: item element uses the IDEntite class name and type | When embedding is off, the export writes synthetic ID wrapper objects with just the `mID` field. The XSD references the IDEntite class type. |
| 6.8 | **Polymorphic collections** | `<xs:choice minOccurs="0" maxOccurs="unbounded">` listing `<xs:element ref="BaseType"/>` and all exported descendants | When a collection's child type has subclasses, the runtime items may use any subclass element name. The `xs:choice` explicitly lists the base type and all exported descendants, providing strict validation. Descendants are sorted alphabetically. |
| 6.9 | **Unknown child type** | **Error** | Throws `IllegalStateException` — collection fields must reference a type that exists in the reference schema. |
| 6.10 | **Array fields** | Treated identically to collections (same wrapper + sequence/choice structure) | The export converts arrays to `List` and processes them through the same `exportCollectionLikeField()` path. The XSD structure must match. |
| 6.11 | **Non-exported collection child type** | Collection field skipped with warning when child type has `migrate=false` | A non-exported child class has no complexType in the XSD. The entire collection field is omitted. Also applies when a collection's embedded IDEntite `pointsTo` target is non-exported. |

---

## 6b. Map Fields (Hashtable, HashMap, etc.)

Map fields are detected by `CollectionTypeUtil.isMapType()` which recognises HashMap, TreeMap, Hashtable, LinkedHashMap, ConcurrentHashMap, and Map. Ancestry-based detection (`isMapByAncestry()`) additionally walks the class hierarchy for DB4O `GenericObject`-wrapped maps. Map detection takes priority over collection detection in both the XSD generator and the export engine.

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 6b.1 | **Wrapper element** | `<xs:element name="fieldName" minOccurs="0" maxOccurs="1">` containing an anonymous `<xs:complexType>` | Maps are written as `<fieldName size="N"><entry>…</entry>…</fieldName>`. The outer wrapper matches the field's destination name. |
| 6b.2 | **Entry elements** | `<xs:element name="entry" minOccurs="0" maxOccurs="unbounded">` inside an `<xs:sequence>` | Each map entry is written as an `<entry>` element containing key and value children. |
| 6b.3 | **Entry content model** | `<xs:any minOccurs="0" maxOccurs="2" processContents="lax"/>` inside each entry | Map key and value types are unknown at schema generation time (the reference schema does not record generic type parameters). `xs:any` with `processContents="lax"` accepts any two child elements. This is the only place in the XSD where `xs:any` is used — see exception in 11.1. |
| 6b.4 | **`size` attribute** | `<xs:attribute name="size" type="xs:int"/>` on the wrapper's complexType | Same as collections (6.3) — the export writes a `size="N"` attribute on map wrappers. |
| 6b.5 | **DB4O GenericObject maps** | `exportGenericMapField()` handles `GenericObject`-wrapped Hashtables via `isMapByAncestry()` | DB4O may store Hashtable instances as `GenericObject` wrappers. The export detects this via schema ancestry and writes an empty map element (internal DB4O entries cannot be iterated). |
| 6b.6 | **Empty maps** | Written with `size="0"` or skipped via `skipWhen` conditions | Same skip logic as collections — `EMPTY_COLLECTION` and `NULL` conditions apply to maps. |

---

## 7. Complex (Non-Primitive, Non-Collection) Fields

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 7.1 | **Non-embedded IDEntite reference** (`embedContents=false`, `pointsTo` present) | `<xs:element name="fieldName" type="xs:long"/>` | When embedding is off, the export writes only the `mID` value as a scalar long. `xs:long` matches the `mID` type. |
| 7.2 | **Embedded IDEntite reference** (`embedContents=true`, `pointsTo` present) | Wrapped `xs:choice` element listing the pointed-to class and its descendants | The export resolves the IDEntite to its target entity and writes it inline. The `xs:choice` lists the target class and all its exported descendants. |
| 7.3 | **Non-IDEntite complex field, no subclasses** | `<fieldName><ChildType>…</ChildType></fieldName>` — wrapper element containing a single typed child | Wrapped typed element: the field is written as a wrapper containing one child element named after the type. |
| 7.4 | **Non-IDEntite complex field with subclasses** | Wrapped `xs:choice` listing base class and all exported descendants | Polymorphism is handled by explicitly listing all possible concrete types in an `xs:choice`, providing strict validation. |
| 7.5 | **Complex field, non-exported type** (`migrate=false`) | **Skipped with warning** | Fields whose type resolves to a class with `migrate=false` are omitted from the XSD with a logged warning. A non-exported class has no complexType, so referencing it would produce an unresolvable type. |
| 7.6 | **Complex field, type not in schema** | **Skipped with warning** | If the field type is not found in the reference schema, the field is omitted with a warning indicating a potential missing class. |
| 7.7 | **Primitive mapped to `embedContents` (java.lang.Class)** | `xs:string` simple element | Despite `isPrimitiveType()` returning true, when `embedContents=true` and the field type is `java.lang.Class`, the export writes a plain class name string. |

---

## 8. Wrapper Element Patterns

The XSD uses three distinct wrapper patterns for complex/embedded fields:

| # | Pattern | XSD Structure | Used When | Justification |
|---|---------|--------------|-----------|---------------|
| 8.1 | **Wrapped typed element** | `<xs:element name="field"><xs:complexType><xs:sequence><xs:element name="Type" type="Type" minOccurs="0"/></xs:sequence></xs:complexType></xs:element>` | Non-IDEntite complex fields without subclasses | The export writes `<field><Type>…</Type></field>`. One specific child element is expected. |
| 8.2 | **Wrapped choice element** | `<xs:element name="field"><xs:complexType><xs:choice minOccurs="0"><xs:element ref="Type1"/><xs:element ref="Type2"/>…</xs:choice></xs:complexType></xs:element>` | Polymorphic fields, embedded IDEntite references | When the concrete child element may be one of several types (base + descendants), `xs:choice` lists all possibilities for strict validation. |
| 8.3 | **Wrapped text element** | `<xs:element name="field" type="xs:string"/>` | `java.lang.Class` with `embedContents=true` | No wrapper needed — the export writes a plain string value directly inside the element. |

---

## 9. Full-Schema XSD Generation

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 9.1 | **Schema-driven generation** | The XSD is generated from the complete reference schema: all classes with `migrate=true` are included | Eliminates observation-based gaps. Every exported class has a type definition regardless of whether data existed in the current database. Deterministic output across different databases. |
| 9.2 | **No transitive discovery** | All types are known upfront from the reference schema | The iterative discovery loop is no longer needed. The reference schema defines the complete type universe — no types need to be "discovered" at runtime. |
| 9.3 | **No observation hooks** | `observeObject()`, `observeField()`, `observeReferencedClass()` hooks have been removed | The XSD is no longer built incrementally during export. All type information comes from the reference schema, making observation unnecessary. |
| 9.4 | **Descendant resolution** | `XSDContext.getAllExportedDescendants()` uses BFS over `parentClassName` | For polymorphic fields and collections, all exported descendants of a class are discovered by traversing the class hierarchy in the reference schema. |

---

## 10. Inheritance Handling — `xs:extension`

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 10.1 | **`xs:extension` for inherited classes** | Classes with an exported parent use `<xs:complexContent><xs:extension base="ParentType">` | Makes the inheritance hierarchy visible in the XSD. Inherited fields are defined in the parent type and extended with own fields. Reduces redundancy compared to the flattened approach. |
| 10.2 | **Own fields only in extended types** | `getOwnExportedFields()` returns only fields declared on this class (not inherited) | When using `xs:extension`, inherited fields must not be redeclared — they come from the base type. |
| 10.3 | **Flattened fields for root types** | Classes without an exported parent use `getAllExportedFieldsIncludingAncestors()` | When no parent type is available for extension, all fields (including inherited) are flattened into the type. |
| 10.4 | **Child overrides parent** | Root-first merge: child field with same `destinationName` replaces ancestor's field | If a child class redefines a field (different type, different export settings), the child definition wins. |
| 10.5 | **Exported parent detection** | `getExportedParent()` checks if the parent class exists and has `migrate=true` | Extension is only used when the parent class is also exported (has a type definition in the XSD). Non-exported parents are skipped, and fields are flattened instead. |
| 10.6 | **Subclass detection for polymorphism** | `hasAnySubclass()` checks if any class in the schema has `parentClassName` equal to the target class's source name | Used to decide between typed elements and `xs:choice` groups for polymorphic fields. |

---

## 11. XSD Composition & Validation

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 11.1 | **No `xs:any` wildcards (except maps)** | All polymorphic fields use `xs:choice` with explicit element refs. Map entry contents (6b.3) are the sole exception, using `xs:any` because key/value types are unknown at schema time. | Strict validation — every possible element is declared. The only `xs:any` usage is inside map `<entry>` elements where generic type parameters are unavailable from the reference schema. |
| 11.2 | **Post-export validation** | `XMLValidator.validateMultiple()` validates all exported XML files against the generated XSD | Catches XSD/XML mismatches before the user consumes the data. Uses `javax.xml.validation.Validator` with a custom error handler for detailed line/column error reporting. |
| 11.3 | **XML escaping in XSD** | `XSDTypeMapper.escapeXml()` escapes `&`, `<`, `>`, `"`, `'` in documentation text and enumeration values | Prevents broken XSD syntax from special characters in schema descriptions and value map entries. |

---

## 12. Sorted Field Output

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 12.1 | **Alphabetical field sorting in XML** | `FieldExporter.sortFieldsByDestinationName()` sorts `StoredField[]` by `DOSchemaField.destinationName` before export | Guarantees a deterministic, reviewable field order in every exported XML file. Required for `xs:sequence` validation to work correctly. |
| 12.2 | **Unmapped fields skipped** | Fields without a matching `DOSchemaField` (null) are skipped in both export and count | No `"unknown"` element fallback. Only mapped fields with valid destination names are exported. |

---

## 13. ISO Date Formatting

| # | Criterion | Current Implementation | Justification |
|---|-----------|----------------------|---------------|
| 13.1 | **Date formatting** | `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")` applied to `java.util.Date` instances | Produces ISO 8601 formatted date strings, enabling `xs:dateTime` validation. Applied in both `exportRegularField()` and `exportFieldValue()`. |
| 13.2 | **instanceof Date check** | `value instanceof Date` guard before formatting | Ensures date formatting is only applied to actual Date objects, not strings or other types. |

---

## 14. Error Handling

| # | Area | Behaviour | Justification |
|---|------|-----------|---------------|
| 14.1 | **Null/empty field type** | Skipped with warning in `XSDFieldWriter` (`XSDTypeMapper` still throws) | Fields with null or empty types are omitted from the XSD with a warning log. This can happen for inherited DB4O internal fields. `XSDTypeMapper` still throws for null/empty types as a safety net for unexpected callers. |
| 14.2 | **Unknown collection child type** | `IllegalStateException` thrown in `XSDFieldWriter` | Collection fields must reference a type that exists in the reference schema. |
| 14.3 | **Unknown embedded field type** | Skipped with warning in `XSDFieldWriter` | Embedded fields referencing a type not found in the reference schema are omitted with a warning. May indicate a missing class in the reference schema. |
| 14.4 | **Unknown complex field type** | Skipped with warning in `XSDFieldWriter` | Complex field types not found in the reference schema are omitted with a warning. No `xs:anyType` fallback. May indicate a missing class in the reference schema. |
| 14.5 | **Non-exported field type** | Skipped with warning in `XSDFieldWriter` | Fields referencing a class with `migrate=false` are omitted. Applies to direct types, collection children types, and embedded IDEntite `pointsTo` targets. |
