# Data Interpretation

How we interpret the values we read from DB4O objects — primitives, IDEntite references, EntiteContientID entities, and special types.

## Type Classification

Every value read from a `StoredField.get(obj)` falls into one of these categories:

| Category | How to detect | Interpretation |
|----------|--------------|----------------|
| Null | `value == null` | Empty field |
| Java primitive wrapper | `getID(value) <= 0` and not GenericObject | Leaf value; export as string |
| Persistent object | `getID(value) > 0` | Another DB4O object; classify further |
| Collection | `instanceof Collection` or schema `isCollection` | Container of items; extract and interpret each |
| GenericObject proxy for collection | `instanceof GenericObject` + schema `isCollection` | Extract via translator fields |
| byte[] | `instanceof byte[]` | Binary data; export as Base64 |
| Array (non-byte) | `value.getClass().isArray()` | Ordered list of items |
| Class reference | Schema type is `java.lang.Class` or `Class` | Class name string |

Detection code: `FieldExporter.exportAllFields()`, `FieldExporter.exportRegularField()`, `FieldExporter.exportFieldValue()`

## Primitive / Leaf Values

- Detected when `container.ext().getID(fieldValue)` returns `<= 0`
- Includes: `String`, `Long`, `Integer`, `Double`, `Float`, `Boolean`, `Date`, `BigDecimal`, etc.
- Full list of types considered primitive → `TypeUtil.isPrimitiveType()`
  - Java primitives: boolean, byte, char, short, int, long, float, double
  - Java wrappers: `java.lang.String`, `Integer`, `Long`, `Double`, `Float`, `Boolean`, `Character`, `Byte`, `Short`
  - Math types: `BigDecimal`, `BigInteger`
  - Date/time types: `java.util.Date`, SQL date/time/timestamp, Java 8 LocalDate/LocalTime/LocalDateTime/ZonedDateTime
  - `java.util.UUID`
- **Formatting** applied before export → `ValueUtil.formatFieldValue(value, schemaField)`:
  - `TRIM` — trims whitespace
  - `LOWERCASE` — converts to lowercase
  - `UPPERCASE` — converts to uppercase
  - Multiple keywords can be comma-separated in `DOSchemaField.format`
- **Value mapping** replaces raw values with configured values → `FieldValueMapper.applyMapping()`
  - `DOSchemaField.valueMap` is a `Map<String, String>` (raw → mapped)
  - If no mapping exists for the value, the original is kept

## The gest.gen.IDEntite Pattern

IDEntite is a reference object pattern used extensively in the source database.

### What It Is
- An IDEntite object is a lightweight reference stored as a field value
- Contains an `mID` field (long) that references the actual entity
- The mID is **not** a DB4O object ID; it's an application-level identifier
- Example: a `Vehicule` has a field `mIDTypeVehicule` of type `gest.gen.IDTypeVehicule`, which contains `mID = 42` pointing to the `TypeVehicule` instance whose `mID` is also `42`

### How We Detect It
- `DOSchemaClass.isIDEntite(schema)` → checks `isDescendantOf("gest.gen.IDEntite", schema)` → `SchemaUtil.isDescendantOf()`
- Inheritance check walks up `parentClassName` chain

### How We Extract the mID
- `IDEntityHandler.extractMID(container, idEntiteObject)` → `ReferenceUtil.extractMIDField()`
- Process:
  1. Cast to `GenericObject`
  2. **Deep activate** (`Integer.MAX_VALUE`) — critical because mID may be inherited from a deep ancestor
  3. Get `StoredClass` → `getStoredFields()` → find field named `"mID"`
  4. `field.get(genericObj)` → return as `Long`
- Fallback: if `MAX_VALUE` causes `StackOverflowError`, retry with depth 10

### Special mID Values
- `mID == -1` → invalid/placeholder reference (no real target) → `IDEntityHandler.isInvalidMID()`
- `mID > 0` → valid reference → `IDEntityHandler.isValidMID()`
- `mID == 0` → ambiguous; treated as potentially valid

