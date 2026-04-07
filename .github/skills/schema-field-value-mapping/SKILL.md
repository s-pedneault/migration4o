---
name: schema-field-value-mapping
description: Translate raw database values to export values using the DOSchemaValueMap configured on a DOSchemaField. Use this skill when applying value substitutions during export (e.g. mapping codes to labels).
---

# DOSchemaField — Value Mapping

The mapping object is `DOSchemaValueMap`, stored on `field.valueMap`.

## Field / method map

| What you want | How to get it |
|---|---|
| Translate a raw DB value | `field.attributes.valueMap.getMappedValue(rawString)` — returns original if no match |
| Is a value map configured? | `field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()` |
| Inspect / edit the map | `field.attributes.valueMap` direct field access |

## DOSchemaValueMap key methods

```java
// Translate — safe to call; returns input unchanged when no mapping exists
String exported = valueMap.getMappedValue(databaseValue);

// Add or replace a mapping
valueMap.add(fromValue, toValue);

// Check contents
boolean hasEntries = !valueMap.isEmpty();

// Create from a plain Map<String,String>
DOSchemaValueMap vm = DOSchemaValueMap.copyOf(existingMap); // null when map is null/empty
```

## Full export pipeline (correct ordering)

```java
// 1. Skip evaluation
if (ValueUtil.shouldSkipField(rawValue, field, schema)) continue;

// 2. Format pipeline
String s = ValueUtil.formatFieldValue(rawValue, field);

// 3. Value mapping
if (field.attributes.valueMap != null && !field.attributes.valueMap.isEmpty()) {
    s = field.attributes.valueMap.getMappedValue(s);
}

// 4. Write
writer.writeField(field.destinationName, s);
```

## Notes
- `field.attributes.valueMap` is `null` for the vast majority of fields — this is the common case.
- `getMappedValue` passes through unmapped values unchanged, so it is safe to call unconditionally after a null check.
- Insertion order is preserved (backed by `LinkedHashMap`).

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaValueMap.java`
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
