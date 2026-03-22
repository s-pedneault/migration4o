# Schema Field Actions — API Inventory

A `DOSchemaField` is the export configuration for one field. It describes how a database field should be read, filtered, transformed, and written. It carries no DB4O runtime state — it holds only configuration that drives how the database field API operates.

---

## 1. Identity & Metadata

| Action | Description |
|---|---|
| **getSourceName** | Return the raw database field name this schema field targets (`source`). For virtual fields, this includes the `@` prefix. |
| **getDestinationName** | Return the mapped export element name (`destinationName`). Used as the XML tag name in output. |
| **getTypeName** | Return the declared type name of the field (e.g., `long`, `java.util.Vector`, `gest.gen.IDVehicule`). Drives type resolution and classification. |
| **getTitle** | Return the human-readable label for this field, used in the UI and documentation. |
| **getDescription** | Return the extended description of the field's semantic purpose. |
| **getParentClass** | Return the `DOSchemaClass` that declares this field. |

---

## 2. Behavioral Flags

| Action | Description |
|---|---|
| **isExported** | Return whether this field is included in the export. Fields with `isExported = false` are silently skipped. |
| **isCollection** | Return whether this field holds a collection of child objects. Drives the collection extraction path in the database field API. |
| **isVirtualField** | Return whether this is a virtual field (source starts with `@`). Virtual fields have no `StoredField` counterpart; their value is computed from criteria queries. |
| **isSharedField** | Return whether this field delegates its configuration to a shared field definition (`definitionId != null`). |
| **embedContents** | Return whether the referenced object's fields should be inlined (`true`) or the reference should be exported as a scalar ID (`false`). Applies to both IDEntite references and collection children. |

---

## 3. Skip Evaluation

| Action | Description |
|---|---|
| **shouldSkip** | Evaluate `skipWhen` conditions against a runtime value and return whether the field should be omitted from output. Supported conditions: `NULL`, `ZERO`, `MINUS_ONE`, `EMPTY_STRING`, `EMPTY_COLLECTION`, `FALSE`. |
| **shouldSkipByUserOption** | Return whether the field should be skipped because the user has selected it in the runtime skip-options list (`skipUserOption`). |
| **matchesSkipCondition** | Test whether a given value matches a specific named condition keyword. Lower-level building block for `shouldSkip`. |

---

## 4. Value Formatting

| Action | Description |
|---|---|
| **formatValue** | Apply the format transformation pipeline (`format`) to a string value. Supported keywords (comma-separated): `TRIM`, `UPPERCASE`, `LOWERCASE`. Unknown keywords are silently ignored. |

---

## 5. Value Mapping

| Action | Description |
|---|---|
| **mapValue** | Translate a raw database value to its export representation using `valueMap` (`DOSchemaValueMap`). Returns the mapped string if a match exists, or the original value if not. |
| **hasValueMap** | Return whether a non-empty value map is configured (`valueMap != null && !valueMap.isEmpty()`). |
| **getValueMap** | Return the `DOSchemaValueMap` instance for direct inspection or editing (e.g., in the field editor UI). |

---

## 6. Collection Configuration

| Action | Description |
|---|---|
| **getChildrenType** | Return the declared type name of elements inside the collection (`childrenType`). Used by the database field API to guide item processing. |
| **getChildrenSchemaClass** | Return the linked `DOSchemaClass` for collection elements (`childrenSchemaClass`). Resolved at schema-load time via class name lookup. |

---

## 7. Reference Configuration

| Action | Description |
|---|---|
| **getPointsTo** | Return the explicit target class name for an IDEntite reference (`pointsTo`). Used as a hint during reference resolution when the target type cannot be inferred from the field name. |

---

## 8. Virtual Field Configuration

| Action | Description |
|---|---|
| **getVirtualFieldName** | Return the actual field name for a virtual field — the source name with the `@` prefix stripped. |
| **getCriterias** | Return the list of `DOFieldCriteria` query conditions that define this virtual field. Each criterion has a `match` field reference, a `with` comparison target, and an `operator` (`equals`, `notEquals`, `greaterThan`, `lessThan`, etc.). |
| **getCriteriasOperator** | Return the logical operator (`AND` or `OR`) that combines multiple criteria. Defaults to `AND`. |

---

## 9. Shared Definition

| Action | Description |
|---|---|
| **getDefinitionId** | Return the shared field definition ID that this field references. When set, the field's configuration is sourced from a reusable definition block rather than declared inline. |

---

## 10. Lifecycle

| Action | Description |
|---|---|
| **copy** | Produce a deep copy of this schema field — duplicating criterias and valueMap — used when instantiating shared field definitions into a specific class context. |