### Two Export Modes for IDEntite Fields

Controlled by `DOSchemaField.embedContents`:

#### Mode 1: Scalar Reference (`embedContents = false`, the default for IDEntite)
- Export just the mID value as inline text → `FieldExporter.exportRegularField()`
- The IDEntite object itself is counted as "reached" but not traversed
- Example output: `<typeVehicule>42</typeVehicule>`

#### Mode 2: Embedded Target (`embedContents = true`)
- Resolve the mID to find the actual target entity → `ReferenceObjectExporter.resolveAndExport()`
- `ReferenceUtil.resolveIDEntiteForExport()` → `ReferenceUtil.resolveIDEntiteReference()` → `ReferenceUtil.findObjectByMID()`
- Search process:
  1. Extract target type name from field name (e.g., `mIDTypeVehicule` → `TypeVehicule`) → `ReferenceUtil.extractExpectedTypeFromFieldName()`
  2. Scan all `EntiteContientID` descendant classes in the database schema
  3. For each matching class, iterate all objectIds, activate, and compare mID
  4. Return the object ID of the first match
- The resolved target object is then exported recursively (full traversal)

### IDEntite in Collections

- When a collection field has `embedContents = false` and `childrenType` is set:
  - `IDReferenceDetector.detectIDReference()` finds the corresponding ID class via `IDClassResolver.findIDClass()`
  - `IDClassResolver` searches all classes where `pointsTo` equals the `childrenType`
  - Each item is exported as a synthetic ID wrapper with the entity's DB4O object ID → `IDReferenceExporter.exportAsIDReference()`
  - The actual entity is also exported separately (non-embedded)

## The gest.gen.EntiteContientID Pattern

### What It Is
- An Entite (EntiteContientID descendant) is a full domain entity that "contains an ID"
- Detected by `DOSchemaClass.isEntite(schema)` → `isDescendantOf("gest.gen.EntiteContientID", schema)`
- Entities have their own `mID` field and are the **targets** of IDEntite references
- Example: `Vehicule` extends `EntiteContientID`, has `mID = 42`

### Role in Navigation
- When resolving an IDEntite reference with `embedContents = true`, we search only among `EntiteContientID` descendants → `ReferenceUtil.findObjectByMID()`
- The `pointsTo` field on IDEntite schema classes links to the entity class name (e.g., `IDVehicule.pointsTo = "Vehicule"`)

## The gest.gen.EntiteParam Pattern

- Detected by `DOSchemaClass.isParam(schema)` → `isDescendantOf("gest.gen.EntiteParam", schema)`
- "Param" entities are parameter/lookup tables, a subtype of entity

## Class References

- Some fields store `java.lang.Class` objects (the actual Java class, not an instance)
- Schema type: `java.lang.Class` or `Class`
- DB4O may wrap them as GenericObject → extract via `toString()`, strip `"class "` prefix
- Always exported as a string (the class name) → `FieldExporter.exportRegularField()`

## Binary Data (byte[])

- Detected by `instanceof byte[]`
- Converted to Base64 string → `Base64.getEncoder().encodeToString()`
- Empty byte arrays may be skipped based on skip conditions
- **Not** treated as a collection (explicitly excluded in `RecipeCollectionItems`)

## Collections Interpretation

- After extraction (see [Data Navigation](data-navigation.md)), each item is interpreted independently
- Size is recorded as XML attribute (when format supports it)
- Empty collections may be skipped per skip conditions
- Items that are IDEntite references may be batch-exported as ID references

## Value Emptiness Rules

`ValueUtil.isEmpty()` defines what "empty" means (used for collection/array emptiness checks):

- `null` → empty
- `String` → empty if `trim()` yields `""`
- `Collection` → empty if `.isEmpty()`
- Array → empty if length is 0
